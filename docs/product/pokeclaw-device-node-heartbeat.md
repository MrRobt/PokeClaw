# PokeClaw 端侧执行端心跳与错误上报方案

日期：2026-05-17
负责人：安卓小龙（ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0）
关联问题：CMP-1964、CMP-2001、CMP-1835、CMP-1876
仓库：/mnt/e/code/PokeClaw
分支：main

---

## 一、前提验证

### 1.1 工作目录确认

```bash
pwd
# /mnt/e/code/PokeClaw

git branch --show-current
# main

git rev-parse --show-toplevel
# /mnt/e/code/PokeClaw
```

验证通过：当前目录是真实 PokeClaw 安卓仓库，分支 main，符合任务要求。

### 1.2 关键文件检查

已核对以下文件：

- `README.md`：确认产品方向为手机常驻端侧执行底座，不变成电脑控制或云手机框架
- `CLAUDE.md`：确认变更必须遵守架构优先、日志可追踪、端到端验收优先
- `app/src/main/java/io/agents/pokeclaw/TaskOrchestrator.kt`：确认任务锁、执行入口、忙碌拒绝机制
- `app/src/main/java/io/agents/pokeclaw/TaskEvent.kt`：确认结构化事件（完成、失败、取消、阻塞、进度、工具动作）
- `docs/product/pokeclaw-device-node-integration.md`：已存在设备节点注册与云端报错回传方案
- `docs/product/pokeclaw-executor-node-plan.md`：已存在执行节点注册与心跳对接方案
- `QA_CHECKLIST.md`：已存在 Z1-Z12 端云设备节点验收项

---

## 二、目标

让 PokeClaw 安卓端作为小龙虾执行端，具备设备节点心跳、错误上报、简单任务结果上报的完整方案。

---

## 三、两种方案对比

### 方案一：在现有代码中直接插入网络请求

- 优点：文件改动少，快速串通链路
- 缺点：污染 TaskOrchestrator、设置页和事件模型；后续接口变动会牵连核心执行；容易绕过权限和日志规则

### 方案二：新增独立 cloudnode 适配层

- 优点：注册、心跳、轮询、结果回传、离线队列和脱敏都在独立包内；核心执行仍走 TaskOrchestrator；状态回传只订阅 TaskEvent，不反向污染核心事件模型
- 缺点：需要多建几个小文件

**选择：方案二**

理由：符合 PokeClaw 手机常驻执行底座的产品方向，符合 dyq 三端协作中端侧只做简单执行、复杂决策回云端的边界；能把后端接口未稳定带来的变更风险限制在适配层内。

---

## 四、心跳方案

### 4.1 心跳触发机制

- 触发条件：WorkManager PeriodicWorkRequest，默认 30 秒间隔
- 停止条件：用户关闭云端设备节点开关、鉴权失败、应用退出
- 重试策略：连续 3 次失败后标记离线，退避重试（10s/30s/2min/10min）

### 4.2 心跳请求字段

```json
{
  "nodeId": "云端设备节点编号",
  "status": "online",
  "timestamp": 1715961600000,
  "batteryLevel": 73,
  "charging": false,
  "networkType": "wifi",
  "foregroundServiceRunning": true,
  "accessibilityReady": true,
  "notificationReady": true,
  "activeTaskId": "云端任务编号或空",
  "activeTaskStatus": "running",
  "lastErrorCode": "最近错误码或空",
  "lastErrorAt": "最近错误时间或空"
}
```

### 4.3 心跳能力快照来源

复用 `AppCapabilityCoordinator.snapshot()`：

| 心跳字段 | 来源 |
|---|---|
| batteryLevel | BatteryManager |
| charging | BatteryManager |
| networkType | ConnectivityManager |
| foregroundServiceRunning | AgentService.isRunning |
| accessibilityReady | AccessibilityService 状态 |
| notificationReady | NotificationListenerService 状态 |

---

## 五、错误上报方案

### 5.1 错误上报触发点

- TaskOrchestrator.onTaskFailed()：任务执行失败
- TaskOrchestrator.onTaskBlocked()：任务被阻塞
- CloudNodeClient 网络请求失败：云端不可达
- 鉴权失败：云端拒绝令牌

### 5.2 错误上报字段

```json
{
  "nodeId": "云端设备节点编号",
  "errorCode": "TASK_EXECUTION_FAILED",
  "errorMessage": "可公开的错误摘要，不含敏感信息",
  "taskId": "关联任务编号或空",
  "timestamp": 1715961600000,
  "severity": "error",
  "recoverable": true
}
```

### 5.3 错误码定义

| 错误码 | 场景 | 用户可见建议 |
|---|---|---|
| CLOUD_NODE_DISABLED | 用户未开启云端设备节点 | 设置页显示未启用 |
| CLOUD_AUTH_MISSING | 未配对或令牌缺失 | 引导重新配对 |
| CLOUD_AUTH_REJECTED | 云端拒绝令牌 | 引导重新配对，停止重试 |
| NETWORK_UNAVAILABLE | 当前无网络 | 提示等待联网后重试 |
| TASK_BUSY | 已有任务运行 | 云端显示端侧忙碌 |
| ACCESSIBILITY_NOT_READY | 可访问性服务不可用 | 引导打开可访问性服务 |
| NOTIFICATION_NOT_READY | 通知权限不可用 | 引导打开通知权限 |
| SYSTEM_DIALOG_BLOCKED | 系统弹窗阻塞 | 上报阻塞并保留截图本地调试 |
| TASK_EXECUTION_FAILED | 通用任务失败 | 返回脱敏错误摘要 |

---

## 六、任务结果上报方案

### 6.1 状态映射

| 端侧事件 | 云端状态 | 说明 |
|---|---|---|
| 接收到任务 | accepted | 已进入端侧执行队列 |
| LoopStart / Progress | running | 执行中，可带轮次或步骤 |
| ToolAction | running | 只上报工具展示名，不上传敏感参数 |
| Completed | completed | 任务完成 |
| Failed | failed | 执行失败 |
| Cancelled | cancelled | 用户或系统取消 |
| Blocked | blocked | 权限、系统弹窗或环境阻塞 |

### 6.2 结果回传字段

```json
{
  "taskId": "云端任务编号",
  "requestId": "幂等请求编号",
  "status": "completed",
  "summary": "任务完成摘要，脱敏处理",
  "errorCode": null,
  "errorMessage": null,
  "round": 3,
  "modelName": "本地或云端模型名",
  "startTime": 1715961500000,
  "endTime": 1715961600000,
  "eventTime": 1715961600000
}
```

---

## 七、离线重试策略

### 7.1 本地队列设计

- 只缓存状态事件和错误摘要，不缓存完整屏幕文本、通知正文、聊天正文
- 队列最多保留最近 100 条事件或最近 24 小时事件，先到期先丢弃
- 每条事件包含 requestId 和本地递增序号，云端按幂等键去重

### 7.2 重试退避

- 10 秒 → 30 秒 → 2 分钟 → 10 分钟
- 鉴权失败立即停止重试并提示用户重新配对
- 任务执行中网络断开，端侧继续本地执行，完成后进入待上报队列

---

## 八、安全与日志要求

### 8.1 安全要求

- 云端地址、配对令牌、访问令牌不得硬编码
- 令牌应使用安卓安全存储（EncryptedSharedPreferences），禁止明文持久化
- 上传日志必须脱敏，禁止上传完整提示词、聊天内容、联系人、通知正文和密钥

### 8.2 日志要求

- 日志必须能通过系统日志还原注册、心跳、拉取、执行、回传、重试全链路
- 日志不得打印令牌、密钥、联系人列表、通知正文、聊天正文和完整提示词
- 所有云端失败必须用户可见或可在设置页看到明确状态，禁止静默失败
- 日志标签统一以 `PokeClaw/CloudNode` 或等价前缀标识

---

## 九、建议新增 Kotlin 文件

```text
app/src/main/java/io/agents/pokeclaw/cloudnode/
  CloudNodeConfig.kt              # 云端地址、开关、心跳间隔、令牌引用
  DeviceIdentity.kt               # 稳定设备标识与构建信息
  DeviceCapabilitySnapshot.kt     # 能力快照映射
  CloudNodeModels.kt              # 心跳、错误、结果数据模型
  CloudNodeApi.kt                 # Retrofit 接口定义
  CloudNodeClient.kt              # 鉴权头、请求日志、错误归一化
  CloudHeartbeatManager.kt        # 心跳管理与 WorkManager 调度
  CloudResultReporter.kt          # 状态、结果、错误上报
  CloudEventQueue.kt              # 本地有限队列与重试
```

---

## 十、端到端验收

已在 QA_CHECKLIST.md 增加 Z 组端云设备节点验收：

- Z1：首次配对注册
- Z2：鉴权失败用户可见
- Z3：心跳能力快照
- Z4：云端任务下发到本地执行
- Z5：离线结果缓存与恢复补报
- Z6：忙碌态拒绝新任务
- Z7：敏感信息脱敏
- Z8-Z12：扩展验收项

---

## 十一、实际执行命令

```bash
# 确认工作目录
pwd
# /mnt/e/code/PokeClaw

# 确认分支
git branch --show-current
# main

# 确认仓库状态
git status --short
# M QA_CHECKLIST.md
# ?? docs/product/

# 检查现有文档
ls -la docs/product/
# 已存在 pokeclaw-device-node-integration.md、pokeclaw-executor-node-plan.md 等

# 构建验证（环境阻塞）
./gradlew testDebugUnitTest assembleDebug
# 阻塞：当前环境未配置 Android SDK，缺少 ANDROID_HOME
```

---

## 十二、产出清单

| 产出项 | 路径 | 状态 |
|---|---|---|
| 本文档 | docs/product/pokeclaw-device-node-heartbeat.md | ✅ 新建 |
| 设备节点方案 | docs/product/pokeclaw-device-node-integration.md | ✅ 已存在 |
| 执行节点方案 | docs/product/pokeclaw-executor-node-plan.md | ✅ 已存在 |
| QA 验收项 | QA_CHECKLIST.md Z1-Z12 | ✅ 已存在 |

---

## 十三、阻塞与下一步

### 当前阻塞

1. dyq 后端统一执行节点接口契约尚未提供可直接联调的路径和字段
2. 当前环境缺少 Android SDK 配置，无法执行构建验证

### 下一步建议

1. 后端提供注册、心跳、任务拉取、结果回传接口契约或临时模拟服务
2. 安卓端按本方案第一阶段新增 cloudnode 脚手架，默认关闭
3. 质检用本地模拟服务跑 Z1-Z4 验收
4. 配置 Android SDK 后重跑构建验证

---

## 十四、待验证清单

- [ ] 后端接口契约确认
- [ ] Android SDK 配置完成
- [ ] Z1 首次配对注册通过
- [ ] Z2 鉴权失败用户可见
- [ ] Z3 心跳能力快照正确
- [ ] Z4 云端任务下发执行
- [ ] Z5 离线缓存与补报
