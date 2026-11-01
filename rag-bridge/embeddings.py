"""
本地模型单例。

BGE-M3 与本项目的重排模型体积都不小（合计数 GB），
Web 服务里必须保证**进程内只加载一份**，否则内存会迅速翻倍。
author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import logging
import threading

import config  # noqa: F401  （导入即设置 HF_ENDPOINT）

_lock = threading.Lock()
_embed_model = None
_reranker = None


def embed_ready() -> bool:
    return _embed_model is not None


def rerank_ready() -> bool:
    return _reranker is not None


def warm_up(background: bool = True) -> None:
    """预热模型：Web 服务启动后调用，避免第一次提问卡住十几秒。"""
    def _load():
        try:
            get_embed_model()
            get_reranker()
        except Exception:  # pragma: no cover - 预热失败不应影响服务启动
            logging.getLogger(__name__).exception("模型预热失败")

    if background:
        threading.Thread(target=_load, name="model-warmup", daemon=True).start()
    else:
        _load()


def get_embed_model():
    """惰性加载向量模型（BAAI/bge-m3）。"""
    global _embed_model
    if _embed_model is None:
        with _lock:
            if _embed_model is None:
                from sentence_transformers import SentenceTransformer

                config.apply_torch_threads()
                _embed_model = SentenceTransformer(config.EMBED_MODEL, device="cpu")
    return _embed_model


def get_reranker():
    """惰性加载重排模型；未启用时返回 None。"""
    global _reranker
    if not config.RERANK_ENABLED:
        return None
    if _reranker is None:
        with _lock:
            if _reranker is None:
                from FlagEmbedding import FlagReranker

                config.apply_torch_threads()
                _reranker = FlagReranker(
                    config.RERANK_MODEL,
                    use_fp16=config.USE_FP16,
                    devices="cpu",
                    batch_size=config.RERANK_BATCH_SIZE,
                )
    return _reranker
