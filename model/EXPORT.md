# 鲸鱼娘 3D 模型 · 导入指南

本目录包含鲸鱼娘的 **Minecraft 风格方块模型**（BlockBench 源文件）与基础贴图。

- `whalechan.bbmodel` — BlockBench 模型源文件（可用 BlockBench 打开编辑）
- `whalechan.png` — 64×64 基础贴图（分区着色，可替换成精细像素画）
- `generate-texture.ps1` — 重新生成基础贴图的脚本（PowerShell 执行）

## 设计说明

按 deepseek-whalechan 的「鲸鱼娘」角色卡，模型采用默认 **semi-chibi（约 2.5 头身）** 方块比例：

| 部件 | 说明 | 颜色 |
|---|---|---|
| 头部 | 白发 + 蓝光大眼 + 呆毛 | 白 / 蓝眼 |
| 鳍耳 | 头两侧的鲸类鳍耳 | 蓝 |
| 身体 | 萌系女仆装（白围裙） | 深蓝 / 白 |
| 手臂 / 腿 | 女仆装袖子 / 白袜 | 白 |
| 鲸尾 | 身后的鲸鱼尾鳍 | 蓝 |

模型采用骨骼（bone）分组：`head` / `body` / `left_arm` / `right_arm` /
`left_leg` / `right_leg` / `tail`，方便后续在 BlockBench 里加行走、坐下等动画。

## 导入 ItemsAdder 的步骤（需要 BlockBench 桌面版）

> 模型最终要导出成 ItemsAdder 的 `.iaentitymodel` 格式。该格式由
> 「ItemsAdder Entity」扩展生成，无法脱离 BlockBench 手工编写，因此
> 请按下面步骤在 BlockBench 中导出一次。

1. **安装 BlockBench**（≥ 4.1.5）：https://www.blockbench.net/ （请用桌面版，不要用网页版）
2. **安装 ItemsAdder 扩展**：https://github.com/ItemsAdder/itemsadder-blockbench-releases/releases
   （解压后按扩展说明放入 BlockBench 的 plugins 目录）
3. 用 BlockBench 打开 `whalechan.bbmodel`。
   - 若贴图未自动关联，手动把 `whalechan.png` 导入为该模型的纹理。
4. 点击菜单 **File → Convert Project**，格式选择 **`ItemsAdder Entity Model`**，确认。
5. 在「ItemsAdder」标签页点 **Settings**：
   - 命名空间（namespace）设为 `whalechan`；
   - 模型缩放模式保持默认（或按需调整）。
6. 点 **ItemsAdder → Export**，得到 `.iaentitymodel` 文件。
7. 把导出的 `.iaentitymodel` 和 `whalechan.png` 放进服务器
   `plugins/ItemsAdder/contents/whalechan/entity/whalechan/` 目录：
   ```
   plugins/ItemsAdder/contents/whalechan/
   ├── configs/whalechan.yml          # 本仓库 itemsadder-content/whalechan/configs/whalechan.yml
   └── entity/whalechan/
       ├── whalechan.iaentitymodel    # 第 6 步导出
       └── whalechan.png              # 本仓库贴图
   ```
8. 服务器内执行 `/iazip`（或 `/iareload`）重新生成资源包并重载。

> 本仓库 `itemsadder-content/whalechan/` 已经按上述结构放好了
> `configs/whalechan.yml`、贴图与 `.bbmodel`，你只需把第 6 步导出的
> `.iaentitymodel` 补进去即可。

## 后续美化建议

- 把 `whalechan.png` 替换成精细的像素画（保持 64×64 或按需放大 UV）；
- 在 BlockBench 中为骨骼添加 `walk` / `idle` / `sit` 等动画，让鲸鱼娘更生动；
- （Premium 喂食）可额外添加 `eat`（进食）/ `pat_belly`（拍拍肚子）等动画，
  然后在 `plugins/DeepseekPet/feeding/foods.yml` 里为食物配置 `animation` / `after-animation`，
  插件会在喂食时自动播放；
- 保持方块（voxel）风格，遵循 Minecraft 风格指南。
