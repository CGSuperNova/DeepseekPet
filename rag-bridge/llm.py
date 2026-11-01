"""
云端大模型调用：把检索到的片段拼成提示词，请求 DeepSeek 生成答案。

- 只把 Top-K 片段发到云端，原始文档与向量不出本地；
- 支持一次性返回与流式返回两种模式；
- 兼容 deepseek-reasoner 这类只返回 reasoning_content 的模型。

author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import logging
from typing import Iterable, Iterator

from openai import OpenAI

import config

logger = logging.getLogger(__name__)

_client: OpenAI | None = None


class LLMNotConfigured(RuntimeError):
    """未配置 API Key。"""


def get_client() -> OpenAI:
    global _client
    if not config.DEEPSEEK_API_KEY:
        raise LLMNotConfigured(
            "未配置 DEEPSEEK_API_KEY，请在项目根目录的 .env 中填写后重启服务。"
        )
    if _client is None:
        _client = OpenAI(
            api_key=config.DEEPSEEK_API_KEY,
            base_url=config.DEEPSEEK_BASE_URL,
            timeout=config.LLM_TIMEOUT,
        )
    return _client


def build_messages(question: str, contexts: list[dict], history: Iterable[dict] | None = None) -> list[dict]:
    """把检索片段、历史对话与当前问题拼成 chat messages。"""
    lines = []
    for i, item in enumerate(contexts, 1):
        meta = item.get("meta") or {}
        source = meta.get("source", "未知")
        chunk = meta.get("chunk", "?")
        lines.append(f"[{i}] 来源：{source}（第 {chunk} 块）\n{item.get('doc', '')}")
    context_text = "\n\n".join(lines) or "（没有检索到相关资料）"

    messages: list[dict] = [{"role": "system", "content": config.SYSTEM_PROMPT}]

    for turn in list(history or [])[-config.MAX_HISTORY_TURNS * 2:]:
        role = turn.get("role")
        content = (turn.get("content") or "").strip()
        if role in ("user", "assistant") and content:
            messages.append({"role": role, "content": content})

    messages.append({
        "role": "user",
        "content": f"资料：\n{context_text}\n\n问题：{question}",
    })
    return messages


def _extract_text(choice) -> str:
    """取回复正文，reasoner 类模型可能只有 reasoning_content。"""
    message = choice.message
    content = (message.content or "").strip()
    if content:
        return content
    return (getattr(message, "reasoning_content", "") or "").strip()


def complete(question: str, contexts: list[dict], history: Iterable[dict] | None = None) -> dict:
    """一次性生成答案。"""
    client = get_client()
    messages = build_messages(question, contexts, history)
    resp = client.chat.completions.create(
        model=config.DEEPSEEK_MODEL,
        messages=messages,
        temperature=config.LLM_TEMPERATURE,
        max_tokens=config.LLM_MAX_TOKENS,
    )
    choice = resp.choices[0]
    answer = _extract_text(choice)
    if not answer:
        answer = "模型未返回有效内容，请重试或换用 deepseek-chat。"
    usage = getattr(resp, "usage", None)
    return {
        "answer": answer,
        "finish_reason": choice.finish_reason,
        "usage": {
            "prompt_tokens": getattr(usage, "prompt_tokens", None),
            "completion_tokens": getattr(usage, "completion_tokens", None),
            "total_tokens": getattr(usage, "total_tokens", None),
        } if usage else None,
    }


def stream(question: str, contexts: list[dict], history: Iterable[dict] | None = None) -> Iterator[dict]:
    """
    流式生成答案，逐个产出事件：
        {"type": "delta", "text": "..."}      正文增量
        {"type": "reasoning", "text": "..."}  思维链增量（reasoner 模型）
        {"type": "usage", "usage": {...}}     结束时用量
    """
    client = get_client()
    messages = build_messages(question, contexts, history)

    response = client.chat.completions.create(
        model=config.DEEPSEEK_MODEL,
        messages=messages,
        temperature=config.LLM_TEMPERATURE,
        max_tokens=config.LLM_MAX_TOKENS,
        stream=True,
        stream_options={"include_usage": True},
    )

    for chunk in response:
        usage = getattr(chunk, "usage", None)
        if usage:
            yield {"type": "usage", "usage": {
                "prompt_tokens": getattr(usage, "prompt_tokens", None),
                "completion_tokens": getattr(usage, "completion_tokens", None),
                "total_tokens": getattr(usage, "total_tokens", None),
            }}
        if not getattr(chunk, "choices", None):
            continue
        delta = chunk.choices[0].delta
        if delta is None:
            continue
        reasoning = (getattr(delta, "reasoning_content", None) or "")
        if reasoning:
            yield {"type": "reasoning", "text": reasoning}
        text = (getattr(delta, "content", None) or "")
        if text:
            yield {"type": "delta", "text": text}


def chat_free(system: str, user: str, temperature: float | None = None) -> str:
    """自由对话（不经过检索）：用于 AI 招商建议等场景，返回纯文本。"""
    client = get_client()
    resp = client.chat.completions.create(
        model=config.DEEPSEEK_MODEL,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        temperature=config.LLM_TEMPERATURE if temperature is None else temperature,
        max_tokens=config.LLM_MAX_TOKENS,
    )
    return _extract_text(resp.choices[0]).strip() or "模型未返回内容。"


# ------------------------------------------------------------------ 鲸鱼娘角色卡对话
def _build_messages(system: str, question: str, contexts: list[dict] | None,
                    history: Iterable[dict] | None, with_context: bool) -> list[dict]:
    """构造 messages：system 用自定义角色卡，可选拼接检索资料。"""
    messages: list[dict] = [{"role": "system", "content": system}]

    for turn in list(history or [])[-config.MAX_HISTORY_TURNS * 2:]:
        role = turn.get("role")
        content = (turn.get("content") or "").strip()
        if role in ("user", "assistant") and content:
            messages.append({"role": role, "content": content})

    if with_context and contexts:
        lines = []
        for i, item in enumerate(contexts, 1):
            meta = item.get("meta") or {}
            source = meta.get("source", "未知")
            chunk = meta.get("chunk", "?")
            lines.append(f"[{i}] 来源：{source}（第 {chunk} 块）\n{item.get('doc', '')}")
        context_text = "\n\n".join(lines)
        messages.append({
            "role": "user",
            "content": f"资料：\n{context_text}\n\n问题：{question}",
        })
    else:
        messages.append({"role": "user", "content": question})
    return messages


def complete_with_system(system: str, question: str, contexts: list[dict],
                         history: Iterable[dict] | None = None) -> str:
    """带检索资料 + 自定义 system（角色卡）生成答案。"""
    client = get_client()
    messages = _build_messages(system, question, contexts, history, with_context=True)
    resp = client.chat.completions.create(
        model=config.DEEPSEEK_MODEL,
        messages=messages,
        temperature=config.LLM_TEMPERATURE,
        max_tokens=config.LLM_MAX_TOKENS,
    )
    return _extract_text(resp.choices[0]).strip() or "模型未返回内容。"


def chat_with_system(system: str, question: str, history: Iterable[dict] | None = None) -> str:
    """自由聊天 + 自定义 system（角色卡），不经过检索。"""
    client = get_client()
    messages = _build_messages(system, question, None, history, with_context=False)
    resp = client.chat.completions.create(
        model=config.DEEPSEEK_MODEL,
        messages=messages,
        temperature=config.LLM_TEMPERATURE,
        max_tokens=config.LLM_MAX_TOKENS,
    )
    return _extract_text(resp.choices[0]).strip() or "模型未返回内容。"
