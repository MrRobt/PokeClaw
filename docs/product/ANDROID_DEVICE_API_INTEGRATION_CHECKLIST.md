# PokeClaw 安卓端侧设备 API 联调清单

> 任务编号：CMP-137  
> 文档路径：`docs/product/ANDROID_DEVICE_API_INTEGRATION_CHECKLIST.md`  
> 对齐后端契约：`/mnt/e/code/dyq/api-contracts/device.openapi.yaml`

---

## 一、当前状态摘要

### 1.1 已完成实现

| 模块 | 文件路径 | 状态 |
|:-----|:---------|:-----|
| Retrofit API 接口 | `cloud/api/CloudDeviceApi.kt` | 已实现 5 个核心接口 |
| DTO 数据模型 | `cloud/model/CloudModels.kt` | 已对齐 OpenAPI 契约 |
| API 工厂与拦截器 | `cloud/api/CloudDeviceApiFactory.kt` | 已实现 Token 自动注入 |
| Token 安全存储 | `cloud/auth/CloudDeviceTokenStore.kt` | Android Keystore AES-GCM 加密 |
| 云端客户端 | `cloud/DeviceCloudClient.kt` | RetrofitDeviceCloudClient 实现 |
| 心跳管理器 | `cloud/CloudHeartbeatManager.kt` | WorkManager 周期性心跳 |
| 离线队列 | `cloud/CloudEventQueue.kt` | 本地缓存 + 指数退避重试 |
| 编排器 | `cloud/CloudNodeOrchestrator.kt` | 注册/心跳/任务拉取/结果上报闭环 |
| Application 集成 | `ClawApplication.kt` | 初始化并启动云端节点 |

### 1.2 API 接口覆盖

| 后端接口 | 前端方法 | 认证方式 |
|:---------|:---------|:---------|
| `POST /api/claw-device/register` | `register()` | 无（获取 Token） |
| `POST /api/claw-device/heartbeat` | `heartbeat()` | Bearer Token |
| `GET /api/claw-device/devices/{deviceId}/pending-tasks` | `getPendingTasks()` | Bearer Token |
| `POST /api/claw-device/tasks/{taskUuid}/result` | `submitTaskResult()` | Bearer Token |
| `POST /api/claw-device/token/refresh` | `refreshDeviceToken()` | 无（使用 Refresh Token） |

---

## 二、联调检查项

### 2.1 基础环境检查

- [ ] 后端服务 `http://192.168.250.3:8080` 可访问
- [ ] 后端 `/api/claw-device/register` 接口已部署
- [ ] 后端数据库表 `claw_device` 已创建
- [ ] Android 设备网络连通（WiFi 或蜂窝数据）

### 2.2 Android 端检查

- [ ] KV 存储中 `cloud_enabled` 设为 `true`
- [ ] KV 存储中 `cloud_base_url` 配置正确
- [ ] 应用权限：网络、电池状态、后台运行已授权

### 2.3 接口字段对齐检查

#### DeviceRegisterRequest（设备注册）

```kotlin
val request = DeviceRegisterRequest(
    deviceId = "pokeclaw-sn-a1b2c3",      // 必填：设备唯一标识
    deviceName = "Xiaomi 14",              // 可选：设备名称
    deviceModel = "Xiaomi 14",             // 可选：设备型号
    androidVersion = "14",                 // 可选：Android 版本
    appVersion = "0.7.0",                  // 可选：App 版本
    publicKey = null                       // 可选：设备公钥
)
```

#### DeviceHeartbeatRequest（心跳）

```kotlin
val request = DeviceHeartbeatRequest(
    batteryLevel = 85,                   // 电量百分比 (0-100)
    isCharging = true,                   // 是否充电中
    networkType = "wifi"                 // wifi / cellular / offline
)
```

#### TaskResultRequest（任务结果上报）

```kotlin
val request = TaskResultRequest(
    status = TaskResultRequest.Status.SUCCESS,  // SUCCESS/FAILED/RUNNING/CANCELLED
    result = "执行成功",
    errorMessage = null,
    executionTimeMs = 1500,
    toolCalls = "[...]",                   // JSON 字符串
    evidenceUrls = "[...]",                // JSON 字符串
    modelUsed = "local",
    errorCategory = null,                  // NETWORK/PERMISSION/TIMEOUT/UNKNOWN
    errorCode = null,                    // E1001/E2003...
    errorDetail = null,                    // 技术错误详情
    recoverable = null,                  // 是否可重试
    suggestedAction = null,              // 建议用户操作
    screenshotBase64 = null,             // Base64 截图（失败时）
    logSnippet = null                    // 相关日志片段
)
```

---

## 三、测试用例

### 3.1 设备注册测试

```bash
# 使用 curl 测试后端注册接口（模拟 Android 端）
curl -X POST http://192.168.250.3:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "test-device-001",
    "deviceName": "测试设备",
    "deviceModel": "Xiaomi 14",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'
```

**预期响应：**
```json
{
  "code": 0,
  "data": {
    "deviceToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 604800
  }
}
```

### 3.2 心跳测试

```bash
# 使用获取到的 deviceToken 测试心跳
curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Authorization: Bearer <deviceToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "batteryLevel": 85,
    "isCharging": true,
    "networkType": "wifi"
  }'
```

**预期响应：**
```json
{
  "code": 0,
  "data": {
    "pendingTaskCount": 0,
    "skillVersion": 1,
    "serverTime": 1715952000000
  }
}
```

### 3.3 ADB 测试（Android 端）

```bash
# 安装 APK 并启动
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.agents.pokeclaw/.ui.activity.LaunchActivity

# 查看日志（过滤云端相关）
adb logcat -s "PokeClaw/ClawApplication" "PokeClaw/CloudNodeOrchestrator" "PokeClaw/DeviceCloudClient" "PokeClaw/CloudHeartbeatManager"
```

---

## 四、待解决问题

### 4.1 后端编译状态

> 根据 CMP-137 描述，本任务被阻塞在等待后端编译通过。

需确认：
1. dyq 后端是否已完成编译？
2. `/api/claw-device/*` 接口是否已部署到 `192.168.250.3:8080`？
3. 数据库 Flyway 迁移是否已执行？

### 4.2 Android 端待验证项

- [ ] 真机/模拟器上注册流程能否走通
- [ ] Token 刷新机制是否正确工作
- [ ] 离线队列在网络恢复后能否补报
- [ ] 心跳间隔和失败重试策略是否符合预期
- [ ] 任务拉取后能否正确分发给执行器

---

## 五、文件改动清单

### 5.1 已存在文件（无需改动）

```
app/src/main/java/io/agents/pokeclaw/cloud/
├── api/
│   ├── CloudDeviceApi.kt           # Retrofit 接口契约
│   └── CloudDeviceApiFactory.kt    # 客户端工厂
├── auth/
│   └── CloudDeviceTokenStore.kt    # Keystore Token 存储
├── model/
│   └── CloudModels.kt              # DTO 数据模型
├── DeviceCloudClient.kt            # 云端客户端实现
├── CloudHeartbeatManager.kt        # WorkManager 心跳
├── CloudEventQueue.kt              # 离线事件队列
├── CloudNodeOrchestrator.kt        # 执行节点编排器
└── CloudTaskExecutor.kt            # 任务执行器接口

app/src/main/java/io/agents/pokeclaw/ClawApplication.kt  # 云端节点初始化
```

### 5.2 测试文件

```
app/src/test/java/io/agents/pokeclaw/cloud/
├── api/CloudDeviceApiContractTest.kt
└── cloudnode/CloudExecutorNodeContractTest.kt
```

---

## 六、后续联调步骤

1. **后端准备**：确认 dyq 后端编译通过，接口部署完成
2. **网络连通**：Android 设备能访问 `192.168.250.3:8080`
3. **注册测试**：运行 Android 应用，观察注册流程日志
4. **心跳验证**：观察周期性心跳上报
5. **任务下发**：从管理端下发测试任务，验证端到端闭环
6. **离线测试**：断网后执行任务，恢复网络后验证补报

---

## 七、风险与边界

| 风险 | 缓解措施 |
|:-----|:---------|
| Token 泄露 | 使用 Android Keystore AES-GCM 加密存储 |
| 离线数据丢失 | 本地队列持久化到 SharedPreferences |
| 心跳过于频繁 | WorkManager 最小周期 15 分钟，如需更短需自定义 Handler |
| 后端接口变更 | 单元测试 `CloudDeviceApiContractTest` 会检测路径变化 |

---

## 八、验收标准

- [ ] Android 端可成功注册设备并获取 Token
- [ ] 心跳正常上报，返回 pendingTaskCount 正确
- [ ] 任务结果可成功上报到云端
- [ ] 离线时结果缓存，网络恢复后补报成功
- [ ] Token 过期前自动刷新

---

**文档生成时间**：2026-05-18  
**生成人**：安卓小龙（Android Agent）  
**关联 Issue**：CMP-137
