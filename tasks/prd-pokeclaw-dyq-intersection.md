# PRD: PokeClaw ∩ dyq 交集能力（第二期）

## 1. Introduction / Overview

第一期 PRD（`prd-claw-endcloud.md` + `.omc/prd.json`）已完成 30 条端云核心 + 端侧基础 story。本期聚焦**端侧独立可实现**的扩展能力，参考 dyq 后端的 599 条 PRD 找出与 PokeClaw 端侧可直接落地、不需要完整端云联调即能跑通的 11 条 story。

本期范围划定原则：
- **纯端侧**：完全本地能力，不依赖 dyq 后端
- **端侧 + 轻云契约**：端侧实现 cache / 读取 / 弹窗，云端契约定义完整但本期不联调跑通
- **不引入**：完整端云联调、跨设备状态同步、云端 UI、对外 API 鉴权流程

本期完成目标：把 PokeClaw 端侧从"通用手机代理"扩展为"可定制的手机代理平台"——支持多角色（不同 AI 人格/边界）、轻量模型、本地指令、用户控制审批、端云混合经验复用。

## 2. Goals

- 端侧 5 条 dyq 端侧独立 story 全部落地（小虾角色、端内控制台、任务追踪、接收云端指令、注册兼容性）
- 端侧 3 条 BACKLOG 端侧独立 story 全部落地（错过电话跟进、全局指令、1B/1.5B 轻量模型）
- 端侧 3 条"组合"轻云契约 story 全部落地端侧部分（角色商店、经验回读、主人审批）
- 不破坏第一期已实现的 30 条 story
- 不引入额外 MMKV schema migration（沿用现有 KV key 命名规范）
- 单 APK 即可演示所有 11 条 story 的核心路径

## 3. User Stories

### 3.1 段 1 — dyq 端侧独立 5 条

#### US-B-CMP-1-1: 小虾角色模型
**Description:** As a PokeClaw user, I want different "AI personalities" (roles) so that I can switch the agent's behavior for different contexts (work vs play vs family).

**Acceptance Criteria:**
- [ ] `LobsterRole` 数据类：`id, name, role, status (ENABLED|PAUSED|DISABLED), duties (List<String>), history, version, updatedAt, source (USER|SYSTEM)`
- [ ] `LobsterRoleManager.kt`：`list() / get(id) / activate(id) / pause(id) / disable(id) / createLocal() / updateLocal()`
- [ ] 端侧 KV 持久化（MMKV key: `lobster_roles_v1`），云端调整通过 `LobsterRoleSync.sync()` 拉取 diff 后写入
- [ ] 默认角色："通用助手 / 工作模式 / 家庭模式 / 谨慎模式"
- [ ] 角色状态机可手动切换（UI 入口：Settings → 角色）
- [ ] 当前激活角色 ID 写到 `KVUtils.LOBSTER_ACTIVE_ROLE_ID`，`TaskOrchestrator.startNewTask` 注入到 system prompt
- [ ] `XLog.d('lobster-role: activate id=X name=Y')`

#### US-B-OW-001: 端侧后台控制台
**Description:** As a tester, I want an in-app "control console" Activity so that I can drive PokeClaw tasks without needing the cloud admin panel.

**Acceptance Criteria:**
- [ ] `OnDeviceConsoleActivity.kt` — 端内 Activity
- [ ] 顶部 EditText 输入指令 + 发送按钮 + 历史指令下拉
- [ ] 点击发送 → 走 `TaskOrchestrator.startNewTask(channel, task, messageID)`
- [ ] 中部 RecyclerView 实时显示任务事件（via `TaskEventCallback`）
- [ ] 底部显示当前 active model + role + network type
- [ ] 不依赖云端（`channel=Channel.LOCAL`，`messageId=UUID.randomUUID().toString()`）
- [ ] 注册到 `AndroidManifest.xml`
- [ ] `XLog.d('on-device-console: send task=X')`

#### US-B-CMP-1-3: 任务追踪补完
**Description:** As a developer, I want a complete task status state machine persisted locally so I can audit / debug what each task did across restarts.

**Acceptance Criteria:**
- [ ] `TaskTracker` 状态机扩展：`PENDING / CLAIMED / RUNNING / PAUSED / COMPLETED / FAILED / ABORTED / REJECTED`
- [ ] `task_track` SQLite 表：`id, taskUuid, actor, status, prevStatus, occurredAt, note`
- [ ] `transition(taskUuid, newStatus, actor, note)` 方法：写入并 `XLog.i`
- [ ] `query(taskUuid)` / `query(actor, sinceMillis)` / `query(status, limit)` 三个查询入口
- [ ] 在 `TaskOrchestrator` 关键节点埋点：`onAcquire → CLAIMED`、`onLoopStart → RUNNING`、`onComplete → COMPLETED`、`onError → FAILED`、`revokeCurrentTask → ABORTED`
- [ ] 容量：保留最近 200 条，超出 FIFO 丢弃
- [ ] `XLog.i('task-track: task=X from=Y to=Z actor=A')`

#### US-B-CMP-2-3: 接收云端指令
**Description:** As a device registered to dyq cloud, I want to consume pending tasks and dispatch them to the local TaskOrchestrator so the cloud can drive the device.

**Acceptance Criteria:**
- [ ] `CloudNodeOrchestrator.dispatchPendingTasks()` — 在心跳响应中检测 `pendingTaskCount > 0` 时调用
- [ ] 调 `GET /api/claw-device/devices/{deviceId}/pending-tasks`（沿用现有 Retrofit API）
- [ ] 对每个 `PendingTaskItem`：
  - 立即上报 `taskUuid + receivedAt`（ACK）
  - 写入 `TaskTracker.transition(taskUuid, CLAIMED, "cloud")`
  - 走 `TaskOrchestrator.startNewTask(channel=LOCAL, task=command, messageId=taskUuid)`
- [ ] 失败任务走现有 `CloudTaskExecutor.executeControlAction` 路径（沿用第一期 US-CMP-2-4）
- [ ] WebSocket 路径：保留 `CloudWebSocketClient` 钩子，本期默认走轮询
- [ ] `XLog.d('cloud-dispatch: count=N taskUuid=X command=Y')`

#### US-B-3T-002: 设备注册兼容性
**Description:** As a tester on different environments, I want to switch between local/staging/production cloud backends so that I can verify registration behavior without rebuilding.

**Acceptance Criteria:**
- [ ] `ClawNodeEnrollmentManager` 支持多后端枚举：`LOCAL / STAGING / PRODUCTION`
- [ ] 每个后端独立存：`deviceId` + `deviceToken` + `refreshToken`（MMKV key: `claw_enroll_{env}_*`）
- [ ] Settings → 「云端控制」新增后端选择 RadioGroup（默认 PRODUCTION）
- [ ] 注册失败显示 4xx/5xx 错误码 + 具体原因 + 重试按钮
- [ ] 切换后端不丢失其他后端的 token（仅切换 active 指针）
- [ ] `XLog.i('enroll: env=X status=Y code=Z')`

### 3.2 段 2 — BACKLOG 端侧独立 3 条

#### US-B-MISSED-CALL: 错过电话自动跟进
**Description:** As a user who misses a call, I want PokeClaw to automatically send a follow-up SMS so I don't lose contact.

**Acceptance Criteria:**
- [ ] `MissedCallReceiver` 注册 `android.intent.action.PHONE_STATE` 广播
- [ ] 判定逻辑：`EXTRA_STATE=IDLE` + `incomingNumber` 非空 + 通话时长 < 5s
- [ ] `MissedCallFollowupJob`（WorkManager 5s 延迟）：用 `SmsManager.sendTextMessage` 发预置短信
- [ ] 短信模板可在 Settings 改（默认："抱歉刚才没接到电话，方便回拨吗？— PokeClaw"）
- [ ] 在 `ComposeChatActivity` 顶部显示「自动跟进」卡片（含来电号码、发送时间、SMS 状态）
- [ ] 关键约束：**不依赖 accessibility / WhatsApp 自动化**（BACKLOG P0 明确要求）
- [ ] 关闭开关：Settings → 「错过电话自动跟进」
- [ ] 必要权限：`READ_PHONE_STATE` / `SEND_SMS`
- [ ] `XLog.i('missed-call: number=X duration=Y s sent=Z')`

#### US-B-LOCAL-INSTRUCTIONS: 持久化全局指令
**Description:** As a user, I want a persistent "global instructions" text so every new task automatically includes my personal context.

**Acceptance Criteria:**
- [ ] Settings 新增「全局指令」多行 EditText（≤500 字符校验）
- [ ] 存储：`KVUtils.LOBSTER_GLOBAL_INSTRUCTIONS`
- [ ] `AgentConfig.systemPrompt` 注入位置：`${SAFETY_RULES}\n\n[User instructions]\n${globalInstructions}\n\n[Task]\n${task}`
- [ ] 不覆盖硬安全规则和工具规则，单独段落
- [ ] 切换聊天/任务时全局指令不丢失
- [ ] 删除时仅清空字符串，不删除 KV key
- [ ] `XLog.d('local-instructions: len=N')`

#### US-B-1B-MODELS: 1B / 1.5B 轻量模型
**Description:** As a low-RAM device user, I want smaller model options so I can still use PokeClaw on older phones.

**Acceptance Criteria:**
- [ ] `LocalModelManager.AVAILABLE_MODELS` 新增：
  - `gemma4-1b`（0.6GB，minRamGb=4）
  - `gemma4-1.5b`（1.0GB，minRamGb=6）
- [ ] URL 沿用 `litert-community/gemma-4-*-it-litert-lm` 命名规则（占位 URL 需用户填）
- [ ] `recommendedModel()` 改为 3 段判定：< 6GB → 1B；6-8GB → 1.5B；>8GB → 2.6GB
- [ ] `LlmConfigActivity` 列表自动显示新条目
- [ ] 不改 LiteRT-LM runtime 路径（复用现有 `LocalModelManager.downloadModel`）
- [ ] `XLog.d('model-recommend: ram=Xgb model=Y')`

### 3.3 段 3 — 组合轻云 3 条

#### US-B-ROLE-STORE: 角色商店
**Description:** As a user, I want to browse and activate pre-defined roles from a (mock) cloud role store so I can quickly switch my agent's personality.

**Acceptance Criteria:**
- [ ] `LobsterRoleStore.kt`：`list() / get(id) / activate(id) / sync()`
- [ ] 端侧 cache 命中即返回；首次或缓存过期触发 `sync()`
- [ ] `sync()` 调 `GET /api/claw/roles/list`（**契约定义；本期不联调跑通**）
- [ ] 响应写入 `lobster_roles_store` 表
- [ ] Settings → 「角色商店」入口：列表 + 激活按钮 + 状态显示
- [ ] 端侧只读 + activate；写操作（创建/编辑）仍在云端管理后台（本期不实现）
- [ ] 离线时显示本地 cache 列表 + 「云端不同步」角标
- [ ] `XLog.d('role-store: sync count=N fromCache=Y')`

#### US-B-EXPERIENCE-READ: 经验包端侧读取
**Description:** As a user, I want to reuse my own past successful/failed task experiences as few-shot prompts so similar tasks run better.

**Acceptance Criteria:**
- [ ] `ExperienceReader` 与 `ExperienceUploader` 对称
- [ ] 契约 `GET /api/claw-experience/list?deviceId=X&since=Y`（**契约 + 端侧 cache 读取逻辑**）
- [ ] 使用场景：相似任务开始时，注入 top-3 成功 + top-1 失败经验作为 few-shot
- [ ] 离线时 fallback 到 `ExperienceLocalCache`（KV 持久化上次拉到的副本）
- [ ] 仍遵守"只读自己写的"语义，不读他人经验
- [ ] `XLog.d('experience-read: hit=N miss=M')`

#### US-B-TASK-APPROVAL: 主人审批
**Description:** As a user, I want to approve/reject high-stakes tasks before they execute so I keep control.

**Acceptance Criteria:**
- [ ] `TaskOrchestrator.startNewTask` 增加 `requiresApproval: Boolean` 参数
- [ ] 触发条件：任务 metadata 含 `priority=HIGH` 或 `costYuan >= 1.0`
- [ ] 弹窗 `OwnerApprovalDialog`（端内 AlertDialog，非云端推送）
- [ ] 主人点"批准" → 继续执行；"拒绝" → 任务进入 `REJECTED` 终态（写 `TaskTracker`）
- [ ] 审批结果通过 `POST /api/claw/task-approval`（**契约 + 端侧上报**）写入云端
- [ ] 离线时审批结果入 `CloudEventQueue`，网络恢复后补报
- [ ] 弹窗内显示任务摘要 + 预计成本 + 风险评估
- [ ] `XLog.i('task-approval: taskUuid=X approved=Y actor=owner')`

## 4. Functional Requirements

### 4.1 数据持久化
- FR-1: 所有新增持久化均使用 MMKV，key 命名遵循 `{namespace}_{version}` 规范（如 `lobster_roles_v1`）
- FR-2: SQLite 表 `task_track` 必须有索引 `(taskUuid, occurredAt DESC)`
- FR-3: 任何新增的 `KVUtils` 常量必须在 `KVUtils.kt` 集中定义，禁止散落

### 4.2 角色 / 全局指令
- FR-4: 全局指令为空字符串时不得注入 `[User instructions]` 段落
- FR-5: 当前激活角色 ID 必须能从任意 TaskOrchestrator 入口注入到 system prompt
- FR-6: 角色状态机变更必须触发 XLog.i 日志
- FR-7: 角色创建/编辑在端侧允许，但来源标记为 `USER`；`SYSTEM` 来源仅由云端 sync 写入

### 4.3 任务追踪
- FR-8: 每次状态机 transition 必须记录 `prevStatus → newStatus` 而非只记终态
- FR-9: `REJECTED` 状态只能由 `OwnerApprovalDialog` 触发
- FR-10: `query(taskUuid)` 返回该任务所有 transition，按时间升序

### 4.4 接收云端指令
- FR-11: ACK 必须在 dispatch 之前发出，确保云端能追踪到任务被领取
- FR-12: 单次心跳最多 dispatch 5 个 pending task，避免突发流量
- FR-13: 同一 taskUuid 重复 dispatch 必须幂等（已在 `task_track` 标记 CLAIMED 则跳过）

### 4.5 注册兼容性
- FR-14: 切换后端时显示确认对话框（"将重新注册到新后端"）
- FR-15: 每个后端的 token 独立存，删除时只删该后端的 key
- FR-16: 注册失败时区分网络错误 / 4xx / 5xx，分别提示

### 4.6 错过电话跟进
- FR-17: 仅在通话时长 < 5s 时判定为"错过"（拒接/挂断不触发）
- FR-18: 同一号码 5 分钟内仅触发 1 次（防骚扰）
- FR-19: SMS 发送失败时卡片显示失败状态 + 重试按钮

### 4.7 轻量模型
- FR-20: 设备 RAM < 4GB 不得推荐任何本地模型
- FR-21: 模型 URL 在 UI 显示并可手动覆盖（Settings → 「模型源」）

### 4.8 角色商店 / 经验回读 / 主人审批
- FR-22: 所有轻云契约本期仅定义 Retrofit API + DTO，不调用真实后端
- FR-23: 端侧 cache 写入时同步 XLog.d 记录条目数与来源
- FR-24: 离线时所有 sync 调用必须 fast-fail 并使用本地 cache

## 5. Non-Goals (Out of Scope)

- **联调跑通**：本期不与 dyq 后端真实联调，契约仅定义
- **角色创建/编辑云端 UI**：云端管理后台不在本期范围
- **跨设备任务接力**：跨设备状态同步不在本期
- **任务市场（用户发布任务）**：仅"角色商店 + 经验回读 + 主人审批"3 个轻云入口
- **完整 WebSocket 启用**：`CloudWebSocketClient` 仍默认关闭
- **WhatsApp 自动化跟随**：`MissedCallReceiver` 仅发 SMS，不驱动 WhatsApp
- **云端 AI 角色生成**：角色内容仅来自云端预置 + 端侧自建
- **大模型升级**：1B/1.5B 是补充，不替换现有 2.6GB / 3.6GB 选项

## 6. Design Considerations

### 6.1 UI 入口分布
- `SettingsActivity`：
  - 「全局指令」多行编辑
  - 「角色」入口 → `LobsterRoleManagerActivity`（列表 + 切换状态）
  - 「角色商店」入口 → `LobsterRoleStoreActivity`
  - 「云端控制」后端选择 RadioGroup
  - 「错过电话自动跟进」开关 + 短信模板
- `ComposeChatActivity`：
  - 顶部「自动跟进」卡片（仅在 MissedCallJob 触发后显示）
  - 任务事件流不变
- 新 Activity：
  - `OnDeviceConsoleActivity`（端内控制台）
  - `LobsterRoleManagerActivity`
  - `LobsterRoleStoreActivity`

### 6.2 复用现有组件
- `TaskOrchestrator.startNewTask` 沿用
- `TaskTracker` 新建但写入位置与 `TaskStepRecorder` 共存
- `CloudEventQueue` 沿用，主人审批结果用新事件类型 `task_approval`
- `KVUtils` 沿用，新增常量集中管理
- `LocalModelManager.downloadModel` 沿用
- `XLog` 沿用

### 6.3 不引入
- 不引入新依赖（gson/okhttp/retrofit 已就绪）
- 不引入 Room（用 SQLiteOpenHelper 沿用 `TaskStepRecorder` 模式）
- 不引入 LiveData/Flow（用 callback + 主线程 Handler 沿用）
- 不引入 Compose（用 LinearLayout 沿用）

## 7. Technical Considerations

### 7.1 状态机扩展影响
- `TaskSessionStore` 当前 phase 是 `IDLE / RUNNING / STOPPING / CANCELLED`
- 新增 `REJECTED` 终态时需更新 `releaseTask()` 的语义
- `TaskTracker` 是独立 SQL 表，与 `TaskSessionStore` 并存不冲突

### 7.2 端侧 + 轻云契约
- 契约文件 `app/src/main/java/io/agents/pokeclaw/cloud/api/DeviceApi.kt` 新增：
  - `GET /api/claw/roles/list` → `RolesList200Response`
  - `GET /api/claw-experience/list` → `ExperienceList200Response`
  - `POST /api/claw/task-approval` → `TaskApprovalRequest`
- DTO 放 `app/src/main/java/io/agents/pokeclaw/cloud/model/`，命名沿用 `*Request` / `*200Response`
- 不调真实后端：`Retrofit` 可构造但本期不发起请求

### 7.3 WorkManager 使用
- `MissedCallFollowupJob` 必须 constraint：`NETWORK_TYPE_NONE`（SMS 发送不需网络）+
  `setRequiresBatteryNotLow(false)`（低电量也发）
- 唯一 work 名称：`missed_call_followup_${number}_${timestamp}`

### 7.4 内存 / 性能
- `task_track` 表超过 200 行时 `DELETE FROM task_track WHERE id IN (SELECT id FROM task_track ORDER BY occurredAt ASC LIMIT 50)`
- 角色列表 cache 限 100 条
- 经验 cache 限 50 条

### 7.5 权限
- 新增权限：
  - `READ_PHONE_STATE`（错过电话）
  - `SEND_SMS`（错过电话跟进）
  - `RECEIVE_SMS`（可选，读取发送回执）
- `AndroidManifest.xml` 中声明并 `requestLegacyExternalStorage` 不需要

## 8. Success Metrics

- 11 条 story 全部 `passes: true`
- 单 APK 即可演示所有 11 条 story 核心路径
- 0 个 story 破坏第一期 30 条 story 的现有行为
- `XLog.d/i` 覆盖率 100%（每条 story 关键路径都有日志）
- 单 story 代码量 ≤ 250 行（保持 KISS）

## 9. Open Questions

- 角色 ID 命名空间如何区分 SYSTEM / USER？建议：`role-system-{uuid}` / `role-user-{uuid}`
- 主人审批弹窗是否需要 PIN 码二次确认？（暂定不引入，超出 MVP）
- 1B / 1.5B 模型的真实 URL 何时填入？（本期占位 `https://example.com/{model}.litertlm`，后续 release 替换）
- `ExperienceReader` 是否需要用户授权才读取？（暂定不需要，因为是读自己的数据）

---

**PRD 生成日期**: 2026-06-13
**对应 .omc/prd.json 增量**: 11 条新 story（计划追加到现有 30 条之后）
**关联**: 第一期 `prd-claw-endcloud.md` + `.omc/prd.json`（30 条已 passes）
**联调策略**: 本期全部 11 条为端侧独立 + 轻云契约，不联调跑通
