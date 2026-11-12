# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- 新增 MIT 许可证（`LICENSE`）。
- 付费版 `deepseekpet-premium/` 改为私有模块：加入 `.gitignore`，不再推送到公开仓库；
  本地构建需显式激活 `-Ppremium` profile。
- 新增喂食系统（free / premium 均含）：
  - 鲸鱼娘拥有 20 点饱食度，随时间自然减少、每次主动对话也会消耗；
  - 手持可食用物品右键（双击确认）喂食，恢复值默认取原版食物，可在 `feeding/foods.yml` 覆盖；
  - 饥饿值低于阈值时自主喊饿，归零时拒绝对话；
  - 配置独立放在 `plugins/DeepseekPet/feeding/`；
  - 新增 `/dp hunger` 查看饱食度。
- 【Premium 专有】喂食表现：可配置每种食物吃下后的台词、自定义命令、进食/进食后动画。
- 新增 ItemsAdder 缺失时的降级方案（free / premium 均含）：
  - 一级：Citizens 玩家模型 NPC（无动画，导航跟随）；
  - 二级：原版盔甲架（蓝色皮革甲 + 玩家头颅，无动画，超出距离传送跟随并避免卡墙）。
- 新增跟随开关 `/dp follow [on|off]`：关闭后不再跟随（含跨世界），重新生成时回到玩家身边。
- 新增更丰富的信息读取并注入对话：
  - free：Vault 金币、背包 + 末影箱摘要；
  - premium：PlaceholderAPI Player 扩展、CMI 采纳变量（依据 `CMI 采纳变量合集.md`）、自定义 PAPI 变量；
  - 带缓存（默认 3 秒），注意性能。
- 新增日志系统：`plugins/DeepseekPet/logs/deepseekpet.log` 记录运行时与玩家操作事件。
- 新增 `/dp gui` 命令：直接打开鲸鱼娘主界面，无需点击实体（free / premium 均可用）。

### Changed / Fixed

- 盔甲架降级改进：改为**小型盔甲架**（约一格高）；头颅支持 base64 自定义皮肤
  （`fallback.armorstand.head-texture`）；装备受保护，右键只打开 GUI / 喂食，无法替换模型装备。

### Added（可攻击 / 系统化）

- **可攻击（free / premium）**：鲸鱼娘可被攻击，但伤害被取消（无敌、永不死亡）；
  被玩家攻击时随机说出 `attack.lines` 中的反馈台词，冷却见 `attack.cooldown-seconds`。
- **系统化（free / premium）**：代码按 7 大板块组织（Avatar / Status / Interaction /
  Communication / Cognition / Context / Platform），并新增 `BoardExtension` + `BoardRegistry`
  扩展点，Premium 通过 `PremiumFeatureRegistry` 注册实现，Free / Premium 共用同一套骨架。

### Fixed

- 盔甲架右键无反应：补上 `PlayerInteractAtEntityEvent` 监听（盔甲架走 INTERACT_AT 而非 INTERACT），
  现在右键可正常打开 GUI / 喂食。
- `/dp gui` 改用 Paper 1.21.6+ 的 Menu Type API（`MenuType.GENERIC_9X1`）创建原生菜单视图，
  不再使用旧式 `Bukkit.createInventory`。

## [1.0.0] - 2026-10-31

### Added

- 生成鲸鱼娘 Minecraft 方块风格 3D 模型（`model/whalechan.bbmodel`）与基础贴图（`whalechan.png`）。
- 新增 ItemsAdder 实体配置（`whalechan.yml`），以原版狼（`type: WOLF`）作为基座实体，
  使鲸鱼娘移动/跟随复用原版狼 AI。
- 玩家进服自动生成鲸鱼娘，头顶悬浮字显示「玩家 XXX 的鲸鱼娘」。
- 右键点击鲸鱼娘打开交互 GUI（1.21+ Inventory 自定义界面），含「与鲸鱼娘对话」按钮。
- 新增对话框（聊天栏输入）与鲸鱼娘对话；采用「本地检索（RAG）+ 云端生成」，
  并以鲸鱼娘角色卡（`persona.yml` / `rag-bridge/persona.txt`）说话，不再沿用专业术语式回答。
- 新增持久记忆（`MemoryStore`）：按玩家把对话历史持久化到本地 JSON，
  让鲸鱼娘像和认识的人一样记得之前的聊天。
- 新增定时/条件随机台词（`AmbientLineManager`）：支持时间点、时间段（可跨午夜）、
  日期段、单双周、工作日/休息日、固定间隔等条件，满足时随机输出一句固定文本，
  以悬浮字显示在头顶并播放客户端 UI 点击音效。
- 新增收起/释放指令：`/dp show`、`/dp hide`、`/dp toggle`。
- 新增 rag-bridge 侧车服务：`/chat`（有资料→RAG，无资料→自由聊天）、`/health`、`/ask`、`/search`。
- 拆分 free / premium 双版本（Maven 多模块），premium 为 free 完整超集。
- premium 新增「长期记忆摘要」专有功能（`LongTermMemoryFeature`）。
- premium 提供 ProGuard 混淆配置（`proguard.pro`，`mvn package -Pobfuscate`）。

### Changed

- 无。

### Deprecated

- 无。

### Removed

- 无。

### Fixed

- 无。

### Security

- 无。
