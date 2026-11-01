"""
RAG 问答服务层：把「检索 → 生成」的完整流程收敛到一处。

站内 AI 浮窗（rag/routes.py）与对外 API（blueprints/api_v1.py）都调用这里，
避免同一套逻辑写两遍、两处行为不一致。
author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import logging
import time

import config
import llm
from retriever import KnowledgeBaseEmpty, format_sources, retriever

logger = logging.getLogger(__name__)


class ServiceError(RuntimeError):
    """可预期的服务层错误（向量库为空、只读等），调用方应转成 4xx/5xx 响应。"""


def retrieve_contexts(question: str):
    """统一检索入口，把底层异常翻译成可读错误。"""
    try:
        return retriever.retrieve(question)
    except KnowledgeBaseEmpty as exc:
        raise ServiceError(str(exc)) from exc
    except Exception as exc:  # 向量库损坏 / 只读等
        logger.exception("检索失败")
        message = str(exc)
        if "readonly" in message.lower():
            message = f"向量库目录不可写：{config.DB_DIR}。请在 .env 中把 DB_DIR 指向可写目录。"
        raise ServiceError(message) from exc


def search(question: str) -> dict:
    """只做检索，不调用大模型（适合做「有没有相关资料」的轻量探测）。"""
    started = time.time()
    contexts = retrieve_contexts(question)
    sources = format_sources(contexts)
    return {
        "sources": sources,
        "from_knowledge_base": bool(contexts),
        "elapsed": round(time.time() - started, 2),
    }


def answer_question(question: str, history: list[dict] | None = None) -> dict:
    """检索 + 生成，返回统一结构。

    {
      "answer": str,
      "sources": [...],
      "from_knowledge_base": bool,
      "usage": {...} | None,
      "elapsed": float
    }
    """
    started = time.time()
    contexts = retrieve_contexts(question)
    sources = format_sources(contexts)

    if not contexts:
        return {
            "answer": config.NO_CONTEXT_REPLY,
            "sources": [],
            "from_knowledge_base": False,
            "usage": None,
            "elapsed": round(time.time() - started, 2),
        }

    try:
        result = llm.complete(question, contexts, history or [])
    except llm.LLMNotConfigured as exc:
        raise ServiceError(str(exc)) from exc
    except Exception as exc:
        logger.exception("调用大模型失败")
        raise ServiceError(f"云端模型调用失败：{exc}") from exc

    return {
        "answer": result["answer"],
        "sources": sources,
        "from_knowledge_base": True,
        "usage": result.get("usage"),
        "elapsed": round(time.time() - started, 2),
    }
