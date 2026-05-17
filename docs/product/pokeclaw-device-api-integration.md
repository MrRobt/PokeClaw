# PokeClaw 设备API联调准备清单

## 问题编号
CMP-137: 【Android】PokeClaw端侧对接 — 设备API联调准备

## 审计日期
2026-05-16

---

## 一、现有实现审计结果

### 1. 模块结构
```
app/src/main/java/io/agents/pokeclaw/cloud/
├── api/
│   ├── CloudDeviceApi.kt          # Retrofit 接口定义
│   └── CloudDeviceApiFactory.kt   # OkHttp + Retrofit 构建工厂
├── auth/
│   └── CloudDeviceTokenStore.kt   # Android Keystore JWT 存储
├── model/
│   └── CloudModels.kt             # 请求/响应 DTO
├── DeviceCloudClient.kt           # 客户端接口 + Retrofit 实现
├── CloudEventQueue.kt             # 离线结果缓存队列
├── CloudNodeOrchestrator.kt       # 云端任务编排器
└── CloudTaskExecutor.kt           # 云端任务执行器
```

### 2. 已实现功能
- ✅ 设备注册 POST /api/claw-device/register
- ✅ 心跳发送 POST /api/claw-device/heartbeat
- ✅ 任务拉取 GET /api/claw-device/devices/{deviceId}/pending-tasks
- ✅ 结果上报 POST /api/claw-device/tasks/{taskUuid}/result
- ✅ Token 刷新 POST /api/claw-device/refresh-token
- ✅ Android Keystore 加密存储 (AES-GCM)
- ✅ 离线队列 (SharedPreferences + Gson)
- ✅ 指数退避重试策略
- ✅ 结果脱敏 (长度截断)

### 3. 依赖配置
```kotlin
// app/build.gradle.kts
implementation(libs.okhttp)
implementation(libs.okhttp.logging)
implementation(libs.retrofit)
implementation(libs.retrofit.gson)
```

---

## 二、阻塞问题

### 1. 后端服务状态【当前阻塞】
- **路径**: `http://192.168.250.3:8080`
- **检查时间**: 2026-05-16 20:00
- **状态**: ⛔ 后端服务不可达 (curl 返回空，连接失败)
- **影响**: 无法进行真实联调，只能做端侧静态验证
- **下一步**: 等待后端服务恢复或确认新的联调地址

### 2. OpenAPI 契约文件状态【已对齐】
- **路径**: `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` ✅ 存在
- **端侧DTO对齐**: `app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` 已对齐契约字段
- **状态**: ✅ 字段已对齐，可启动联调

### 3. 集成点状态【已实现】
- **ClawApplication.onCreate()** ✅ 已添加 `initCloudNode()`，默认关闭，可通过KV配置启用
- **CloudNodeOrchestrator** ✅ 已实现，支持注册/心跳/任务拉取/结果上报/离线队列
- **TaskOrchestrator** 待后续接入云端任务执行
- **设置页** 待添加云端管理开关UI

---

## 三、联调检查清单

### 3.1 设备注册
| 检查项 | 端侧实现 | 联调状态 |
|--------|---------|---------|
| 首次启动生成 deviceId (UUID) | ✅ CloudNodeOrchestrator.loadOrGenerateDeviceId() | ⛔ 等待后端 |
| POST /api/claw-device/register | ✅ RetrofitDeviceCloudClient.register() | ⛔ 等待后端 |
| deviceToken/refreshToken 保存 | ✅ AndroidKeystoreCloudDeviceTokenStore | ⛔ 等待后端 |
| 注册失败静默重试 (指数退避) | ✅ 已实现 | ⛔ 等待后端 |

### 3.2 心跳保活
| 检查项 | 端侧实现 | 联调状态 |
|--------|---------|---------|
| 协程心跳循环 30s 间隔 | ✅ CloudNodeOrchestrator.heartbeatLoop() | ⛔ 等待后端 |
| POST /api/claw-device/heartbeat | ✅ RetrofitDeviceCloudClient.sendHeartbeat() | ⛔ 等待后端 |
| 携带 batteryLevel/networkType | ✅ DeviceHeartbeatRequest 构建 | ⛔ 等待后端 |
| 连续3次失败标记离线 | ✅ consecutiveHeartbeatFailures 计数 | ⛔ 等待后端 |

### 3.3 任务拉取
| 检查项 | 端侧实现 | 联调状态 |
|--------|---------|---------|
| 心跳响应 pendingTaskCount>0 触发 | ✅ DeviceHeartbeatResponse.pendingTaskCount | ⛔ 等待后端 |
| GET /api/claw-device/devices/{deviceId}/pending-tasks | ✅ CloudDeviceApi.getPendingTasks() | ⛔ 等待后端 |
| 任务列表非空时调度执行 | ✅ onPendingTasksAvailable() | ⛔ 等待后端 |

### 3.4 结果上报
| 检查项 | 端侧实现 | 联调状态 |
|--------|---------|---------|
| 任务完成后调用 submitTaskResult | ✅ executeCloudTask() 内部调用 | ⛔ 等待后端 |
| 成功时移除离线队列 | ✅ CloudEventQueue.markSucceeded() | ⛔ 等待后端 |
| 失败时缓存到离线队列 | ✅ CloudEventQueue.enqueue() | ⛔ 等待后端 |
| 网络恢复后补报 | ✅ flushOfflineQueue() | ⛔ 等待后端 |

### 3.5 Token 管理
| 检查项 | 端侧实现 | 联调状态 |
|--------|---------|---------|
| 过期前自动触发刷新 | ✅ refreshTokenIfNeeded() | ⛔ 等待后端 |
| POST /api/claw-device/token/refresh | ✅ CloudDeviceApi.refreshDeviceToken() | ⛔ 等待后端 |
| 新 token 更新到 Keystore | ✅ tokenStore.updateDeviceToken() | ⛔ 等待后端 |
| 刷新失败清空 token 重新注册 | ✅ tokenStore.clear() | ⛔ 等待后端 |

---

## 四、字段对齐验证

### DTO 对比

| OpenAPI 字段 | Kotlin DTO | 状态 |
|--------------|-----------|------|
| DeviceRegisterRequest.deviceId | CloudModels.DeviceRegisterRequest.deviceId | ✅ 对齐 |
| DeviceRegisterRequest.deviceName | CloudModels.DeviceRegisterRequest.deviceName | ✅ 对齐 |
| DeviceRegisterResponse.deviceToken | CloudModels.DeviceRegisterResponse.deviceToken | ✅ 对齐 |
| DeviceRegisterResponse.refreshToken | CloudModels.DeviceRegisterResponse.refreshToken | ✅ 对齐 |
| DeviceHeartbeatRequest.batteryLevel | CloudModels.DeviceHeartbeatRequest.batteryLevel | ✅ 对齐 |
| DeviceHeartbeatRequest.isCharging | CloudModels.DeviceHeartbeatRequest.isCharging | ✅ 对齐 |
| DeviceHeartbeatRequest.networkType | CloudModels.DeviceHeartbeatRequest.networkType | ✅ 对齐 |
| DeviceHeartbeatResponse.pendingTaskCount | CloudModels.DeviceHeartbeatResponse.pendingTaskCount | ✅ 对齐 |
| PendingTaskItem.taskUuid | CloudModels.PendingTaskItem.taskUuid | ✅ 对齐 |
| PendingTaskItem.command | CloudModels.PendingTaskItem.command | ✅ 对齐 |
| TaskResultRequest.status | CloudModels.TaskResultRequest.status | ✅ 对齐 |
| TaskResultRequest.result | CloudModels.TaskResultRequest.result | ✅ 对齐 |

---

## 五、风险边界确认

| 风险项 | 现状 | 措施 |
|--------|------|------|
| 分层破坏 | 否 | cloud 包独立，不依赖 UI 层 |
| 跨模块 JOIN | 否 | 无数据库操作 |
| 敏感信息泄露 | 否 | Keystore 加密 + 脱敏 |
| 非官方接口 | 否 | 仅使用标准 Android API |
| 后台常驻 | 合规 | 使用协程循环，非 WorkManager |

---

## 六、产出文件清单

| 文件路径 | 状态 | 说明 |
|----------|------|------|
| docs/product/pokeclaw-device-api-integration.md | 本文件 | 联调准备清单 (已更新) |
| app/src/main/java/io/agents/pokeclaw/cloud/ | 已实现 | 云端模块完整实现 |
| /mnt/e/code/dyq/api-contracts/device.openapi.yaml | 存在 | 后端契约文件 |

---

## 七、联调启动条件

以下条件全部满足后方可启动真实联调：
1. ✅ 端侧实现审计完成
2. ✅ 后端 OpenAPI 文件提供
3. ⛔ 后端服务部署完成（当前不可达）
4. ⛔ 测试设备可用
5. ⛔ 联调环境配置完成

---

## 八、当前阻塞与下一步

### 阻塞项
- 后端服务 `http://192.168.250.3:8080` 当前不可达
- curl 测试注册接口无响应

### 下一步
1. 等待后端服务恢复
2. 确认联调地址（可能不是 192.168.250.3:8080）
3. 获取测试用设备凭证
4. 执行完整端到端联调

---

**审计完成时间**: 2026-05-16  
**审计人**: 安卓小龙  
**问题编号**: CMP-137
