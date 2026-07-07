# PRD: PokeClaw 端云集成（CMP-2 主线 + P 泳道）

> **来源**：`D:\work\code\dyqbackupdd\ralph\prd.json` 中 `project` 字段为 `claw-detail` / `claw-prd` / `claw-3terminal` 的所有 stories（合计 55 条）。
> **整理日期**：2026-06-13
> **整理人**：Claude (PokeClaw 项目助理)
> **过滤策略**：
> - 保留：PokeClaw 端侧可直接实现的功能（含需要云端 API 配合的端侧部分）
> - 跳过（联调验收类）：US-CLAW3T-007 总体验收、US-CLAW-CMP-3-4-1 MVP 用例覆盖
> - 跳过（属于 WeFlow 微信端）：claw-3terminal 004/005、OW-002、CMP-3-1~3-4、LANE-W1/W2
> - 跳过（属于 dyq Claw 云端后端）：CLAW3T-001、CMP-1-1~1-5、LANE-C1
> - 跳过（属于 dyq 号源市场）：全部 17 条 US-AM-PKG-* stories（项目名误归 claw-*，实为 dyq 号源市场工作包）
> - 标记（场景类，远期规划）：LANE-S1~S4

---

## 1. Introduction

把 dyqbackupdd 项目中规划好的「Claw 三端商业化」端侧 stories 同步到 PokeClaw 项目，对齐「PokeClaw 端云任务下发与结果回传联调清单」(CMP-1940) 与「PokeClaw 端云闭环 P1-P2」工作的 story 视图。本 PRD 集中描述 PokeClaw 安卓端需要实现的功能，不包括联调验收步骤。

**Why**：当前 BACKLOG.md 中 P1-P2 端云闭环工作以联调清单形式存在，缺少按 story 粒度的需求描述；本 PRD 补全这部分，让实现工作有可勾选的 AC。

## 2. Goals

- 把 14 条端云集成 story 落到 PokeClaw 端侧 backlog
- 与现有的 `CloudHeartbeatManager` / `CloudNodeOrchestrator` / `CloudModels.kt` 实现形成 story ↔ 代码可追溯关系
- 明确哪些是端侧单端可实现、哪些需要 dyq Claw 云端 API 配合
- 跳过联调验收类 story，专注功能实现

## 3. User Stories

### Story 归属图

| 原始 Story ID | 标题（缩写） | 端侧实现 | 需要云端 | 联调 | 风险 |
|---|---|---|---|---|---|
| `US-CLAW3T-002` | 设备注册与心跳 | ✅ | ✅ | 跳 | 5 |
| `US-CLAW3T-003` | PokeClaw 领取任务并回传证据 | ✅ | ✅ | 跳 | 5 |
| `US-CLAW3T-006` | 经验包沉淀（端侧上传） | ✅ | ✅ | 跳 | 3 |
| `US-CLAW-OW-001` | 主人后台→Claw→PokeClaw 执行 | ✅ | ✅ | 跳 | 5 |
| `US-CLAW-OW-003` | 执行失败反馈与人工接管 | ✅ | - | 跳 | 5 |
| `US-CLAW-OW-004` | 撤销授权（端侧取消任务） | ✅ | ✅ | 跳 | 3 |
| `US-CLAW-OW-005` | 日志脱敏（端侧） | ✅ | - | 跳 | 3 |
| `US-CLAW-CMP-2-1` | 设备注册为执行节点 | ✅ | ✅ | 跳 | 3 |
| `US-CLAW-CMP-2-2` | 心跳保活机制 | ✅ | - | 跳 | 3 |
| `US-CLAW-CMP-2-3` | 接收云端指令 | ✅ | ✅ | 跳 | 3 |
| `US-CLAW-CMP-2-4` | 执行简单手机控制任务 | ✅ | - | 跳 | 3 |
| `US-CLAW-CMP-2-5` | 结果与错误上报 | ✅ | ✅ | 跳 | 3 |
| `US-CLAW-CMP-2-6` | 端侧安全与降级 | ✅ | - | 跳 | 3 |
| `US-CLAW-LANE-C2` | 设备管理与心跳（WebSocket，云端视角） | △云端 | ✅ | 跳 | 5 |
| `US-CLAW-LANE-P1` | PokeClaw 端侧执行链路 | ✅ | ✅ | 跳 | 5 |
| `US-CLAW-LANE-P2` | 端侧稳定性与自愈 | ✅ | - | 跳 | 3 |

> **说明**：
> - 端侧实现 ✅ = 端代码要写
> - 需要云端 ✅ = 必须有 dyq Claw 云端接口才能联调
> - 联调 跳 = 本期只做功能实现，联调验收不在本 PRD 范围

---

### US-CLAW3T-002 设备注册与心跳（端侧视角）

**Description**: As a PokeClaw 端, I want 在首次启动时向 dyq Claw 后端注册为执行节点并维持心跳 so that 我能被纳入「主人后台 → Claw 编排 → PokeClaw 执行」链路。

**端侧 Acceptance Criteria**:
- [ ] 启动时调用 `POST /api/claw-device/register`，携带 deviceId/deviceName/deviceModel/androidVersion/appVersion
- [ ] 响应中 `deviceToken`/`refreshToken`/`expiresIn` 落 Android Keystore 加密存储
- [ ] 后台周期心跳 `POST /api/claw-device/heartbeat`（30s 间隔），携带 batteryLevel/isCharging/networkType
- [ ] 心跳响应中 `pendingTaskCount > 0` 时触发任务拉取
- [ ] Token 失效自动调 `POST /api/claw-device/token/refresh`
- [ ] 现有 DTO：`CloudModels.kt` 中 `DeviceRegisterRequest/Response/HeartbeatRequest/Response/TokenRefreshRequest/Response`
- [ ] 现有实现：`CloudHeartbeatManager`（已实现，提交 6dee526 / bdcc36a）
- [ ] QA-First：QA_CHECKLIST 新增一条「设备注册与心跳 E2E」用例（联调阶段）

**Notes**:
- 端字段已对齐 `device.openapi.yaml`（NetworkType 枚举对齐见提交 55e42d3）
- 原始 story 风险 5，风险点已在端侧通过 Keystore + Token 刷新规避

---

### US-CLAW3T-003 PokeClaw 领取任务并回传证据（端侧视角）

**Description**: As a PokeClaw 端, I want 在心跳后拉取云端待执行任务、执行后回传结果与证据 so that 端云任务链路完整。

**端侧 Acceptance Criteria**:
- [ ] 拉取 `GET /api/claw-device/devices/{deviceId}/pending-tasks`，按 mode（TASK/INTERACTIVE）分类入队
- [ ] 任务进入执行前，状态标记为 RUNNING，落本地任务表
- [ ] 执行完成后调用 `POST /api/claw-device/tasks/{taskUuid}/result`，body 包含 status/result/errorMessage/executionTimeMs/toolCalls/evidenceUrls/modelUsed
- [ ] 失败时携带 errorCategory/errorCode/errorDetail/recoverable/suggestedAction
- [ ] 可选：失败时上传 screenshotBase64 + logSnippet
- [ ] 幂等：同一 taskUuid 重复 result 提交不造成副作用（云端去重）
- [ ] 现有实现：`CloudNodeOrchestrator`（已实现，提交 6dee526 / 9801a1b）
- [ ] QA-First：QA_CHECKLIST 新增「任务拉取→执行→结果回传」E2E 用例（联调阶段）

**Notes**:
- 提交 6dee526 「p1-p2端云闭环」已实现端侧任务领取 + 证据回传
- 风险 5，主要在端侧可控（幂等 + 重试）

---

### US-CLAW3T-006 经验包沉淀（端侧上传）

**Description**: As a PokeClaw 端, I want 在任务完成后把可复用经验上传到云端 so that 后续 Claw 编排可以参考。

**端侧 Acceptance Criteria**:
- [ ] 任务完成（成功）时自动生成经验摘要：任务类型 + 关键步骤 + 成功条件
- [ ] 调用 `POST /api/claw-experience/upload`，body 含 commercialTaskId/experienceType/summary/strategyKeywords
- [ ] 失败任务也可沉淀「失败经验」：失败原因 + 设备状态
- [ ] 网络异常时经验入本地队列，恢复后批量上传
- [ ] 端侧只读不写云端经验库（避免冲突）

**Notes**:
- 端侧单端可实现，依赖云端 `/api/claw-experience/upload` 端点
- 风险 3，可与 US-CLAW-CMP-2-5 共用本地队列

---

### US-CLAW-OW-001 主人后台 → Claw 编排 → PokeClaw 执行（端侧视角）

**Description**: As a PokeClaw 端, I want 接收 Claw 编排下发的任务指令并执行手机任务 so that 主人后台指令能在端侧落地。

**端侧 Acceptance Criteria**:
- [ ] 任务 ID 全局唯一，格式 `CMP-TASK-{日期}-{序号}`（端侧展示用）
- [ ] 内部主键用云端 `commercialTaskId`（DB 字段 UUID v7）作为查询 key
- [ ] 任务下发到 PokeClaw 后，端任务列表立即可见
- [ ] 执行完成后主人能看到结果摘要、执行耗时、节点编号
- [ ] 端侧记录经验：任务类型 / 节点类型 / 结果分类 / 耗时 / 可复用策略摘要
- [ ] 任务状态变化通过心跳 + 拉取轮询同步

**Notes**:
- 端 ID 格式 `CMP-TASK-{日期}-{序号}` 与云端 `commercialTaskId` 双轨（见原 story 边界声明）
- 端侧实现优先级：先做 `commercialTaskId` 接收 + 展示，不重复定义

---

### US-CLAW-OW-003 执行失败时主人收到明确反馈并可选择人工接管（端侧视角）

**Description**: As a PokeClaw 端, I want 任务执行失败时产生明确反馈（错误码/分类/可重试标记） so that 主人可以选择重试/切换节点/忽略/接管。

**端侧 Acceptance Criteria**:
- [ ] 失败时 result.errorCategory 至少分三类：环境异常 / 业务异常 / 永久失败
- [ ] result.errorCode + errorMessage 给主人可读摘要
- [ ] result.recoverable 标记是否可重试
- [ ] result.suggestedAction 给建议（如「重试」/「切换节点」/「人工接管」/「联系客服」）
- [ ] 端侧 UI：失败任务在任务列表显示「失败」徽章 + 「重试/人工接管」按钮
- [ ] 选择「人工接管」时任务状态变 PAUSED，不再自动重试
- [ ] 连续失败 >3 次自动标记 NEEDS_HUMAN，禁止无限重试

**Notes**:
- 端侧 UI 部分需要聊天 UI 或任务 Tab 配合（参考现有 TaskEvent 流）
- 风险 5，主要在 UX 细节

---

### US-CLAW-OW-004 边界：撤销授权（端侧视角）

**Description**: As a PokeClaw 端, I want 主人撤销任务授权时立即停止执行并保留部分结果 so that 端侧不会出现「主人撤销但任务还在跑」。

**端侧 Acceptance Criteria**:
- [ ] 端任务状态机增加 CANCELLED 终态
- [ ] 收到云端 cancel 通知（轮询/WS 推送），立即停止当前执行
- [ ] 已执行的部分结果保留，标记为「已撤销」
- [ ] 端操作人/时间/原因写本地审计（XLog + audit log）
- [ ] 撤销后不响应同 taskUuid 的重试
- [ ] UI：撤销任务在任务列表显示「已撤销」状态，不可重启

**Notes**:
- 端侧可独立实现：状态机 + 停止逻辑 + 审计
- 需云端 cancel 通知推送才能实时（但可降级为轮询）

---

### US-CLAW-OW-005 边界：日志脱敏（端侧）

**Description**: As a PokeClaw 端, I want 任务结果、错误信息、经验记录中的敏感字段自动脱敏 so that 主人看到的是「安全」摘要。

**端侧 Acceptance Criteria**:
- [ ] 工具：实现 `LogSanitizer` 工具类，识别令牌/密码/密钥模式（regex + 关键词）并替换为 `***`
- [ ] 任务结果/错误信息输出前过 `LogSanitizer`
- [ ] 微信消息全文只保留摘要（前 20 字 + 省略号）
- [ ] 联系人真实姓名 → `联系人A`（递增编号）；群聊名 → `群聊X`
- [ ] 截图：含聊天内容的截图缩略图模糊化或仅保留元信息（宽×高+大小）
- [ ] 错误日志中的 Android 绝对路径只保留相对路径
- [ ] XLog 写入前统一过脱敏中间件
- [ ] 单元测试覆盖：6 类脱敏场景

**Notes**:
- 端侧单端可实现，无需云端配合
- 风险 3

---

### US-CLAW-CMP-2-1 设备注册为执行节点

**Description**: As a PokeClaw 端, I want 在启用「云端控制」时注册为执行节点并获取 JWT so that 后续可被云端识别和调度。

**端侧 Acceptance Criteria**:
- [ ] 「云端控制」开关在 Settings → 远程控制 → 加入 dyq Claw（参考现有 External Automation 开关）
- [ ] 启用时立即调 `/api/claw-device/register`，失败提示「请检查后端服务」
- [ ] JWT 落 Android Keystore（已有基础设施，可复用）
- [ ] Token 有效期 2h，过期前 10min 主动 refresh
- [ ] Token 刷新失败 → 重新走 register 流程
- [ ] 设备能力列表：上报「可执行 Android UI 自动化任务」「本地模型可用」
- [ ] 版本号上报：appVersionName + appVersionCode

**Notes**:
- 与 US-CLAW3T-002 重叠，AC 拆分：本 story 关注「注册」动作，US-CLAW3T-002 关注「心跳」动作
- 风险 3

---

### US-CLAW-CMP-2-2 心跳保活机制

**Description**: As a PokeClaw 端, I want 每 30 秒发一次心跳，离线检测 ≤ 90 秒 so that 云端知道端侧在线状态和当前任务。

**端侧 Acceptance Criteria**:
- [ ] 心跳间隔 30s（可通过配置覆盖）
- [ ] 心跳 body 含 onlineStatus（ONLINE/BUSY/IDLE）、currentTaskId
- [ ] 前台服务 + WorkManager 双保险：进程被杀后心跳恢复
- [ ] 离线检测：端侧 3 次心跳连续失败 → 标记 OFFLINE，UI 状态栏红点
- [ ] 在线恢复：端侧 1 次心跳成功 → 标记 ONLINE
- [ ] 现有实现：`CloudHeartbeatManager`（提交 bdcc36a / 6dee526）

**Notes**:
- 风险 3
- 与 US-CLAW3T-002 重叠：本 story 关注「端侧心跳策略」

---

### US-CLAW-CMP-2-3 接收云端指令

**Description**: As a PokeClaw 端, I want 主动轮询或长连接接收云端指令 so that 不依赖云端推送也能拿到新任务。

**端侧 Acceptance Criteria**:
- [ ] 默认轮询：心跳响应中 `pendingTaskCount > 0` 时立即拉取 `/api/claw-device/devices/{deviceId}/pending-tasks`
- [ ] 备用长连接：可选 WebSocket（依赖云端 WS 端点 `/ws/claw/device`）
- [ ] 指令解析：taskUuid / command / mode / priority
- [ ] 指令 ACK：收到后立即返回 `ack`（可作为云端去重依据）
- [ ] 网络异常时指令入本地队列，恢复后重试拉取

**Notes**:
- 本期实现轮询；WS 留作可选
- 风险 3

---

### US-CLAW-CMP-2-4 执行简单手机控制任务

**Description**: As a PokeClaw 端, I want 接收 Claw 下发的基础控制指令（点击/滑动/输入/返回/Home）并执行 so that 云端可以驱动端侧 UI 自动化。

**端侧 Acceptance Criteria**:
- [ ] 指令白名单：`tap` / `swipe` / `input_text` / `back` / `home` / `open_app` / `screenshot`
- [ ] 复用现有 `AccessibilityService` + Tool 体系
- [ ] 执行前参数校验：tap 需要坐标，input_text 需要文本，swipe 需要 from/to 坐标
- [ ] 执行超时：单条工具调用默认 30s 超时
- [ ] 执行结果：成功 → toolResult；失败 → errorCode + errorMessage
- [ ] 失败时截图（保留 Base64，最多 200KB）

**Notes**:
- 与现有 `Tools: tap/swipe/long_press/input_text/open_app/send_message/auto_reply/get_screen_info/take_screenshot/finish` 高度重合
- 风险 3，端侧单端可独立实现

---

### US-CLAW-CMP-2-5 结果与错误上报

**Description**: As a PokeClaw 端, I want 任务执行结果结构化上报到云端 so that 主人后台可看到执行历史。

**端侧 Acceptance Criteria**:
- [ ] 上报 body 字段：status（SUCCESS/FAILED/RUNNING/CANCELLED）/ result / errorMessage / executionTimeMs / toolCalls (JSON) / evidenceUrls / modelUsed
- [ ] 错误上报增强字段：errorCategory / errorCode / errorDetail / recoverable / suggestedAction / screenshotBase64 (optional) / logSnippet (optional)
- [ ] 离线时任务入本地 Room/SQLite 队列，恢复后批量上报
- [ ] 上报失败重试 3 次（指数退避 1s/5s/30s）
- [ ] 终失败：本地保留证据，等待下次心跳重试

**Notes**:
- 现有 `TaskResultRequest` DTO 已对齐（见 `CloudModels.kt`）
- 风险 3

---

### US-CLAW-CMP-2-6 端侧安全与降级

**Description**: As a PokeClaw 端, I want Token 安全存储、网络异常重试、离线时降级到本地 Gemma so that 即使云端不可用，端侧基本能力仍可用。

**端侧 Acceptance Criteria**:
- [ ] Token 存储：Android Keystore + EncryptedSharedPreferences
- [ ] 网络异常重试：3 次指数退避（1s/5s/30s），可配置
- [ ] 离线降级：心跳连续失败 → 切到「本地模式」，主人后台任务降级为「本地提示任务已离线」
- [ ] 离线时任务可继续用本地 Gemma 模型执行（不依赖云端）
- [ ] 离线时 UI 显示「云端不可用，仅本地模式」提示
- [ ] 网络恢复后自动切回「云端模式」

**Notes**:
- 端侧单端可实现
- 风险 3

---

### US-CLAW-LANE-C2 设备管理与心跳机制（WebSocket，云端视角）

**Description**: As a PokeClaw 端, I want 支持 WebSocket 长连接接收云端指令（备用）so that 不依赖轮询时延。

**端侧 Acceptance Criteria**:
- [ ] 可选连接：`ws://{host}/ws/claw/device?deviceId=...&token=...`
- [ ] 收到云端消息（ping / task / cancel）后 ACK
- [ ] WS 断线自动重连（指数退避 1s/5s/30s）
- [ ] WS 与轮询可同时启用，以先到为准
- [ ] 心跳：WS 30s ping，90s 无响应 → 重连

**Notes**:
- 本期实现：保留 WS 客户端代码结构，**默认走轮询**（依赖云端 API）
- 风险 5（WS 双向时序、ACK、重连都比较复杂）

---

### US-CLAW-LANE-P1 PokeClaw 端侧执行链路

**Description**: As a PokeClaw 端, I want 端侧任务执行链路完整：poll → 解析 → 执行 → 上报 so that 端云任务闭环。

**端侧 Acceptance Criteria**:
- [ ] `GET /api/claw/agent/tasks/poll`（P 泳道接口）/ `GET /api/claw-device/devices/{deviceId}/pending-tasks`（CMP-2 接口）二选一
- [ ] `POST /api/claw/agent/tasks/{id}/evidence` 截图上传，返回可访问 URL
- [ ] `POST /api/claw/agent/tasks/{id}/result` 包含 commercialTaskId + 截图证据链
- [ ] 端侧设备认证 + deviceId 绑定
- [ ] 幂等：同一 taskUuid result 重复提交不造成副作用
- [ ] 现有实现：`CloudNodeOrchestrator`（提交 6dee526 / 9801a1b）

**Notes**:
- 风险 5，与 US-CLAW3T-003 高度重叠（接口路径不同，故事描述的接口是 P 泳道 P1 定义）
- 端侧实现可同时支持两套接口（CMP-2 的 `/claw-device/*` + P 泳道的 `/claw/agent/*`），但**本期只走 CMP-2 接口**

---

### US-CLAW-LANE-P2 端侧稳定性与自愈

**Description**: As a PokeClaw 端, I want 离线后自动重连、恢复未完成任务、超时自动重试 so that 端云链路稳定。

**端侧 Acceptance Criteria**:
- [ ] 网络断开 → 心跳失败 3 次 → 标记 OFFLINE
- [ ] 网络恢复 → 自动重连 + 重新 register（如需）+ 拉取 pending-tasks
- [ ] 任务执行超时（默认 5 分钟）→ 自动标记 FAILED + 上报 timeout
- [ ] 超时任务入重试队列（最多 3 次）
- [ ] 终失败任务入死信队列，UI 提示「任务失败需人工」
- [ ] 健康检查：每 10 分钟自检一次（Token / 网络 / 任务队列），输出自愈报告到 XLog
- [ ] 自愈报告 API（云端视角，本端可忽略）：端侧主动上报 `POST /api/claw-device/health-report`

**Notes**:
- 端侧单端可独立实现
- 风险 3

---

## 4. Functional Requirements

- **FR-1**：PokeClaw 端在「云端控制」开关启用时，注册为 dyq Claw 执行节点
- **FR-2**：心跳 30s 一次，离线检测 ≤ 90s（端侧策略）
- **FR-3**：任务执行白名单：`tap`/`swipe`/`input_text`/`back`/`home`/`open_app`/`screenshot`
- **FR-4**：结果上报包含 `errorCategory` 分类（环境异常/业务异常/永久失败）
- **FR-5**：Token 存 Android Keystore，过期前 10min 主动 refresh
- **FR-6**：网络异常重试 3 次（指数退避 1s/5s/30s）
- **FR-7**：离线时入本地队列，恢复后批量上报
- **FR-8**：日志脱敏：令牌/密码/密钥 → `***`；联系人/群聊名脱敏
- **FR-9**：任务 ID 双轨：展示 `CMP-TASK-{日期}-{序号}`，DB 主键 `commercialTaskId` (UUID v7)
- **FR-10**：任务状态机增加 `CANCELLED` 终态，收到云端 cancel 立即停止执行

## 5. Non-Goals (Out of Scope)

- 联调验收（E2E with dyq Claw 后端）：由 US-CLAW3T-007 在联调阶段处理，本 PRD 不包含
- WeFlow 微信端 stories（CLAW3T-004/005、OW-002、CMP-3-1~3-4、LANE-W1/W2）：属于另一端
- 纯云端 stories（CLAW3T-001、CMP-1-1~1-5、LANE-C1）：属于 dyq 后端
- 场景类（LANE-S1~S4）：远期规划，本期不实现
- dyq 号源市场 stories（US-AM-PKG-*）：不属于 PokeClaw

## 6. Design Considerations

### 现有代码映射

| 已有实现 | 归属 story |
|---|---|
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudModels.kt` | CMP-2-1 / 3T-002 |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudHeartbeatManager.kt` | CMP-2-2 / 3T-002 |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudNodeOrchestrator.kt` | CMP-2-3 / 3T-002 / LANE-P1 |
| `app/src/main/java/io/agents/pokeclaw/automation/ExternalAutomationActivity.kt` | 旧 External Automation（可参考 Settings UI） |
| `app/src/main/java/io/agents/pokeclaw/...accessibility/...` | CMP-2-4 工具执行 |

### UI 关联

- Settings → 远程控制：增加「加入 dyq Claw 执行节点」开关（CMP-2-1）
- Settings → 远程控制：增加「日志脱敏」开关（OW-005）
- Task Tab / 任务列表：失败任务显示「重试/人工接管」按钮（OW-003）

### QA 关联

- QA_CHECKLIST.md 应新增以下条目（与本 PRD 同步，**仅在联调阶段启用**）：
  - L1: 设备注册与心跳 E2E（CMP-2-1/2）
  - L2: 任务拉取→执行→回传 E2E（CMP-2-3/4/5）
  - L3: 失败反馈 + 人工接管（OW-003）
  - L4: 撤销授权（OW-004）
  - L5: 端侧降级（无云端时本地模式可用）

## 7. Technical Considerations

### 依赖

- 需要 dyq Claw 云端 API：
  - `POST /api/claw-device/register`（已存在）
  - `POST /api/claw-device/heartbeat`（已存在）
  - `GET /api/claw-device/devices/{deviceId}/pending-tasks`（已存在）
  - `POST /api/claw-device/tasks/{taskUuid}/result`（已存在）
  - `POST /api/claw-device/token/refresh`（已存在）
  - `POST /api/claw-experience/upload`（待确认）
  - `POST /api/claw-device/health-report`（待确认）
  - `ws://{host}/ws/claw/device`（可选，待确认）

### 端侧基础设施

- Android Keystore：Token 加密（已有）
- WorkManager：心跳保活（已有）
- Foreground Service：心跳 + WS（已有）
- Room/SQLite：离线任务队列（已有）
- XLog：日志脱敏中间件（待实现）

### 端侧单端可独立开发（无需云端）

- OW-004 撤销授权
- OW-005 日志脱敏
- CMP-2-4 简单手机控制任务
- CMP-2-6 端侧安全与降级
- LANE-P2 端侧稳定性与自愈（自愈部分）

### 需要云端配合（先做 mock / 接口定义）

- CMP-2-1 设备注册
- CMP-2-3 接收云端指令
- CMP-2-5 结果上报
- LANE-C2 WebSocket（可选）
- LANE-P1 端侧执行链路

## 8. Success Metrics

- 16 条 story 全部进入 BACKLOG.md 并标注「P1 端云闭环 - 子任务」
- 端侧单端可开发 story（OW-004 / OW-005 / CMP-2-4 / CMP-2-6 / LANE-P2 自愈部分）不依赖云端即可 QA 通过
- 需要云端 story 提供 mock server 便于单元测试
- QA-First：每条 story 在 BACKLOG 中挂一条 QA_CHECKLIST 候选项

## 9. Open Questions

- Q1: 「加入 dyq Claw 执行节点」开关是否复用现有「External Automation」开关？还是独立？  
  → 待与 Nicole 确认。建议独立（语义不同：External Automation 是用户触发 API，dyq Claw 是注册为被调度节点）。
- Q2: 离线降级到本地 Gemma 时，主人后台任务如何呈现？  
  → 建议端任务列表保留，状态显示「云端不可用，本地继续」；主人后台可见「端离线」提示。
- Q3: WebSocket 客户端本期是否实现？  
  → 建议保留代码骨架，默认走轮询；待云端 WS 端点稳定后再启用。
- Q4: 经验包上传端点 `/api/claw-experience/upload` 在云端是否已存在？  
  → 待确认；如不存在需在云端建基线。
- Q5: 端侧单端可开发的 story 优先级：先做 OW-005 日志脱敏（最低风险）还是 CMP-2-6 端侧安全（最高价值）？  
  → 建议 OW-005 → CMP-2-4 → CMP-2-6 → OW-004 → LANE-P2。

---

## 附：跳过的 stories 索引（备忘）

| Story ID | 标题 | 跳过原因 |
|---|---|---|
| US-CLAW3T-001 | 云端创建并编排商业任务 | 纯云端（dyq 后端） |
| US-CLAW3T-004 | WeFlow 上报微信消息 | 属于 WeFlow 端 |
| US-CLAW3T-005 | WeFlow 安全回复 | 属于 WeFlow 端 |
| **US-CLAW3T-007** | **总体验收** | **联调验收类（按用户要求跳过）** |
| US-CLAW-OW-002 | 主人微信→Claw→weflow | 属于 WeFlow 端 |
| US-CLAW-CMP-1-1 | 小龙虾角色管理 | 纯云端 |
| US-CLAW-CMP-1-2 | 统一执行节点模型 | 纯云端 |
| US-CLAW-CMP-1-3 | 任务编排核心 | 纯云端 |
| US-CLAW-CMP-1-4 | 经验汇总与查询 | 纯云端 |
| US-CLAW-CMP-1-5 | 指挥调度接口 | 纯云端 |
| US-CLAW-CMP-3-1 | 微信消息接收 | 属于 WeFlow 端 |
| US-CLAW-CMP-3-2 | 微信消息发送 | 属于 WeFlow 端 |
| US-CLAW-CMP-3-3 | 状态查询能力 | 属于 WeFlow 端 |
| US-CLAW-CMP-3-4 | 设备注册接口（预留） | 属于 WeFlow 端 |
| **US-CLAW-CMP-3-4-1** | **MVP 用例覆盖** | **联调验收类（按用户要求跳过）** |
| US-CLAW-LANE-C1 | Claw Controller 核心 API | 纯云端 |
| US-CLAW-LANE-W1 | WeFlow 微信事件采集 | 属于 WeFlow 端 |
| US-CLAW-LANE-W2 | 安全回复闭环 | 属于 WeFlow 端 |
| US-CLAW-LANE-S1 | 自动化运营场景 | 场景类，远期规划 |
| US-CLAW-LANE-S2 | 截流获客场景 | 场景类，远期规划 |
| US-CLAW-LANE-S3 | 商城交易场景 | 场景类，远期规划 |
| US-CLAW-LANE-S4 | 养号孵化场景 | 场景类，远期规划 |
| US-AM-PKG-P0-* (5) | P0 底座（事务拦截/菜单/凭证/打款） | dyq 号源市场 |
| US-AM-PKG-P1-* (6) | P1 商业化（套餐/目录/凭证揭示/纠纷/对账/库存） | dyq 号源市场 |
| US-AM-PKG-P2-* (6) | P2 模式（商家/分销/评级/智能体/发票/评价） | dyq 号源市场，远期不开工 |
