# 端云商业化闭环 · 借鉴与落地计划

> 复核 2026-07-09 · 依据:三轮代码勘察 + 全景体检（`docs/closed-loop-maturity.html`）
> 判定基准：读到的实现代码，非 PRD 叙事。证据等级 E0(走查)→E3(真机)。

## 0. 核心判断（主线思想）

**闭环缺的不是"从零造"，而是"借鉴 + 复用 + 接线"。** 三轮勘察显示：能赚钱的底座（模型网关 / 计费分佣 / 内容生产）和执行手脚（云手机 / claw 设备链路 / adb+脚本 / 端侧 Agent / 技能进化）**大多已是真代码**；连最缺的两根柱子，零件也大半存在——只是没接起来。

> 所以本计划的每一条，第一问都是"**已有哪块真代码可借鉴/复用**"，只在确无可用件时才新造。

---

## 1. 可复用的真资产（= 借鉴基础）

| 真资产 | 位置 | 可借鉴 / 复用给 |
|---|---|---|
| **AI 多厂商网关** | `dyq-module-aigateway` / `dyq-spring-boot-starter-ai-proxy`（17 家真对接 + key 路由 + 熔断 + 计费） | 任何需要 LLM/VLM 的环节（决策大脑、编排、进化）直接 `ChatProxyClient` 注入 |
| **计费/积分/分佣/配额** | `dyq-module-billing-settlement`（行级 CAS 扣费 + 幂等 + 阶梯分佣） | 视觉推理、训练、每次自主操作的按次计费直接挂 `BillingCreditApi` |
| **云手机机群管控** | `dyq-module-cloudphone`（4 家真厂商 adapter，`/control` 真下发） | 两条群控路线的执行落点；远程 Observer 也从这里的投屏流接 |
| **端云任务链路（云侧）** | `dyq-module-claw` 设备面（register/心跳/领任务/HMAC 回传，有验收测试） | 降级链、任务分派、数据回传都在此扩展，不新建通道 |
| **真 YOLO 训练管线** | `gui-xml-engine`（`gxe.train.yolo_train` 真 ultralytics + `models/yolo_ui_v0.pt`） | H1 视觉推理、H5 真训练：直接调，不重训框架 |
| **真 YOLO 推理雏形** | `gui-xml-engine`：`gxe/serve/api.py`（FastAPI `/parse`）+ `gxe/eval/infer.py::infer_tree` | **H1 的核心借鉴对象**——把 `/parse` 扩成 `:8081` 期望的端点即可 |
| **视觉决策 client + bridge（已存在）** | `dyq-module-control`：`PythonBridgeClient`（打 `:8081`）、`ControlDecisionService`、`DetectorBridgeService`、`OcrBridgeService`、`ControlScriptEngine.doAiDecide`（截图→决策→点击闭环骨架） | H1 的**消费端已接好**，只差 `:8081` 服务是真的 |
| **GRPO+LoRA 训练引擎** | `claw-training-service`（真 PyTorch + `TaskManager` + MQ 回调 + HMAC） | H5 策略训练；H2 的产物→版本→计费闭环已通 |
| **本次 per-software YOLO 飞轮** | App `vision/explore/collect` + `claw-yolo-hub` + `dyq-module-claw` YOLO（采集→训→分发 + promote gate） | H1 的燃料 + **H2 的门禁范式**（`check_promotion_gate` / `shadow_only` 已实现，可移植到策略侧） |
| **端侧完整 Agent（E3）** | pokeclaw `DefaultAgentService`（LLM AgentLoop + `StuckDetector` + `TaskEvent.NeedsHuman/Blocked`） | H3 一级：把云下发执行体从"确定性 9 技能"换成这个 AgentLoop |
| **云端 Agent 执行体** | `dyq-module-claw` `ClawHermesService` / `ClawHermesCallbackController`（沙箱 Hermes Agent，走 `cloud_agent`） | H3 二级：作为端侧失败后的"云端大模型接手"目标 |
| **灰度/接管件（已存在）** | `ClawCanaryServiceImpl`（Redis 灰度）、`maybeAutoTakeoverOnExhaustion`、`ClawTaskTakeoverStatusEnum` | H2 影子对比、H3 人工接管——补消费者即可闭环 |
| **pc-agent 参考架构** | 原始参考项目 | flywheel / software-explorer 模式（explorer 本次已落地，见 §6） |

---

## 2. 逐问题：借鉴 → 落地

> 格式：【问题】→【借鉴什么】→【落地步骤】→【涉及文件】→【验收/证据】

### ■ H1 · 感知决策大脑（视觉推理 `:8081`） — P0 · 命门

- **问题**：`control` 的 `ai_decide` 要调 `:8081` 的 `/api/multimodal/decision`、`/api/yolo/detect`、`/api/ocr`、`/api/multimodal/verify`，但全仓无此服务，CLI 兜底返回写死假数据 → "该点哪 (x,y)"没人算。端侧 `YoloModelBackend` 同样是 stub。
- **借鉴什么（不重造）**：
  1. `gxe/serve/api.py` 已是 FastAPI 推理服务（`/parse`），`gxe/eval/infer.py::infer_tree(png, yolo_weights, use_ocr, vlm_fn)` 已是真检测+OCR+组树 → **直接扩成 `:8081` 期望的 4 个端点**。
  2. 权重从 `claw-yolo-hub` 按 `software_key` 取 active（本次已实现 resolve/download/checksum）→ 每软件独立加载。
  3. 多模态"决策"层用 `ai-proxy` 的 `ChatProxyClient`（已真对接 VLM 厂商）做 `/multimodal/decision`，不自建模型网关。
  4. 消费端 `PythonBridgeClient` + `DetectorBridgeService` + `doAiDecide` **已接好**，零改动。
- **落地步骤**：
  1. 新建 `dyq-vision-service`（或扩 `gxe/serve`）：`GET /health` + `POST /api/yolo/detect`（复用 `infer_tree` 的检测部分）+ `POST /api/ocr`（复用 easyocr 分支）+ `POST /api/multimodal/decision`（截图+目标 → VLM via `ChatProxyClient` → `{action,x,y,confidence,reasoning}`）+ `POST /api/multimodal/verify`。默认端口 `:8081`（对齐 `PythonBridgeClient` base-url）。
  2. 加载器：启动/请求时从 `claw-yolo-hub /models/resolve` 取该 `software_key` 的 active 权重缓存到本地；无专用则用 generic（gxe `yolo_ui_v0.pt`）。
  3. 删除/降级 `multimodal_decision_cli.py`、`yolo_detect_cli.py` 的写死假数据（保留为真·CLI 代理）。
  4. 端侧：把 pokeclaw `YoloModelBackend.detect` 从"弱标签"接到 **本地 ONNX/LiteRT**（导出 `yolo_ui_v0.pt`→onnx）或调同一 `:8081`（端侧在云手机内，可访问）——两种后端择一，检测框 `source=model` 且带 `model_id/version`。
- **涉及**：`gui-xml-engine/gxe/serve/`、`gxe/eval/infer.py`、`dyq-module-control` `PythonBridgeClient`（仅确认 base-url）、`claw-yolo-hub`（已就绪）、pokeclaw `vision/YoloModelBackend.kt`。
- **验收**：`control` 一条 `ai_decide` 脚本在真云手机上拿到**真检测框**并点中目标（E3）；端侧探索采集的 `boxes.source=model`。

### ■ H2 · 模型安全上端（影子验证 + 晋升门禁） — P0

- **问题**：`ClawPolicyServiceImpl.createPolicyVersion` 直接 `setCurrentVersion(new)`，无影子、无门禁；YOLO 在线训练是模拟；新模型可能直接上端翻车。
- **借鉴什么**：
  1. **本次 YOLO hub 已实现门禁范式**：`check_promotion_gate(map50≥门槛 且 样本≥门槛)` + `candidate`/`shadow_only`/`promote`/`rollback` 状态机 → **把这套范式移植到策略（LoRA）侧**。
  2. `ClawCanaryServiceImpl`（Redis 灰度：白名单/百分比/kill）→ 借来做"新模型 vs 旧模型"的**影子/灰度对比流量**，而非现在的功能灰度。
  3. `claw-training-service` 回调里已带 `avgReward`/`steps` 指标 → 作门禁输入。
- **落地步骤**：
  1. `createPolicyVersion` 改为产出 `status=CANDIDATE`（不自动 current），复用 YOLO hub 的状态字段设计。
  2. 加"影子跑"：candidate 在灰度流量/回放集上评估（借 `ClawCanaryService` 分流），达标（reward/mAP 门槛）才 `promote`→current；不达标保留 candidate + 告警。
  3. YOLO 侧把 `training_sim` 换成真训（见 H5），门禁已在。
  4. 统一"晋升门禁 + 回滚"到一个 `ModelPromotionService`（策略 + YOLO 共用）。
- **涉及**：`ClawPolicyServiceImpl`、`ClawCanaryServiceImpl`、`claw-yolo-hub`（范式源）、`dyq-module-claw` YOLO promote（已有）。
- **验收**：新 LoRA/YOLO 版本必须过门禁才 current；门禁不达标自动挡下并可回滚（E2/E3）。

### ■ H3 · 三级降级接管链（端侧→云端大模型→人工） — P1

- **问题**：真接通的只有"端侧执行↔人工"两级直连；中间"云端大模型接手"没接；端云错误协议字段对不齐；`ClawTaskTakeoverMessage` 无消费者。且云下发执行体是**确定性 9 技能**，非 E3 那个 AgentLoop，本身"处理能力"就弱。
- **借鉴什么**：
  1. 一级升级：pokeclaw **`DefaultAgentService`（真 AgentLoop，E3 已验证）** 已存在 → 把云下发执行体 `LocalAgentTaskExecutor` 从 9 技能映射换成/兜底到这个 AgentLoop（技能能处理走技能，处理不了转 AgentLoop）。
  2. 二级目标：`ClawExecutorTypeEnum.cloud_agent` + `ClawHermesService`（沙箱 Agent）**已存在** → 让端侧失败在转人工**之前**先重派到 `cloud_agent`（Hermes 接手），而不是现在的"只在原设备重试"。
  3. 三级：`maybeAutoTakeoverOnExhaustion` + `ClawTaskTakeoverStatusEnum` + admin 三端点**已存在** → 只需给 `ClawTaskTakeoverMessage` **补一个消费者**（告警/工单），闭合通知。
  4. 端侧升级信号：pokeclaw `TaskEvent.NeedsHuman/Blocked` 已存在，只是没上云 → 接到上报里。
- **落地步骤**：
  1. 端：`LocalAgentTaskExecutor` 技能 miss / `TOOL_FAILED` 时回退到 `DefaultAgentService` AgentLoop；`NeedsHuman/Blocked/预算耗尽` → 上报新增状态 `NEEDS_MODEL` / `NEEDS_HUMAN`。
  2. 协议对齐：`TaskResultRequest`（端）与 `AppClawDeviceTaskResultReqVO`（云）补齐 `recoverable/suggestedAction/needsEscalation` 字段，别再靠 errorMessage 关键字猜。
  3. 云：`submitTaskResult` 收到 `NEEDS_MODEL` → `ClawTaskDispatcher` **重派 `cloud_agent`（Hermes）**；Hermes 也失败或 `NEEDS_HUMAN` → `maybeAutoTakeover`→PENDING_MANUAL。
  4. 补 `@RocketMQMessageListener` 消费 `ClawTaskTakeoverMessage`（告警 + 建工单）。
- **涉及**：pokeclaw `cloud/CloudTaskExecutor.kt`/`CloudNodeOrchestrator.kt`/`CloudModels.kt`；dyq `ClawDeviceServiceImpl`/`ClawTaskDispatcher`/`AppClawDeviceTaskResultReqVO`/`ClawEventPublisher` + 新消费者。
- **验收**：一条端侧处理不了的任务，链路自动走到 `cloud_agent` 接手；仍不行才 PENDING_MANUAL 且**自动告警**（E2/E3）。

### ■ H4 · 端云链路真后端 E2E 核实 — P0（低成本、先做）

- **问题**：`CLOUD_SUBSYSTEM_BOUNDARY.md` 说 mock-only/E1；另有材料声称 `:48081` E3。矛盾未决 → 采集数据/任务能否真打通存疑。
- **借鉴什么**：`ClawThreeEndAcceptanceTest`（云侧三端验收测试已有）+ `api-contracts/device.openapi.yaml` + 本次 App 侧 `DeviceApi`/HMAC 契约测试。
- **落地步骤**：测试服务器起 `dyq-server`（tenant ignore-tables 已修）→ 用真 pokeclaw 或 curl 走 register→heartbeat→pending-tasks→submitResult(HMAC) 全环 → 把结论一锤定音写回 `CLOUD_SUBSYSTEM_BOUNDARY.md`（E1→E3 或证伪）。
- **验收**：真后端 E2E 通过并留 logcat/HTTP 证据（E3）。

### ■ 半成品收尾（P1/P2）

| 项 | 借鉴 / 复用 | 落地要点 |
|---|---|---|
| **YOLO 真训练**（去 `training_sim`） | `gxe.dataset.build_yolo.write_dataset` + `gxe.train.yolo_train.train`（真 ultralytics） | `claw-yolo-hub` 的 `_real_train` 接 gxe，`YOLOHUB_REAL_TRAINER=1`；需 GPU 盒子 |
| **证据落 OSS**（去内存 Map） | yudao `infra FileApi`（`createFile/createFileStream`，`MaterialServiceImpl` 有范例） | `AppClawDeviceController` evidence + YOLO 截图/artifact 接 `FileApi`；claw-biz pom 加 `infra-api` 依赖 |
| **远程云手机 Observer** | `cloudphone` 投屏流（`/screen-wall-url` wss）+ 本次 `CloudPhoneActuator` | 解码投屏帧→喂 `:8081` 检测→远程也能"看+点"，补齐远程路线 B |
| **养号/发布/截流成体系编排** | `behavior-simulation`（状态机+LLM 真）+ `content-distribution`（任务流）+ `cloudphone AccountApi`（真执行） | 把 `…DemoFlowTest` 提升为生产编排：persona→计划→分派→执行→反馈闭环 |
| **Lobster EVOLVED 生命周期** | `ClawLobsterServiceImpl` 已有等级/经验原子逻辑 | 补 `MATURE→EVOLVED/DORMANT` 的自动置位 + 触发 `ClawSkillEvolver`（已真） |
| **无人商城 AI 运营层** | `dyq-module-mall`（真电商中台）+ `director`/`aigc`（真 AI） | 在中台上加"AI 选品/自动上架/定价"编排层（新造，但底座真） |
| **两条群控路线统一分派**（可选） | `ClawTaskDispatchStrategy`（真）+ `ControlDispatcher`（真） | 若需 A/B 混合调度，加一层"按任务性质选脚本 or Agent"的上层 router；否则保持独立 |

---

## 3. 分阶段路线图

```
Phase 1 · 闭环骨架 (P0)          Phase 2 · 智能化 + 收尾 (P1)        Phase 3 · 规模变现 (P2)
─────────────────────────       ─────────────────────────────      ────────────────────────
H4 端云链路核实（先，低成本）     H1 端侧真 YOLO 推理/真训练           养号→获客→转化 成体系编排
H1 视觉推理 :8081（大脑）         H3 三级降级链补齐                    无人商城 AI 运营层
H2 影子门禁（安全上端）          远程 Observer + 证据落 OSS          Lobster EVOLVED
                                真机整链路 E3 + 编译/测试落测试服    统一分派（如需）
```

**依赖关系**：
- H4 无依赖，**最先做**（几小时，决定后面数据链是否可信）。
- H1 依赖 `claw-yolo-hub`（✅已就绪）+ gxe（✅已有）+ ai-proxy（✅）→ 主要是**接线 + 起服务**。
- H2 依赖 H1（YOLO 真指标）+ 借 YOLO hub 门禁范式（✅）。
- H3 依赖 H1（端侧有真感知才谈"处理能力"）+ pokeclaw AgentLoop（✅）+ Hermes（✅）。
- P2 依赖 P1 稳定。

---

## 4. 证据门（每项交付必须标）

- **E0** 走查就绪 · **E1** 单测 · **E2** 模拟/emulator · **E3** 真机/真后端。
- 门槛：任何"自主控制 / 模型上端 / 计费扣费"能力，**不到 E3 不标 PASS**（沿用 `QA_CHECKLIST.md` 证据纪律）。
- 本次 YOLO 飞轮现状：App 逻辑 E1（19 单测）+ 云端 hub 运行时验证（pytest 5/5）；Java 云端 CODE-READY（待测试服务器 mvn）；真机整链路 E3 = 待做（P1）。

---

## 5. 风险与取舍

- **GPU 依赖**：H1 推理（可 CPU/小模型起步）、H5 真训练（需 GPU 盒子）。起步用 gxe 现成 `yolo_ui_v0.pt` + CPU 推理验证链路，再上 GPU 提精度。
- **端侧推理成本**：云手机算力有限，端侧 ONNX 可能吃紧 → 优先"端侧调云手机内网 `:8081`"，端侧本地推理作为无网兜底。
- **改动波及面**：H3 动端云协议字段，要同步 `api-contracts/device.openapi.yaml` + 两端契约测试，别只改一边（本次已踩过 metrics 类型对不齐的坑）。
- **不重造**：凡表格里标"✅已有"的，一律接线复用；新造只限 `:8081` 服务壳、门禁 Service、消费者、商城 AI 层这几处。

---

## 6. 附：pc-agent 借鉴映射（呼应初始目标）

| pc-agent 模块 | 本系统对应 | 状态 |
|---|---|---|
| `yolo-software-explorer` | pokeclaw `explore/`（状态哈希去重+覆盖+SoftwareExplorer） | ✅ 本次已落地 |
| `yolo-data` | pokeclaw `collect/` + `claw-yolo-hub` datasets（按 software_key） | ✅ 本次已落地 |
| `yolo-vision` | `:8081` 检测服务 + 端侧 `YoloModelBackend` | ⏳ H1（借 gxe infer） |
| `yolo-training` | `gui-xml-engine`（真 ultralytics） | ✅ 有引擎，⏳ 接真训（H5） |
| `yolo-flywheel` | `claw-yolo-hub`（采集→训→candidate→promote/rollback） | ✅ 本次已落地（训练待接真） |
| `trajectory-recorder` | pokeclaw `collect/TrajectoryRecorder` + `reliability/ExecutionTrace` | ✅ 已有 |
| `local-agent` | pokeclaw `DefaultAgentService`（E3） | ✅ 已有 |
| `desktop-client` | pokeclaw `ui/console/VisionConsoleActivity` + dyq admin | ✅ 端侧已有；云端控制台复用 dyq |

> 结论：pc-agent 的**数据飞轮 + explorer + trajectory + local-agent**在本系统均已有真对应，唯一实缺的是 **yolo-vision 的在线推理服务（H1）**——这也正是全景图里那根空的"感知大脑"柱子。
</content>
