# DeepseekPet · rag-bridge（本地检索 + 云端生成 · 鲸鱼娘角色卡）

本目录是 DeepseekPet 插件的 **AI 侧车服务**：由 `local_rag_qa` 的思路改写而来，
为鲸鱼娘提供「本地检索 + 云端生成」的问答能力，并始终以 **鲸鱼娘角色卡** 作为
system prompt 回答（不再使用 local_rag_qa 里那种专业术语式的回答）。

- 检索在本地：`bge-m3` 向量检索 + `jieba`/BM25 关键词检索 → RRF 融合 → 重排
- 生成在云端：调用 OpenAI 兼容接口（默认 DeepSeek）
- 角色卡：`persona.txt`（依据 deepseek-whalechan 设定整理）

## 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| GET | `/status` | 知识库 / 模型状态 |
| POST | `/chat` | **鲸鱼娘对话**（插件使用；有资料→RAG，无资料→自由聊天） |
| POST | `/ask` | 原版专业问答（保留，兼容调试） |
| POST | `/ask/stream` | 流式问答（SSE） |
| POST | `/search` | 只检索不生成 |

`/chat` 请求体：

```json
{
  "question": "鲸鱼娘，1+1 等于几？",
  "history": [
    { "role": "user", "content": "你好呀" },
    { "role": "assistant", "content": "主人好呀，本鲸已经就位啦 🐋" }
  ]
}
```

`/chat` 响应体：

```json
{
  "ok": true,
  "data": {
    "answer": "当然是 2 啦，主人！本鲸可不会算错～",
    "from_knowledge_base": false,
    "sources": [],
    "mode": "free",
    "elapsed": 0.8
  }
}
```

- `mode = "rag"`：命中了本地知识库，回答依据资料 + 角色卡生成；
- `mode = "free"`：未命中资料，直接按角色卡自由聊天。

## 快速部署（Windows）

```powershell
cd rag-bridge
py -m pip install -r requirements.txt
copy .env.example .env          # 填入 DEEPSEEK_API_KEY
# （可选）把领域文档放进 data/，然后建索引：
py ingest.py
# 启动服务（插件默认连 127.0.0.1:8100）
py server.py
```

## 配置要点

- 所有配置都可通过 `.env` 覆盖，见 `.env.example`。
- `persona.txt` 是鲸鱼娘角色卡；改它即可调整鲸鱼娘的说话风格，
  改完重启服务即可，无需重建索引。
- `HOST`/`PORT` 默认 `127.0.0.1:8100`，与插件 `config.yml` 里的 `ai.rag-url` 对应。
- 若设置 `API_KEYS`，插件 `config.yml` 里的 `ai.rag-api-key` 需填同一个 key。

## 性能优化

针对 local_rag_qa 的检索耗时问题做了四层优化（详见 `.env.example` 注释）：

| 优先级 | 优化项 | 说明 |
|---|---|---|
| P0 | BM25 换成 `bm25s` + 去全量内存 | 不再把全量文档拉进内存；BM25 用稀疏矩阵 + 内存映射，快 1~2 个数量级；`/status` 的 count 走 ChromaDB O(1) |
| P0 | 查询级 LRU 缓存 | 相同问题整条检索链路直接命中，0 成本返回（`QUERY_CACHE_*`） |
| P0 | 修正单候选跳过重排 | 单候选不再白跑一次重排 |
| P1 | 重排串行化 | 多请求并发下防线程超订（`RERANK_SERIAL`） |
| P1 | 重排 ONNX fp32 | 约 1.33× 加速、排序完全一致；先跑 `export_rerank_onnx.py` 导出再配置 |
| P1 | 语义缓存 | 相似问题复用结果，P95 秒级→毫秒级（`SEMANTIC_CACHE_*`） |
| P2 | 动态 TOP_K_RERANK | 简单查询少送重排、难查询多送（`DYNAMIC_RERANK_*`） |
| P2 | 换更小重排模型 | `bge-reranker-base` 3.84× 加速，但 top-3 overlap 约 80%，需自测后切换 |

### ONNX 重排（可选，P1）

```powershell
py export_rerank_onnx.py --model BAAI/bge-reranker-v2-m3 --output bge-reranker-v2-m3.onnx
```

然后在 `.env` 里设置 `RERANK_ONNX_PATH` 为导出文件的绝对路径、`RERANK_BACKEND=auto`。
加载失败会自动回退到 PyTorch，不影响可用性。

### 换更小的重排模型（可选，P2，有精度代价）

`bge-reranker-base`（277MB）比 `bge-reranker-v2-m3` 快约 3.84×，但 top-3 overlap 只有 80%。
对「答案必须出自 top-3」要求高时保持 v2-m3；对延迟更敏感时可切 base，但**切换前务必用自己的
eval 集跑一遍 top-k 对比**：

```dotenv
RERANK_MODEL=BAAI/bge-reranker-base
```

## 与 local_rag_qa 的区别

| 项目 | local_rag_qa | rag-bridge |
|---|---|---|
| system prompt | 领域专家助手（专业术语） | 鲸鱼娘角色卡 |
| 无资料时 | 返回「知识库中没有…」 | 按角色卡自由聊天 |
| 新增接口 | `/ask` `/search` `/ask/stream` | 额外提供 `/chat` |
| 检索 / 重排 | bge-m3 + BM25 + RRF + reranker | 相同（复用同一套代码） |
