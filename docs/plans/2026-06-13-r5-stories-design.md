# R5 用户故事设计 — 2026-06-13 发版 bundle 衍生的端侧待办

> 来源：`D:\work\code\dyqbackupdd\sql\mysql\2026-06-10-prod-发版统一-bundle.sql`
> 目标：从发版 bundle 中识别 PokeClaw 端侧可直接落地、或可预先埋点的用户故事。
> 关联：R3 (US-D-017~021) / R4 (US-D-022~023) 已完成；R5 集中在「发版 bundle 衍生的端侧能力」。

---

## 一、Bundle 内容摘要

| 分区 | 内容 | 端侧是否可消费 | 备注 |
|------|------|----------------|------|
| A1-1 `cloudphone_video_metrics_task` | 云手机 video 指标 | ❌ 后端内部表 | 端侧无埋点 |
| A1-2 `billing_vendor_charge_record` | WP-B 扣费真相源 | ⚠️ 间接受益 | 当 `submitTaskResult` 携带 `creditConsumed` 时可显示 |
| A1-3 `aigc_remake_scene` + 6 场景 seed | 视频复刻公共引擎 | ⚠️ 需新接口 | `GET /aigc/remake/scenes` 未在 `device.openapi.yaml` 暴露 |
| A1-3 字典 `native_audio` | muxi capability 字典 | ✅ 可消费 | 通过 Muxi 模型 API 间接获取 |
| A1-3 菜单权限 `aigc:remake-scene:*` | 运营管理 | ❌ 后端权限 | 端侧无关 |
| A2 `aigc_template_execution.publish_contents` | 执行记录文案 | ❌ 后端 | 端侧无关 |
| A2 `aigc_template` 5 列 + `uk_channel_source` | 模板渠道底座 | ⚠️ 需新接口 | 端侧需 `GET /aigc/template/page` 拉取 |
| A2 `aigc_template.preview_gif_url` | GIF 预览 | ⚠️ 需新接口 | 模板卡片渲染 |
| A3-7/8 PixVerse effect/image 模板 seed | 64 + 3 条 | ⚠️ 需新接口 | 端侧需拉取 + 渲染 |
| A3-10/11/12 billing_vendor_pricing_config 占位 | 4 个 vendor 定价 | ⚠️ 需新接口 | 端侧需拉取展示 |
| A4 4 个 Job | 缓存预热/同步/GIF/Pixverse 反推 | ❌ 后端调度 | 端侧无关 |
| B-16/17/18 PixVerse model capability | pixverse-extend / -text / -image-template | ✅ 可消费 | Muxi API 增量 |
| C-20 `aigc_template_usage_log` 扩字段 | 后端台账 | ❌ | 端侧无关 |
| C-21 `aigc_remake_scene.example_inputs` | 场景示例输入 | ⚠️ 需新接口 | 复刻表单一键回填 |

**关键约束**：`device.openapi.yaml` 当前不暴露 remake scene / aigc template / pricing / model capability 接口（见 `D:\work\code\dyqbackupdd\api-contracts\device.openapi.yaml` 共 12 个端点）。因此 R5 故事按「**端侧可立即落地**」与「**埋点/等接口**」两类区分。

---

## 二、R5 候选用户故事

### A. 端侧可立即落地（Muxi 模型注册侧）

#### US-D-024-MUXI-PIXVERSE-MODEL-REGISTRY (P1)
- **来源**：B-16/17/18 `muxi_model_capability` 三条 PixVerse 模型 seed（pixverse-extend / pixverse-text / pixverse-image-template）
- **端侧实现**：在 `LocalModelManager` 注册表 / Muxi 客户端能力探测中追加 3 个新 modelId
- **验收**：
  - `Muxi` 模型下拉/能力列表中出现 `pixverse-extend`、`pixverse-text`、`pixverse-image-template` 三项
  - 每项带有 `category` / `capabilities` 字段（如 `video_extend` / `text_to_image` / `image_to_image`）
  - 用户从云端拉取模型能力时（已有 `GET /muxi/capabilities` 之类接口）正确包含
  - 选定模型后，写入 `LocalModelRuntime` 的最近使用记录
- **影响文件**：`agent/llm/LocalModelManager.kt`、`agent/llm/MuxiCapability.kt`（如无则新建）

#### US-D-025-NATIVE-AUDIO-CAPABILITY-BADGE (P2)
- **来源**：A1-3 字典 `native_audio` (id 1780963200001, sort 90)
- **端侧实现**：Muxi 模型能力渲染时，识别 `capabilities` 包含 `native_audio` 时显示徽章
- **验收**：
  - Muxi 模型列表项右侧显示「🎙️ 原生带配音」小徽章
  - `native_audio` 字典 label 写死/从 KV 缓存
  - 模型降级提示中若不含此能力，加 "本模型无配音" 说明
- **影响文件**：`ui/settings/ModelPickerActivity.kt` 或对应 Compose 组件、`agent/llm/CapabilityBadge.kt`（新建）

### B. 间接受益（云端返回字段时即消费）

#### US-D-026-TASK-COST-CHIP-IN-CHAT (P1)
- **来源**：A1-2 `billing_vendor_charge_record` (amount_fen / credit_consumed / quota_consumed)
- **端侧实现**：`TaskResultRequest` DTO 增 `creditConsumed: Long?` 字段；ChatMessage 渲染时显示「花费 5 积分」小芯片
- **验收**：
  - 当 `submitTaskResult` 的 `result` JSON 包含 `creditConsumed` 时，ChatMessage 行末显示对应积分（红/黄/绿色 chip）
  - 0 或 null 时不显示
  - 字段容错：result 解析失败时降级为不显示
- **影响文件**：`cloud/model/CloudModels.kt`、`ui/chat/ChatMessage.kt`、`ui/chat/ChatMessageAdapter.kt`
- **上游依赖**：云端 `submitTaskResult` API 增 `creditConsumed` 字段（**非本 PRD 范围**；先建字段，零时不显示）

#### US-D-027-CS-AI-CREDIT-AWARE-TOAST (P3)
- **来源**：A3-12 `cs_ai` token 积分占位
- **端侧实现**：CS-AI 对话（已有 / 待建）显示 token 折算积分小提示
- **验收**：
  - CS-AI 对话时（如有），底部 toast「本次回复消耗约 N 积分」
  - 字段未就绪时回退为「积分计量未启用」
- **影响文件**：`channel/telegram/` 或 `chat/` 内 CS-AI 路径
- **上游依赖**：CS-AI 入口尚需客户端通道

### C. 埋点（等云端补接口后即可启用）

#### US-D-028-REMAKE-SCENE-PICKER (P2, deferred)
- **来源**：A1-3 6 场景 + A1-3 `example_inputs` 字段
- **端侧实现**：Chat 工具栏 + 复刻场景选择器（grid 6 卡），点击后将场景 + 示例输入回填到当前任务文本
- **验收**：
  - 工具栏新增「复刻」入口，点击展开 6 个场景卡
  - 场景卡显示 `name` + `description` + 缩略图（`example_image_url`）
  - 点击「使用此示例」→ 拼接 `styleDescription/productDescription/fixedScript` 等并填入 chat 输入框
  - 字段缺失时回退为仅显示场景名
- **影响文件**：`ui/chat/ChatInputBar.kt`、`ui/remake/ScenePickerSheet.kt`（新建）、`ui/remake/RemakeScene.kt`（新建）
- **上游依赖**：`GET /aigc/remake/scenes` 接口（`aigc:remake-scene:query` 权限已加）
- **兜底**：接口未上线前使用客户端 hardcode 的 6 场景（与 seed 同源），保持可演示

#### US-D-029-AIGC-TEMPLATE-PARAMS-FORM (P2, deferred)
- **来源**：A2-5/6 `aigc_template` 5 列（`paramsSchema`/`preview_gif_url`/`channel_code` 等）
- **端侧实现**：通用 paramsSchema 表单渲染器（基于现有 `CustomModelSource` 思路 + 动态 form 渲染）
- **验收**：
  - 接收 `paramsSchema = { fields: [{key, type, label, required, options/maxLen/max/min}] }` 时：
    - `text` → EditText（maxLength 生效）
    - `textarea` → 多行 EditText
    - `enum` → 单选 chips
    - `image[]` / `image` → 系统图片选择器（受 min/max 约束）
    - `int` → 数字输入（min/max 校验）
  - 必填校验：缺失必填字段时禁用提交按钮并红色提示
  - 提交时序列化为 `Map<String, Any>` 注入到 task payload
- **影响文件**：`ui/template/ParamsFormRenderer.kt`（新建）、`ui/template/TemplatePickerActivity.kt`（新建）
- **上游依赖**：`GET /aigc/template/page?channelCode=pixverse&type=video_effect` 类接口

#### US-D-030-CHANNEL-CODE-TEMPLATE-FILTER (P2, deferred)
- **来源**：A2-5 `aigc_template.channel_code` (pixverse/comfyui/muxi-canvas)
- **端侧实现**：模板列表顶部 chip 过滤：全部 / PixVerse / ComfyUI / Muxi Canvas
- **验收**：
  - 列表加载后展示 channel 过滤 chip
  - 选中后只显示对应 `channel_code` 的模板
  - 「全部」恢复显示
- **影响文件**：`ui/template/TemplatePickerActivity.kt`
- **上游依赖**：`GET /aigc/template/page?channelCode=...`

### D. 跨切（UX 微改，不依赖接口）

#### US-D-031-SETTINGS-BILLING-SECTION (P3)
- **背景**：bundle 频繁出现 WP-B 积分概念，端侧 Settings 缺一个集中的「计费 / 积分」入口
- **端侧实现**：Settings 新增「积分与计费」分组，列出 4 个 vendor 的当前 credit_cost 状态（云端拉取失败时显示「未配置」）
- **验收**：
  - 分组下 4 行：云手机时长 / 数字人按时长 / 数字人按次 / CS-AI 按 token
  - 每行右侧显示当前 credit_cost（成功）/「未配置」（失败）
  - 点击行打开对应 vendor 详情页（占位：Toast "请前往 dyq 后台调整"）
- **影响文件**：`ui/settings/SettingsActivity.kt`、`ui/settings/VendorBillingActivity.kt`（新建）
- **上游依赖**：`GET /billing/vendor-pricing` 类接口（**非本 PRD 范围**；本故事先建 UI 框架 + 失败回退）

---

## 三、优先级与执行顺序

| 顺序 | 故事 ID | 标题 | 优先级 | 状态 |
|------|---------|------|--------|------|
| 1 | US-D-024 | Muxi PixVerse 模型注册 | P1 | 立即可做 |
| 2 | US-D-025 | 原生带配音徽章 | P2 | 立即可做 |
| 3 | US-D-026 | 任务积分 chip | P1 | 立即可做（字段预留，零时降级） |
| 4 | US-D-028 | 复刻场景选择器 | P2 | 接口到后即可启用，先建 UI + hardcode |
| 5 | US-D-029 | 模板参数表单渲染器 | P2 | 同上 |
| 6 | US-D-030 | 渠道 chip 过滤 | P2 | 跟随 D-029 |
| 7 | US-D-031 | Settings 计费分组 | P3 | 框架先行 |
| 8 | US-D-027 | CS-AI 积分 toast | P3 | 跟随 D-026 / 等 CS-AI 通道 |

预计 8 个故事，PRD 故事总数 48 → 56（48+8）。

---

## 四、不在 R5 范围（已记入 BACKLOG.md 后续 P）

- 视频复刻完整链路：场景选择 → 视频上传 → 骨架反推 → 仿写生成 → 视频合成。这是 C 端 web 流程；端侧 PokeClaw 触发可由 D-028 + D-029 组合完成，但完整链路设计在 dyq 后端。
- 云手机/数字人计费的端到端对接：需要 dyq 后端先开通端侧账单查询接口（`GET /billing/v2/charge/...`）。
- PixVerse 视频反推的端侧能力：纯后端 Job，端侧无操作。

---

## 五、验证方式

- **代码检查**（Android SDK 本地不可用）:
  - `compileDebugKotlin` + `compileDebugUnitTestKotlin` 全过
  - 单测覆盖每个故事的核心逻辑（解析、降级、空值）
- **端到端 QA**（可执行 ADB 时）:
  - D-024：Muxi 设置页可见 3 个新模型
  - D-025：含 `native_audio` 的模型行可见徽章
  - D-026：发送一次云端任务后 chat 末尾 chip 显示（积分需后端填充）

---

## 六、R5 PRD 注入计划

将 8 个新故事追加到 `.omc/prd.json` 的 `userStories` 数组，初始 `passes: false`，priority / category / estimatedFiles / acceptanceCriteria 如上文。

`source` 字段：标注 `dyq-bundle-2026-06-10` 或对应小节号（如 `B-16/17/18` / `A1-3` / `A1-2`）。

预计 PRD 故事总数：48 → **56**。
