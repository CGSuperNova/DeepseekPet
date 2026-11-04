"""
把重排模型（默认 BAAI/bge-reranker-v2-m3）导出为 ONNX fp32。

用法：
    python export_rerank_onnx.py --model BAAI/bge-reranker-v2-m3 --output bge-reranker-v2-m3.onnx

导出后在 .env 里设置：
    RERANK_ONNX_PATH=/绝对路径/bge-reranker-v2-m3.onnx
    RERANK_BACKEND=auto     # auto 会优先 ONNX，失败回退 PyTorch

说明：
    - 只导出 fp32：动态 INT8 会显著改变 top-k 排序（top-1 overlap 约 0.625），
      对重排器不可接受，故本脚本只做 fp32（无损，约 1.33× 加速）。
    - 导出后可用一个小 eval 集跑一遍 top-k 对比，确认排序一致再上线。
"""

from __future__ import annotations

import argparse
from pathlib import Path


def export(model_name: str, output: str, max_length: int = 512) -> None:
    import torch
    from transformers import AutoModelForSequenceClassification, AutoTokenizer

    print(f"加载模型 {model_name} …")
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSequenceClassification.from_pretrained(model_name)
    model.eval()

    # XLM-RoBERTa 系（bge-reranker-v2-m3）没有 token_type_ids，只需 input_ids / attention_mask
    dummy = tokenizer(
        ["query"], ["passage"],
        padding=True, truncation=True, max_length=max_length, return_tensors="pt",
    )

    print(f"导出到 {output} …")
    torch.onnx.export(
        model,
        (dummy["input_ids"], dummy["attention_mask"]),
        output,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "logits": {0: "batch"},
        },
        opset_version=17,
    )
    print("完成。请在 .env 中设置 RERANK_ONNX_PATH 指向该文件，并设 RERANK_BACKEND=auto（或 onnx）。")


def main() -> None:
    parser = argparse.ArgumentParser(description="导出重排模型为 ONNX fp32")
    parser.add_argument("--model", default="BAAI/bge-reranker-v2-m3")
    parser.add_argument("--output", default="bge-reranker-v2-m3.onnx")
    parser.add_argument("--max-length", type=int, default=512)
    args = parser.parse_args()

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    export(args.model, str(out), args.max_length)


if __name__ == "__main__":
    main()
