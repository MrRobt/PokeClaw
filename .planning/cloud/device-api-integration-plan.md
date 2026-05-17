# PokeClaw 设备 API 联调准备清单

## 任务来源
CMP-137: 【Android】PokeClaw端侧对接 — 设备API联调准备

## 执行时间
2026-05-17

## 执行者
安卓小龙 (ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0)

---

## 一、前提验证结果

| 检查项 | 状态 | 说明 |
|--------|------|------|
| PokeClaw 仓库路径 | ✅ | /mnt/e/code/PokeClaw，main 分支 |
| device.openapi.yaml 存在 | ✅ | /mnt/e/code/dyq/api-contracts/device.openapi.yaml (20903 字节) |
| 后端编译状态 | ⏳ | 待确认（Issue 描述中"等后端编译通过后"） |
| 现有 cloud 模块 | ✅ | 已存在 io.agents.pokeclaw.cloud 包，有完整实现 |

---

## 二、OpenAPI 代码生成结果

使用 openapi-generator-cli 7.10.0 从 device.openapi.yaml 生成 Kotlin Retrofit 客户端代码。

### 生成配置
```json
{
  "modelPackage": "io.agents.pokeclaw.cloud.model",
  "apiPackage": "io.agents.pokeclaw.cloud.api",
  "library": "jvm-retrofit2",
  "serializationLibrary": "gson",
  "useCoroutines": true
}
```

### 生成的 DTO 清单（25 个）

| 类名 | 对应 OpenAPI Schema | 用途 |
|------|---------------------|------|
| DeviceRegisterRequest | DeviceRegisterRequest | 设备注册请求 |
| DeviceRegisterResponse | DeviceRegisterResponse | 设备注册响应 |
| DeviceHeartbeatRequest | DeviceHeartbeatRequest | 心跳请求 |
| DeviceHeartbeatResponse | DeviceHeartbeatResponse | 心跳响应 |
| TokenRefreshRequest | TokenRefreshRequest | Token 刷新请求 |
| TokenRefreshResponse | TokenRefreshResponse | Token 刷新响应 |
| PendingTaskItem | PendingTaskItem | 待处理任务项 |
| TaskResultRequest | TaskResultRequest | 任务结果上报 |
| DeviceVO | DeviceVO | 设备详情 |
| DeviceTaskVO | DeviceTaskVO | 任务详情 |
| DeviceExecuteRequest | DeviceExecuteRequest | 执行任务请求 |
| DeviceUpdateStatusRequest | DeviceUpdateStatusRequest | 更新设备状态 |
| DevicePageRequest | DevicePageRequest | 设备分页查询 |
| DeviceTaskPageRequest | DeviceTaskPageRequest | 任务分页查询 |
| CommonResult | CommonResult | 通用响应包装 |
| PageResult | PageResult | 分页结果 |

### 生成的 API 接口（1 个）

**DefaultApi** - 包含所有设备端和管理端 API：
- `POST /api/claw-device/register` - deviceRegister
- `POST /api/claw-device/heartbeat` - deviceHeartbeat
- `GET /api/claw-device/devices/{deviceId}/pending-tasks` - getPendingTasks
- `POST /api/claw-device/tasks/{taskUuid}/result` - submitTaskResult
- `POST /api/claw-device/token/refresh` - refreshDeviceToken
- `GET /claw/device/list` - getDevicePage (管理端)
- `GET /claw/device/{deviceId}` - getDeviceDetail (管理端)
- `POST /claw/device/{deviceId}/execute` - executeTaskOnDevice (管理端)
- `GET /claw/device/{deviceId}/tasks` - getDeviceTaskHistory (管理端)
- `PUT /claw/device/{deviceId}/status` - updateDeviceStatus (管理端)
- `GET/PUT /claw/device/{deviceId}/skills` - getDeviceSkills/updateDeviceSkill (Phase 2)

---

## 三、字段对齐验证

### 设备注册请求 (DeviceRegisterRequest)

| 字段 | OpenAPI 类型 | 生成代码 | 现有代码 | 状态 |
|------|-------------|---------|---------|------|
| deviceId | string (required) | String | String | ✅ 一致 |
| deviceName | string? | String? | String? | ✅ 一致 |
| deviceModel | string? | String? | String? | ✅ 一致 |
| androidVersion | string? | String? | String? | ✅ 一致 |
| appVersion | string? | String? | String? | ✅ 一致 |
| publicKey | string? | String? | String? | ✅ 一致 |

### 设备注册响应 (DeviceRegisterResponse)

| 字段 | OpenAPI 类型 | 生成代码 | 现有代码 | 状态 |
|------|-------------|---------|---------|------|
| deviceToken | string? | String? | String | ⚠️ 现有非空，生成可为空 |
| refreshToken | string? | String? | String | ⚠️ 同上 |
| expiresIn | integer? | Int? | Int | ⚠️ 同上 |

### 心跳请求 (DeviceHeartbeatRequest)

| 字段 | OpenAPI 类型 | 生成代码 | 现有代码 | 状态 |
|------|-------------|---------|---------|------|
| batteryLevel | integer? | Int? | Int? | ✅ 一致 |
| isCharging | boolean? | Boolean? | Boolean? | ✅ 一致 |
| networkType | string? | String? | String? | ✅ 一致 |

### 任务结果上报 (TaskResultRequest)

| 字段 | OpenAPI 类型 | 生成代码 | 现有代码 | 状态 |
|------|-------------|---------|---------|------|
| status | string (required) | String | String | ✅ 一致 |
| result | string? | String? | String? | ✅ 一致 |
| errorMessage | string? | String? | String? | ✅ 一致 |
| executionTimeMs | integer? | Int? | Long? | ⚠️ 类型不同 |
| toolCalls | string? | String? | String? | ✅ 一致 |
| evidenceUrls | string? | String? | String? | ✅ 一致 |
| modelUsed | string? | String? | String? | ✅ 一致 |
| errorCategory | - | - | String? | ⚠️ 现有扩展字段 |
| errorCode | - | - | String? | ⚠️ 现有扩展字段 |
| errorDetail | - | - | String? | ⚠️ 现有扩展字段 |
| recoverable | - | - | Boolean? | ⚠️ 现有扩展字段 |
| suggestedAction | - | - | String? | ⚠️ 现有扩展字段 |
| screenshotBase64 | - | - | String? | ⚠️ 现有扩展字段 |
| logSnippet | - | - | String? | ⚠️ 现有扩展字段 |

---

## 四、差异处理建议

### 1. 可空性问题
OpenAPI 生成的字段多为可空，而现有代码部分字段非空。
**建议**：保持现有代码非空约束，在业务层校验后再构造对象。

### 2. executionTimeMs 类型差异
- OpenAPI: integer (Kotlin Int)
- 现有代码: Long

**建议**：修改为 Long 以支持更大范围的时间值。

### 3. 扩展字段
现有 TaskResultRequest 包含多个扩展字段用于错误回传（errorCategory, errorCode 等）。
**建议**：这些字段应同步到后端 OpenAPI spec，保持前后端一致。

---

## 五、Retrofit 接口使用示例

```kotlin
// 创建 API 客户端
val apiClient = ApiClient(
    baseUrl = "http://192.168.250.3:8080",
    authNames = arrayOf("DeviceBearerAuth")
)
apiClient.setBearerToken(deviceToken)

val api = apiClient.createService(DefaultApi::class.java)

// 1. 设备注册（无需 Token）
val registerRequest = DeviceRegisterRequest(
    deviceId = "pokeclaw-sn-a1b2c3",
    deviceName = "小龙虾-测试机",
    deviceModel = "Xiaomi 14",
    androidVersion = "14",
    appVersion = "0.7.0"
)
val registerResponse = api.deviceRegister(registerRequest)

// 2. 发送心跳
val heartbeatRequest = DeviceHeartbeatRequest(
    batteryLevel = 85,
    isCharging = true,
    networkType = "wifi"
)
val heartbeatResponse = api.deviceHeartbeat(heartbeatRequest)

// 3. 拉取待处理任务
val pendingTasks = api.getPendingTasks(deviceId)

// 4. 提交任务结果
val resultRequest = TaskResultRequest(
    status = "SUCCESS",
    result = "任务执行成功",
    executionTimeMs = 1500
)
api.submitTaskResult(taskUuid, resultRequest)

// 5. 刷新 Token
val refreshRequest = TokenRefreshRequest(refreshToken)
val refreshResponse = api.refreshDeviceToken(refreshRequest)
```

---

## 六、Token 管理（Android Keystore）

后端 spec 要求：
- deviceToken: 短期有效（7 天）
- refreshToken: 长期有效（30 天）

Android 端应使用 Keystore 安全存储 refreshToken，deviceToken 可存于 EncryptedSharedPreferences。

现有实现：CloudDeviceTokenStore.kt 已处理 Token 存储和刷新逻辑。

---

## 七、离线降级逻辑

当云端不可用时，应降级到本地 Gemma 4 模型执行简单任务。

现有实现：CloudEventQueue.kt 已处理离线队列缓存。

---

## 八、下一步开发任务

### 阻塞项
1. 后端编译通过并部署到 192.168.250.3:8080
2. 后端确认设备 API 接口可正常访问

### 可独立完成的任务
1. 创建 Retrofit 接口适配层（包装生成代码与现有模型）
2. 实现 Token 自动刷新拦截器
3. 添加请求/响应日志拦截器
4. 编写单元测试

### 需要联调验证的任务
1. 设备注册流程 E2E 测试
2. 心跳保活机制测试
3. 任务拉取+执行+上报闭环测试
4. Token 刷新无感续期测试

---

## 九、文件清单

### 新增文件
| 路径 | 说明 |
|------|------|
| app/src/main/java/io/agents/pokeclaw/cloud/model/*.kt | OpenAPI 生成的 DTO（25 个类） |
| app/src/main/java/io/agents/pokeclaw/cloud/api/DefaultApi.kt | Retrofit 接口定义 |
| app/src/main/java/org/openapitools/client/infrastructure/*.kt | OpenAPI 基础设施类 |
| app/src/main/java/org/openapitools/client/auth/HttpBearerAuth.kt | Bearer Token 认证 |

### 修改建议
| 路径 | 说明 |
|------|------|
| app/src/main/java/io/agents/pokeclaw/cloud/model/TaskResultRequest.kt | executionTimeMs 改为 Long |
| /mnt/e/code/dyq/api-contracts/device.openapi.yaml | 添加扩展错误字段到 spec |

---

## 十、待验证清单

- [ ] 后端 /api/claw-device/register 接口可正常访问
- [ ] 后端 /api/claw-device/heartbeat 接口可正常访问
- [ ] 后端 /api/claw-device/devices/{deviceId}/pending-tasks 接口可正常访问
- [ ] 后端 /api/claw-device/tasks/{taskUuid}/result 接口可正常访问
- [ ] 后端 /api/claw-device/token/refresh 接口可正常访问
- [ ] Android 端设备注册成功并返回有效 Token
- [ ] Android 端心跳正常发送并接收服务器时间
- [ ] Android 端可正常拉取待处理任务
- [ ] Android 端任务结果上报成功
- [ ] Token 过期自动刷新机制正常
- [ ] 离线队列缓存和补报机制正常

---

## 执行摘要

1. **实际检查文件**：
   - /mnt/e/code/dyq/api-contracts/device.openapi.yaml（完整读取，768 行）
   - /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt
   - /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt

2. **实际执行命令**：
   - `curl -s http://127.0.0.1:3101/api/companies/.../issues`（查询名下任务）
   - `java -jar openapi-generator-cli-7.10.0.jar generate`（生成 Kotlin 代码）
   - 文件移动和包路径修复

3. **产出路径**：
   - /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/*.kt
   - /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/DefaultApi.kt
   - /mnt/e/code/PokeClaw/app/src/main/java/org/openapitools/client/infrastructure/
   - /mnt/e/code/PokeClaw/.planning/cloud/device-api-integration-plan.md（本文档）

4. **改动摘要**：
   - 新增 25 个 DTO 模型类（从 OpenAPI 生成）
   - 新增 Retrofit API 接口（DefaultApi.kt）
   - 新增基础设施类（ApiClient, HttpBearerAuth 等）
   - 字段对齐验证：核心字段一致，存在可空性和类型小差异

5. **阻塞原因**：
   - 后端 API 需编译通过并部署后才能进行联调测试
   - 当前产出为接口准备阶段，等待后端就绪后继续 E2E 验证
