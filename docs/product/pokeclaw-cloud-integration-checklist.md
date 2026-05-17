# PokeClaw 端云任务下发与结果回传联调清单

## 问题编号
CMP-1940: 自动派活：PokeClaw端云任务下发与结果回传联调清单

## 审计日期
2026-05-17

## 负责人
安卓小龙

---

## 一、前提假设验证

### 1.1 仓库路径确认
| 检查项 | 结果 |
|--------|------|
| PokeClaw 安卓仓库 | `/mnt/e/code/PokeClaw` ✅ |
| 当前分支 | `main` ✅ |
| dyq 后端仓库 | `/mnt/e/code/dyq` ✅ |

### 1.2 端侧实现状态
| 模块 | 路径 | 状态 |
|------|------|------|
| Cloud API 接口定义 | `cloud/api/CloudDeviceApi.kt` | ✅ 已实现 |
| Cloud DTO 数据模型 | `cloud/model/CloudModels.kt` | ✅ 已实现 |
| DeviceCloudClient | `cloud/DeviceCloudClient.kt` | ✅ 已实现 |
| CloudNodeOrchestrator | `cloud/CloudNodeOrchestrator.kt` | ✅ 已实现 |
| CloudTaskExecutor | `cloud/CloudTaskExecutor.kt` | ✅ 已实现 |
| CloudEventQueue | `cloud/CloudEventQueue.kt` | ✅ 已实现 |
| CloudNode 桥接 | `cloudnode/` 包 | ✅ 已实现 |

### 1.3 后端契约文件
| 文件 | 路径 | 状态 |
|------|------|------|
| device.openapi.yaml | `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` | ✅ 存在 |
| executor-node.openapi.yaml | `/mnt/e/code/dyq/api-contracts/executor-node.openapi.yaml` | ✅ 存在 |

---

## 二、接口字段映射清单

### 2.1 设备注册接口

| 端侧字段 (Kotlin) | 后端字段 (OpenAPI) | 类型 | 说明 |
|-------------------|-------------------|------|------|
| `DeviceRegisterRequest.deviceId` | `deviceId` | String | 设备唯一标识 |
| `DeviceRegisterRequest.deviceName` | `deviceName` | String? | 设备名称 |
| `DeviceRegisterRequest.deviceModel` | `deviceModel` | String? | 设备型号 |
| `DeviceRegisterRequest.androidVersion` | `androidVersion` | String? | Android 版本 |
| `DeviceRegisterRequest.appVersion` | `appVersion` | String? | App 版本 |
| `DeviceRegisterRequest.publicKey` | `publicKey` | String? | 设备公钥 |

**响应映射：**

| 端侧字段 | 后端字段 | 说明 |
|----------|----------|------|
| `DeviceRegisterResponse.deviceToken` | `deviceToken` | JWT 短期令牌 (7天) |
| `DeviceRegisterResponse.refreshToken` | `refreshToken` | JWT 刷新令牌 (30天) |
| `DeviceRegisterResponse.expiresIn` | `expiresIn` | 过期时间（秒） |

**接口路径：** `POST /api/claw-device/register`

---

### 2.2 心跳接口

| 端侧字段 (Kotlin) | 后端字段 (OpenAPI) | 类型 | 说明 |
|-------------------|-------------------|------|------|
| `DeviceHeartbeatRequest.batteryLevel` | `batteryLevel` | Int? | 电量百分比 (0-100) |
| `DeviceHeartbeatRequest.isCharging` | `isCharging` | Boolean? | 是否充电中 |
| `DeviceHeartbeatRequest.networkType` | `networkType` | String? | wifi/cellular/offline |

**响应映射：**

| 端侧字段 | 后端字段 | 说明 |
|----------|----------|------|
| `DeviceHeartbeatResponse.pendingTaskCount` | `pendingTaskCount` | 待处理任务数量 |
| `DeviceHeartbeatResponse.skillVersion` | `skillVersion` | 当前技能版本号 |
| `DeviceHeartbeatResponse.serverTime` | `serverTime` | 服务器时间戳（毫秒） |

**接口路径：** `POST /api/claw-device/heartbeat`
**认证方式：** `Authorization: Bearer {deviceToken}`

---

### 2.3 任务拉取接口

**请求参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `deviceId` (Path) | String | 设备编号 |
| `Authorization` (Header) | String | Bearer Token |

**响应：** `List<PendingTaskItem>`

| 端侧字段 | 后端字段 | 类型 | 说明 |
|----------|----------|------|------|
| `taskUuid` | `taskUuid` | String | 任务 UUID |
| `command` | `command` | String | 执行命令 |
| `mode` | `mode` | String? | 执行模式 |
| `createdAt` | `createdAt` | Long | 创建时间戳（毫秒） |
| `priority` | `priority` | String? | 优先级 |

**接口路径：** `GET /api/claw-device/devices/{deviceId}/pending-tasks`

---

### 2.4 任务结果上报接口

| 端侧字段 (Kotlin) | 后端字段 (OpenAPI) | 类型 | 说明 |
|-------------------|-------------------|------|------|
| `status` | `status` | String | SUCCESS/FAILED/RUNNING/CANCELLED |
| `result` | `result` | String? | 执行结果文本 |
| `errorMessage` | `errorMessage` | String? | 错误信息 |
| `executionTimeMs` | `executionTimeMs` | Long? | 执行耗时（毫秒） |
| `toolCalls` | `toolCalls` | String? | 工具调用记录（JSON） |
| `evidenceUrls` | `evidenceUrls` | String? | 证据 URL 列表（JSON） |
| `modelUsed` | `modelUsed` | String? | 使用的模型 |
| `errorCategory` | - | String? | 错误大类（端侧扩展） |
| `errorCode` | - | String? | 错误码（端侧扩展） |
| `errorDetail` | - | String? | 详细错误信息（端侧扩展） |
| `recoverable` | - | Boolean? | 是否可重试（端侧扩展） |
| `suggestedAction` | - | String? | 建议用户操作（端侧扩展） |
| `screenshotBase64` | - | String? | 失败时截图（端侧扩展） |
| `logSnippet` | - | String? | 相关日志片段（端侧扩展） |

**接口路径：** `POST /api/claw-device/tasks/{taskUuid}/result`
**认证方式：** `Authorization: Bearer {deviceToken}`

---

### 2.5 Token 刷新接口

| 端侧字段 | 后端字段 | 说明 |
|----------|----------|------|
| `TokenRefreshRequest.refreshToken` | `refreshToken` | 刷新令牌 |

**响应：**

| 端侧字段 | 后端字段 | 说明 |
|----------|----------|------|
| `TokenRefreshResponse.deviceToken` | `deviceToken` | 新的 JWT 设备令牌 |
| `TokenRefreshResponse.expiresIn` | `expiresIn` | 新的过期时间（秒） |

**接口路径：** `POST /api/claw-device/token/refresh`

---

## 三、端云数据流时序

```
┌─────────────────────────────────────────────────────────────────────┐
│                           端云联调数据流                               │
└─────────────────────────────────────────────────────────────────────┘

[阶段1: 设备注册]
┌──────────────┐     POST /api/claw-device/register      ┌──────────────┐
│ PokeClaw端侧  │ ──────────────────────────────────────> │ DYQ云端      │
│              │                                         │              │
│ deviceId     │     响应: deviceToken, refreshToken      │ 创建设备记录  │
│ deviceName   │ <────────────────────────────────────── │ 返回JWT令牌  │
│ ...          │                                         │              │
└──────────────┘                                         └──────────────┘

[阶段2: 心跳循环 + 任务拉取]
┌──────────────┐     POST /api/claw-device/heartbeat     ┌──────────────┐
│              │ ──────────────────────────────────────> │              │
│              │                                         │              │
│              │     响应: pendingTaskCount               │              │
│              │ <────────────────────────────────────── │              │
│              │                                         │              │
│              │  if pendingTaskCount > 0:               │              │
│              │     GET /pending-tasks                  │              │
│              │ ──────────────────────────────────────> │              │
│              │                                         │              │
│              │     响应: List<PendingTaskItem>          │              │
│              │ <────────────────────────────────────── │              │
└──────────────┘                                         └──────────────┘

[阶段3: 任务执行 + 结果上报]
┌──────────────┐                                        ┌──────────────┐
│              │     POST /tasks/{taskUuid}/result       │              │
│              │ ──────────────────────────────────────> │              │
│              │                                         │              │
│ 执行云端任务  │     status: SUCCESS/FAILED/...          │ 更新任务状态  │
│ (本地Agent)  │     result/errorMessage/...             │              │
│              │ <────────────────────────────────────── │              │
└──────────────┘                                         └──────────────┘

[阶段4: Token刷新]
┌──────────────┐     POST /token/refresh                 ┌──────────────┐
│              │ ──────────────────────────────────────> │              │
│              │                                         │              │
│ refreshToken │     响应: 新的 deviceToken             │ 验证并颁发   │
│              │ <────────────────────────────────────── │ 新令牌       │
└──────────────┘                                         └──────────────┘
```

---

## 四、端侧关键类职责

### 4.1 CloudNodeOrchestrator（云端编排器）

| 方法 | 职责 |
|------|------|
| `start()` | 启动编排器：读取/生成 deviceId → 注册 → 启动心跳循环 |
| `stop()` | 停止编排器，取消心跳协程 |
| `heartbeatLoop()` | 30秒间隔心跳：刷新Token → 补报离线队列 → 发送心跳 → 拉取任务 |
| `executeCloudTask()` | 执行单个云端任务，调用 CloudTaskExecutor，上报结果 |
| `onPendingTasksAvailable()` | 收到待处理任务时的回调入口 |

### 4.2 DeviceCloudClient（云端客户端）

| 方法 | 职责 |
|------|------|
| `register()` | 设备注册，成功后保存 Token |
| `sendHeartbeat()` | 发送心跳，返回是否成功 |
| `getPendingTasks()` | 拉取待处理任务列表 |
| `submitTaskResult()` | 提交任务结果，失败时缓存到离线队列 |
| `refreshTokenIfNeeded()` | 过期前自动刷新 Token |
| `flushOfflineQueue()` | 补报离线队列中的事件 |

### 4.3 CloudEventQueue（离线事件队列）

| 方法 | 职责 |
|------|------|
| `enqueue()` | 将结果事件加入离线队列（上限100条） |
| `peekDue()` | 获取到期的待上报事件 |
| `markSucceeded()` | 标记事件上报成功，从队列移除 |
| `markFailed()` | 标记事件上报失败，增加重试计数和延迟 |
| `clear()` | 清空队列 |

**重试策略：** 指数退避（1s, 2s, 4s, 8s, 16s, 32s）

### 4.4 CloudTaskExecutor（任务执行器）

| 实现类 | 职责 |
|--------|------|
| `LocalAgentTaskExecutor` | 基于本地 AgentService 执行任务 |
| `ExternalAutomationTaskExecutor` | 基于 ExternalAutomationEntrypoint 注入任务 |

### 4.5 CloudTaskExecutorBridge（执行桥接）

| 方法 | 职责 |
|------|------|
| `execute()` | 将云端任务映射为本地 Skill 并执行 |
| `canExecuteLocally()` | 判断任务是否可在本地确定性执行 |

---

## 五、联调步骤

### 步骤1：后端服务启动验证

```bash
# 检查后端服务是否可达
curl -s http://192.168.250.3:8080/api/claw-device/register \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "pokeclaw-test-001",
    "deviceName": "联调测试机",
    "deviceModel": "TestModel",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'
```

**预期：** 返回 JSON 包含 `deviceToken` 和 `refreshToken`

### 步骤2：端侧配置

```kotlin
// ClawApplication.kt 或设置页
CloudNodeConfig(
    baseUrl = "http://192.168.250.3:8080",
    heartbeatIntervalMs = 30_000L,
    autoRegisterOnStart = true
)
```

### 步骤3：端到端验证

| 步骤 | 操作 | 验证点 |
|------|------|--------|
| 3.1 | 启动 PokeClaw 应用 | logcat 查看 `CloudNodeOrchestrator.start` |
| 3.2 | 等待注册完成 | logcat 查看 `register: 注册成功` |
| 3.3 | 等待心跳循环 | logcat 每30秒查看 `sendHeartbeat` |
| 3.4 | 后端下发测试任务 | 调用后端接口创建设备任务 |
| 3.5 | 验证任务拉取 | logcat 查看 `getPendingTasks` |
| 3.6 | 验证任务执行 | logcat 查看 `executeCloudTask` |
| 3.7 | 验证结果上报 | logcat 查看 `submitTaskResult: 结果上报成功` |

### 步骤4：离线场景验证

| 步骤 | 操作 | 验证点 |
|------|------|--------|
| 4.1 | 断开网络后执行任务 | logcat 查看 `enqueue: 云端结果进入离线队列` |
| 4.2 | 恢复网络 | logcat 查看 `flushOfflineQueue` 补报 |
| 4.3 | 验证后端收到补报结果 | 后端数据库查询任务状态 |

### 步骤5：Token刷新验证

| 步骤 | 操作 | 验证点 |
|------|------|--------|
| 5.1 | 等待 Token 接近过期 | logcat 查看 `refreshTokenIfNeeded` |
| 5.2 | 验证自动刷新 | logcat 查看 `设备令牌已刷新` |
| 5.3 | 验证后续请求使用新 Token | 心跳/任务接口正常返回 |

---

## 六、当前阻塞与风险

### 6.1 当前阻塞

| 阻塞项 | 状态 | 说明 |
|--------|------|------|
| 后端服务不可达 | ⛔ 阻塞 | `http://192.168.250.3:8080` 当前连接失败 |
| 需要后端确认接口路径 | ⚠️ 待定 | 确认是否使用 `/api/claw-device/*` 或新统一执行节点接口 |

### 6.2 风险边界确认

| 风险项 | 现状 | 措施 |
|--------|------|------|
| 分层破坏 | 否 | cloud 包独立，不依赖 UI 层 |
| 跨模块事务 | 否 | 无数据库操作，纯网络通信 |
| 敏感信息泄露 | 否 | Keystore 加密 + 脱敏（2048字符截断） |
| 非官方接口 | 否 | 仅使用标准 Android API |
| 后台常驻 | 合规 | 使用协程循环，非 WorkManager |

---

## 七、产出文件清单

| 文件路径 | 说明 |
|----------|------|
| `docs/product/pokeclaw-cloud-integration-checklist.md` | 本联调清单文档 |
| `app/src/main/java/io/agents/pokeclaw/cloud/` | 云端模块完整实现 |
| `app/src/main/java/io/agents/pokeclaw/cloudnode/` | 云端节点桥接实现 |
| `docs/product/pokeclaw-device-api-integration.md` | 设备API联调准备文档 |
| `docs/product/pokeclaw-device-node-integration.md` | 设备节点集成方案文档 |

---

## 八、待验证清单

- [ ] 后端服务 `http://192.168.250.3:8080` 可正常访问
- [ ] 设备注册接口返回正确的 JWT Token
- [ ] 心跳接口正常响应，返回 `pendingTaskCount`
- [ ] 任务拉取接口返回待处理任务列表
- [ ] 任务执行完成后结果上报成功
- [ ] 离线时结果缓存到本地队列
- [ ] 网络恢复后离线结果自动补报
- [ ] Token 过期前自动刷新成功
- [ ] 鉴权失败时引导用户重新配对
- [ ] 敏感信息脱敏后上传（长度截断）

---

**文档生成时间**: 2026-05-17  
**负责人**: 安卓小龙  
**问题编号**: CMP-1940
