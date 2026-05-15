# PokeClaw 执行节点注册与心跳对接方案

日期：2026-05-15
负责人：安卓小龙
仓库：`/mnt/e/code/PokeClaw`
分支：`main`
关联问题：`CMP-1835`、`CMP-1876`、`CMP-1861`、`CMP-1801`、`CMP-1794`

## 一、当前定位

PokeClaw 是安卓端侧执行节点，不是云端统领中枢，也不是云手机框架。复杂任务拆解、角色编排、经验汇总和自主学习归 dyq 后端 Claw 小龙虾中枢；安卓端只负责接收简单指令、按本机权限和运行时约束执行、回传状态、结果、报错摘要和必要设备状态。

## 二、已确认的真实仓库与分支

- 当前工作目录：`/mnt/e/code/PokeClaw`
- 仓库顶层：`/mnt/e/code/PokeClaw`
- 当前分支：`main`
- 远端：`origin=https://github.com/MrRobt/PokeClaw.git`
- 当前未提交变更：`QA_CHECKLIST.md`、`docs/product/pokeclaw-device-node-integration.md`、`docs/product/pokeclaw-executor-node-plan.md`

## 三、已检查文件清单

- `CLAUDE.md`：确认本仓库强制要求架构优先、日志可追踪、每次变更包含端到端验收。
- `/mnt/e/code/dyq/AGENTS.md`：确认 PokeClaw 是小龙虾云端中枢下面的手机执行手下，端侧只做必要本地判断。
- `/mnt/e/code/dyq/.planning/pm/current/THREE_MAIN_GOALS.md`：确认三条主线和统一执行节点抽象。
- `app/src/main/java/io/agents/pokeclaw/TaskOrchestrator.kt`：确认现有任务入口、任务锁、忙碌拒绝、完成失败回调都在任务编排器内。
- `app/src/main/java/io/agents/pokeclaw/TaskEvent.kt`：确认已有完成、失败、取消、阻塞、进度、工具动作等结构化事件，可映射为云端状态。
- `app/src/main/java/io/agents/pokeclaw/AppCapabilityCoordinator.kt`：确认已有可访问性、通知权限、前台服务、悬浮窗、电池优化、存储权限的能力快照来源。
- `app/src/main/java/io/agents/pokeclaw/support/DebugReportManager.kt`：确认可参考调试报告与日志收集能力，但云端上报必须脱敏，不能上传完整聊天、通知、联系人或密钥。
- `app/src/main/java/io/agents/pokeclaw/server/ConfigServer.kt`、`ConfigServerManager.kt`：确认现有本机配置服务可参考，但端云对接应由手机主动外连云端，不能要求云端反连手机局域网服务。
- `app/build.gradle.kts`：确认已有 `okhttp`、`retrofit`、`gson` 依赖，第一阶段无需新增网络库。
- `QA_CHECKLIST.md`：已新增“Z 端云设备节点验收”草案，覆盖注册、鉴权失败、心跳、任务下发、离线补报、忙碌拒绝和脱敏。

## 四、第一阶段最小闭环

第一阶段只做注册和心跳脚手架，不直接改动任务执行主流程。

1. 设置页提供“云端设备节点”开关、云端地址、配对令牌入口，默认关闭。
2. 安卓端生成稳定设备标识，采集应用版本、构建指纹、系统版本、厂商、机型和能力列表。
3. 调用 dyq 云端注册接口，换取云端节点编号与短期访问令牌。
4. 定时心跳上报在线状态、能力快照、当前任务状态、最近错误摘要。
5. 鉴权失败时停止重试并给用户可见提示。
6. 网络不可用时不影响本地执行，只记录待重试状态事件。

## 五、第二阶段最小任务闭环

第二阶段在接口契约确认后接入任务拉取和结果回传。

1. 安卓端主动拉取云端下发任务，不暴露手机本地服务给云端反连。
2. 云端任务必须进入 `TaskOrchestrator.startNewTask`，不得绕过任务锁、前台服务、权限检查、可访问性约束和日志链路。
3. 若已有任务运行，按现有忙碌逻辑拒绝或排队，并向云端回传忙碌状态。
4. 将 `TaskEvent` 映射为云端状态：已接受、执行中、完成、失败、取消、阻塞。
5. 回传结果只包含摘要、状态、错误码、轮次、模型名、事件时间和任务编号，不上传敏感正文。

## 六、建议新增包结构

```text
app/src/main/java/io/agents/pokeclaw/cloudnode/
  CloudNodeConfig.kt              云端地址、令牌、轮询间隔、开关
  DeviceIdentity.kt               稳定设备标识与构建信息
  DeviceCapabilitySnapshot.kt     端侧能力快照映射
  CloudNodeModels.kt              注册、心跳、任务、结果、错误模型
  CloudNodeApi.kt                 网络接口定义
  CloudNodeClient.kt              请求、鉴权头、错误归一化
  CloudTaskPoller.kt              任务拉取与调度桥接
  CloudResultReporter.kt          状态、结果、错误上报
  CloudEventQueue.kt              本地有限队列与重试
```

## 七、云端契约草案

### 注册请求

```json
{
  "deviceId": "端侧稳定标识",
  "deviceName": "用户可识别名称",
  "appVersion": "应用版本",
  "buildFingerprint": "构建指纹",
  "androidVersion": "系统版本",
  "manufacturer": "厂商",
  "model": "机型",
  "capabilities": ["task", "chat", "accessibility", "notification", "localModel", "cloudModel"],
  "timezone": "设备时区"
}
```

### 注册响应

```json
{
  "nodeId": "云端节点编号",
  "accessToken": "短期访问令牌",
  "heartbeatIntervalSec": 30,
  "taskPollIntervalSec": 10
}
```

### 心跳请求

```json
{
  "nodeId": "云端节点编号",
  "status": "online",
  "foregroundServiceRunning": true,
  "accessibilityReady": true,
  "notificationReady": true,
  "activeTaskId": "云端任务编号或空",
  "lastErrorCode": "最近错误码或空",
  "lastErrorAt": "最近错误时间或空"
}
```

### 任务下发

```json
{
  "taskId": "云端任务编号",
  "requestId": "幂等请求编号",
  "taskText": "查询当前电量",
  "source": "dyq-claw",
  "priority": "normal",
  "timeoutSec": 300,
  "requireUserVisible": true,
  "createdAt": "云端创建时间"
}
```

### 结果回传

```json
{
  "taskId": "云端任务编号",
  "requestId": "幂等请求编号",
  "status": "completed",
  "summary": "任务完成摘要",
  "errorCode": null,
  "errorMessage": null,
  "round": 3,
  "modelName": "模型名称",
  "eventTime": "端侧事件时间"
}
```

## 八、状态映射

| 端侧事件 | 云端状态 | 说明 |
|---|---|---|
| 接收到任务 | 已接受 | 已进入端侧队列或任务锁 |
| `LoopStart`、`Progress` | 执行中 | 可带轮次和步骤摘要 |
| `ToolAction` | 执行中 | 只上报工具展示名，不上传敏感参数 |
| `Completed` | 已完成 | 任务成功完成 |
| `Failed` | 已失败 | 执行失败 |
| `Cancelled` | 已取消 | 用户或系统取消 |
| 权限或环境阻塞 | 已阻塞 | 可访问性、通知、系统弹窗等阻塞 |

## 九、错误码草案

| 错误码 | 场景 | 用户可见建议 |
|---|---|---|
| `CLOUD_NODE_DISABLED` | 云端节点未启用 | 设置页显示未启用 |
| `CLOUD_AUTH_MISSING` | 缺少令牌 | 引导重新配对 |
| `CLOUD_AUTH_REJECTED` | 云端拒绝令牌 | 引导重新配对并停止重试 |
| `NETWORK_UNAVAILABLE` | 当前无网络 | 等待联网后重试 |
| `TASK_BUSY` | 已有任务运行 | 云端显示端侧忙碌 |
| `ACCESSIBILITY_NOT_READY` | 可访问性不可用 | 引导打开可访问性服务 |
| `NOTIFICATION_NOT_READY` | 通知权限不可用 | 引导打开通知权限 |
| `SYSTEM_DIALOG_BLOCKED` | 系统弹窗阻塞 | 上报阻塞摘要 |
| `TASK_EXECUTION_FAILED` | 通用执行失败 | 返回脱敏错误摘要 |

## 十、安全与日志要求

- 云端地址、配对令牌、访问令牌不得硬编码。
- 令牌应使用安卓安全存储，禁止明文持久化。
- 日志必须能通过系统日志还原注册、心跳、拉取、执行、回传、重试全链路。
- 日志不得打印令牌、密钥、联系人列表、通知正文、聊天正文和完整提示词。
- 所有云端失败必须用户可见或可在设置页看到明确状态，禁止静默失败。

## 十一、端到端验收入口

已在 `QA_CHECKLIST.md` 增加“Z 端云设备节点验收”：

- `Z1` 首次配对注册。
- `Z2` 鉴权失败用户可见。
- `Z3` 心跳能力快照。
- `Z4` 云端任务下发到本地执行。
- `Z5` 离线结果缓存与恢复补报。
- `Z6` 忙碌态拒绝新任务。
- `Z7` 敏感信息脱敏。

## 十二、本轮实际执行命令

```bash
pwd
git branch --show-current
git status --short
git remote -v
git rev-parse --show-toplevel
git log --oneline -5

curl -sS -H "Authorization: Bearer $PAPERCLIP_API_KEY" \
  "http://127.0.0.1:3101/api/companies/bfc57cd0-e725-42e2-b221-400eaca22123/issues?limit=50"

./gradlew testDebugUnitTest assembleDebug
```

执行结果：

- 目录和分支确认通过：`/mnt/e/code/PokeClaw`，`main`。
- 纸夹接口读取通过，并确认当前成员有 `CMP-1876`、`CMP-1861`、`CMP-1835`、`CMP-1801`、`CMP-1794` 五个未完成问题。
- 构建命令已执行，但当前环境 `JAVA_HOME` 为空，且找不到 `java`、`javac`，构建未进入编译阶段。

## 十三、当前阻塞

1. 当前问题注入上下文里的“问题编号、标题、正文”为空；本轮按当前成员名下最相关的 PokeClaw 问题执行，并准备把结果评论到 `CMP-1876` 和 `CMP-1835`。
2. dyq 后端统一执行节点正式接口路径、鉴权方式和字段命名尚未在本仓库提供稳定契约；安卓端不能凭空猜字段直接写实现。
3. 当前执行环境缺少 JDK，无法完成安卓单元测试和构建验证。

## 十四、下一步建议

1. 后端阿诚先确认统一执行节点接口契约和本地模拟服务地址。
2. 安卓端在契约确认后按第一阶段新增 `cloudnode` 脚手架，默认关闭，不影响现有用户。
3. 质检按 `QA_CHECKLIST.md` 的 `Z1` 到 `Z4` 先跑注册、鉴权失败、心跳和任务下发闭环。
4. 编译环境先安装或配置 JDK，再重跑 `./gradlew testDebugUnitTest assembleDebug`。

## 十五、本轮补充核查与方案取舍

### 前提假设核查

- 纸夹任务来源：已通过纸夹接口查询当前成员 `ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0` 名下未完成任务，并选择最高优先级 `CMP-1835`。
- 工作目录：`/mnt/e/code/PokeClaw`。
- 当前分支：`main`，符合 PokeClaw 安卓端仓库规则。
- 当前远端：`origin=https://github.com/MrRobt/PokeClaw.git`，`public-upstream=https://github.com/agents-io/PokeClaw.git`。
- 产品方向：已复核 `README.md` 的产品方向、路线图和平台约束，确认不能把 PokeClaw 做成电脑控制、云手机或后端大脑；它必须保持手机常驻端侧执行底座。
- 团队方向：已复核 `/mnt/e/code/dyq/AGENTS.md` 和 `.planning/pm/current/THREE_MAIN_GOALS.md`，确认端侧是统一执行节点之一，复杂决策回到 dyq 后端 Claw 中枢。

### 两种实现方案

方案一：在现有任务链路里直接塞云端注册、轮询和回传逻辑。

- 优点：短期文件少，能快速把网络请求串起来。
- 缺点：会污染 `TaskOrchestrator`、设置页和任务事件模型；后续接口变动会牵连核心任务执行；也容易绕过现有权限、前台服务和用户可见错误规则。

方案二：新增独立 `cloudnode` 端云适配层，只通过公开入口接入现有能力。

- 优点：注册、心跳、轮询、结果回传、离线队列和脱敏都在独立包内；核心执行仍走 `TaskOrchestrator.startNewTask`；能力快照复用 `AppCapabilityCoordinator`；状态回传只订阅 `TaskEvent`，不反向污染核心事件模型。
- 缺点：第一阶段需要多建几个小文件，初始结构比直接硬塞稍多。

选择：采用方案二。理由是它符合 PokeClaw “手机常驻执行底座”的产品方向，也符合 dyq 三端协作中“统一执行节点、端侧只做简单执行、复杂决策回云端”的边界；同时能把后端接口尚未稳定带来的变更风险限制在端云适配层内。

## 十六、Kotlin 文件改动清单

本轮不直接改核心 Kotlin 代码；后续接口契约确认后，建议按下面清单实施。全部文件应使用中文注释，日志标签统一以 `PokeClaw/CloudNode` 或等价前缀标识，令牌和敏感正文不得进入日志。

### 新增文件

- `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudNodeConfig.kt`：云端地址、开关、心跳间隔、任务轮询间隔、节点编号和令牌引用；令牌落安全存储，不明文写配置。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/DeviceIdentity.kt`：稳定设备标识、应用版本、构建指纹、系统版本、厂商、机型、时区。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/DeviceCapabilitySnapshot.kt`：把 `AppCapabilityCoordinator.snapshot()` 映射为云端能力列表和心跳字段。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudNodeModels.kt`：注册、心跳、任务拉取、任务确认、结果回传、错误上报的数据模型。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudNodeApi.kt`：`Retrofit` 接口定义，路径以后端契约为准。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudNodeClient.kt`：鉴权头、请求日志、错误归一化、重试入口；禁止打印令牌。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudTaskPoller.kt`：主动拉取云端任务，进入 `TaskOrchestrator.startNewTask`，忙碌时回传 `TASK_BUSY`。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudResultReporter.kt`：订阅 `TaskEvent`，回传已接受、执行中、完成、失败、取消、阻塞状态。
- `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudEventQueue.kt`：网络不可用时缓存有限条数状态事件，恢复后按幂等请求编号补报。

### 需要小改的既有文件

- `app/src/main/java/io/agents/pokeclaw/ClawApplication.kt`：初始化云节点适配层，但默认不启用、不自动外连。
- `app/src/main/java/io/agents/pokeclaw/ui/settings/SettingsActivity.kt` 与 `SettingsViewModel.kt`：增加“云端设备节点”开关、云端地址和配对状态入口；所有错误用户可见。
- `app/src/main/java/io/agents/pokeclaw/AppViewModel.kt`：为云任务入口提供受控桥接，不绕过现有任务锁。
- `app/src/main/java/io/agents/pokeclaw/TaskEvent.kt`：原则上不新增云端字段；若确需扩展，只添加通用状态，不引入云端接口字段。

### 明确不改或谨慎改

- 不在 `TaskOrchestrator.kt` 里直接写网络请求。
- 不新增另一套任务状态机。
- 不改现有本地模型、云模型和技能执行策略。
- 不在未确认后端契约前硬编码接口路径、字段或数据库表名。

## 十七、本轮验证记录

```bash
pwd
# /mnt/e/code/PokeClaw

git rev-parse --show-toplevel
# /mnt/e/code/PokeClaw

git branch --show-current
# main

git status --short
# M QA_CHECKLIST.md
# ?? docs/product/

curl -s "http://127.0.0.1:3101/api/companies/bfc57cd0-e725-42e2-b221-400eaca22123/issues?assigneeAgentId=ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0" \
  -H "Authorization: Bearer $PAPERCLIP_API_KEY"
# 已确认自己名下未完成任务 12 条，最高优先级任务为 CMP-1835。

./gradlew testDebugUnitTest assembleDebug
# 阻塞：当前环境未配置 Android SDK，缺少 ANDROID_HOME 或 local.properties 中的 sdk.dir；构建未进入 Kotlin/Android 编译阶段。
```

结论：方案文档和端到端验收清单已落地；代码实现阶段仍阻塞于两个前置条件：后端统一执行节点接口契约未确认、当前环境缺少 Android SDK 配置。

## 十八、CMP-1876 本轮复核记录

本轮在无直接问题编号注入的情况下，已先通过纸夹接口查询当前成员名下未完成问题，并选择优先级最高的 `CMP-1876` 执行复核。

实际执行命令：

```bash
pwd
# /mnt/e/code/PokeClaw

git rev-parse --show-toplevel
# /mnt/e/code/PokeClaw

git branch --show-current
# main

git remote -v
# origin=https://github.com/MrRobt/PokeClaw.git

curl -s "http://127.0.0.1:3101/api/companies/bfc57cd0-e725-42e2-b221-400eaca22123/issues?assigneeAgentId=ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0" \
  -H "Authorization: Bearer $PAPER...KEY"
# 已确认自己名下未完成任务 11 条，最高优先级任务为 CMP-1876。

command -v java
# /usr/bin/java

command -v javac
# /usr/bin/javac

printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"
# ANDROID_HOME=
# ANDROID_SDK_ROOT=

./gradlew testDebugUnitTest assembleDebug
# 失败：SDK location not found。需要配置 ANDROID_HOME 或在 local.properties 设置 sdk.dir。
```

本轮确认：

- 真实仓库路径匹配：`/mnt/e/code/PokeClaw`，不是 `zeroclaw`、`metaclaw` 等相近仓库。
- 当前分支为 `main`；本轮只做摸底文档和验收清单，不改核心执行链路。
- `README.md` 产品方向、路线图和平台约束已复核：PokeClaw 必须保持手机常驻端侧执行底座，不能变成电脑控制或云手机框架。
- 设备注册、心跳、任务轮询、执行状态、结果回传、错误上报、本地离线缓存的最小闭环已写入本文档和 `docs/product/pokeclaw-device-node-integration.md`。
- `QA_CHECKLIST.md` 已有 `Z1` 到 `Z7` 端云设备节点验收项；本轮新增阻塞记录，说明当前构建阻塞在 Android SDK 配置。

待后端契约确认后，下一步应按独立 `cloudnode` 适配层方式实现第一阶段注册与心跳脚手架，默认关闭，不把网络请求塞进 `TaskOrchestrator`。
