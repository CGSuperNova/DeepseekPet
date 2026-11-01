# DeepseekPet 🐋

一个基于 **Paper 1.21.11** 与 **ItemsAdder 4.x** 的 Minecraft 宠物插件：在服务器里生成一只
「鲸鱼娘」——它会像原版狼一样跟随你，头顶悬浮着「玩家 XXX 的鲸鱼娘」，可以点击交互、
跟它聊天、答疑解惑，还会在特定时间/条件随机冒出一句台词。

> 角色形象与设定来自开源二创项目 [deepseek-whalechan](https://github.com/DeepSeekAI/deepseek-whalechan)
> （非 DeepSeek 官方出品）。本插件与 deepseek-whalechan 一样属于社区同人创作。

- **作者**：buzizi
- **版本**：免费版（Free）/ 付费版（Premium），共享同一套核心
- **AI 能力**：本地检索（RAG）+ 云端生成，角色卡说话风格

---

## 目录

1. [功能特性](#功能特性)
2. [整体架构](#整体架构)
3. [环境要求](#环境要求)
4. [构建（Maven）](#构建maven)
5. [部署](#部署)
6. [使用](#使用)
7. [配置](#配置)
8. [Free 与 Premium](#free-与-premium)
9. [项目结构](#项目结构)
10. [许可证与版权](#许可证与版权)

---

## 功能特性

### 免费版（Free）

- 🐋 **鲸鱼娘实体**：进服自动生成在玩家身边，外观为 Minecraft 方块风格的鲸鱼娘模型，
  移动/跟随直接复用**原版狼 AI**（驯服 + 跟随 + 传送）。
- 🏷️ **头顶悬浮字**：显示「玩家 XXX 的鲸鱼娘」。
- 🖱️ **交互 GUI**：右键点击鲸鱼娘打开自定义界面（1.21+ Inventory GUI），
  内含「与鲸鱼娘对话」按钮。
- 💬 **对话系统**：点击按钮后进入对话框（聊天栏输入），鲸鱼娘用角色卡人格回复。
  - 本地检索（RAG）+ 云端生成；
  - **持久记忆**：记住和每个玩家聊过的内容（像和认识的人说话）。
- ⏰ **定时/条件随机台词**：满足条件时从固定文本随机抽一句，以悬浮字显示在头顶并播放
  UI 点击音效。支持条件：
  - 现实特定时间点（如凌晨 1:00）或时间段（可跨午夜）；
  - 指定日期段、单双周、工作日/休息日；
  - 每隔固定时长执行一次。
- 🎛️ **收起/释放**：`/dp show|hide|toggle` 控制鲸鱼娘显隐。

### 付费版（Premium）

- 包含免费版全部内容；
- 额外 premium 专有功能（通过 `PremiumFeatureRegistry` 扩展）：
  - **长期记忆摘要**：历史超过上限时，自动把较早对话概括成长期记忆沉淀到本地，
    让鲸鱼娘记得更久；
- 发布构建支持 **ProGuard 混淆**，防止付费版逻辑被轻易反编译。

> 后续新增的付费功能会在更新日志中明确标注「Premium 专有」。

---

## 整体架构

```
┌────────────────────────────────────────────────────────────────┐
│  DeepseekPet 插件 (Paper 1.21.11, Java 21)                     │
│                                                                │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐  │
│  │ 宠物实体层   │  │ 交互层       │  │ AI 层                   │  │
│  │ PetManager   │  │ GUI / 对话框 │  │ ChatService             │  │
│  │ ItemsAdder   │  │ 指令 / 监听  │  │  ├ RagClient (HTTP)     │  │
│  │ 狼 AI + 模型 │  │ 悬浮字/音效  │  │  ├ LlmClient (HTTP)     │  │
│  └─────────────┘  └─────────────┘  │  └ MemoryStore(持久记忆) │  │
│                                     └────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                    │ HTTP  (127.0.0.1:8100)
                    ▼
        ┌───────────────────────────┐
        │  rag-bridge (Python 侧车)  │
        │  本地检索 + 云端生成        │
        │  鲸鱼娘角色卡 persona.txt  │
        └───────────────────────────┘
```

对应开发大纲的四步：

1. **模型生成** → `model/whalechan.bbmodel`（方块风鲸鱼娘）+ `whalechan.png` 贴图；
2. **模型导入** → `itemsadder-content/whalechan/`（ItemsAdder 实体配置 + 模型/贴图）；
3. **问答系统** → `rag-bridge/`（本地检索 + 云端生成 + 角色卡）；
4. **行为规划** → 跟随（狼 AI）、悬浮字、交互 GUI、对话框、持久记忆、随机台词、收起/释放。

---

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| Paper | 1.21.11 | 服务端 |
| Java | 21 | 编译与运行 |
| ItemsAdder | 4.x（本项目基于 4.0.15 开发） | 模型 / 自定义实体 |
| ProtocolLib | ItemsAdder 自带依赖 | ItemsAdder 需要 |
| Python | 3.10+ | 运行 rag-bridge（rag-bridge 使用 3.14 验证过语法） |
| Maven | 3.8+ | 构建插件 |

---

## 构建（Maven）

在项目根目录执行：

```bash
# 免费版（普通构建）
mvn clean package -pl deepseekpet-free -am

# 付费版（普通构建，未混淆，用于开发调试）
mvn clean package -pl deepseekpet-premium -am

# 付费版（发布构建：混淆）
mvn clean package -pl deepseekpet-premium -am -Pobfuscate
```

产物：

- `deepseekpet-free/target/DeepseekPet-Free-1.0.0.jar`
- `deepseekpet-premium/target/DeepseekPet-Premium-1.0.0.jar`
- `deepseekpet-premium/target/DeepseekPet-Premium-1.0.0-obf.jar`（`-Pobfuscate` 时产生）

> 首次构建需要联网拉取 paper-api、ItemsAdder API（JitPack）等依赖。
> ItemsAdder API 使用 `com.github.LoneDev6:API-ItemsAdder`，若无法访问 JitPack，
> 可改用本地 jar：`mvn install:install-file -Dfile=ItemsAdder_4.0.15.jar -DgroupId=com.github.LoneDev6 -DartifactId=API-ItemsAdder -Dversion=<ver> -Dpackaging=jar`。

---

## 部署

### 1. 放置插件与依赖

1. 服务器安装 **Paper 1.21.11**、**ItemsAdder 4.x**（及 ProtocolLib）；
2. 把构建出的 jar 放入 `plugins/`（免费版与付费版二选一，不可同时装）；
3. 启动服务器一次，生成默认配置后按需修改 `plugins/DeepseekPet/config.yml`。

### 2. 导入鲸鱼娘模型到 ItemsAdder

模型源码在 `model/`，ItemsAdder 内容在 `itemsadder-content/whalechan/`。完整步骤见
[`model/EXPORT.md`](model/EXPORT.md)，简要流程：

1. 用 BlockBench（≥4.1.5）+ ItemsAdder 扩展打开 `model/whalechan.bbmodel`；
2. File → Convert Project → 选 `ItemsAdder Entity Model`；
3. 导出得到 `.iaentitymodel`；
4. 把 `.iaentitymodel` + `whalechan.png` + `configs/whalechan.yml` 放进
   `plugins/ItemsAdder/contents/whalechan/`；
5. 服务器内执行 `/iazip` 重载资源包。

### 3. 部署 rag-bridge（AI 侧车）

```powershell
cd rag-bridge
py -m pip install -r requirements.txt
copy .env.example .env      # 填 DEEPSEEK_API_KEY
# （可选）放入领域文档 data/ 后： py ingest.py
py server.py                # 默认 127.0.0.1:8100
```

详细见 [`rag-bridge/README.md`](rag-bridge/README.md)。

---

## 使用

- 玩家进服后，鲸鱼娘自动生成在身旁，头顶显示「玩家 XXX 的鲸鱼娘」；
- **右键点击鲸鱼娘** → 打开交互 GUI → 点「💬 与鲸鱼娘对话」→ 进入对话框，
  之后在聊天栏直接打字即可发给鲸鱼娘；输入 `/dp end` 结束对话；
- 指令：

| 指令 | 说明 |
|---|---|
| `/dp show` | 召唤鲸鱼娘 |
| `/dp hide` | 收起鲸鱼娘 |
| `/dp toggle` | 显示 / 隐藏切换 |
| `/dp end` | 结束与鲸鱼娘的对话 |
| `/dp info` | 查看版本 |
| `/dp reload` | 重载配置（`deepseekpet.admin`） |

---

## 配置

- `plugins/DeepseekPet/config.yml` — 主配置（宠物、AI、随机台词、提示文本）；
- `plugins/DeepseekPet/persona.yml` — 鲸鱼娘角色卡（system prompt 来源）；
- `plugins/DeepseekPet/ambient.yml` — 定时/条件随机台词规则；
- `rag-bridge/persona.txt` — rag-bridge 侧的角色卡（与 persona.yml 对应）。

随机台词条件示例（`ambient.yml`）：

```yaml
rules:
  - id: "late-night-1am"
    enabled: true
    when:
      time: "01:00"
    lines:
      - "大半夜的还不睡吗"
      - "本鲸都要困死了，你怎么还不睡"
      - "不要再熬夜了，本鲸想睡觉了"
```

---

## Free 与 Premium

| 功能 | Free | Premium |
|---|---|---|
| 鲸鱼娘实体（狼 AI 跟随） | ✅ | ✅ |
| 头顶悬浮字 | ✅ | ✅ |
| 交互 GUI + 对话框 | ✅ | ✅ |
| RAG + 云端 AI + 持久记忆 | ✅ | ✅ |
| 定时/条件随机台词 | ✅ | ✅ |
| 收起 / 释放 | ✅ | ✅ |
| 长期记忆摘要 | ❌ | ✅ |
| 代码混淆 | ❌ | ✅ |

付费版为**免费版的完整超集**，且用同一个插件名（`DeepseekPet`），可无缝替换免费版。

---

## 项目结构

```
DeepseekPet/
├── pom.xml                          # 父工程
├── deepseekpet-common/              # 共享核心（free/premium 都用）
├── deepseekpet-free/                # 免费版
├── deepseekpet-premium/             # 付费版（含 ProGuard 混淆）
├── itemsadder-content/whalechan/    # ItemsAdder 内容（实体配置 + 模型 + 贴图）
├── model/                           # 鲸鱼娘 3D 模型源文件 + 导入指南
├── rag-bridge/                      # 本地检索 + 云端生成（Python 侧车）
├── wiki/                            # MediaWiki 格式 wiki
├── README.md
├── CHANGELOG.md                     # Keep a Changelog
└── .gitignore
```

---

## 许可证与版权

- 本插件的代码（`deepseekpet-*`、`rag-bridge`）采用 MIT 许可证；
- 鲸鱼娘角色设定与视觉资产来自 [deepseek-whalechan](https://github.com/DeepSeekAI/deepseek-whalechan)，
  其角色美术素材受 CC BY-NC-SA 4.0 约束，请遵守其许可（详见其 `LICENSE-CC-BY-NC-SA-4.0`）；
- DeepSeek 及相关品牌名称归其各自所有者所有，本插件为社区同人项目，非官方出品。
