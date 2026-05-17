# PokeClaw 端云任务下发与结果回传联调清单

> 文档编号: PC-DYQ-001  
> 关联: CMP-1940  
> 日期: 2026-05-16  
> 更新日期: 2026-05-17  
> 作者: 安卓小龙

---

## 一、端云对接架构概览

### 1.1 角色关系

```
┌─────────────────────────────────────────────────────────────────┐
│                        DYQ 云端 (Claw小龙虾中枢)                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐  │
│  │ ClawDeviceService │  │ ExecutorNodeService │  │ 任务调度/下发      │  │
│  │ (设备管理服务)     │  │ (统一执行节点服务)    │  │                  │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬─────────┘  │
│           │                    │                     │              │
│  ┌────────▼────────────────────▼─────────────────────▼─────────┐   │
│  │              AppClawDeviceController                           │   │
│  │   /api/claw-device/register    (设备注册)                    │   │
│  │   /api/claw-device/heartbeat   (心跳上报)                    │   │
│  │   /api/claw-device/{id}/pending-tasks (任务拉取)              │   │
│  │   /api/claw-device/tasks/{uuid}/result (结果回传)           │   │
│  │   /api/claw-device/token/refresh (令牌刷新)                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTPS
                              │
┌─────────────────────────────▼─────────────────────────────────────┐
│                      PokeClaw 端侧 (Android)                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              CloudNodeOrchestrator                            │  │
│  │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │  │
│  │   │ DeviceCloudClient │ │ CloudEventQueue │ │ CloudTaskExecutor │  │
│  │   │ (云端通信)      │  │ (离线缓存)      │  │ (任务执行)      │  │
│  │   └──────────────┘  └──────────────┘  └──────────────┘     │  │
│  │              │                    │            │            │  │
│  │   ┌──────────▼──────────┐  ┌─────▼──────┐  ┌──▼─────────┐  │  │
│  │   │ CloudDeviceApi      │  │ 离线队列     │  │ AgentService │  │  │
│  │   │ (Retrofit)          │  │ (持久化)     │  │ (本地执行)   │  │  │
│  │   └─────────────────────┘  └────────────┘  └────────────┘  │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 数据流向

```
设备注册 → 心跳循环 → 任务拉取 → 本地执行 → 结果上报
   │           │           │           │           │
   │           │           │           │           │
   ▼           ▼           ▼           ▼           ▼
POST        POST         GET        执行       POST
register   heartbeat  pending    AgentLoop   result
           (30s轮询)   tasks                  (含离线补报)
```

---

## 二、接口字段映射表

### 2.1 设备注册

| PokeClaw Android (CloudModels.kt) | DYQ Backend (AppClawDeviceController) | 说明 |
|:---------------------------------|:--------------------------------------|:-----|
| `DeviceRegisterRequest.deviceId` | `ClawDeviceRegisterReqVO.deviceId` | 设备唯一标识 |
| `deviceName` | `deviceName` | 展示名称 |
| `deviceModel` | `deviceModel` | 设备型号 |
| `androidVersion` | `androidVersion` | 系统版本 |
| `appVersion` | `appVersion` | App版本 |
| `publicKey` | `publicKey` | 可选公钥 |
| ← Response → | ← Response → | |
| `deviceToken` | `deviceToken` | JWT短期令牌 |
| `refreshToken` | `refreshToken` | 刷新令牌 |
| `expiresIn` | `expiresIn` | 有效期秒数 |

### 2.2 心跳上报

| PokeClaw Android | DYQ Backend | 说明 |
|:-----------------|:------------|:-----|
| `DeviceHeartbeatRequest.batteryLevel` | `batteryLevel` | 电量百分比 |
| `isCharging` | `isCharging` | 是否充电中 |
| `networkType` | `networkType` | wifi/cellular/offline |
| ← Response → | ← Response → | |
| `pendingTaskCount` | `pendingTaskCount` | 待处理任务数 |
| `skillVersion` | `skillVersion` | 技能版本 |
| `serverTime` | `serverTime` | 服务器时间戳 |

### 2.3 任务拉取

| PokeClaw Android | DYQ Backend | 说明 |
|:-----------------|:------------|:-----|
| `PendingTaskItem.taskUuid` | `taskUuid` | 任务唯一编号 |
| `command` | `command` | 执行指令内容 |
| `mode` | `mode` | 执行模式 |
| `createdAt` | `createTime` | 创建时间戳 |
| `priority` | `priority` | 优先级 |

### 2.4 结果回传

| PokeClaw Android | DYQ Backend | 说明 |
|:-----------------|:------------|:-----|
| `TaskResultRequest.status` | `status` | SUCCESS/FAILED/RUNNING |
| `result` | `result` | 执行结果内容 |
| `errorMessage` | `errorMessage` | 错误信息 |
| `executionTimeMs` | `executionTimeMs` | 执行耗时毫秒 |
| `toolCalls` | `toolCalls` | 工具调用记录 |
| `evidenceUrls` | `evidenceUrls` | 证据URL |
| `modelUsed` | `modelUsed` | 使用模型 |

---

## 三、端侧核心代码清单

### 3.1 已存在文件 (PokeClaw main分支)

| 文件路径 | 职责 | 状态 |
|:---------|:-----|:-----|
| `cloud/DeviceCloudClient.kt` | 云端通信客户端 | ✅ 已实现 |
| `cloud/CloudNodeOrchestrator.kt` | 端云编排器(心跳/任务循环) | ✅ 已实现 |
| `cloud/CloudTaskExecutor.kt` | 任务执行器接口 | ✅ 已实现 |
| `cloud/CloudEventQueue.kt` | 离线事件队列 | ✅ 已实现 |
| `cloud/api/CloudDeviceApi.kt` | Retrofit API接口 | ✅ 已实现 |
| `cloud/model/CloudModels.kt` | 数据模型 | ✅ 已实现 |
| `cloud/auth/CloudDeviceTokenStore.kt` | JWT令牌安全存储 | ✅ 已实现 |
| `cloudnode/CloudExecutorNodeContract.kt` | 执行节点契约定义 | ✅ 已实现 |
| `cloudnode/CloudTaskSkillMapper.kt` | 云端指令到本地技能映射 | ✅ 已实现 |
| `cloudnode/CloudTaskExecutorBridge.kt` | 执行器桥接层 | ✅ 已实现 |

### 3.2 关键类图

```
┌────────────────────────┐
│ CloudNodeOrchestrator  │◄──── 生命周期管理 (start/stop)
├────────────────────────┤
│ - deviceId: String     │
│ - state: State         │◄──── IDLE/REGISTERING/RUNNING/EXECUTING/STOPPED/ERROR
│ - heartbeatJob: Job    │
├────────────────────────┤
│ + start()              │
│ + stop()               │
│ - heartbeatLoop()      │◄──── 30s间隔轮询
│ - executeCloudTask()   │◄──── 任务执行闭环
└──────────┬─────────────┘
           │
    ┌──────┴──────┬──────────────┐
    │             │              │
    ▼             ▼              ▼
┌────────┐  ┌──────────┐  ┌──────────────┐
│DeviceCloud│  │CloudEvent│  │CloudTaskExecutor│
│Client   │  │Queue     │  │               │
└────────┘  └──────────┘  └──────────────┘
    │             │              │
    ▼             ▼              ▼
CloudDeviceApi   持久化缓存    LocalAgentTaskExecutor
(Retrofit)                  ExternalAutomationTaskExecutor
```

---

## 四、联调步骤与验证命令

### 4.1 环境准备

```bash
# 1. 确认 PokeClaw 仓库
$ cd /mnt/e/code/PokeClaw && git branch --show-current
# 预期输出: main

# 2. 确认 DYQ 后端仓库
$ cd /mnt/e/code/dyq && git branch --show-current
# 预期输出: hermes

# 3. 检查后端 API 文件存在性
$ ls -la /mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/controller/app/device/AppClawDeviceController.java
# 预期: 文件存在
```

### 4.2 后端启动验证

```bash
# 1. DYQ 后端编译
$ cd /mnt/e/code/dyq
$ mvn clean install -DskipTests -pl dyq-module-claw/dyq-module-claw-biz -am

# 2. 启动服务 (需Docker环境)
$ docker-compose -p dyqhermes up -d

# 3. 验证 API 可达性
$ curl -s http://localhost:8080/api/claw-device/register \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test-device-001","deviceName":"测试设备"}'
# 预期: 返回 deviceToken/refreshToken/expiresIn
```

### 4.3 Android 端集成验证

```bash
# 1. 检查 cloud 目录完整
$ ls -la /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/
# 预期: api/ auth/ model/ DeviceCloudClient.kt CloudEventQueue.kt CloudNodeOrchestrator.kt CloudTaskExecutor.kt

# 2. 编译检查
$ cd /mnt/e/code/PokeClaw
$ ./gradlew :app:compileDebugKotlin --no-daemon
# 预期: BUILD SUCCESSFUL
```

### 4.4 E2E 联调测试

#### Test 1: 设备注册流程
```bash
# 预期测试步骤:
# 1. 首次启动 PokeClaw
# 2. CloudNodeOrchestrator.start() 被调用
# 3. 生成 deviceId (pokeclaw-${UUID})
# 4. 调用 POST /api/claw-device/register
# 5. 保存 deviceToken/refreshToken 到 Android Keystore

# ADB 验证命令:
$ adb logcat -s "PokeClaw/CloudNodeOrchestrator:D" | grep -E "register|deviceToken"
# 预期日志: "register: 注册成功", "saveTokens: device token saved"
```

#### Test 2: 心跳循环
```bash
# 预期测试步骤:
# 1. 注册成功后启动心跳协程
# 2. 每 30s 发送 POST /api/claw-device/heartbeat
# 3. 响应中 pendingTaskCount > 0 时触发任务拉取

# ADB 验证命令:
$ adb logcat -s "PokeClaw/CloudNodeOrchestrator:D" | grep -E "heartbeatLoop|getPendingTasks"
# 预期日志: "心跳循环，间隔=30000ms", "拉取到 X 个待处理任务"
```

#### Test 3: 任务执行与结果上报
```bash
# 预期测试步骤:
# 1. 云端下发任务 (通过管理后台)
# 2. 端侧下次心跳响应 pendingTaskCount>0
# 3. 调用 GET /api/claw-device/{id}/pending-tasks
# 4. 执行任务 (CloudTaskExecutor.execute)
# 5. 调用 POST /api/claw-device/tasks/{uuid}/result

# ADB 验证命令:
$ adb logcat -s "PokeClaw/CloudNodeOrchestrator:D" | grep -E "executeCloudTask|submitTaskResult"
# 预期日志: "开始执行 taskUuid=xxx", "结果上报成功"
```

#### Test 4: 离线缓存与补报
```bash
# 预期测试步骤:
# 1. 断开网络连接
# 2. 执行任务
# 3. 结果进入 CloudEventQueue 缓存
# 4. 恢复网络后心跳时补报

# ADB 验证命令:
$ adb logcat | grep -E "CloudEventQueue|flushOfflineQueue"
# 预期日志: "enqueue: 离线事件入队", "markSucceeded: 补报成功"
```

---

## 五、待修复/待联调问题清单

### 5.0 本次更新状态（2026-05-17）

| 检查项 | 状态 | 说明 |
|:-------|:-----|:-----|
| 联调清单文档字段修正 | ✅ 已完成 | `osVersion` → `androidVersion` 字段名对齐修正 |
| Android端编译检查 | ✅ 已通过 | `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL |
| dyq后端设备API编译 | ✅ 已通过 | `dyq-module-claw` 相关模块编译成功 |
| 字段映射验证 | ✅ 已完成 | 注册/心跳/结果上报字段与后端完全对齐 |
| 统一执行节点API迁移 | 🟡 待规划 | 当前 `/api/claw-device/*` 保持兼容，未来迁移到 `/api/claw/executor-nodes/*` |

### 5.1 已知问题

| 问题 | 位置 | 优先级 | 状态 |
|:-----|:-----|:-------|:-----|
| CloudTaskExecutor.execute 未接入 AgentService | `cloud/CloudTaskExecutor.kt:54-64` | P0 | 🔴 待实现 |
| DeviceCloudClient.create 调用点未添加 | 需 Application/Service 初始化 | P0 | 🔴 待添加 |
| CloudNodeOrchestrator 启动时机 | 需绑定 ForegroundService | P1 | 🟡 待确定 |
| baseUrl 配置来源 | 需从 BuildConfig 或配置中心读取 | P1 | 🟡 待配置 |

### 5.2 端云字段对齐检查

| Android 字段 | 后端字段 | 状态 | 备注 |
|:-------------|:---------|:-----|:-----|
| `DeviceRegisterRequest.deviceId` | `ClawDeviceRegisterReqVO.deviceId` | ✅ 对齐 | |
| `DeviceRegisterRequest.deviceName` | `ClawDeviceRegisterReqVO.deviceName` | ✅ 对齐 | |
| `DeviceRegisterRequest.deviceModel` | `ClawDeviceRegisterReqVO.deviceModel` | ✅ 对齐 | |
| `DeviceRegisterRequest.androidVersion` | `ClawDeviceRegisterReqVO.androidVersion` | ✅ 对齐 | |
| `DeviceRegisterRequest.appVersion` | `ClawDeviceRegisterReqVO.appVersion` | ✅ 对齐 | |
| `DeviceHeartbeatRequest.batteryLevel` | `AppClawDeviceHeartbeatReqVO.batteryLevel` | ✅ 对齐 | |
| `DeviceHeartbeatRequest.isCharging` | `AppClawDeviceHeartbeatReqVO.isCharging` | ✅ 对齐 | |
| `DeviceHeartbeatRequest.networkType` | `AppClawDeviceHeartbeatReqVO.networkType` | ✅ 对齐 | |

### 5.3 统一执行节点 API (未来演进)

> 注意：`/api/claw-device/*` 是兼容接口，未来会迁移到统一执行节点 API。
> 参见: `/mnt/e/code/dyq/api-contracts/executor-node.openapi.yaml`

| 当前接口 | 未来统一接口 | 说明 |
|:---------|:-------------|:-----|
| POST /api/claw-device/register | POST /api/claw/executor-nodes/register | 统一节点注册 |
| POST /api/claw-device/heartbeat | POST /api/claw/executor-nodes/{key}/heartbeat | 统一心跳 |
| GET /api/claw-device/{id}/pending-tasks | GET /api/claw/executor-nodes/{key}/commands/pending | 统一指令拉取 |
| POST /api/claw-device/tasks/{uuid}/result | POST /api/claw/executor-nodes/{key}/commands/{id}/result | 统一结果上报 |

---

## 六、产出文件清单

| 文件路径 | 类型 | 说明 |
|:---------|:-----|:-----|
| `/mnt/e/code/PokeClaw/docs/pokeclaw-dyq-integration-checklist.md` | 文档 | 本联调清单 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/` | 代码目录 | 端云对接核心代码 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloudnode/` | 代码目录 | 执行节点契约与桥接 |
| `/mnt/e/code/dyq/api-contracts/executor-node.openapi.yaml` | 契约 | 统一执行节点API规范 |

---

## 七、待验证清单 (QA)

- [ ] Test 1: 设备注册成功，deviceToken 保存到 Keystore
- [ ] Test 2: 心跳正常循环，30s间隔稳定发送
- [ ] Test 3: 云端下发任务后端侧正确接收
- [ ] Test 4: 任务执行完成后结果成功上报
- [ ] Test 5: 网络中断时结果缓存到离线队列
- [ ] Test 6: 网络恢复后离线队列自动补报
- [ ] Test 7: Token 过期前自动刷新
- [ ] Test 8: 连续心跳失败3次后标记离线状态

---

## 八、备注

1. **Token 安全**: deviceToken 和 refreshToken 使用 Android Keystore AES-GCM 加密存储，不落盘明文。
2. **离线优先**: 结果上报在 Token 失效或网络异常时自动进入离线队列，恢复后补报。
3. **状态机**: CloudNodeOrchestrator 使用显式状态机 (IDLE→REGISTERING→RUNNING→EXECUTING→STOPPED/ERROR)。
4. **向后兼容**: 当前 `/api/claw-device/*` 接口保持兼容，未来平滑迁移到统一执行节点 API。
