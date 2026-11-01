"""
命令行问答入口（与网页版共用同一套检索与生成逻辑）。

用法：
    python query.py                 # 交互式提问
    python query.py "问题内容"       # 直接提问一次
    python query.py --search "关键词"  # 只看检索结果，不调用云端模型

author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import argparse
import sys

import config
import llm
from retriever import KnowledgeBaseEmpty, format_sources, retriever


def print_sources(sources: list[dict]) -> None:
    if not sources:
        print("\n=== 引用来源 ===\n（无）")
        return
    print("\n=== 引用来源 ===")
    for item in sources:
        scores = []
        if item.get("similarity") is not None:
            scores.append(f"向量 {item['similarity']:.3f}")
        if item.get("bm25_score") is not None:
            scores.append(f"BM25 {item['bm25_score']:.2f}")
        if item.get("rerank_score") is not None:
            scores.append(f"重排 {item['rerank_score']:.3f}")
        print(f"[{item['index']}] {item['source']} 第 {item['chunk']} 块 —— {' / '.join(scores)}")


def search_only(question: str, top_k: int) -> None:
    try:
        contexts = retriever.search(question, top_k=top_k)
    except KnowledgeBaseEmpty as exc:
        print(f"检索失败：{exc}")
        return
    print(f"\n=== 检索结果（{len(contexts)} 条）===")
    for item in format_sources(contexts):
        print(f"\n[{item['index']}] {item['source']} 第 {item['chunk']} 块")
        print(item["preview"])


def ask(question: str) -> None:
    try:
        contexts = retriever.retrieve(question)
    except KnowledgeBaseEmpty as exc:
        print(f"\n=== 回答 ===\n{exc}")
        return

    sources = format_sources(contexts)
    if not contexts:
        print(f"\n=== 回答 ===\n{config.NO_CONTEXT_REPLY}")
        return

    try:
        result = llm.complete(question, contexts)
    except llm.LLMNotConfigured as exc:
        print(f"\n=== 回答 ===\n无法调用云端模型：{exc}")
        print_sources(sources)
        return

    print("\n=== 回答 ===")
    print(result["answer"])
    print_sources(sources)


def main() -> None:
    parser = argparse.ArgumentParser(description="本地知识库问答（命令行）")
    parser.add_argument("question", nargs="*", help="要提问的问题")
    parser.add_argument("--search", action="store_true", help="只检索，不调用云端模型")
    parser.add_argument("--top-k", type=int, default=config.TOP_K_FINAL, help="最终使用的片段数")
    args = parser.parse_args()

    question = " ".join(args.question).strip()
    if question:
        if args.search:
            search_only(question, args.top_k)
        else:
            ask(question)
        return

    print("本地知识库问答（输入 q 退出）")
    while True:
        try:
            question = input("\n请输入问题：").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if question.lower() in ("q", "quit", "exit"):
            break
        if not question:
            continue
        if args.search:
            search_only(question, args.top_k)
        else:
            ask(question)


if __name__ == "__main__":
    sys.exit(main())
