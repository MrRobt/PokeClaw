# PokeClaw 端侧设备 API 联调准备清单

> 任务：CMP-137 — PokeClaw端侧对接设备API联调准备  
> 生成时间：2026-05-18  
> 最后更新：2026-05-18（安卓小龙执行汇报）  
> 对齐文档：/mnt/e/code/dyq/api-contracts/device.openapi.yaml  
> 执行报告：docs/product/CMP-137-execution-report.md

---

## 一、接口契约对齐检查

### 1.1 设备端 API 端点清单

| 端点 | 方法 | 认证方式 | Android端实现 | 状态 |
|------|------|----------|---------------|------|
| /api/claw-device/register | POST | 无 | CloudDeviceApi.register() | ✅ 已实现 |
| /api/claw-device/heartbeat | POST | Bearer JWT | CloudDeviceApi.heartbeat() | ✅ 已实现 |
| /api/claw-device/devices/{deviceId}/pending-tasks | GET | Bearer JWT | CloudDeviceApi.getPendingTasks() | ✅ 已实现 |
| /api/claw-device/tasks/{taskUuid}/result | POST | Bearer JWT | CloudDeviceApi.submitTaskResult() | ✅ 已实现 |
| /api/claw-device/token/refresh | POST | 无 | CloudDeviceApi.refreshDeviceToken() | ✅ 已实现 |

### 1.2 DTO 字段对齐检查

#### DeviceRegisterRequest

| 字段 | OpenAPI类型 | Kotlin实现 | 可空 | 状态 |
|------|-------------|------------|------|------|
| deviceId | String (required) | String | 否 | ✅ 对齐 |
| deviceName | String | String? | 是 | ✅ 对齐 |
| deviceModel | String | String? | 是 | ✅ 对齐 |
| androidVersion | String | String? | 是 | ✅ 对齐 |
| appVersion | String | String? | 是 | ✅ 对齐 |
| publicKey | String | String? | 是 | ✅ 对齐 |

#### DeviceRegisterResponse

| 字段 | OpenAPI类型 | Kotlin实现 | 状态 |
|------|-------------|------------|------|
| deviceToken | String | String | ✅ 对齐 |
| refreshToken | String | String | ✅ 对齐 |
| expiresIn | Integer | Int | ✅ 对齐 |

#### DeviceHeartbeatRequest

| 字段 | OpenAPI类型 | Kotlin实现 | 枚举值 | 状态 |
|------|-------------|------------|--------|------|
| batteryLevel | Integer (0-100) | Int? | - | ✅ 对齐 |
| isCharging | Boolean | Boolean? | - | ✅ 对齐 |
| networkType | String | String? | wifi/cellular/offline | ✅ 对齐 |

#### PendingTaskItem

| 字段 | OpenAPI类型 | Kotlin实现 | 状态 |
|------|-------------|------------|------|
| taskUuid | String | String | ✅ 对齐 |
| command | String | String | ✅ 对齐 |
| mode | String | String? | ✅ 对齐 |
| createdAt | Integer (timestamp ms) | Long | ✅ 对齐 |
| priority | String | String? | ✅ 对齐 |

#### TaskResultRequest

| 字段 | OpenAPI类型 | Kotlin实现 | 状态 |
|------|-------------|------------|------|
| status | Enum | TaskResultRequest.Status | ✅ 对齐 |
| result | String | String? | ✅ 对齐 |
| errorMessage | String | String? | ✅ 对齐 |
| executionTimeMs | Integer | Long? | ✅ 对齐 |
| toolCalls | String | String? | ✅ 对齐 |
| evidenceUrls | String | String? | ✅ 对齐 |
| modelUsed | String | String? | ✅ 对齐 |
| errorCategory | String | String? | ✅ 对齐 |
| errorCode | String | String? | ✅ 对齐 |
| errorDetail | String | String? | ✅ 对齐 |
| recoverable | Boolean | Boolean? | ✅ 对齐 |
| suggestedAction | String | String? | ✅ 对齐 |
| screenshotBase64 | String | String? | ✅ 对齐 |
| logSnippet | String | String? | ✅ 对齐 |

### 1.3 枚举值对齐

| 枚举 | OpenAPI值 | Kotlin枚举 | 状态 |
|------|-----------|------------|------|
| TaskStatus | SUCCESS/FAILED/RUNNING/CANCELLED | TaskResultRequest.Status | ✅ 对齐 |
| NetworkType | wifi/cellular/offline | NetworkType | ✅ 对齐 |

---

## 二、Android Keystore Token 管理检查

### 2.1 实现状态

| 功能 | 实现类 | 状态 |
|------|--------|------|
| Token AES-GCM 加密存储 | AndroidKeystoreCloudDeviceTokenStore | ✅ 已实现 |
| 密钥生成与存储 | Android KeyStore | ✅ 已实现 |
| Token 过期检测 | CloudDeviceTokenSnapshot.shouldRefresh() | ✅ 已实现 |
| Token 刷新 | DeviceCloudClient.refreshTokenIfNeeded() | ✅ 已实现 |

### 2.2 安全合规检查

- ✅ Token 明文不写入 SharedPreferences
- ✅ 使用 Android Keystore 保护加密密钥
- ✅ AES-GCM 模式加密（128位认证标签）
- ✅ Token 过期检测（默认提前10分钟刷新）

---

## 三、离线降级逻辑检查

### 3.1 离线队列实现

| 功能 | 实现类 | 状态 |
|------|--------|------|
| 离线结果缓存 | CloudEventQueue | ✅ 已实现 |
| 队列大小限制 | maxSize=100 | ✅ 已实现 |
| 指数退避重试 | retryDelayMillis() | ✅ 已实现 |
| 补报触发 | DeviceCloudClient.flushOfflineQueue() | ✅ 已实现 |
| 敏感信息脱敏 | TaskResultRequest.sanitized() | ✅ 已实现 |

### 3.2 离线降级策略

```
网络正常:
  注册 → 心跳 → 任务拉取 → 执行 → 结果上报

网络异常:
  注册失败 → 延迟重试(指数退避)
  心跳失败 → 连续3次标记离线
  结果上报失败 → 进入离线队列 → 网络恢复后补报
```

---

## 四、联调步骤清单

### 4.1 后端服务启动检查

| 检查项 | 命令/方法 | 预期结果 | 状态 |
|--------|-----------|----------|------|
| 后端服务健康检查 | curl http://192.168.250.3:8080/actuator/health | HTTP 200 | ❌ **阻塞：服务未启动（等待@后端阿诚）** |
| 设备注册接口 | POST /api/claw-device/register | 返回 deviceToken | ❌ **阻塞：依赖后端** |
| 心跳接口 | POST /api/claw-device/heartbeat | 返回 pendingTaskCount | ❌ **阻塞：依赖后端** |

### 4.2 Android端联调步骤

#### Step 1: 设备注册测试
```bash
# 通过 ADB 触发注册（需安装 Debug APK）
adb shell am start -n io.agents.pokeclaw/.ui.ComposeChatActivity
adb logcat -s PokeClaw/DeviceCloudClient:D | grep register
```
预期输出：
```
PokeClaw/DeviceCloudClient: register: 开始注册设备，deviceId=xxx
PokeClaw/DeviceCloudClient: register: 注册成功，deviceId=xxx
```

#### Step 2: 心跳测试
```bash
adb logcat -s CloudHeartbeatManager:D | grep -E "(心跳|pendingTaskCount)"
```
预期输出：
```
CloudHeartbeatManager: 心跳成功，状态=在线
CloudHeartbeatManager: 拉取到 N 个待处理任务
```

#### Step 3: 任务上报测试
```bash
adb logcat -s PokeClaw/CloudNodeOrchestrator:D | grep -E "(executeCloudTask|submitTaskResult)"
```
预期输出：
```
PokeClaw/CloudNodeOrchestrator: executeCloudTask: 开始执行 taskUuid=xxx
PokeClaw/CloudNodeOrchestrator: executeCloudTask: 结果上报成功，taskUuid=xxx
```

### 4.3 Mock测试方案

使用 curl 模拟后端响应：

```bash
# 1. 启动本地 Mock 服务（如需独立测试）
# 或使用 Postman Mock Server

# 2. 测试设备注册
curl -X POST http://192.168.250.3:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "pokeclaw-test-001",
    "deviceName": "测试设备",
    "deviceModel": "Test Model",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'

# 预期响应
{
  "code": 0,
  "data": {
    "deviceToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 604800
  }
}

# 3. 测试心跳
curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "batteryLevel": 85,
    "isCharging": true,
    "networkType": "wifi"
  }'

# 预期响应
{
  "code": 0,
  "data": {
    "pendingTaskCount": 0,
    "skillVersion": 1,
    "serverTime": 1715990400000
  }
}
```

---

## 五、文件清单

| 文件路径 | 作用 | 状态 |
|----------|------|------|
| app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt | Retrofit API定义 | ✅ 已完成 |
| app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt | DTO数据模型 | ✅ 已完成 |
| app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt | Token安全存储 | ✅ 已完成 |
| app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt | 设备云端客户端 | ✅ 已完成 |
| app/src/main/java/io/agents/pokeclaw/cloud/CloudHeartbeatManager.kt | 心跳管理器 | ✅ 已完成 |
| app/src/main/java/io/agents/pokeclaw/cloud/CloudEventQueue.kt | 离线事件队列 | ✅ 已完成 |
| app/src/main/java/io/agents/pokeclaw/cloud/CloudNodeOrchestrator.kt | 端云编排器 | ✅ 已完成 |
| app/src/main/java/io/agents/pokeclaw/cloud/CloudTaskExecutor.kt | 任务执行器 | ⚠️ **TODO占位**（需接入AgentService，见执行报告） |

---

## 六、待验证清单

- [ ] 后端服务启动并健康检查通过
- [ ] 设备注册接口返回有效 Token
- [ ] 心跳接口正常响应并携带 pendingTaskCount
- [ ] Token 自动刷新逻辑验证
- [ ] 任务拉取接口正常响应
- [ ] 任务结果上报接口正常响应
- [ ] 离线模式下结果缓存验证
- [ ] 网络恢复后离线队列补报验证
- [ ] Android Keystore Token 加密存储验证
- [ ] Token 过期自动刷新验证

---

## 七、阻塞与风险

| 风险项 | 影响 | 缓解方案 |
|--------|------|----------|
| 后端服务未启动 | 无法联调 | 等待后端编译启动或启动本地Mock |
| 网络环境不通 | ADB联调受阻 | 使用本地模拟器或USB调试 |
| 设备API字段变更 | 端侧解析失败 | 严格对齐OpenAPI契约，变更时同步更新 |

---

## 八、下一步行动

1. **后端启动**：启动 dyq 后端服务（hermes分支）
2. **编译验证**：确保 PokeClaw Debug APK 可正常构建
3. **设备注册**：通过 ADB 触发首次注册，验证 Token 获取
4. **心跳联调**：验证周期性心跳和任务拉取
5. **任务闭环**：验证任务执行结果上报完整链路

---

## 九、产出物清单

| 产出物 | 路径 | 状态 |
|--------|------|------|
| 联调准备清单 | docs/product/pokeclaw-device-api-integration-checklist.md | ✅ 已产出 |
| Kotlin DTO | app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt | ✅ 已验证对齐 |
| Retrofit接口 | app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt | ✅ 已验证对齐 |
| 联调curl示例 | 本文档 4.3 节 | ✅ 已产出 |
