"""
文档解析：把 .md / .docx / .pdf / .txt 统一解析为纯文本。

author: buzizi (2026级电子商务陈逸轩)
"""

from __future__ import annotations

import config  # noqa: F401  （导入即完成 HF_ENDPOINT 等环境准备）

from pypdf import PdfReader
from docx import Document
from markdown_it import MarkdownIt

MARKDOWN_EXTS = (".md", ".markdown")
WORD_EXTS = (".docx",)
PDF_EXTS = (".pdf",)
TEXT_EXTS = (".txt", ".text", ".log", ".yml", ".yaml", ".json", ".csv")

_md = MarkdownIt()


def parse_markdown(path: str) -> str:
    """解析 Markdown：去掉语法标记但保留标题层级，便于后续按章节切块。"""
    with open(path, "r", encoding="utf-8") as f:
        raw = f.read()

    tokens = _md.parse(raw)
    lines: list[str] = []
    for token in tokens:
        if token.type == "heading_open":
            lines.append("\n" + "#" * int(token.tag[1]) + " ")
        elif token.type == "inline" and token.content:
            lines.append(token.content)
        elif token.type in ("fence", "code_block") and token.content:
            lines.append("\n```\n" + token.content.strip() + "\n```\n")
    return "\n".join(lines)


def parse_word(path: str) -> str:
    """解析 .docx：提取段落文本与表格内容。"""
    doc = Document(path)
    paragraphs = [p.text.strip() for p in doc.paragraphs if p.text.strip()]
    for table in doc.tables:
        for row in table.rows:
            row_text = [cell.text.strip() for cell in row.cells if cell.text.strip()]
            if row_text:
                paragraphs.append(" | ".join(row_text))
    return "\n".join(paragraphs)


def parse_pdf(path: str) -> str:
    """解析 PDF：逐页提取文本。"""
    reader = PdfReader(path)
    pages = []
    for page in reader.pages:
        text = page.extract_text() or ""
        if text.strip():
            pages.append(text.strip())
    return "\n\n".join(pages)


def parse_text(path: str) -> str:
    """其他纯文本格式直接读取。"""
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        return f.read()


def parse_file(path: str) -> str:
    """统一入口：根据扩展名选择解析器。"""
    lower = str(path).lower()
    if lower.endswith(MARKDOWN_EXTS):
        return parse_markdown(path)
    elif lower.endswith(WORD_EXTS):
        return parse_word(path)
    elif lower.endswith(PDF_EXTS):
        return parse_pdf(path)
    return parse_text(path)
