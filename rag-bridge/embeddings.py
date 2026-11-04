"""
本地模型单例。

BGE-M3 与本项目的重排模型体积都不小（合计数 GB），
Web 服务里必须保证**进程内只加载一份**，否则内存会迅速翻倍。

性能优化（v2）：
- 重排模型可选 ONNX fp32 推理（onnxruntime），约 1.33× 加速、排序完全一致；
  加载失败自动回退到 FlagEmbedding（PyTorch）。
author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import logging
import threading
from pathlib import Path

import config  # noqa: F401  （导入即设置 HF_ENDPOINT）

logger = logging.getLogger(__name__)

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
            logger.exception("模型预热失败")

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


class _OnnxReranker:
    """基于 onnxruntime 的交叉编码器重排器（fp32，排序结果与原始模型一致）。"""

    def __init__(self, session, tokenizer, max_length: int = 512):
        self.session = session
        self.tokenizer = tokenizer
        self.max_length = max_length
        self._input_names = [i.name for i in session.get_inputs()]

    def compute_score(self, pairs, batch_size: int = 8, max_length: int | None = None, **kwargs):
        """对 [(query, doc), ...] 打分，返回与 FlagReranker(默认 normalize=False) 一致的 logits。"""
        import numpy as np

        ml = max_length or self.max_length
        scores: list[float] = []
        for i in range(0, len(pairs), batch_size):
            batch = pairs[i:i + batch_size]
            queries = [p[0] for p in batch]
            docs = [p[1] for p in batch]
            encoded = self.tokenizer(
                queries, docs,
                padding=True, truncation=True, max_length=ml, return_tensors="np",
            )
            inputs = {name: encoded[name] for name in self._input_names if name in encoded}
            if not inputs:  # 兜底：至少喂 input_ids / attention_mask
                inputs = {k: v for k, v in encoded.items() if k in ("input_ids", "attention_mask")}
            logits = self.session.run(None, inputs)[0]
            logits = np.asarray(logits).reshape(len(batch), -1)[:, 0]
            scores.extend(logits.astype("float32").tolist())
        return scores


def _load_onnx_reranker():
    """尝试加载 ONNX 重排器；失败返回 None（调用方回退 PyTorch）。"""
    path = getattr(config, "RERANK_ONNX_PATH", "") or ""
    if not path or not Path(path).exists():
        return None
    try:
        import onnxruntime as ort
        from transformers import AutoTokenizer

        tokenizer = AutoTokenizer.from_pretrained(config.RERANK_MODEL)
        session = ort.InferenceSession(
            path, providers=["CPUExecutionProvider"]
        )
        return _OnnxReranker(session, tokenizer, max_length=getattr(config, "RERANK_MAX_LENGTH", 512) or 512)
    except Exception as exc:  # pragma: no cover
        logger.warning("ONNX 重排器加载失败，回退 PyTorch：%s", exc)
        return None


def _load_flag_reranker():
    from FlagEmbedding import FlagReranker

    config.apply_torch_threads()
    return FlagReranker(
        config.RERANK_MODEL,
        use_fp16=config.USE_FP16,
        devices="cpu",
        batch_size=config.RERANK_BATCH_SIZE,
    )


def get_reranker():
    """惰性加载重排模型；未启用时返回 None。

    后端选择（RERANK_BACKEND）：
      onnx  —— 强制 ONNX，失败则回退 PyTorch；
      torch —— 强制 FlagEmbedding（PyTorch）；
      auto  —— 配置了 RERANK_ONNX_PATH 则优先 ONNX，否则 PyTorch。
    """
    global _reranker
    if not config.RERANK_ENABLED:
        return None
    if _reranker is None:
        with _lock:
            if _reranker is None:
                backend = getattr(config, "RERANK_BACKEND", "auto") or "auto"
                if backend in ("onnx", "auto"):
                    _reranker = _load_onnx_reranker()
                if _reranker is None:
                    _reranker = _load_flag_reranker()
    return _reranker
