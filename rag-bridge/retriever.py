"""
混合检索：向量检索 + BM25 关键词检索 → RRF 融合 → 重排 → 阈值过滤。

设计要点：
- 模型惰性加载、进程内复用（见 models.py）；
- 向量库中的文档快照缓存在内存里，可直接搜索、也可按关键词本地过滤；
- 建库后调用 reload() 即可热更新，无需重启 Web 服务。

author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import hashlib
import logging
import re
import threading
from collections import OrderedDict

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

# 中文常见疑问道词，BM25 里属于噪声
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
# 可直接复用上次的分数，省掉整段重排（约几十秒）。
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
    """给状态接口/运维看的缓存概况。"""
    return {
        "entries": len(_rerank_cache),
        "size": getattr(config, "RERANK_CACHE_SIZE", 0) or 0,
        "max_length": getattr(config, "RERANK_MAX_LENGTH", 0) or 0,
        "skip_margin": getattr(config, "RERANK_SKIP_MARGIN", 0.0) or 0.0,
    }


class HybridRetriever:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._loaded = False
        self._client = None
        self._collection = None
        self._bm25 = None
        self.all_docs: list[str] = []
        self.all_metas: list[dict] = []
        self.all_ids: list[str] = []

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

    def ensure_loaded(self) -> None:
        """把向量库中的片段读进内存，构建 BM25 索引。"""
        if self._loaded:
            return
        with self._lock:
            if self._loaded:
                return
            data = self.collection.get(include=["documents", "metadatas"])
            self.all_docs = list(data.get("documents") or [])
            self.all_metas = [m or {} for m in (data.get("metadatas") or [])]
            self.all_ids = list(data.get("ids") or [])

            if self.all_docs:
                from rank_bm25 import BM25Okapi

                tokenized = [tokenize(doc) for doc in self.all_docs]
                # BM25 对空文档会抛异常，这里兜一个占位 token
                tokenized = [t or ["\u200b"] for t in tokenized]
                self._bm25 = BM25Okapi(tokenized)
            else:
                self._bm25 = None
            self._loaded = True
            logger.info("知识库已载入内存：%d 个片段", len(self.all_docs))

    def reload(self) -> None:
        """重新读取向量库（重建索引后调用）。"""
        with self._lock:
            self._loaded = False
            self._bm25 = None
            self.all_docs, self.all_metas, self.all_ids = [], [], []
            self._collection = None
        self.ensure_loaded()

    # ------------------------------------------------------------ 基本信息
    def stats(self) -> dict:
        try:
            count = self.collection.count()
        except Exception as exc:  # pragma: no cover - 数据库损坏等异常
            logger.warning("读取向量库失败：%s", exc)
            return {"count": 0, "sources": [], "error": str(exc)}
        sources = self.list_sources()
        error = None
        if count == 0 and not sources:
            error = f"向量库为空：{config.DB_DIR}。请先上传文档并点击「重建索引」。"
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
    def _vector_search(self, query: str, top_k: int) -> list[dict]:
        if not self.all_docs:
            return []
        model = get_embed_model()
        q_vec = model.encode([query], normalize_embeddings=True).tolist()
        res = self.collection.query(
            query_embeddings=q_vec,
            n_results=min(top_k, len(self.all_docs)),
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
        if self._bm25 is None or not self.all_docs:
            return []
        tokens = tokenize(query)
        if not tokens:
            return []
        scores = self._bm25.get_scores(tokens)
        order = np.argsort(scores)[::-1][:top_k]
        hits = []
        for idx in order:
            score = float(scores[idx])
            if score <= 0:
                continue
            hits.append({
                "doc": self.all_docs[idx],
                "meta": self.all_metas[idx],
                "bm25_score": score,
                "id": self.all_ids[idx],
            })
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

    def _should_skip_rerank(self, vec_results: list[dict], fused: list[dict]) -> bool:
        """判断能否安全跳过重排。

        条件（全部满足才跳过，宁可多算不可错排）：
        1. RERANK_SKIP_MARGIN > 0（默认 0.08，设 0 即完全关闭该优化）；
        2. 至少有两个向量命中，且分数可比较；
        3. RRF 的第一名恰好就是向量检索的第一名 —— 排名没有分歧；
        4. 向量第一名领先第二名至少 margin（余弦分差）。
        """
        margin = getattr(config, "RERANK_SKIP_MARGIN", 0.0) or 0.0
        if margin <= 0 or len(fused) <= 1 or len(vec_results) < 2:
            return False

        top_vec = vec_results[0]
        second = vec_results[1]
        if top_vec.get("similarity") is None or second.get("similarity") is None:
            return False
        if fused[0].get("id") != top_vec.get("id"):
            return False                      # 两路排名有分歧 → 老老实实重排

        return (float(top_vec["similarity"]) - float(second["similarity"])) >= margin

    def _rerank(self, query: str, candidates: list[dict]) -> list[dict]:
        """用交叉编码器重排候选。

        三重提速（实测重排占整条链路约 97% 的时间）：
        1. 结果缓存：同一 (问题, 片段) 直接复用上次分数；
        2. 长度截断：只把片段前 RERANK_MAX_LENGTH 个 token 交给模型
           （耗时几乎正比于 token 数：512→55s，256→26s）；
        3. 只在必要时才重排（见 retrieve() 里的 RERANK_SKIP_MARGIN）。
        """
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
                try:
                    scores = reranker.compute_score(pairs, **kwargs)
                except TypeError:
                    # 旧版 FlagEmbedding 的 compute_score 不接受 max_length
                    scores = reranker.compute_score(pairs, batch_size=config.RERANK_BATCH_SIZE)
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
    def count(self) -> int:
        """当前索引里的片段总数（供状态接口与运维检查使用）。"""
        self.ensure_loaded()
        return len(self.all_docs)

    def retrieve(self, query: str, top_k: int | None = None) -> list[dict]:
        """完整检索：向量 + BM25 → RRF → 重排 → 阈值过滤。"""
        query = (query or "").strip()
        if not query:
            return []

        self.ensure_loaded()
        if not self.all_docs:
            raise KnowledgeBaseEmpty("向量库为空，请先上传文档并重建索引。")

        top_k = top_k or config.TOP_K_FINAL
        vec_results = self._vector_search(query, config.TOP_K_RETRIEVE)
        bm25_results = self._bm25_search(query, config.TOP_K_RETRIEVE)

        fused = self._rrf_fusion(vec_results, bm25_results)[: config.TOP_K_RERANK]
        if not fused:
            return []

        if self._should_skip_rerank(vec_results, fused):
            # 排序已经很确定，直接省掉最贵的一步
            logger.debug("向量检索优势明显，跳过重排")
            candidates = fused
        else:
            candidates = self._rerank(query, fused)
        if not candidates:
            return []

        # 阈值是可选的「及格线」：只有显式配置了大于 0 的阈值才做过滤，
        # 否则一律全部保留（否则默认阈值会误杀本该入选的片段）。
        if config.SIMILARITY_THRESHOLD > 0:
            passed = [c for c in candidates if c.get("similarity", 1.0) >= config.SIMILARITY_THRESHOLD]
            if passed:
                candidates = passed
        if config.RERANK_THRESHOLD > 0:
            passed = [c for c in candidates if c.get("rerank_score", 1.0) >= config.RERANK_THRESHOLD]
            if passed:
                candidates = passed

        return candidates[:top_k]

    def search(self, query: str, top_k: int = 10) -> list[dict]:
        """纯检索接口（给“检索预览”用，不调用大模型）。"""
        return self.retrieve(query, top_k=top_k)

    def keyword_filter(self, keyword: str) -> list[dict]:
        """在内存快照里按关键词做一次简单过滤。"""
        self.ensure_loaded()
        keyword = (keyword or "").strip().lower()
        if not keyword:
            return []
        hits = []
        for doc, meta, uid in zip(self.all_docs, self.all_metas, self.all_ids):
            if keyword in doc.lower() or keyword in str(meta.get("source", "")).lower():
                hits.append({"id": uid, "doc": doc, "meta": meta})
        return hits


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
