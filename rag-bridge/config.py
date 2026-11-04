"""
领域知识问答系统 · 独立配置
============================

本地检索（bge-m3 + BM25 + RRF + 重排）+ DeepSeek 云端生成。

所有可调项都能通过同目录下的 `.env` 覆盖，无需改动代码。
配置项都带默认值，**不写 .env 也能跑**（除 DeepSeek API Key 外）。

注意：本模块必须在导入 torch / sentence_transformers / FlagEmbedding **之前**
导入，因为 CPU 线程数等环境变量只有在这些库初始化前设置才生效。
"""

from __future__ import annotations

import os
from pathlib import Path

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - 允许不装 python-dotenv
    def load_dotenv(*_args, **_kwargs):
        return False

# ---------------------------------------------------------------- 路径
BASE_DIR = Path(__file__).resolve().parent
ENV_FILE = BASE_DIR / ".env"
load_dotenv(ENV_FILE)


def _env_raw(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()


def _env_path(name: str, default: str) -> Path:
    """读取相对路径配置，统一解析为绝对路径（相对本文件所在目录）。"""
    raw = os.getenv(name, default).strip() or default
    path = Path(raw)
    return path if path.is_absolute() else (BASE_DIR / path)


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, "").strip() or default)
    except ValueError:
        return default


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, "").strip() or default)
    except ValueError:
        return default


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name, "").strip().lower()
    if not raw:
        return default
    return raw in ("1", "true", "yes", "on", "y")


def _env_list(name: str) -> tuple[str, ...]:
    return tuple(x.strip() for x in os.getenv(name, "").split(",") if x.strip())


# ---------------------------------------------------------------- 目录
DATA_DIR = _env_path("DATA_DIR", "data")        # 放你的文档（md/txt/pdf/docx）
DB_DIR = _env_path("DB_DIR", "db_index")        # ChromaDB 向量库
BM25_DIR = _env_path("BM25_DIR", "bm25_index")  # bm25s 关键词索引（磁盘 / 内存映射）
LOG_DIR = _env_path("LOG_DIR", "logs")

COLLECTION_NAME = os.getenv("COLLECTION_NAME", "knowledge").strip() or "knowledge"

# ---------------------------------------------------------------- 嵌入与重排模型
EMBED_MODEL = os.getenv("EMBED_MODEL", "BAAI/bge-m3").strip()
RERANK_MODEL = os.getenv("RERANK_MODEL", "BAAI/bge-reranker-v2-m3").strip()
RERANK_ENABLED = _env_bool("RERANK_ENABLED", True)
# 重排批大小。CPU 上批太大反而更慢（实测 8 左右最优）
RERANK_BATCH_SIZE = _env_int("RERANK_BATCH_SIZE", 8)
USE_FP16 = _env_bool("USE_FP16", False)         # CPU 请保持 false

# HuggingFace 镜像（国内下载模型用）。必须在导入 transformers 之前设置。
HF_ENDPOINT = os.getenv("HF_ENDPOINT", "https://hf-mirror.com").strip()
if HF_ENDPOINT:
    os.environ["HF_ENDPOINT"] = HF_ENDPOINT

# 只用本地模型缓存，不联网校验。
# 模型已下好时强烈建议保持 true：实测首次加载从 163 秒降到 37 秒。
# 换模型（如改 EMBED_MODEL）时先设为 false 跑一次，让它下载完再改回 true。
HF_HUB_OFFLINE = _env_bool("HF_HUB_OFFLINE", True)
if HF_HUB_OFFLINE:
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"

# ------------------------------------------------- CPU 线程（必须在 import torch 前设置）
def _detect_threads() -> int:
    """按核心数选一个保守线程数。

    实测：在 20 逻辑核的 i7-13650HX 上，torch 默认取 14 线程会造成严重的
    线程超额订阅（大小核混合）；压到 8 线程 + interop=1 后，嵌入与重排快约 4 倍。
    """
    cores = os.cpu_count() or 4
    return max(4, min(8, cores // 2 if cores > 8 else cores))


_env_threads = _env_raw("TORCH_THREADS", "0")
if _env_threads in ("", "0", "auto"):
    TORCH_THREADS = _detect_threads()
else:
    try:
        TORCH_THREADS = max(1, int(_env_threads))
    except ValueError:
        TORCH_THREADS = _detect_threads()

TORCH_INTEROP_THREADS = _env_int("TORCH_INTEROP_THREADS", 1)

# 这些环境变量必须早于任何 torch 相关导入生效
for _var in ("OMP_NUM_THREADS", "MKL_NUM_THREADS", "NUMEXPR_NUM_THREADS"):
    os.environ.setdefault(_var, str(TORCH_THREADS))
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")

TORCH_THREADS_APPLIED = False


def apply_torch_threads() -> None:
    """把线程设置写进 torch（幂等，import torch 之后调用）。"""
    global TORCH_THREADS_APPLIED
    if TORCH_THREADS_APPLIED:
        return
    try:
        import torch

        torch.set_num_threads(TORCH_THREADS)
        try:
            torch.set_num_interop_threads(TORCH_INTEROP_THREADS)
        except RuntimeError:
            # interop 线程池已初始化时无法再改，忽略即可
            pass
        TORCH_THREADS_APPLIED = True
    except Exception:  # pragma: no cover - 未安装 torch 时忽略
        pass


apply_torch_threads()

# ---------------------------------------------------------------- 分块
CHUNK_SIZE = _env_int("CHUNK_SIZE", 600)
CHUNK_OVERLAP = _env_int("CHUNK_OVERLAP", 80)

# 支持的文件类型
SUPPORTED_EXTS = {".md", ".markdown", ".txt", ".pdf", ".docx"}
# 建库时跳过的文件（通配符，逗号分隔，匹配文件名或相对路径）。
# 用于排除草稿、被替代的旧版本等，例：INGEST_EXCLUDE=*v1.0*,*.draft.md
INGEST_EXCLUDE = _env_list("INGEST_EXCLUDE")

# ---------------------------------------------------------------- 检索
TOP_K_RETRIEVE = _env_int("TOP_K_RETRIEVE", 20)   # 向量/BM25 每路召回数量
# 进入重排的候选数。CPU 上重排很贵（实测约 6~8 秒/对），默认只重排 RRF 前 6 名，
# 把单次问答压到 70 秒左右；有 GPU 或想更准时可调到 10~30。
TOP_K_RERANK = _env_int("TOP_K_RERANK", 5)
TOP_K_FINAL = _env_int("TOP_K_FINAL", 5)          # 最终送入大模型的片段数
RRF_K = _env_int("RRF_K", 60)                     # RRF 平滑常数

# ---------------------------------------------------------------- 重排性能
# 重排是整条链路里最贵的一步（实测占 97% 的时间），耗时几乎正比于送入模型的 token 数。
# 实测同一批 6 个候选：max_length=512 → 55s ｜ 384 → 41s ｜ 256 → 26s ｜ 192 → 19s
# 默认截断到 256，在基本不损失排序质量的前提下把重排耗时压到约一半。
RERANK_MAX_LENGTH = _env_int("RERANK_MAX_LENGTH", 256)
# 重排结果缓存条数（按 问题+片段 记忆分数）。0 = 关闭。
RERANK_CACHE_SIZE = _env_int("RERANK_CACHE_SIZE", 512)
# 向量检索第一名明显领先时直接跳过重排。0 = 关闭（总是重排）。
RERANK_SKIP_MARGIN = _env_float("RERANK_SKIP_MARGIN", 0.08)

# ---- 重排后端：auto 优先 ONNX（需先 export_rerank_onnx.py 导出），失败回退 PyTorch ----
RERANK_BACKEND = os.getenv("RERANK_BACKEND", "auto").strip()   # auto | onnx | torch
RERANK_ONNX_PATH = _env_raw("RERANK_ONNX_PATH", "")            # 导出的 .onnx 文件路径
# 多请求并发时把重排串行化，避免每个请求各起 8 个 torch 线程导致超订
RERANK_SERIAL = _env_bool("RERANK_SERIAL", True)
# 跳过重排时是否额外要求 BM25 第一名与向量第一名一致（更保守、置信度更高）
RERANK_SKIP_REQUIRE_BM25 = _env_bool("RERANK_SKIP_REQUIRE_BM25", False)

# ---- 查询级缓存（整条检索链路，命中直接返回；键 = 问题 + top_k）----
QUERY_CACHE_SIZE = _env_int("QUERY_CACHE_SIZE", 512)   # 精确查询 LRU 条数；0 = 关闭
QUERY_CACHE_TTL = _env_int("QUERY_CACHE_TTL", 0)       # 秒；0 = 不过期（reload 时清空）

# ---- 语义缓存（相似问题复用结果）----
SEMANTIC_CACHE_ENABLED = _env_bool("SEMANTIC_CACHE_ENABLED", True)
SEMANTIC_CACHE_SIZE = _env_int("SEMANTIC_CACHE_SIZE", 1024)
# 余弦相似度阈值（0~1），越大越保守；需按自己的数据校准
SEMANTIC_CACHE_THRESHOLD = _env_float("SEMANTIC_CACHE_THRESHOLD", 0.95)

# ---- 动态 TOP_K_RERANK：按向量置信度自适应候选数 ----
DYNAMIC_RERANK_ENABLED = _env_bool("DYNAMIC_RERANK_ENABLED", True)
DYNAMIC_RERANK_MARGIN = _env_float("DYNAMIC_RERANK_MARGIN", 0.12)  # 向量 top1-top2 分差阈值
DYNAMIC_RERANK_MIN_K = _env_int("DYNAMIC_RERANK_MIN_K", 3)          # 分差大时只重排这么少
DYNAMIC_RERANK_MAX_K = _env_int("DYNAMIC_RERANK_MAX_K", 10)         # 分差小时放大到这么多

# 阈值是「可选及格线」：只有显式设置 > 0 时才做过滤，否则一律全部保留。
# 不要为了方便而随手填一个默认阈值，否则会误杀本该入选的片段。
SIMILARITY_THRESHOLD = _env_float("SIMILARITY_THRESHOLD", 0.0)
RERANK_THRESHOLD = _env_float("RERANK_THRESHOLD", 0.0)

# ---------------------------------------------------------------- 云端大模型
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "").strip()
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").strip()
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat").strip()
LLM_TEMPERATURE = _env_float("LLM_TEMPERATURE", 0.2)
LLM_MAX_TOKENS = _env_int("LLM_MAX_TOKENS", 4096)
LLM_TIMEOUT = _env_float("LLM_TIMEOUT", 120.0)
MAX_HISTORY_TURNS = _env_int("MAX_HISTORY_TURNS", 3)   # 带多少轮对话上下文

# 兼容任意 OpenAI 协议的服务（如自建 vLLM / 其他厂商）：
# 只要改 DEEPSEEK_BASE_URL、DEEPSEEK_MODEL、DEEPSEEK_API_KEY 即可。

SYSTEM_PROMPT = os.getenv(
    "SYSTEM_PROMPT",
    "你是一个领域专家助手。只能依据提供的资料回答问题，"
    "必须用 [编号] 标注引用来源。"
    "如果资料中没有答案，就明确说不知道，不要编造。"
    "回答要条理清晰，必要时分点说明。",
).strip()

NO_CONTEXT_REPLY = os.getenv(
    "NO_CONTEXT_REPLY",
    "知识库中没有找到与问题相关的资料，请换一种问法，或先上传相关文档并重建索引。",
).strip()

# ---------------------------------------------------------------- 鲸鱼娘角色卡
# 角色卡放在同目录 persona.txt；缺省时用内置默认值。
_PERSONA_FILE = BASE_DIR / "persona.txt"

_DEFAULT_PERSONA = (
    "你正在扮演一只叫做「鲸鱼娘」的 Minecraft 服务器宠物，元气可爱、有点小傲娇，"
    "永远用中文、软萌的语气和主人聊天。知识类问题要依据给出的资料回答，"
    "资料里没有就坦白说不知道，不要编造，也不要自称 AI。"
)


def _load_persona() -> str:
    try:
        if _PERSONA_FILE.exists():
            text = _PERSONA_FILE.read_text(encoding="utf-8").strip()
            if text:
                return text
    except Exception:
        pass
    return _DEFAULT_PERSONA


PERSONA_PROMPT = _load_persona()

# ---------------------------------------------------------------- HTTP 服务
HOST = os.getenv("HOST", "127.0.0.1").strip()
PORT = _env_int("PORT", 8100)
THREADS = _env_int("THREADS", 4)

# 允许调用 HTTP 接口的 Key（逗号分隔）。**留空表示不鉴权**（仅建议本机使用）。
API_KEYS = _env_list("API_KEYS")
# 允许跨域来源（逗号分隔）；留空则不发送 CORS 头。
CORS_ORIGINS = _env_list("CORS_ORIGINS")


def ensure_dirs() -> None:
    """建好数据 / 向量库 / BM25 索引 / 日志目录。"""
    for d in (DATA_DIR, DB_DIR, BM25_DIR, LOG_DIR):
        d.mkdir(parents=True, exist_ok=True)


def summary() -> dict:
    """打印/返回当前配置概览，便于排查。"""
    return {
        "data_dir": str(DATA_DIR),
        "db_dir": str(DB_DIR),
        "collection": COLLECTION_NAME,
        "embed_model": EMBED_MODEL,
        "rerank_enabled": RERANK_ENABLED,
        "rerank_model": RERANK_MODEL if RERANK_ENABLED else None,
        "rerank_batch_size": RERANK_BATCH_SIZE,
        "chunk_size": CHUNK_SIZE,
        "chunk_overlap": CHUNK_OVERLAP,
        "top_k_retrieve": TOP_K_RETRIEVE,
        "top_k_rerank": TOP_K_RERANK,
        "top_k_final": TOP_K_FINAL,
        "rrf_k": RRF_K,
        "similarity_threshold": SIMILARITY_THRESHOLD,
        "rerank_threshold": RERANK_THRESHOLD,
        "llm_model": DEEPSEEK_MODEL,
        "llm_base_url": DEEPSEEK_BASE_URL,
        "llm_configured": bool(DEEPSEEK_API_KEY),
        "torch_threads": TORCH_THREADS,
        "hf_hub_offline": HF_HUB_OFFLINE,
        "serve": f"http://{HOST}:{PORT}",
        "api_keys_configured": bool(API_KEYS),
    }
