"""
建库脚本：解析 data/ → 分块 → 本地向量化 → 写入 ChromaDB。

用法：
    python ingest.py                      # 重建索引（先清空旧集合）
    python ingest.py --append             # 增量追加，不删除已有片段
    python ingest.py --data other_dir     # 指定文档目录
    python ingest.py --reset-db           # 连向量库目录一起重建（物理删除）

author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import argparse
import json
import logging
import shutil
import time
from fnmatch import fnmatch
from pathlib import Path

import chromadb

import config
from chunking import split_text
from embeddings import get_embed_model
from parsers import parse_file

logger = logging.getLogger(__name__)

BATCH_SIZE = 100


class IngestError(RuntimeError):
    """建库过程中的可预期错误（文件为空、解析失败等）。"""


def is_excluded(path: Path, data_dir: Path) -> bool:
    """按 INGEST_EXCLUDE 通配规则排除文件（用于跳过草稿、被替代的旧版本等）。

    规则同时匹配「文件名」与「相对 data 目录的路径」，大小写不敏感。
    例如 INGEST_EXCLUDE=*Schema v1.0*,*.draft.md
    """
    patterns = getattr(config, "INGEST_EXCLUDE", ()) or ()
    if not patterns:
        return False
    name = path.name.lower()
    try:
        rel = str(path.relative_to(data_dir)).lower().replace("\\", "/")
    except ValueError:
        rel = name
    for pat in patterns:
        pat = pat.strip().lower()
        if not pat:
            continue
        if fnmatch(name, pat) or fnmatch(rel, pat):
            return True
    return False


def collect_files(data_dir: Path, exts: set[str] | None = None) -> list[Path]:
    """递归收集待处理文档（自动跳过 INGEST_EXCLUDE 命中的文件）。"""
    exts = exts or config.SUPPORTED_EXTS
    if not data_dir.exists():
        return []
    return [
        p for p in sorted(data_dir.rglob("*"))
        if p.is_file() and not p.name.startswith(".") and p.suffix.lower() in exts
        and not is_excluded(p, data_dir)
    ]


def build_chunks(
    files: list[Path],
    data_dir: Path,
    chunk_size: int | None = None,
    overlap: int | None = None,
    progress=None,
) -> tuple[list[str], list[str], list[dict], list[str]]:
    """把文件解析并切块，返回 (ids, documents, metadatas, skipped)。"""
    chunk_size = chunk_size or config.CHUNK_SIZE
    overlap = overlap if overlap is not None else config.CHUNK_OVERLAP

    ids: list[str] = []
    docs: list[str] = []
    metas: list[dict] = []
    skipped: list[str] = []

    for path in files:
        try:
            relative = path.relative_to(data_dir).as_posix()
        except ValueError:
            relative = path.name

        try:
            text = parse_file(str(path))
        except Exception as exc:
            logger.warning("解析失败：%s（%s）", relative, exc)
            skipped.append(f"{relative}（解析失败：{exc}）")
            if progress:
                progress(f"跳过 {relative}：解析失败")
            continue

        if not text.strip():
            skipped.append(f"{relative}（内容为空）")
            if progress:
                progress(f"跳过 {relative}：内容为空")
            continue

        try:
            mtime = int(path.stat().st_mtime)
        except OSError:
            mtime = 0

        pieces = split_text(text, chunk_size=chunk_size, overlap=overlap)
        for i, piece in enumerate(pieces):
            ids.append(f"{relative}::{i}")
            docs.append(piece)
            metas.append({
                "source": relative,
                "chunk": i,
                "chunks_total": len(pieces),
                "file_type": path.suffix.lower(),
                "mtime": mtime,
            })
        if progress:
            progress(f"已处理 {relative}，切出 {len(pieces)} 块")

    return ids, docs, metas, skipped


def build_bm25_index(ids: list[str], docs: list[str], save_dir: Path, progress=None) -> None:
    """构建并保存 bm25s 关键词索引（与向量库同一次建库完成，避免查询时全量加载）。"""
    import bm25s

    from retriever import tokenize

    tokenized = [tokenize(doc) or ["\u200b"] for doc in docs]
    retriever = bm25s.BM25()
    retriever.index(tokenized)
    save_dir.mkdir(parents=True, exist_ok=True)
    retriever.save(str(save_dir))
    (save_dir / "ids.json").write_text(json.dumps(ids, ensure_ascii=False), encoding="utf-8")
    if progress:
        progress(f"已保存 BM25 索引：{len(ids)} 个片段 -> {save_dir}")


def ingest(
    data_dir: Path | None = None,
    db_dir: Path | None = None,
    collection_name: str | None = None,
    chunk_size: int | None = None,
    overlap: int | None = None,
    append: bool = False,
    progress=None,
) -> dict:
    """执行建库，返回统计信息。可被 CLI 与 Web 接口共同调用。"""
    data_dir = Path(data_dir or config.DATA_DIR)
    db_dir = Path(db_dir or config.DB_DIR)
    collection_name = collection_name or config.COLLECTION_NAME

    def report(message: str) -> None:
        logger.info(message)
        if progress:
            progress(message)

    started = time.time()
    files = collect_files(data_dir)
    if not files:
        raise IngestError(
            f"{data_dir} 中没有找到可处理的文档"
            f"（支持 {', '.join(sorted(config.SUPPORTED_EXTS))}）。"
        )

    ids, docs, metas, skipped = build_chunks(
        files, data_dir, chunk_size=chunk_size, overlap=overlap, progress=report
    )
    if not ids:
        raise IngestError("所有文档解析后都没有有效内容，未写入任何片段。")

    db_dir.mkdir(parents=True, exist_ok=True)
    client = chromadb.PersistentClient(path=str(db_dir))

    if append:
        collection = client.get_or_create_collection(
            name=collection_name, metadata={"hnsw:space": "cosine"}
        )
    else:
        # 全量重建：删除旧集合后重新创建，避免重复片段堆积
        try:
            client.delete_collection(collection_name)
            report(f"已清空旧集合 {collection_name}")
        except Exception:
            pass
        collection = client.create_collection(
            name=collection_name, metadata={"hnsw:space": "cosine"}
        )

    report(f"正在向量化 {len(docs)} 个片段（模型 {config.EMBED_MODEL}）…")
    model = get_embed_model()
    embeddings = model.encode(
        docs, normalize_embeddings=True, batch_size=32, show_progress_bar=False
    ).tolist()

    for i in range(0, len(ids), BATCH_SIZE):
        collection.add(
            ids=ids[i:i + BATCH_SIZE],
            documents=docs[i:i + BATCH_SIZE],
            embeddings=embeddings[i:i + BATCH_SIZE],
            metadatas=metas[i:i + BATCH_SIZE],
        )
        report(f"已写入 {min(i + BATCH_SIZE, len(ids))}/{len(ids)} 个片段")

    try:
        build_bm25_index(ids, docs, config.BM25_DIR, progress=report)
    except Exception as exc:  # BM25 失败不影响向量检索
        report(f"BM25 索引构建失败（不影响向量检索）：{exc}")

    elapsed = time.time() - started
    summary = {
        "files": len(files),
        "chunks": len(ids),
        "skipped": skipped,
        "collection": collection_name,
        "db_dir": str(db_dir),
        "data_dir": str(data_dir),
        "count": collection.count(),
        "elapsed": round(elapsed, 2),
        "chunk_size": chunk_size or config.CHUNK_SIZE,
        "overlap": overlap if overlap is not None else config.CHUNK_OVERLAP,
    }
    report(f"完成：{summary['files']} 个文件 / {summary['chunks']} 个片段，用时 {elapsed:.1f}s")
    return summary


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(message)s")

    parser = argparse.ArgumentParser(description="构建本地知识库索引")
    parser.add_argument("--data", default=str(config.DATA_DIR), help="文档目录")
    parser.add_argument("--db", default=str(config.DB_DIR), help="向量库目录")
    parser.add_argument("--collection", default=config.COLLECTION_NAME, help="集合名")
    parser.add_argument("--chunk-size", type=int, default=config.CHUNK_SIZE, help="分块字符数")
    parser.add_argument("--overlap", type=int, default=config.CHUNK_OVERLAP, help="分块重叠字符数")
    parser.add_argument("--append", action="store_true", help="增量追加，不重建")
    parser.add_argument("--reset-db", action="store_true", help="先物理删除向量库目录再重建")
    args = parser.parse_args()

    if args.reset_db:
        db_path = Path(args.db)
        if db_path.exists():
            print(f"删除向量库目录：{db_path}")
            shutil.rmtree(db_path)

    try:
        summary = ingest(
            data_dir=Path(args.data),
            db_dir=Path(args.db),
            collection_name=args.collection,
            chunk_size=args.chunk_size,
            overlap=args.overlap,
            append=args.append,
            progress=print,
        )
    except IngestError as exc:
        print(f"建库失败：{exc}")
        raise SystemExit(1)

    print(f"集合内片段总数：{summary['count']}")


if __name__ == "__main__":
    main()
