# PokeClaw 设备 API 联调准备清单

> 任务编号：CMP-137
> 后端契约：/mnt/e/code/dyq/api-contracts/device.openapi.yaml
> 端侧实现：/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/

---

## 一、接口字段对齐状态

### 1.1 设备端 API（PokeClaw 调用）

| 接口 | 端点 | 端侧 DTO | 状态 |
|------|------|----------|------|
| 设备注册 | POST /api/claw-device/register | DeviceRegisterRequest/Response | ✅ 已实现 |
| 设备心跳 | POST /api/claw-device/heartbeat | DeviceHeartbeatRequest/Response | ✅ 已实现 |
| 获取待处理任务 | GET /api/claw-device/devices/{deviceId}/pending-tasks | PendingTaskItem | ✅ 已实现 |
| 提交任务结果 | POST /api/claw-device/tasks/{taskUuid}/result | TaskResultRequest | ✅ 已实现 |
| Token 刷新 | POST /api/claw-device/token/refresh | TokenRefreshRequest/Response | ✅ 已实现 |

### 1.2 DTO 字段核对

**DeviceRegisterRequest（请求）**
- ✅ deviceId — 必填，设备唯一标识
- ✅ deviceName — 可选，用户自定义名称
- ✅ deviceModel — 可选，设备型号
- ✅ androidVersion — 可选，Android 版本
- ✅ appVersion — 可选，App 版本
- ✅ publicKey — 可选，设备公钥

**DeviceRegisterResponse（响应）**
- ✅ deviceToken — JWT 设备令牌（7天有效）
- ✅ refreshToken — JWT 刷新令牌（30天有效）
- ✅ expiresIn — 过期时间（秒）

**DeviceHeartbeatRequest（请求）**
- ✅ batteryLevel — 电量百分比（0-100）
- ✅ isCharging — 是否充电中
- ✅ networkType — 网络类型（wifi/cellular/offline）

**DeviceHeartbeatResponse（响应）**
- ✅ pendingTaskCount — 待处理任务数量
- ✅ skillVersion — 技能版本号
- ✅ serverTime — 服务器时间戳（毫秒）

**TaskResultRequest（请求）**
- ✅ status — 任务状态枚举（SUCCESS/FAILED/RUNNING/CANCELLED）
- ✅ result — 执行结果文本
- ✅ errorMessage — 错误信息（用户可读）
- ✅ executionTimeMs — 执行耗时（毫秒）
- ✅ toolCalls — 工具调用记录（JSON 字符串）
- ✅ evidenceUrls — 证据 URL 列表（JSON 字符串）
- ✅ modelUsed — 使用的模型
- ✅ errorCategory — 错误大类（NETWORK/PERMISSION/TIMEOUT/UNKNOWN）
- ✅ errorCode — 错误码（如 E1001）
- ✅ errorDetail — 详细错误信息（技术层面）
- ✅ recoverable — 是否可重试
- ✅ suggestedAction — 建议用户操作
- ✅ screenshotBase64 — 失败截图（Base64）
- ✅ logSnippet — 日志片段（脱敏后）

---

## 二、端侧实现清单

### 2.1 数据模型（model/）

| 文件 | 说明 |
|------|------|
| CloudModels.kt | 所有 DTO 定义，对齐 device.openapi.yaml |

### 2.2 API 接口（api/）

| 文件 | 说明 |
|------|------|
| CloudDeviceApi.kt | Retrofit 接口定义，5 个端点 |
| CloudDeviceApiFactory.kt | API 工厂，带日志拦截器和 Bearer Token 注入 |

### 2.3 Token 管理（auth/）

| 文件 | 说明 |
|------|------|
| CloudDeviceTokenStore.kt | Android Keystore 安全存储实现 |

### 2.4 心跳与任务管理

| 文件 | 说明 |
|------|------|
| CloudHeartbeatManager.kt | WorkManager 周期性心跳调度 |
| CloudNodeOrchestrator.kt | 云端任务编排与执行 |
| CloudTaskExecutor.kt | 云端任务执行器 |
| CloudEventQueue.kt | 事件队列管理 |
| DeviceCloudClient.kt | 云端客户端封装 |

---

## 三、Retrofit 接口定义（CloudDeviceApi.kt）

```kotlin
interface CloudDeviceApi {
    // 注册（无 Token）
    @POST("/api/claw-device/register")
    suspend fun register(@Body request: DeviceRegisterRequest): ApiResponse<DeviceRegisterResponse>

    // 心跳（需 Bearer Token）
    @POST("/api/claw-device/heartbeat")
    suspend fun heartbeat(
        @Header("Authorization") authorization: String,
        @Body request: DeviceHeartbeatRequest
    ): ApiResponse<DeviceHeartbeatResponse>

    // 获取待处理任务（需 Bearer Token）
    @GET("/api/claw-device/devices/{deviceId}/pending-tasks")
    suspend fun getPendingTasks(
        @Header("Authorization") authorization: String,
        @Path("deviceId") deviceId: String
    ): ApiResponse<List<PendingTaskItem>>

    // 提交任务结果（需 Bearer Token）
    @POST("/api/claw-device/tasks/{taskUuid}/result")
    suspend fun submitTaskResult(
        @Header("Authorization") authorization: String,
        @Path("taskUuid") taskUuid: String,
        @Body request: TaskResultRequest
    ): ApiResponse<String>

    // Token 刷新（无 Token）
    @POST("/api/claw-device/token/refresh")
    suspend fun refreshDeviceToken(@Body request: TokenRefreshRequest): ApiResponse<TokenRefreshResponse>
}
```

---

## 四、Token 管理策略

### 4.1 存储方式
- Android Keystore AES-GCM 加密
- SharedPreferences 仅存储密文（IV + ciphertext）
- 密钥别名：`pokeclaw_cloud_device_token_aes`

### 4.2 Token 生命周期
- deviceToken：7 天有效
- refreshToken：30 天有效
- 刷新窗口：到期前 10 分钟自动刷新

### 4.3 离线降级
- 网络异常时降级到本地 Gemma 4 模型
- 任务结果本地缓存，网络恢复后批量上报

---

## 五、联调步骤（待执行）

### Step 1：后端启动验证
```bash
# 在 dyq 后端执行
mvn clean compile -DskipTests
# 确认无编译错误
```

### Step 2：设备注册测试
```bash
# curl 测试注册接口
curl -X POST http://192.168.250.3:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "test-device-001",
    "deviceName": "测试设备",
    "deviceModel": "Xiaomi 14",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'
# 预期响应包含 deviceToken、refreshToken、expiresIn
```

### Step 3：心跳测试
```bash
# 使用 Step 2 返回的 deviceToken
curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" \
  -d '{
    "batteryLevel": 85,
    "isCharging": true,
    "networkType": "wifi"
  }'
# 预期响应包含 pendingTaskCount、skillVersion、serverTime
```

### Step 4：任务轮询测试
```bash
curl -X GET "http://192.168.250.3:8080/api/claw-device/devices/test-device-001/pending-tasks" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}"
# 预期返回待处理任务列表
```

### Step 5：任务结果上报测试
```bash
curl -X POST "http://192.168.250.3:8080/api/claw-device/tasks/test-task-001/result" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" \
  -d '{
    "status": "SUCCESS",
    "result": "任务执行成功",
    "executionTimeMs": 5000,
    "modelUsed": "local"
  }'
```

### Step 6：Token 刷新测试
```bash
curl -X POST http://192.168.250.3:8080/api/claw-device/token/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "${REFRESH_TOKEN}"}'
# 预期返回新的 deviceToken
```

### Step 7：安卓端集成测试
```bash
# 1. 构建 APK
./gradlew assembleDebug

# 2. 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. 查看日志
adb logcat -s "PokeClaw/CloudDeviceTokenStore" "PokeClaw/CloudHeartbeatManager" "PokeClaw/CloudNodeOrchestrator" "PokeClaw/DeviceCloudClient"

# 4. 触发设备注册（首次启动或设置页面开启云端管理）
# 5. 观察心跳日志（每 30 秒一次）
```

---

## 六、已知约束与注意事项

### 6.1 后端约束
- 后端设备 API 已实现（AppClawDeviceController.java）
- JWT Token 认证通过 Filter 实现
- 任务结果接口支持完整的错误回传字段

### 6.2 端侧约束
- 最低 Android API 24（Android 7.0）
- 依赖 androidx.work:work-runtime-ktx 用于心跳调度
- 依赖 Retrofit + OkHttp 用于网络通信

### 6.3 安全红线
- Token 必须存储在 Android Keystore，禁止明文 SharedPreferences
- 日志中不得输出完整 Token（已脱敏处理）
- 截图上传需脱敏处理（敏感信息模糊化）

---

## 七、待验证问题清单

| 编号 | 验证项 | 状态 |
|------|--------|------|
| V1 | 后端编译通过 | ⏳ 待验证 |
| V2 | 设备注册 curl 测试通过 | ⏳ 待验证 |
| V3 | 心跳 curl 测试通过 | ⏳ 待验证 |
| V4 | 任务轮询 curl 测试通过 | ⏳ 待验证 |
| V5 | 任务结果上报 curl 测试通过 | ⏳ 待验证 |
| V6 | Token 刷新 curl 测试通过 | ⏳ 待验证 |
| V7 | 安卓端设备注册流程验证 | ⏳ 待验证 |
| V8 | 安卓端心跳定时调度验证 | ⏳ 待验证 |
| V9 | Token 自动刷新机制验证 | ⏳ 待验证 |
| V10 | 离线降级逻辑验证 | ⏳ 待验证 |

---

## 八、产出文件清单

| 文件路径 | 说明 |
|----------|------|
| /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt | DTO 定义 |
| /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt | Retrofit 接口 |
| /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApiFactory.kt | API 工厂 |
| /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt | Token 存储 |
| /mnt/e/code/PokeClaw/docs/product/device-api-integration-checklist.md | 本文档 |

---

## 九、下一步行动

1. **后端编译验证**：在 dyq 仓库执行 `mvn clean compile`，确认无错误
2. **curl 联调**：按 Step 1-6 执行后端 API 验证
3. **安卓端联调**：构建 APK 并验证端到端流程
4. **问题修复**：如联调中发现字段不匹配，更新 CloudModels.kt 并重新测试

---

*文档生成时间：2026-05-18*
*对齐契约版本：device.openapi.yaml v1.0.0*
