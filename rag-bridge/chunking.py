"""
文本分块：把解析出的长文本切成适合向量化的片段。

策略：
1. 先按 Markdown 标题切「章节」，让同一主题的内容尽量落在同一个片段里；
2. 章节过长时按自然边界（段落 → 换行 → 句号 → 逗号 → 硬切）递归二分；
3. 相邻片段保留可配置的重叠长度，避免答案被切断在边界上。

author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import re

# 自然边界，按优先级从高到低
_SEPARATORS = ["\n\n", "\n", "。", "！", "？", "；", ". ", "! ", "? ", "; ", "，", ", ", " "]

# Markdown 标题行（含 ATX 与 Setext 的简单形式）
_HEADING_RE = re.compile(r"^(#{1,6})\s+(.*)$")


def split_by_heading(text: str) -> list[str]:
    """按 Markdown 标题切成章节，标题保留在章节开头（给检索提供上下文）。"""
    lines = text.splitlines()
    sections: list[str] = []
    current: list[str] = []
    heading_stack: list[str] = []

    def flush():
        if current:
            block = "\n".join(current).strip()
            if block:
                sections.append(block)
        current.clear()

    for line in lines:
        m = _HEADING_RE.match(line.strip())
        if m:
            flush()
            level = len(m.group(1))
            title = m.group(2).strip()
            heading_stack = heading_stack[: level - 1]
            heading_stack.append(title)
            # 用标题路径当作章节的“面包屑”，提升小块的可检索性
            current.append("#" * level + " " + " / ".join(heading_stack[-2:]))
        else:
            current.append(line)
    flush()
    return sections or [text]


def _split_recursive(text: str, chunk_size: int, depth: int = 0) -> list[str]:
    """把文本递归切到不超过 chunk_size。"""
    text = text.strip()
    if len(text) <= chunk_size:
        return [text] if text else []

    if depth >= len(_SEPARATORS):
        return [text[i:i + chunk_size] for i in range(0, len(text), chunk_size)]

    sep = _SEPARATORS[depth]
    parts = text.split(sep)
    if len(parts) <= 1:
        return _split_recursive(text, chunk_size, depth + 1)

    chunks: list[str] = []
    buf = ""
    for part in parts:
        candidate = part if not buf else buf + sep + part
        if len(candidate) <= chunk_size:
            buf = candidate
            continue
        if buf:
            chunks.append(buf)
            buf = ""
        if len(part) > chunk_size:
            chunks.extend(_split_recursive(part, chunk_size, depth + 1))
        else:
            buf = part
    if buf:
        chunks.append(buf)
    return [c.strip() for c in chunks if c.strip()]


def _add_overlap(chunks: list[str], chunk_size: int, overlap: int) -> list[str]:
    """给相邻片段加上重叠内容（从上一片段尾部截取）。"""
    if overlap <= 0 or len(chunks) <= 1:
        return chunks

    result = [chunks[0]]
    for prev, cur in zip(chunks, chunks[1:]):
        tail = prev[-overlap:] if len(prev) > overlap else prev
        # 避免重复拼接已经相同的内容
        if tail and not cur.startswith(tail):
            merged = f"{tail}\n{cur}"
            result.append(merged[: chunk_size + overlap])
        else:
            result.append(cur)
    return result


def split_text(
    text: str,
    chunk_size: int = 600,
    overlap: int = 80,
) -> list[str]:
    """统一入口：章节切分 → 递归细分 → 加重叠。"""
    text = (text or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    if not text:
        return []
    if chunk_size <= 0:
        chunk_size = 600

    chunks: list[str] = []
    for section in split_by_heading(text):
        if len(section) <= chunk_size:
            chunks.append(section)
        else:
            chunks.extend(_split_recursive(section, chunk_size))
    return _add_overlap([c for c in chunks if c.strip()], chunk_size, overlap)
