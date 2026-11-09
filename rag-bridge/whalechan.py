"""
鲸鱼娘对话服务：本地检索 + 角色卡生成。

- 有相关资料：依据资料 + 角色卡回答（rag 模式）
- 无相关资料：直接用角色卡自由聊天（free 模式），不会甩出冷冰冰的“知识库中没有…”
"""
from __future__ import annotations

import logging
import time

import config
import llm
from retriever import format_sources
from service import retrieve_contexts

logger = logging.getLogger("whalechan")


def chat(question: str, history: list[dict] | None = None, player_context: str | None = None) -> dict:
    started = time.time()
    history = history or []

    system = config.PERSONA_PROMPT
    if player_context:
        system = system + "\n\n【主人当前信息】（回答问题时参考，不要逐字复述）：\n" + player_context

    contexts = retrieve_contexts(question)
    sources = format_sources(contexts)

    if contexts:
        answer = llm.complete_with_system(system, question, contexts, history)
        return {
            "answer": answer,
            "sources": sources,
            "from_knowledge_base": True,
            "mode": "rag",
            "elapsed": round(time.time() - started, 2),
        }

    answer = llm.chat_with_system(system, question, history)
    return {
        "answer": answer,
        "sources": [],
        "from_knowledge_base": False,
        "mode": "free",
        "elapsed": round(time.time() - started, 2),
    }
