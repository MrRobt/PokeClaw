# PokeClaw 编译环境与 DYQ 接口对接验证

日期：2026-05-15
负责人：安卓小龙
关联问题：CMP-1801
仓库：`/mnt/e/code/PokeClaw`
分支：`main`

## 一、前提假设核查

本轮不是空转，已先用纸夹接口查询当前成员 `ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0` 名下未完成问题，并按优先级选择最高且未完成的 `CMP-1801`。

已确认：

- 当前目录：`/mnt/e/code/PokeClaw`
- 当前分支：`main`
- 当前仓库是真实 PokeClaw 安卓仓库，不是 zeroclaw、metaclaw 等相近仓库。
- 产品定位：PokeClaw 是手机常驻端侧执行底座，不是电脑控制框架、云手机框架，也不是 dyq 云端统领中枢。
- dyq 方向：统一抽象是“执行节点”；PokeClaw 是安卓执行节点，复杂决策和经验沉淀回到 dyq 后端 Claw 小龙虾中枢。

## 二、两种方案取舍

### 方案一：直接按旧设备接口 `/api/claw-device/*` 对接

优点：

- 已有旧设备契约 `api-contracts/device.openapi.yaml`。
- 接口较少，注册、心跳、任务拉取、结果回传路径清晰。
- 适合短期兼容验证。

缺点：

- 只覆盖 PokeClaw 设备端，无法自然复用到 weflow、浏览器节点、桌面节点、服务器脚本节点。
- 字段较偏“设备”，不完整表达统一执行节点能力、能力标签、安全策略、配置拉取、指令进度等信息。

### 方案二：优先按统一执行节点契约 `executor-node.openapi.yaml` 对接，旧设备接口作为兼容层

优点：

- 符合 dyq 三条主线里的“统一执行节点”抽象。
- PokeClaw、weflow 和后续节点可以共享注册、心跳、能力上报、配置拉取、指令拉取、执行进度、结果回传模型。
- 字段统一使用 `string` 编号和 ISO 8601 时间，更适合多端协作。
- 能表达 `capabilityTags`、`safetyPolicy`，方便后端调度和安全过滤。

缺点：

- 当前后端实现和端侧代码需要进一步联调确认。
- 安卓端第一阶段需要做一层 `cloudnode` 适配模型，避免把统一节点字段直接塞进现有 `TaskOrchestrator`。

### 本轮选择

选择方案二：以统一执行节点契约为主，旧 `/api/claw-device/*` 仅作为兼容接口保留。

理由：这更符合 PokeClaw 的长期产品定位，也避免安卓端只围绕单一旧设备接口做窄实现。安卓端应新增独立端云适配层，所有云端任务仍进入现有 `TaskOrchestrator`，不得绕过前台服务、权限、可访问性、安全和日志链路。

## 三、实际检查文件

PokeClaw 仓库：

- `README.md`：复核产品方向、路线图和平台约束。
- `CLAUDE.md`：复核架构优先、日志可追踪、端到端验收要求。
- `app/build.gradle.kts`：确认构建配置、安卓版本、依赖和输出包名逻辑。
- `app/src/main/java/io/agents/pokeclaw/TaskOrchestrator.kt`：确认端侧任务入口和现有任务执行主链路。
- `app/src/main/java/io/agents/pokeclaw/TaskEvent.kt`：确认已有完成、失败、取消、阻塞、进度和工具动作事件，可映射为云端状态。
- `app/src/main/java/io/agents/pokeclaw/AppCapabilityCoordinator.kt`：确认端侧权限和能力状态可作为心跳能力快照来源。
- `app/src/main/java/io/agents/pokeclaw/support/DebugReportManager.kt`：确认可参考调试报告能力，但云端上报必须脱敏。
- `app/src/main/java/io/agents/pokeclaw/server/ConfigServer.kt`、`ConfigServerManager.kt`：确认已有本机配置服务可参考，但端云对接应由端侧主动外连云端。
- `QA_CHECKLIST.md`：确认已有“Z 端云设备节点验收”用例，可承接后续端到端验证。

DYQ 仓库：

- `/mnt/e/code/dyq/AGENTS.md`：确认三条主线和统一执行节点抽象。
- `/mnt/e/code/dyq/api-contracts/device.openapi.yaml`：确认旧设备端兼容接口。
- `/mnt/e/code/dyq/api-contracts/executor-node.openapi.yaml`：确认统一执行节点正式契约。

## 四、编译环境验证结果

环境：

- JDK：OpenJDK 17.0.18
- Gradle 包装器：9.3.1
- Kotlin：2.2.21
- 安卓软件开发工具路径：`/opt/android-sdk`
- 已发现平台：`android-36`、`android-36.1`

已执行命令：

```bash
pwd
git branch --show-current
java -version
./gradlew --version
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --console=plain
```

结果：

- `:app:assembleDebug`：通过。
- `:app:testDebugUnitTest`：通过。
- 调试包产物：`/mnt/e/code/PokeClaw/app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260515_132644.apk`
- 编译警告：存在 4 个既有废弃或冗余警告，未阻塞构建：
  - `DebugTaskReceiver.kt:170` 使用废弃 `get`。
  - `ConfigServer.kt:322` 使用废弃 `parms`。
  - `SettingsActivity.kt:88` 使用废弃 `statusBarColor`。
  - `SettingsActivity.kt:664` 有冗余 `else`。

## 五、DYQ 接口联调清单

### 主线接口：统一执行节点

来自 `/mnt/e/code/dyq/api-contracts/executor-node.openapi.yaml`。

节点端接口：

| 环节 | 方法与路径 | 安卓端用途 |
|---|---|---|
| 注册 | `POST /api/executor-nodes/register` | PokeClaw 首次作为 `POKECLAW_ANDROID` 节点登记，上传稳定节点标识、版本、平台、能力、非敏感配置。 |
| 刷新令牌 | `POST /api/executor-nodes/token/refresh` | 节点令牌到期前刷新，失败时提示重新配对。 |
| 心跳 | `POST /api/executor-nodes/{nodeId}/heartbeat` | 上报 `ONLINE`、`BUSY`、`OFFLINE`、`ERROR`，附当前任务、错误摘要、能力状态。 |
| 能力上报 | `PUT /api/executor-nodes/{nodeId}/capabilities` | 上报安卓点击、输入、截图、任务执行、聊天、本地模型、云模型等能力。 |
| 配置拉取 | `GET /api/executor-nodes/{nodeId}/config` | 拉取轮询间隔、任务开关、安全策略等非敏感配置。 |
| 待执行指令 | `GET /api/executor-nodes/{nodeId}/commands/pending` | 端侧主动拉取云端下发任务，不要求云端反连手机。 |
| 确认接收 | `POST /api/executor-nodes/{nodeId}/commands/{commandId}/ack` | 接到指令后回传已接收，避免重复下发。 |
| 查询指令 | `GET /api/executor-nodes/{nodeId}/commands/{commandId}` | 必要时拉取指令详情。 |
| 进度上报 | `POST /api/executor-nodes/{nodeId}/commands/{commandId}/progress` | 将 `TaskEvent.LoopStart`、`Progress`、`ToolAction` 映射为进度。 |
| 结果回传 | `POST /api/executor-nodes/{nodeId}/commands/{commandId}/result` | 将完成、失败、取消、阻塞结果回传云端。 |

管理端接口：

| 环节 | 方法与路径 | 用途 |
|---|---|---|
| 节点列表 | `GET /claw/executor-nodes` | 后台查看所有执行节点。 |
| 节点详情 | `GET /claw/executor-nodes/{nodeId}` | 后台查看 PokeClaw 设备状态。 |
| 创建指令 | `POST /claw/executor-nodes/commands` | 后台或 Claw 中枢向 PokeClaw 下发任务。 |
| 节点指令列表 | `GET /claw/executor-nodes/{nodeId}/commands` | 查看某个节点任务历史。 |
| 取消指令 | `POST /claw/executor-nodes/commands/{commandId}/cancel` | 云端取消未完成任务。 |

### 兼容接口：旧设备端 API

来自 `/mnt/e/code/dyq/api-contracts/device.openapi.yaml`。

| 环节 | 方法与路径 | 说明 |
|---|---|---|
| 设备注册 | `POST /api/claw-device/register` | 旧设备注册接口，返回 `deviceToken`、`refreshToken`。 |
| 心跳 | `POST /api/claw-device/heartbeat` | 上报电量、充电、网络，响应待处理任务数。 |
| 任务拉取 | `GET /api/claw-device/devices/{deviceId}/pending-tasks` | 拉取旧设备任务。 |
| 结果回传 | `POST /api/claw-device/tasks/{taskUuid}/result` | 回传旧设备任务结果。 |
| 刷新令牌 | `POST /api/claw-device/token/refresh` | 刷新旧设备令牌。 |

## 六、安卓端最小改造清单

建议后续新增独立包，不在本轮直接改任务编排主流程：

```text
app/src/main/java/io/agents/pokeclaw/cloudnode/
  CloudNodeConfig.kt              云端地址、节点编号、令牌、开关、轮询间隔
  CloudNodeModels.kt              统一执行节点注册、心跳、指令、进度、结果模型
  CloudNodeApi.kt                 Retrofit 接口定义
  CloudNodeClient.kt              鉴权、请求、错误归一化、重试边界
  DeviceIdentity.kt               稳定节点标识、应用版本、构建指纹、系统信息
  DeviceCapabilitySnapshot.kt     可访问性、通知、前台服务、模型、网络、电量能力快照
  CloudCommandPoller.kt           待执行指令轮询和确认接收
  CloudResultReporter.kt          `TaskEvent` 到云端进度/结果的映射
  CloudEventQueue.kt              离线有限队列和恢复补报
```

接入点：

- `ClawApplication`：初始化配置，但默认关闭云节点。
- 设置页：新增云端设备节点开关、云端地址、配对令牌、连接状态。
- `TaskOrchestrator`：只暴露事件监听或回调桥接，不把网络请求塞入编排器。
- `TaskSessionStore`：提供当前任务状态快照给心跳，不反向依赖云端。
- `AppCapabilityCoordinator`：提供权限和能力快照。

## 七、端侧状态映射

| PokeClaw 端侧事件 | 统一执行节点状态 | 说明 |
|---|---|---|
| 拉取到任务并入队 | `ACCEPTED` | 已确认接收，后端可停止重复投递。 |
| `LoopStart` | `RUNNING` | 记录轮次。 |
| `Progress` | `RUNNING` | 记录步骤摘要。 |
| `ToolAction` | `RUNNING` | 只上传工具展示名，不上传敏感参数。 |
| `Completed` | `SUCCEEDED` | 上传脱敏摘要、耗时、模型名。 |
| `Failed` | `FAILED` | 上传错误码和脱敏错误摘要。 |
| `Cancelled` | `CANCELLED` | 用户或云端取消。 |
| `Blocked` | `FAILED` 或 `EXPIRED` | 视后端状态机确定，建议新增阻塞原因字段。 |

## 八、待确认问题

1. 后端正式采用统一执行节点接口后，旧 `/api/claw-device/*` 是否只保留兼容，不再新增字段。
2. PokeClaw 节点注册是否使用配对码换取 `nodeToken`，还是直接注册返回令牌。
3. `ExecutorCommandStatus` 当前没有 `BLOCKED`，端侧环境阻塞需映射为 `FAILED` 加 `errorCode`，或后端补充状态。
4. 端侧截图、日志和证据上传的安全白名单还需后端给出文件类型、大小和保留期限。
5. 安卓端令牌安全存储建议用安卓密钥库，不能明文存储到普通偏好配置。

## 九、端到端验证建议

已存在 `QA_CHECKLIST.md` 的 `Z1` 到 `Z7` 用例。后续实现时必须至少执行：

- `Z1` 首次配对注册。
- `Z2` 鉴权失败用户可见。
- `Z3` 心跳能力快照。
- `Z4` 云端任务下发到本地执行。
- `Z5` 离线结果缓存与恢复补报。
- `Z6` 忙碌态拒绝新任务。
- `Z7` 敏感信息脱敏。

本轮为编译环境与接口联调清单验证，已执行构建和单元测试；未连接真机，因此真机端到端用例标记为待后续联调执行。

## 十、结论

当前 PokeClaw 安卓仓库编译环境可用，`assembleDebug` 和 `testDebugUnitTest` 均通过。DYQ 侧已有旧设备接口和统一执行节点接口两套契约；安卓后续应优先按统一执行节点契约实现独立 `cloudnode` 适配层，同时保留旧设备接口兼容映射，不应直接把云端网络请求塞进 `TaskOrchestrator`。
