# PokeClaw 端云任务下发与结果回传联调清单

> 关联 Issue: CMP-1940  
> 生成时间: 2026-05-16  
> 端侧仓库: /mnt/e/code/PokeClaw (main 分支)  
> 后端仓库: /mnt/e/code/dyq (hermes 分支)

---

## 一、接口契约对齐清单

### 1.1 设备端 API 端点对照

| 功能 | 端点 | 方法 | Android 实现 | 状态 |
|------|------|------|-------------|------|
| 设备注册 | `/api/claw-device/register` | POST | `CloudDeviceApi.register()` | ✅ 已实现 |
| 心跳保活 | `/api/claw-device/heartbeat` | POST | `CloudDeviceApi.heartbeat()` | ✅ 已实现 |
| 任务拉取 | `/api/claw-device/devices/{deviceId}/pending-tasks` | GET | `CloudDeviceApi.getPendingTasks()` | ✅ 已实现 |
| 结果上报 | `/api/claw-device/tasks/{taskUuid}/result` | POST | `CloudDeviceApi.submitTaskResult()` | ✅ 已实现 |
| Token 刷新 | `/api/claw-device/token/refresh` | POST | `CloudDeviceApi.refreshDeviceToken()` | ✅ 已实现 |

### 1.2 数据模型字段映射

| OpenAPI 字段 | Kotlin DTO | 类型 | 说明 |
|-------------|-----------|------|------|
| `deviceId` | `DeviceRegisterRequest.deviceId` | String | UUID 格式 pokeclaw-xxx |
| `deviceToken` | `DeviceRegisterResponse.deviceToken` | String | JWT Bearer Token |
| `refreshToken` | `DeviceRegisterResponse.refreshToken` | String | 30天有效期 |
| `batteryLevel` | `DeviceHeartbeatRequest.batteryLevel` | Int? | 0-100 |
| `pendingTaskCount` | `DeviceHeartbeatResponse.pendingTaskCount` | Int | 心跳响应触发任务拉取 |
| `taskUuid` | `PendingTaskItem.taskUuid` | String | 任务唯一标识 |
| `command` | `PendingTaskItem.command` | String | 执行指令 |
| `status` | `TaskResultRequest.status` | String | SUCCESS/FAILED/RUNNING/CANCELLED |

---

## 二、端侧核心实现文件清单

| 文件路径 | 职责 | 关键方法 |
|---------|------|---------|
| `cloud/api/CloudDeviceApi.kt` | Retrofit API 接口定义 | register, heartbeat, getPendingTasks, submitTaskResult, refreshDeviceToken |
| `cloud/DeviceCloudClient.kt` | 云端客户端接口与实现 | register, sendHeartbeat, getPendingTasks, submitTaskResult, flushOfflineQueue |
| `cloud/CloudNodeOrchestrator.kt` | 端云编排器（核心） | start(), heartbeatLoop(), executeCloudTask() |
| `cloud/CloudTaskExecutor.kt` | 任务执行器接口 | execute(), getModelName() |
| `cloud/CloudEventQueue.kt` | 离线结果队列 | enqueue(), peekDue(), markSucceeded(), markFailed() |
| `cloud/model/CloudModels.kt` | 数据模型定义 | DeviceRegisterRequest, DeviceHeartbeatRequest, PendingTaskItem, TaskResultRequest 等 |
| `cloud/auth/CloudDeviceTokenStore.kt` | Token 存储 | saveTokens(), snapshot() |

---

## 三、执行链路流程图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CloudNodeOrchestrator                        │
│                                                                      │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    │
│   │   IDLE   │───▶│REGISTERING│───▶│ RUNNING  │───▶│EXECUTING │    │
│   └──────────┘    └──────────┘    └────┬─────┘    └────┬─────┘    │
│                                        │               │            │
│                                        ▼               ▼            │
│                               ┌──────────────┐   ┌──────────────┐   │
│                               │ heartbeatLoop│   │executeCloud  │   │
│                               │              │   │Task          │   │
│                               │ 30s interval │   └──────────────┘   │
│                               └──────┬───────┘                     │
│                                      │                              │
│                    ┌─────────────────┼─────────────────┐            │
│                    │                 │                 │            │
│                    ▼                 ▼                 ▼            │
│            ┌───────────┐    ┌──────────────┐   ┌─────────────┐      │
│            │  刷新令牌  │    │   补报离线队列  │   │  发送心跳    │      │
│            └───────────┘    └──────────────┘   └─────────────┘      │
│                                                    │                  │
│                                                    ▼                  │
│                                            ┌──────────────┐         │
│                                            │ pendingTask  │         │
│                                            │ Count > 0 ?  │         │
│                                            └──────┬───────┘         │
│                                                   │ YES              │
│                                                   ▼                  │
│                                           ┌──────────────┐          │
│                                           │ getPending   │          │
│                                           │ Tasks        │          │
│                                           └──────┬───────┘          │
└──────────────────────────────────────────────────┼───────────────────┘
                                                   │
                         ┌─────────────────────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │   CloudTaskExecutor  │
              │   .execute(task)     │
              └──────────┬───────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
     ┌────────────────┐    ┌────────────────────┐
     │ LocalAgentTask │    │ ExternalAutomation │
     │ Executor       │    │ TaskExecutor       │
     └────────────────┘    └────────────────────┘
              │                     │
              └──────────┬──────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │ CloudTaskExecution   │
              │ Result               │
              └──────────┬───────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │ submitTaskResult     │
              │ (在线/离线缓存)       │
              └──────────────────────┘
```

---

## 四、联调验证步骤

### 4.1 设备注册联调

```bash
# 1. 检查后端设备注册接口
 curl -s -X POST http://192.168.250.3:8080/api/claw-device/register \
   -H "Content-Type: application/json" \
   -d '{
     "deviceId": "pokeclaw-test-001",
     "deviceName": "测试设备",
     "deviceModel": "Xiaomi 14",
     "androidVersion": "14",
     "appVersion": "0.7.0"
   }' | jq .

# 预期响应：
# {
#   "code": 0,
#   "data": {
#     "deviceToken": "eyJ...",
#     "refreshToken": "eyJ...",
#     "expiresIn": 604800
#   }
# }
```

**Android 验证点：**
- 首次启动生成 deviceId 并持久化到 KVUtils
- 注册成功保存 deviceToken 到 CloudDeviceTokenStore
- 注册失败指数退避重试，不阻塞本地功能

### 4.2 心跳联调

```bash
# 2. 检查心跳接口（需有效 deviceToken）
curl -s -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "batteryLevel": 85,
    "isCharging": true,
    "networkType": "wifi"
  }' | jq .

# 预期响应：
# {
#   "code": 0,
#   "data": {
#     "pendingTaskCount": 0,
#     "skillVersion": 3,
#     "serverTime": ...
#   }
# }
```

**Android 验证点：**
- 30s 间隔发送心跳
- 连续 3 次失败标记离线
- pendingTaskCount > 0 时触发任务拉取

### 4.3 任务拉取联调

```bash
# 3. 先在后端创建任务（管理端接口）
 curl -s -X POST http://192.168.250.3:8080/claw/device/execute \
   -H "Content-Type: application/json" \
   -d '{
     "deviceId": "pokeclaw-test-001",
     "command": "打开微信并发送消息给张三",
     "mode": "TASK"
   }' | jq .

# 4. 检查待处理任务（设备端接口）
curl -s "http://192.168.250.3:8080/api/claw-device/devices/pokeclaw-test-001/pending-tasks" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" | jq .
```

**Android 验证点：**
- heartbeat 响应 pendingTaskCount > 0 后自动拉取
- 拉取到任务后触发 CloudTaskExecutor.execute()

### 4.4 结果上报联调

```bash
# 5. 检查结果上报接口
curl -s -X POST "http://192.168.250.3:8080/api/claw-device/tasks/${TASK_UUID}/result" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SUCCESS",
    "result": "任务执行成功",
    "executionTimeMs": 5000,
    "modelUsed": "local"
  }' | jq .
```

**Android 验证点：**
- 任务执行完成后上报结果
- 无令牌时缓存到 CloudEventQueue
- 网络恢复后 flushOfflineQueue() 补报

---

## 五、关键代码联调检查点

### 5.1 Token 管理

```kotlin
// CloudDeviceTokenStore.kt
// 验证点：
// - deviceToken 存储在 Android Keystore
// - 过期前自动刷新（refreshTokenIfNeeded）
// - 刷新失败清空令牌，触发重新注册
```

### 5.2 离线队列

```kotlin
// CloudEventQueue.kt
// 验证点：
// - 最大缓存 100 条（DEFAULT_MAX_SIZE）
// - 单条数据脱敏（sanitized）
// - 重试间隔指数退避（retryDelayMillis）
```

### 5.3 状态机

```kotlin
// CloudNodeOrchestrator.State
// IDLE -> REGISTERING -> RUNNING -> EXECUTING -> RUNNING
// 验证点：
// - 注册失败进入 ERROR 状态
// - 心跳连续失败进入 ERROR 状态
// - 执行中状态阻止重复拉取任务
```

---

## 六、端到端联调命令（ADB）

### 6.1 启动应用并查看日志

```bash
# 启动 PokeClaw
adb shell am start -n io.agents.pokeclaw/.MainActivity

# 查看云端相关日志
adb logcat --pid=$(adb shell pidof io.agents.pokeclaw) | grep "PokeClaw/Cloud"

# 或过滤关键 TAG
adb logcat -s "PokeClaw/CloudNodeOrchestrator:D" "PokeClaw/DeviceCloudClient:D"
```

### 6.2 模拟任务下发

```bash
# 通过广播注入测试任务（如 ExternalAutomationEntrypoint 支持）
adb shell am broadcast -a io.agents.pokeclaw.TEST_TASK \
  --es "command" "截图并上报" \
  --es "taskUuid" "test-001"
```

### 6.3 验证离线缓存

```bash
# 关闭网络（飞行模式）
adb shell cmd connectivity set-airplane-mode enabled

# 执行任务（结果应进入离线队列）
# ...

# 恢复网络
adb shell cmd connectivity set-airplane-mode disabled

# 验证队列补报（查看日志）
adb logcat -s "PokeClaw/CloudEventQueue:I"
```

---

## 七、待联调问题与风险

| 问题 | 状态 | 备注 |
|------|------|------|
| LocalAgentTaskExecutor 未接入 AgentService | 🔴 阻塞 | CloudTaskExecutor.kt 第 53-64 行为 TODO |
| ExternalAutomationTaskExecutor 未接入 | 🔴 阻塞 | 第 90-96 行为 TODO |
| dyq 后端设备 API 实现状态 | 🟡 待确认 | 需检查后端 /api/claw-device/* 实现 |
| Token 刷新与重新注册边界 | 🟡 待验证 | refreshToken 失效后的重新注册流程 |
| 离线队列持久化存储上限 | 🟢 已处理 | SharedPreferences 存储，最大 100 条 |

---

## 八、下一步开发任务

1. **接入 LocalAgentTaskExecutor**
   - 文件: `cloud/CloudTaskExecutor.kt`
   - 目标: 通过 AgentServiceFactory 创建 AgentService 实例
   - 将云端 command 包装为 Agent prompt 执行

2. **接入 ExternalAutomationTaskExecutor**
   - 文件: `cloud/CloudTaskExecutor.kt`
   - 目标: 通过广播方式注入 ExternalAutomationEntrypoint

3. **后端 API 联调**
   - 确认 dyq 后端 /api/claw-device/* 接口已部署
   - 验证 JWT Token 签发与验证流程

4. **集成测试**
   - 编写 CloudNodeOrchestrator 集成测试
   - 验证完整链路：注册 → 心跳 → 任务 → 执行 → 上报

---

## 九、产出清单

| 产出项 | 路径 | 状态 |
|--------|------|------|
| 联调清单文档 | `.planning/pm/current/pokeclaw-cloud-integration-checklist.md` | ✅ 已完成 |
| 接口契约对齐 | `cloud/api/CloudDeviceApi.kt` ↔ `dyq/api-contracts/device.openapi.yaml` | ✅ 已对齐 |
| 端侧核心实现 | `cloud/` 包下 7 个 Kotlin 文件 | ✅ 已实现 |
| 单元测试 | `cloud/*Test.kt` | 🟡 待补充 |
| 集成测试 | 暂无 | 🔴 待开发 |

---

**文档维护者**: 安卓小龙  
**最后更新**: 2026-05-16
