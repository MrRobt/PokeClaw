# PokeClaw 设备节点注册与云端报错回传方案

日期：2026-05-15
负责人：安卓小龙
关联问题：CMP-2001、CMP-1876、CMP-1835、CMP-1991、CMP-1986、CMP-1940
仓库：`/mnt/e/code/PokeClaw`
分支：`main`

## 一、实际检查结论

本轮已确认当前仓库是真实 PokeClaw 安卓端仓库，不是相近名称仓库。

已检查的关键文件：

- `README.md`：确认产品方向是手机常驻、端侧优先、通用安卓移动智能体底座，不应退化成电脑控制或云手机框架。
- `CLAUDE.md`：确认变更前必须遵守架构优先、日志可追踪、端到端验收优先。
- `app/src/main/java/io/agents/pokeclaw/TaskOrchestrator.kt`：现有任务入口、状态回调、完成/失败/阻塞路径已经集中在任务编排器。
- `app/src/main/java/io/agents/pokeclaw/TaskEvent.kt`：已有结构化任务事件，可作为端云状态映射来源。
- `app/src/main/java/io/agents/pokeclaw/agent/AgentConfig.kt`：现有模型配置与系统提示不适合直接塞入设备节点配置，应单独建端云配置模型。
- `app/src/main/java/io/agents/pokeclaw/support/DebugReportManager.kt`：已有调试报告与日志收集能力，可复用其脱敏思想，但不能直接上传完整调试包。
- `app/src/main/java/io/agents/pokeclaw/server/ConfigServer.kt`、`ConfigServerManager.kt`：已有本机配置服务，适合参考，但设备节点对接应走主动外连 dyq 云端，不暴露手机局域网服务给云端反连。
- `app/build.gradle.kts`：已有 `okhttp`、`retrofit`、`gson` 依赖，后续实现端云客户端不需要新增网络库。
- `QA_CHECKLIST.md`：已有外部自动化与回调验收框架，可扩展为设备节点端云验收项。

## 二、最小闭环目标

PokeClaw 作为端侧执行体，向 dyq 云端登记为设备节点，并围绕简单手机控制任务形成最小闭环：

1. 设备注册：端侧生成稳定设备标识，携带能力、版本、构建指纹、运行模式向云端注册。
2. 心跳上报：定时上报在线状态、能力状态、当前任务状态、错误摘要。
3. 任务拉取：端侧主动轮询或长轮询获取云端下发的简单任务。
4. 本地执行：复用现有 `TaskOrchestrator` 和 `TaskEvent`，不绕过现有安全、权限、前台服务和可访问性检查。
5. 状态回传：把已接受、执行中、完成、失败、取消、阻塞等状态回传给云端。
6. 错误队列：网络不可用或云端不可用时，本地缓存有限条数的状态/错误事件，恢复后重试。

## 三、边界与架构原则

### 必须坚持

- 端侧优先执行，复杂调度与策略归 dyq 云端 Claw 中枢。
- PokeClaw 不新增一套任务执行引擎，只做端云适配层。
- 端云任务必须进入现有 `TaskOrchestrator`，从而继承现有日志、权限、前台服务、完成/失败语义。
- 所有错误必须用户可见或至少可从日志追踪，不能静默吞掉。
- 网络鉴权、设备令牌和云端地址不得写死在代码里。
- 上传日志必须脱敏，禁止上传完整提示词、聊天内容、联系人、通知正文和密钥。

### 明确不做

- 不把 PokeClaw 改造成云手机或电脑控制框架。
- 不绕过安卓可访问性、通知权限和前台服务约束。
- 不引入新的大型架构或多套状态机。
- 不实现非官方危险控制接口。
- 不在本轮直接绑定 dyq 后端具体数据库表；后端契约以接口字段为准。

## 四、建议包结构

后续实现建议新增独立包：

```text
app/src/main/java/io/agents/pokeclaw/cloudnode/
  CloudNodeConfig.kt              # 云端地址、设备令牌、轮询间隔、开关
  DeviceIdentity.kt               # 稳定设备标识与构建信息
  DeviceCapabilitySnapshot.kt     # 能力快照：可访问性、通知、模型、前台服务
  CloudNodeModels.kt              # 注册、心跳、任务、结果、错误数据模型
  CloudNodeApi.kt                 # Retrofit 接口定义
  CloudNodeClient.kt              # 网络调用、鉴权头、错误归一化
  CloudTaskPoller.kt              # 任务拉取与调度
  CloudResultReporter.kt          # 状态/结果/错误上报
  CloudEventQueue.kt              # 本地有限队列与重试策略
```

接入点：

- `ClawApplication`：应用启动后初始化云节点配置，但默认不自动启用。
- 设置页：新增“云端设备节点”开关、云端地址、配对令牌入口。
- `TaskOrchestrator`：只新增事件监听桥接，不改变现有任务执行主流程。
- `TaskEvent`：保持现有语义；云端适配层负责映射，不反向污染核心事件模型。

## 五、接口字段草案

### 设备注册

请求：

```json
{
  "deviceId": "端侧稳定标识",
  "deviceName": "用户可识别名称",
  "appVersion": "0.6.12",
  "buildFingerprint": "构建指纹",
  "androidVersion": "系统版本",
  "manufacturer": "厂商",
  "model": "机型",
  "capabilities": ["task", "chat", "accessibility", "notification", "localModel", "cloudModel"],
  "timezone": "设备时区"
}
```

响应：

```json
{
  "nodeId": "云端设备节点编号",
  "accessToken": "短期访问令牌",
  "heartbeatIntervalSec": 30,
  "taskPollIntervalSec": 10
}
```

### 心跳

```json
{
  "nodeId": "云端设备节点编号",
  "status": "online",
  "batteryLevel": 73,
  "charging": false,
  "networkType": "wifi",
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
  "taskText": "打开设置并查看电量",
  "source": "dyq-claw",
  "priority": "normal",
  "timeoutSec": 300,
  "requireUserVisible": true,
  "createdAt": "云端创建时间"
}
```

### 状态与结果回传

```json
{
  "taskId": "云端任务编号",
  "requestId": "幂等请求编号",
  "status": "completed",
  "summary": "任务完成摘要",
  "errorCode": null,
  "errorMessage": null,
  "round": 3,
  "modelName": "本地或云端模型名",
  "eventTime": "端侧事件时间"
}
```

状态枚举映射：

| PokeClaw 事件 | 云端状态 | 说明 |
|---|---|---|
| 接收到任务 | `accepted` | 已入端侧执行队列 |
| `LoopStart` / `Progress` | `running` | 执行中，可带轮次或步骤 |
| `ToolAction` | `running` | 可上报工具展示名，不上传敏感参数 |
| `Completed` | `completed` | 任务完成 |
| `Failed` | `failed` | 执行失败 |
| `Cancelled` | `cancelled` | 用户或系统取消 |
| `Blocked` | `blocked` | 权限、系统弹窗或环境阻塞 |

## 六、离线缓存与重试策略

- 本地只缓存状态事件和错误摘要，不缓存完整屏幕文本、通知正文、聊天正文。
- 队列建议最多保留最近 100 条事件或最近 24 小时事件，先到期先丢弃。
- 每条事件包含 `requestId` 和本地递增序号，云端按幂等键去重。
- 网络失败重试采用退避策略：10 秒、30 秒、2 分钟、10 分钟。
- 如果云端返回鉴权失败，立即停止上报并提示用户重新配对。
- 如果任务执行中网络断开，端侧继续本地执行，完成后进入待上报队列。

## 七、错误码草案

| 错误码 | 场景 | 用户可见建议 |
|---|---|---|
| `CLOUD_NODE_DISABLED` | 用户未开启云端设备节点 | 设置页显示未启用 |
| `CLOUD_AUTH_MISSING` | 未配对或令牌缺失 | 引导重新配对 |
| `CLOUD_AUTH_REJECTED` | 云端拒绝令牌 | 引导重新配对 |
| `NETWORK_UNAVAILABLE` | 当前无网络 | 提示等待联网后重试 |
| `TASK_BUSY` | 已有任务运行 | 云端显示端侧忙碌 |
| `ACCESSIBILITY_NOT_READY` | 可访问性服务不可用 | 引导打开可访问性服务 |
| `NOTIFICATION_NOT_READY` | 通知权限不可用 | 引导打开通知权限 |
| `SYSTEM_DIALOG_BLOCKED` | 系统弹窗阻塞 | 上报阻塞并保留截图本地调试 |
| `TASK_EXECUTION_FAILED` | 通用任务失败 | 返回脱敏错误摘要 |

## 八、最小实现顺序

### 第一阶段：只做注册与心跳脚手架

1. 新增 `cloudnode` 数据模型与 Retrofit 接口。
2. 设置页新增开关与云端地址/令牌配置，默认关闭。
3. 实现设备标识、能力快照、注册请求、心跳请求。
4. 只上报端侧状态，不拉取任务。
5. 端到端验收：本地模拟 dyq 接口，确认注册与心跳字段正确、鉴权失败可见。

### 第二阶段：任务拉取与本地执行桥接

1. 实现轮询拉取任务。
2. 任务进入现有 `TaskOrchestrator.startNewTask`。
3. `TaskEvent` 映射为云端状态上报。
4. 忙碌时拒绝新任务并上报 `TASK_BUSY`。
5. 端到端验收：云端下发“查询电量”等确定性任务，端侧执行并回传结果。

### 第三阶段：错误队列与离线重试

1. 本地事件队列持久化。
2. 网络断开期间缓存，恢复后重试。
3. 鉴权失败停止重试并提示用户。
4. 端到端验收：断网执行任务、恢复网络后结果能补报。

## 九、端到端验收设计

已在 `QA_CHECKLIST.md` 增加“Z 端云设备节点验收”草案，覆盖：

- 首次配对注册。
- 鉴权失败用户可见。
- 心跳能力快照。
- 云端任务下发到本地执行。
- 离线结果缓存与恢复补报。
- 忙碌态拒绝并回传。
- 敏感信息脱敏。

## 十、本轮实际执行命令与结果

本轮不是只做口头确认，已在 `/mnt/e/code/PokeClaw` 执行以下检查：

```bash
pwd
# /mnt/e/code/PokeClaw

git branch --show-current
# main

git status --short
#  M QA_CHECKLIST.md
# ?? docs/product/

./gradlew testDebugUnitTest assembleDebug
# 阻塞：当前环境未配置 Android SDK，缺少 ANDROID_HOME 或 local.properties 中的 sdk.dir；构建未进入 Kotlin/Android 编译阶段。

command -v java
# /usr/bin/java

command -v javac
# /usr/bin/javac

printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"
# ANDROID_HOME=
# ANDROID_SDK_ROOT=
```

同时已读取并核对：

- `README.md` 的产品方向、路线图、平台约束。
- `app/build.gradle.kts` 的网络依赖，确认已有 `okhttp`、`retrofit`、`gson`，后续第一阶段无需新增网络库。
- `TaskOrchestrator.kt` 的任务锁、执行入口、忙碌拒绝与事件回调。
- `TaskEvent.kt` 的完成、失败、取消、阻塞、进度、工具动作事件。
- `DebugReportManager.kt` 的调试摘要与能力快照来源。
- `automation/ExternalAutomationEntrypoint.kt` 的外部任务入口与忙碌拒绝模式。
- `QA_CHECKLIST.md` 的现有端到端验收结构，并新增 Z 组端云设备节点验收草案。

## 十一、本轮阻塞

- dyq 后端统一执行节点接口尚未在本仓库内提供可直接联调的接口文档或稳定地址。
- 当前执行环境已有 `java`/`javac`，但未配置 Android SDK：`ANDROID_HOME`、`ANDROID_SDK_ROOT` 均为空，且仓库没有 `local.properties` 的 `sdk.dir`，因此 `./gradlew testDebugUnitTest assembleDebug` 未能解析安卓依赖。
- 当前只完成端侧设计与验收清单，不直接改核心执行代码，避免在契约未定时破坏分层。
- 需要后端同学确认注册、心跳、任务拉取、结果回传四类接口路径、鉴权方式和字段命名。

## 十二、下一步建议

1. 后端先产出接口契约或临时模拟服务。
2. 安卓端按第一阶段新增 `cloudnode` 脚手架，默认关闭。
3. 质检用本地模拟服务跑 Z1 到 Z4。
4. 再进入任务拉取和结果回传实现，不要一次性大改任务编排器。
