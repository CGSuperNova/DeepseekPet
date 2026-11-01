"""
领域知识问答系统 · 独立 HTTP 服务
==================================

供其他程序通过网络调用本问答系统。基于 Flask + waitress，无需 ASGI 环境。

启动：
    python server.py                     # 用 .env 里的 HOST/PORT
    python server.py --port 8100 --host 0.0.0.0
    python server.py --threads 8

接口：
    GET  /health          健康检查（无需鉴权）
    GET  /status          知识库与模型状态
    POST /ask             提问并返回答案（含引用来源）
    POST /ask/stream      流式返回（SSE）
    POST /search          只检索，不调用大模型（快、省钱）

鉴权（可选）：
    在 .env 里设置 API_KEYS=k1,k2；设置后请求需带
        Authorization: Bearer <key>
    或  X-API-Key: <key>
    留空则不鉴权（仅建议本机或内网使用）。

跨域（可选）：.env 里 CORS_ORIGINS=http://localhost:3000,https://foo.com
"""

from __future__ import annotations

import argparse
import json
import logging
import time

from flask import Flask, Response, jsonify, request, stream_with_context

import config
from service import ServiceError, answer_question, search
from whalechan import chat as whalechan_chat

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
logger = logging.getLogger("qa.server")

app = Flask(__name__)
app.json.ensure_ascii = False

MAX_QUESTION_CHARS = 2000
MAX_HISTORY_TURNS = 12


# ------------------------------------------------------------------ 工具
def _ok(data=None, **extra):
    payload = {"ok": True}
    if data is not None:
        payload["data"] = data
    payload.update(extra)
    return jsonify(payload)


def _fail(message: str, status: int = 400, **extra):
    payload = {"ok": False, "error": message}
    payload.update(extra)
    return jsonify(payload), status


def _supplied_key() -> str:
    auth = request.headers.get("Authorization") or ""
    if auth.lower().startswith("bearer "):
        return auth[7:].strip()
    return (request.headers.get("X-API-Key") or "").strip()


def _clean_history(raw) -> list[dict]:
    if not isinstance(raw, list):
        return []
    out = []
    for turn in raw[-MAX_HISTORY_TURNS:]:
        if not isinstance(turn, dict):
            continue
        role = turn.get("role")
        content = str(turn.get("content") or "").strip()[:4000]
        if role in ("user", "assistant") and content:
            out.append({"role": role, "content": content})
    return out


# ------------------------------------------------------------------ 中间件
@app.before_request
def _guard():
    if request.method == "OPTIONS":
        return ("", 204)
    # /health 永远放行，便于探活
    if request.path == "/health":
        return None
    if config.API_KEYS:
        supplied = _supplied_key()
        if not supplied:
            return _fail("缺少 API Key（Authorization: Bearer <key> 或 X-API-Key: <key>）", 401)
        if supplied not in config.API_KEYS:
            return _fail("API Key 无效", 401)
    return None


@app.after_request
def _cors(resp):
    if config.CORS_ORIGINS:
        origin = request.headers.get("Origin") or ""
        if origin in config.CORS_ORIGINS:
            resp.headers["Access-Control-Allow-Origin"] = origin
            resp.headers["Vary"] = "Origin"
            resp.headers["Access-Control-Allow-Headers"] = "Authorization, X-API-Key, Content-Type"
            resp.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    return resp


# ------------------------------------------------------------------ 接口
@app.get("/health")
def health():
    return jsonify({"ok": True, "service": "local-rag-qa", "collection": config.COLLECTION_NAME})


@app.get("/status")
def status():
    info = {
        "collection": config.COLLECTION_NAME,
        "data_dir": str(config.DATA_DIR),
        "db_dir": str(config.DB_DIR),
        "embed_model": config.EMBED_MODEL,
        "rerank_enabled": config.RERANK_ENABLED,
        "rerank_model": config.RERANK_MODEL if config.RERANK_ENABLED else None,
        "llm_model": config.DEEPSEEK_MODEL,
        "llm_configured": bool(config.DEEPSEEK_API_KEY),
    }
    try:
        from retriever import retriever

        info["documents"] = retriever.count()
    except Exception as exc:
        info["documents"] = None
        info["note"] = str(exc)
    return _ok(info)


@app.post("/ask")
def ask():
    body = request.get_json(silent=True) or {}
    question = str(body.get("question") or "").strip()
    if not question:
        return _fail("question 不能为空")
    if len(question) > MAX_QUESTION_CHARS:
        return _fail(f"question 过长，最多 {MAX_QUESTION_CHARS} 字")

    history = _clean_history(body.get("history"))
    started = time.time()
    try:
        result = answer_question(question, history)
    except ServiceError as exc:
        return _fail(str(exc), status=409)
    except Exception as exc:  # pragma: no cover
        logger.exception("问答失败")
        return _fail(f"服务内部错误：{exc}", status=500)
    result["elapsed"] = round(time.time() - started, 2)
    return _ok(result)


@app.post("/chat")
def chat():
    """鲸鱼娘对话：本地检索 + 角色卡生成；无资料时自由聊天。"""
    body = request.get_json(silent=True) or {}
    question = str(body.get("question") or "").strip()
    if not question:
        return _fail("question 不能为空")
    if len(question) > MAX_QUESTION_CHARS:
        return _fail(f"question 过长，最多 {MAX_QUESTION_CHARS} 字")

    history = _clean_history(body.get("history"))
    started = time.time()
    try:
        result = whalechan_chat(question, history)
    except ServiceError as exc:
        return _fail(str(exc), status=409)
    except Exception as exc:  # pragma: no cover
        logger.exception("鲸鱼娘对话失败")
        return _fail(f"服务内部错误：{exc}", status=500)
    result["elapsed"] = round(time.time() - started, 2)
    return _ok(result)


@app.post("/ask/stream")
def ask_stream():
    """SSE 流式返回。事件：status / sources / delta / error / done"""
    body = request.get_json(silent=True) or {}
    question = str(body.get("question") or "").strip()
    if not question:
        return _fail("question 不能为空")

    history = _clean_history(body.get("history"))

    def sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

    def generate():
        import llm
        from retriever import format_sources
        from service import retrieve_contexts

        yield sse("status", {"message": "正在检索知识库…"})
        started = time.time()
        try:
            contexts = retrieve_contexts(question)
        except ServiceError as exc:
            yield sse("error", {"error": str(exc)})
            return

        sources = format_sources(contexts)
        yield sse("sources", {"sources": sources})
        if not contexts:
            yield sse("delta", {"text": config.NO_CONTEXT_REPLY})
            yield sse("done", {"elapsed": round(time.time() - started, 2),
                               "from_knowledge_base": False})
            return

        try:
            for chunk in llm.stream(question, contexts, history):
                if chunk.get("type") == "delta":
                    yield sse("delta", {"text": chunk["text"]})
                elif chunk.get("type") == "reasoning":
                    yield sse("reasoning", {"text": chunk["text"]})
                elif chunk.get("type") == "done":
                    yield sse("done", {
                        "usage": chunk.get("usage"),
                        "elapsed": round(time.time() - started, 2),
                        "from_knowledge_base": True,
                    })
        except Exception as exc:  # pragma: no cover
            logger.exception("流式生成失败")
            yield sse("error", {"error": f"生成失败：{exc}"})

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            # 注意：不要设置 Connection 头 —— 它是 hop-by-hop 头，
            # waitress 会直接抛 AssertionError（PEP 3333）。
        },
    )


@app.post("/search")
def search_only():
    body = request.get_json(silent=True) or {}
    question = str(body.get("question") or "").strip()
    if not question:
        return _fail("question 不能为空")
    try:
        return _ok(search(question))
    except ServiceError as exc:
        return _fail(str(exc), status=409)


@app.get("/")
def index():
    return _ok({
        "service": "本地检索 + 云端生成的领域知识问答系统",
        "endpoints": [
            {"method": "GET", "path": "/health", "auth": False, "desc": "健康检查"},
            {"method": "GET", "path": "/status", "auth": bool(config.API_KEYS), "desc": "知识库与模型状态"},
            {"method": "POST", "path": "/ask", "auth": bool(config.API_KEYS),
             "body": {"question": "string(必填)", "history": "[{role,content}](可选)"},
             "desc": "提问并返回答案"},
            {"method": "POST", "path": "/ask/stream", "auth": bool(config.API_KEYS), "desc": "SSE 流式问答"},
            {"method": "POST", "path": "/search", "auth": bool(config.API_KEYS), "desc": "只检索不生成"},
        ],
        "auth_header": "Authorization: Bearer <key>  或  X-API-Key: <key>",
    })


# ------------------------------------------------------------------ 入口
def main() -> None:
    parser = argparse.ArgumentParser(description="领域知识问答系统 · HTTP 服务")
    parser.add_argument("--host", default=config.HOST)
    parser.add_argument("--port", type=int, default=config.PORT)
    parser.add_argument("--threads", type=int, default=config.THREADS)
    parser.add_argument("--debug", action="store_true", help="用 Flask 开发服务器启动（带热重载）")
    args = parser.parse_args()

    config.ensure_dirs()
    logger.info("配置概览：%s", json.dumps(config.summary(), ensure_ascii=False))
    if not config.API_KEYS:
        logger.warning("未设置 API_KEYS，接口将不鉴权；对外提供服务前请务必设置。")

    if args.debug:
        app.run(host=args.host, port=args.port, debug=True)
    else:
        from waitress import serve

        # 后台预热模型：首次加载约 30~40 秒，预热后第一个请求不必等
        try:
            from embeddings import warm_up

            warm_up(background=True)
            logger.info("已在后台预热嵌入 / 重排模型")
        except Exception:
            logger.exception("模型预热启动失败")

        logger.info("服务已启动：http://%s:%s", args.host, args.port)
        serve(app, host=args.host, port=args.port, threads=args.threads)


if __name__ == "__main__":
    main()
