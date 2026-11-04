"""
混合检索：向量检索 + BM25 关键词检索 → RRF 融合 → 重排 → 阈值过滤。

性能优化（v2）：
- BM25 由 rank-bm25 换成 bm25s（SciPy 稀疏矩阵 + 内存映射，快 1~2 个数量级）；
- 不再把全量文档 / 元数据拉进内存：向量走 collection.query，BM25 走 bm25s.retrieve，
  只按 top-k 从向量库取命中文档；
- 查询级 LRU 缓存（精确命中整条链路直接返回）+ 语义缓存（相似问题复用）；
- 重排串行化，避免多请求并发时线程超订。

author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import hashlib
import json
import logging
import re
import threading
import time
from collections import OrderedDict
from pathlib import Path

import jieba
import numpy as np
import chromadb

import config
from embeddings import get_embed_model, get_reranker

logger = logging.getLogger(__name__)

# 让 BM25 对 Minecraft / 插件类专有名词更敏感
for _word in (
    "CustomDrops", "PlaceholderAPI", "Placeholder", "Vault", "Paper",
    "Spigot", "Bukkit", "bStats", "snakeyaml", "Maven", "JDK",
    "COAL_ORE", "DIAMOND_ORE", "时运", "精准采集", "占位符", "掉落物",
    "更新检测", "权限节点", "语义化版本", "撤回", "重排", "向量检索",
):
    jieba.add_word(_word)

_CJK_RE = re.compile(r"[\u4e00-\u9fff]")

# 中文常见疑问词与虚词，BM25 里属于噪声
_STOPWORDS = {
    "什么", "怎么", "如何", "为什么", "哪里", "哪些", "是否", "可以", "有没有",
    "请问", "告诉", "一下", "这个", "那个", "我们", "你们", "他们", "以及",
    "的话", "了吗", "呢", "吗", "的", "了", "是", "在", "和", "与", "或",
    "a", "an", "the", "is", "are", "how", "what", "why", "where", "to", "of",
}


def tokenize(text: str) -> list[str]:
    """中英混排分词：中文走 jieba，英文/数字按词切，去掉停用词与空白。"""
    text = (text or "").lower()
    tokens: list[str] = []
    for token in jieba.cut(text):
        token = token.strip()
        if not token or token in _STOPWORDS:
            continue
        if _CJK_RE.search(token):
            tokens.append(token)
        else:
            tokens.extend(t for t in re.split(r"[^a-z0-9_.\-]+", token) if t and t not in _STOPWORDS)
    return tokens


class KnowledgeBaseEmpty(RuntimeError):
    """向量库为空或尚未建库。"""


# ---------------------------------------------------------------- 重排结果缓存
# 键为「问题 + 片段内容哈希」：同一问题重复提问、或多轮对话里反复用到同一片段时，
# 可直接复用上次的分数，省掉整段重排。
_rerank_cache: "OrderedDict[str, float]" = OrderedDict()


def _rerank_key(query: str, doc: str) -> str:
    digest = hashlib.sha1(doc.encode("utf-8", "ignore")).hexdigest()[:16]
    return f"{query}\x00{digest}"


def _cache_get(key: str):
    if key in _rerank_cache:
        _rerank_cache.move_to_end(key)
        return _rerank_cache[key]
    return None


def _cache_put(key: str, value: float) -> None:
    size = getattr(config, "RERANK_CACHE_SIZE", 0) or 0
    if size <= 0:
        return
    _rerank_cache[key] = value
    _rerank_cache.move_to_end(key)
    while len(_rerank_cache) > size:
        _rerank_cache.popitem(last=False)


def rerank_cache_info() -> dict:
    """给状态接口/运维看的重排缓存概况。"""
    return {
        "entries": len(_rerank_cache),
        "size": getattr(config, "RERANK_CACHE_SIZE", 0) or 0,
        "max_length": getattr(config, "RERANK_MAX_LENGTH", 0) or 0,
        "skip_margin": getattr(config, "RERANK_SKIP_MARGIN", 0.0) or 0.0,
    }


# ---------------------------------------------------------------- 查询级缓存
# 键 (query, top_k) -> (result, timestamp)。命中则整条检索链路直接返回。
_query_cache: "OrderedDict[tuple, tuple]" = OrderedDict()


def clear_query_cache() -> None:
    _query_cache.clear()


def _query_cache_get(key: tuple):
    if key in _query_cache:
        value, ts = _query_cache[key]
        ttl = getattr(config, "QUERY_CACHE_TTL", 0) or 0
        if ttl > 0 and (time.time() - ts) > ttl:
            _query_cache.pop(key, None)
            return None
        _query_cache.move_to_end(key)
        return value
    return None


def _query_cache_put(key: tuple, value) -> None:
    size = getattr(config, "QUERY_CACHE_SIZE", 0) or 0
    if size <= 0:
        return
    _query_cache[key] = (value, time.time())
    _query_cache.move_to_end(key)
    while len(_query_cache) > size:
        _query_cache.popitem(last=False)


# ---------------------------------------------------------------- 语义缓存
# 每条缓存只存一个 query 向量 + 结果。命中用余弦相似度 + 阈值判断。
# 缓存很小（默认 1024 条），线性扫描已足够；如需更大规模可换 hnswlib。
_semantic_cache: list[dict] = []
_semantic_lock = threading.Lock()


def clear_semantic_cache() -> None:
    with _semantic_lock:
        _semantic_cache.clear()


def _semantic_lookup(q_vec: np.ndarray, top_k: int):
    threshold = getattr(config, "SEMANTIC_CACHE_THRESHOLD", 0.95) or 0.95
    if threshold <= 0:
        return None
    q_norm = float(np.linalg.norm(q_vec))
    if q_norm == 0:
        return None
    with _semantic_lock:
        best = None
        best_sim = -1.0
        for entry in _semantic_cache:
            if entry["top_k"] != top_k:
                continue
            sim = float(np.dot(q_vec, entry["vec"])) / (q_norm * entry["norm"] + 1e-12)
            if sim > best_sim:
                best_sim = sim
                best = entry
        if best is not None and best_sim >= threshold:
            return best["result"]
    return None


def _semantic_put(q_vec: np.ndarray, result: list, top_k: int) -> None:
    size = getattr(config, "SEMANTIC_CACHE_SIZE", 0) or 0
    if size <= 0:
        return
    with _semantic_lock:
        _semantic_cache.append({
            "vec": np.asarray(q_vec, dtype="float32").copy(),
            "norm": float(np.linalg.norm(q_vec)),
            "result": result,
            "top_k": top_k,
        })
        while len(_semantic_cache) > size:
            _semantic_cache.pop(0)


# ---------------------------------------------------------------- 重排串行化
# waitress 多线程下，若两个请求同时进入重排，会各起 8 个 torch 线程争抢 CPU。
# 用一把锁强制重排串行：单请求耗时不变，多请求吞吐与尾部延迟显著改善。
_rerank_lock = threading.Lock()


def clear_all_caches() -> None:
    clear_query_cache()
    clear_semantic_cache()
    _rerank_cache.clear()


class HybridRetriever:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._loaded = False
        self._client = None
        self._collection = None
        self._bm25 = None          # bm25s.BM25 索引（mmap 加载）
        self._ids: list[str] = []  # 与 BM25 索引对齐的 ChromaDB id 列表

    # ------------------------------------------------------------ 连接与缓存
    def _connect(self):
        if self._client is None:
            config.DB_DIR.mkdir(parents=True, exist_ok=True)
            self._client = chromadb.PersistentClient(path=str(config.DB_DIR))
        return self._client

    @property
    def collection(self):
        client = self._connect()
        if self._collection is None:
            self._collection = client.get_or_create_collection(
                name=config.COLLECTION_NAME,
                metadata={"hnsw:space": "cosine"},
            )
        return self._collection

    def _bm25_dir(self) -> Path:
        return config.BM25_DIR

    def _bm25_ids_file(self) -> Path:
        return self._bm25_dir() / "ids.json"

    def ensure_loaded(self) -> None:
        """加载 BM25 索引（内存映射）。不再把全量文档 / 元数据拉进内存。"""
        if self._loaded:
            return
        with self._lock:
            if self._loaded:
                return
            self._load_bm25()
            self._loaded = True

    def _load_bm25(self) -> None:
        import bm25s

        ids_file = self._bm25_ids_file()
        if ids_file.exists():
            try:
                self._bm25 = bm25s.BM25.load(str(self._bm25_dir()), mmap=True)
                self._ids = json.loads(ids_file.read_text(encoding="utf-8"))
                logger.info("BM25 索引已加载（内存映射）：%d 个片段", len(self._ids))
                return
            except Exception as exc:  # pragma: no cover - 索引损坏时重建
                logger.warning("BM25 索引加载失败，将重建：%s", exc)
        self._rebuild_bm25()

    def _rebuild_bm25(self) -> None:
        """一次性：从向量库读全量文档构建 BM25（仅首次 / 迁移旧库时）。"""
        import bm25s

        try:
            data = self.collection.get(include=["documents"])
        except Exception as exc:  # pragma: no cover
            logger.warning("读取向量库失败，BM25 暂不可用：%s", exc)
            self._bm25 = None
            self._ids = []
            return

        docs = list(data.get("documents") or [])
        ids = list(data.get("ids") or [])
        if not docs:
            self._bm25 = None
            self._ids = []
            return

        tokenized = [tokenize(doc) or ["\u200b"] for doc in docs]
        retriever = bm25s.BM25()
        retriever.index(tokenized)
        try:
            d = self._bm25_dir()
            d.mkdir(parents=True, exist_ok=True)
            retriever.save(str(d))
            self._bm25_ids_file().write_text(
                json.dumps(ids, ensure_ascii=False), encoding="utf-8"
            )
        except Exception as exc:  # pragma: no cover - 保存失败仍可用内存版
            logger.warning("BM25 索引保存失败（仍可用内存版）：%s", exc)
        self._bm25 = retriever
        self._ids = ids
        logger.info("已构建 BM25 索引：%d 个片段", len(ids))

    def reload(self) -> None:
        """重新读取向量库与 BM25 索引（重建索引后调用），并清空各级缓存。"""
        with self._lock:
            self._loaded = False
            self._bm25 = None
            self._ids = []
            self._collection = None
        clear_all_caches()
        self.ensure_loaded()

    # ------------------------------------------------------------ 基本信息
    def count(self) -> int:
        """当前索引里的片段总数。ChromaDB 原生 O(1)，不触发任何全量加载。"""
        return self.collection.count()

    def stats(self) -> dict:
        try:
            count = self.collection.count()
        except Exception as exc:  # pragma: no cover - 数据库损坏等异常
            logger.warning("读取向量库失败：%s", exc)
            return {"count": 0, "sources": [], "error": str(exc)}
        sources = self.list_sources()
        error = None
        if count == 0 and not sources:
            error = f"向量库为空：{config.DB_DIR}。请先上传文档并重建索引。"
        return {"count": count, "sources": sources, "error": error}

    def list_sources(self) -> list[dict]:
        """列出向量库中所有来源文件及片段数。"""
        try:
            data = self.collection.get(include=["metadatas"])
        except Exception as exc:  # pragma: no cover
            logger.warning("列出文档失败：%s", exc)
            return []
        buckets: dict[str, dict] = {}
        for meta in data.get("metadatas") or []:
            meta = meta or {}
            name = meta.get("source", "未知")
            item = buckets.setdefault(
                name, {"source": name, "chunks": 0, "file_type": meta.get("file_type", "")}
            )
            item["chunks"] += 1
        return sorted(buckets.values(), key=lambda x: x["source"])

    # ------------------------------------------------------------ 两路召回
    def _vector_search(self, query: str, top_k: int, q_vec=None) -> list[dict]:
        count = self.collection.count()
        if count == 0:
            return []
        if q_vec is None:
            model = get_embed_model()
            q_vec = model.encode([query], normalize_embeddings=True).tolist()
        res = self.collection.query(
            query_embeddings=q_vec,
            n_results=min(top_k, count),
            include=["documents", "metadatas", "distances"],
        )
        hits = []
        for uid, doc, meta, dist in zip(
            res["ids"][0],
            res["documents"][0],
            res["metadatas"][0],
            res["distances"][0],
        ):
            hits.append({
                "id": uid,
                "doc": doc,
                "meta": meta or {},
                "similarity": 1.0 - float(dist),
            })
        return hits

    def _bm25_search(self, query: str, top_k: int) -> list[dict]:
        if self._bm25 is None or not self._ids:
            return []
        tokens = tokenize(query)
        if not tokens:
            return []
        try:
            results, scores = self._bm25.retrieve(
                [tokens], k=top_k, corpus=self._ids, show_progress=False
            )
        except Exception as exc:  # pragma: no cover
            logger.warning("BM25 检索失败：%s", exc)
            return []

        ids = [str(x) for x in np.asarray(results).reshape(-1)]
        scores = [float(x) for x in np.asarray(scores).reshape(-1)]
        valid = [i for i, s in zip(ids, scores) if i and s > 0]
        if not valid:
            return []

        try:
            data = self.collection.get(ids=valid, include=["documents", "metadatas"])
        except Exception as exc:  # pragma: no cover
            logger.warning("BM25 命中文档读取失败：%s", exc)
            return []

        doc_by_id = {
            uid: (doc, meta or {})
            for uid, doc, meta in zip(data["ids"], data["documents"], data["metadatas"])
        }
        hits = []
        for uid, score in zip(ids, scores):
            if score <= 0 or uid not in doc_by_id:
                continue
            doc, meta = doc_by_id[uid]
            hits.append({"id": uid, "doc": doc, "meta": meta, "bm25_score": score})
        return hits

    # ------------------------------------------------------------ 融合与重排
    @staticmethod
    def _key_of(item: dict, fallback_index: int | None = None) -> str:
        if item.get("id"):
            return item["id"]
        return (item.get("doc") or "")[:120] or f"__{fallback_index}"

    def _rrf_fusion(self, vec_results: list[dict], bm25_results: list[dict]) -> list[dict]:
        """Reciprocal Rank Fusion：按排名而非分数融合两路结果。"""
        k = config.RRF_K
        scores: dict[str, float] = {}
        merged: dict[str, dict] = {}

        for rank, item in enumerate(vec_results, 1):
            key = self._key_of(item, rank)
            scores[key] = scores.get(key, 0.0) + 1.0 / (k + rank)
            merged.setdefault(key, {}).update(item)

        for rank, item in enumerate(bm25_results, 1):
            key = self._key_of(item, rank)
            scores[key] = scores.get(key, 0.0) + 1.0 / (k + rank)
            if key in merged:
                merged[key]["bm25_score"] = item.get("bm25_score")
            else:
                merged[key] = dict(item)

        ordered = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)
        results = []
        for key, score in ordered:
            item = merged[key]
            item["rrf_score"] = score
            results.append(item)
        return results

    def _should_skip_rerank(self, vec_results: list[dict], bm25_results: list[dict], fused: list[dict]) -> bool:
        """判断能否安全跳过重排。

        条件（全部满足才跳过）：
        1. RERANK_SKIP_MARGIN > 0（设 0 即关闭该优化）；
        2. 单候选时直接跳过 —— 重排一个候选没有任何排序意义；
        3. RRF 第一名 == 向量第一名（排名无分歧）；
        4. 向量第一名领先第二名至少 margin；
        5. （可选）BM25 第一名也与向量第一名一致 —— 两路独立信号一致，置信度更高。
        """
        margin = getattr(config, "RERANK_SKIP_MARGIN", 0.0) or 0.0
        if margin <= 0:
            return False
        if len(fused) <= 1:
            return True
        if len(vec_results) < 2:
            return False

        top_vec = vec_results[0]
        second = vec_results[1]
        if top_vec.get("similarity") is None or second.get("similarity") is None:
            return False
        if fused[0].get("id") != top_vec.get("id"):
            return False
        if (float(top_vec["similarity"]) - float(second["similarity"])) < margin:
            return False
        if getattr(config, "RERANK_SKIP_REQUIRE_BM25", False) and bm25_results:
            if bm25_results[0].get("id") != top_vec.get("id"):
                return False
        return True

    def _dynamic_rerank_k(self, vec_results: list[dict]) -> int:
        """按向量置信度自适应重排候选数：简单查询少送，难区分查询多送。"""
        base = config.TOP_K_RERANK
        if not getattr(config, "DYNAMIC_RERANK_ENABLED", False):
            return base
        if len(vec_results) < 2:
            return max(1, base)
        try:
            margin = float(vec_results[0]["similarity"]) - float(vec_results[1]["similarity"])
        except (TypeError, KeyError):
            return base
        threshold = getattr(config, "DYNAMIC_RERANK_MARGIN", 0.12) or 0.12
        if margin >= threshold:
            return max(1, min(getattr(config, "DYNAMIC_RERANK_MIN_K", 3), base))
        return max(base, getattr(config, "DYNAMIC_RERANK_MAX_K", 10))

    def _rerank(self, query: str, candidates: list[dict]) -> list[dict]:
        """用交叉编码器重排候选。"""
        reranker = get_reranker()
        if not reranker or not candidates:
            return candidates

        # --- 先查缓存，只对未命中的候选做推理 ---
        pending: list[tuple[str, dict]] = []
        for item in candidates:
            key = _rerank_key(query, item.get("doc") or "")
            cached = _cache_get(key)
            if cached is None:
                pending.append((key, item))
            else:
                item["rerank_score"] = cached

        if pending:
            pairs = [[query, item["doc"]] for _key, item in pending]
            try:
                kwargs = {"batch_size": config.RERANK_BATCH_SIZE}
                max_length = getattr(config, "RERANK_MAX_LENGTH", 0) or 0
                if max_length > 0:
                    kwargs["max_length"] = max_length

                def _run():
                    try:
                        return reranker.compute_score(pairs, **kwargs)
                    except TypeError:
                        # 旧版 FlagEmbedding 的 compute_score 不接受 max_length
                        return reranker.compute_score(pairs, batch_size=config.RERANK_BATCH_SIZE)

                if getattr(config, "RERANK_SERIAL", True):
                    with _rerank_lock:
                        scores = _run()
                else:
                    scores = _run()
            except Exception as exc:  # pragma: no cover - 重排模型异常时降级
                logger.warning("重排失败，降级为 RRF 排序：%s", exc)
                for item in candidates:
                    item.pop("rerank_score", None)
                return candidates

            if isinstance(scores, (int, float)):
                scores = [scores]
            for (key, item), score in zip(pending, scores):
                value = float(score)
                item["rerank_score"] = value
                _cache_put(key, value)

        known = [c for c in candidates if "rerank_score" in c]
        if not known:
            return candidates
        return sorted(known, key=lambda x: x.get("rerank_score", 0.0), reverse=True)

    # ------------------------------------------------------------ 对外入口
    def retrieve(self, query: str, top_k: int | None = None) -> list[dict]:
        """完整检索：向量 + BM25 → RRF → 重排 → 阈值过滤。

        加入了查询级缓存与语义缓存，命中时整条链路直接返回。
        """
        query = (query or "").strip()
        if not query:
            return []

        self.ensure_loaded()
        if self.collection.count() == 0:
            raise KnowledgeBaseEmpty("向量库为空，请先上传文档并重建索引。")

        top_k = top_k or config.TOP_K_FINAL

        # 1) 精确查询缓存
        qkey = (query, top_k)
        cached = _query_cache_get(qkey)
        if cached is not None:
            return cached

        # 2) 编码 query（一次，供向量检索与语义缓存复用）
        model = get_embed_model()
        q_vec_np = np.asarray(model.encode([query], normalize_embeddings=True))
        q_vec_1d = q_vec_np[0].astype("float32")
        q_vec_2d = q_vec_np.tolist()

        # 3) 语义缓存：相似问题复用
        if getattr(config, "SEMANTIC_CACHE_ENABLED", False):
            sem = _semantic_lookup(q_vec_1d, top_k)
            if sem is not None:
                _query_cache_put(qkey, sem)
                return sem

        # 4) 两路召回 + RRF
        vec_results = self._vector_search(query, config.TOP_K_RETRIEVE, q_vec=q_vec_2d)
        bm25_results = self._bm25_search(query, config.TOP_K_RETRIEVE)
        fused = self._rrf_fusion(vec_results, bm25_results)
        if not fused:
            return []

        # 5) 重排（可能跳过 / 动态截断候选）
        if self._should_skip_rerank(vec_results, bm25_results, fused):
            logger.debug("检索优势明显，跳过重排")
            candidates = fused
        else:
            rerank_k = self._dynamic_rerank_k(vec_results)
            candidates = self._rerank(query, fused[:rerank_k])
        if not candidates:
            return []

        # 6) 阈值过滤（可选）
        if config.SIMILARITY_THRESHOLD > 0:
            passed = [c for c in candidates if c.get("similarity", 1.0) >= config.SIMILARITY_THRESHOLD]
            if passed:
                candidates = passed
        if config.RERANK_THRESHOLD > 0:
            passed = [c for c in candidates if c.get("rerank_score", 1.0) >= config.RERANK_THRESHOLD]
            if passed:
                candidates = passed

        final = candidates[:top_k]

        # 7) 写缓存
        _query_cache_put(qkey, final)
        if getattr(config, "SEMANTIC_CACHE_ENABLED", False):
            _semantic_put(q_vec_1d, final, top_k)

        return final

    def search(self, query: str, top_k: int = 10) -> list[dict]:
        """纯检索接口（给“检索预览”用，不调用大模型）。"""
        return self.retrieve(query, top_k=top_k)


# 进程内单例
retriever = HybridRetriever()


def format_sources(contexts: list[dict]) -> list[dict]:
    """把检索结果整理成前端可直接渲染的引用来源列表。"""
    sources = []
    for i, item in enumerate(contexts, 1):
        meta = item.get("meta") or {}
        sources.append({
            "index": i,
            "source": meta.get("source", "未知"),
            "chunk": meta.get("chunk", None),
            "preview": (item.get("doc") or "")[:220].strip(),
            "similarity": round(item.get("similarity", 0.0), 4),
            "bm25_score": round(item["bm25_score"], 4) if item.get("bm25_score") else None,
            "rerank_score": round(item["rerank_score"], 4) if item.get("rerank_score") is not None else None,
            "rrf_score": round(item.get("rrf_score", 0.0), 6),
        })
    return sources
