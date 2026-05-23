# DYQ-28 端侧离线执行链路本地闭环 - 验证报告

**日期**: 2026-05-23  
**执行人**: 端侧工程师阿甲  
**状态**: ✅ 验证通过

## 1. 文件清单与归属

### 1.1 应纳入源码的文件 (Kotlin)

| 文件路径 | 类型 | 状态 | 说明 |
|---------|------|------|------|
| `app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt` | 新增 | ✅ | 设备云端客户端接口契约 |
| `app/src/main/java/io/agents/pokeclaw/cloud/RetrofitDeviceCloudClient.kt` | 新增 | ✅ | Retrofit实现，含Token自动刷新 |
| `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudTaskReceiptManager.kt` | 新增 | ✅ | 云端任务回执管理器（本地闭环核心） |
| `app/src/main/java/io/agents/pokeclaw/cloudnode/LocalClosedLoopSampler.kt` | 新增 | ✅ | 本地闭环样例生成器 |

### 1.2 已跟踪的修改文件

| 文件路径 | 变更内容 | 验证状态 |
|---------|---------|---------|
| `CloudClient.kt` | 添加401处理、Token刷新 | ✅ 代码审查通过 |
| `DeviceService.kt` | 任务结果上报字段扩展 | ✅ 代码审查通过 |
| `DeviceApi.kt` | 端点路径对齐 | ✅ 代码审查通过 |
| `CloudDeviceTokenStore.kt` | Token安全存储实现 | ✅ 代码审查通过 |

### 1.3 应忽略的文件

| 文件/目录 | 处理方式 |
|---------|---------|
| `artifacts/` | 已添加至 `.gitignore` |
| `scripts/__pycache__/` | 已添加至 `.gitignore` |

## 2. Token安全存储验证

### 2.1 实现方式
- **存储位置**: Android Keystore (AES-GCM加密)
- **密钥管理**: 密钥在Android Keystore中生成，磁盘只保存加密载荷
- **SharedPreferences内容**: 仅保存 IV + 密文，无明文Token

### 2.2 关键代码证据
```kotlin
// AndroidKeystoreCloudDeviceTokenStore.kt:68-84
class AndroidKeystoreCloudDeviceTokenStore(context: Context) : CloudDeviceTokenStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int, nowMillis: Long) {
        val expiresAt = nowMillis + expiresInSeconds.coerceAtLeast(0) * 1000L
        prefs.edit()
            .putString(KEY_DEVICE_TOKEN, encrypt(deviceToken))  // 加密存储
            .putString(KEY_REFRESH_TOKEN, encrypt(refreshToken)) // 加密存储
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }
}
```

### 2.3 验证结论
✅ **Token安全存储实现正确**，未退回明文SharedPreferences

## 3. 契约字段对齐验证

### 3.1 DeviceRegisterRequest (注册请求)

| 字段 | Kotlin类型 | OpenAPI类型 | 对齐状态 |
|------|-----------|------------|---------|
| deviceId | String | string | ✅ |
| deviceName | String? | string | ✅ |
| deviceModel | String? | string | ✅ |
| androidVersion | String? | string | ✅ |
| appVersion | String? | string | ✅ |
| publicKey | String? | string | ✅ |

### 3.2 DeviceHeartbeatRequest (心跳请求)

| 字段 | Kotlin类型 | OpenAPI类型 | 对齐状态 |
|------|-----------|------------|---------|
| batteryLevel | Int? | integer | ✅ |
| isCharging | Boolean? | boolean | ✅ |
| networkType | String? | string (wifi/cellular/offline) | ✅ |

### 3.3 TaskResultRequest (任务结果上报)

| 字段 | Kotlin类型 | OpenAPI类型 | 说明 |
|------|-----------|------------|------|
| status | Status enum | string enum | SUCCESS/FAILED/RUNNING/CANCELLED ✅ |
| result | String? | string | 执行结果 |
| errorMessage | String? | string | 错误信息（用户可读）|
| executionTimeMs | Long? | integer | 执行耗时（毫秒）|
| errorCategory | String? | string | 错误大类 |
| errorCode | String? | string | 错误码 |
| recoverable | Boolean? | boolean | 是否可重试 |

### 3.4 验证结论
✅ **所有DTO字段与OpenAPI契约对齐**

## 4. 端点路径验证

| 端点 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 设备注册 | POST | /api/claw-device/register | ✅ |
| 心跳发送 | POST | /api/claw-device/heartbeat | ✅ |
| 拉取任务 | GET | /api/claw-device/devices/{deviceId}/pending-tasks | ✅ |
| 提交结果 | POST | /api/claw-device/tasks/{taskUuid}/result | ✅ |
| Token刷新 | POST | /api/claw-device/token/refresh | ✅ |

## 5. 单元测试验证

### 5.1 测试覆盖

| 测试类 | 测试方法 | 覆盖场景 |
|--------|---------|---------|
| CloudExecutorNodeContractTest | `本地模拟任务会按接收运行成功顺序上报` | 成功执行状态流 |
| CloudExecutorNodeContractTest | `本地模拟失败任务会生成可重试错误回传` | 失败错误回传 |
| CloudExecutorNodeContractTest | `状态报告可以折叠为请求回执` | 回执折叠 |
| CloudExecutorNodeContractTest | `本地闭环成功任务会生成回执和模拟云端载荷` | 成功闭环 |
| CloudExecutorNodeContractTest | `本地闭环离线上报会缓存回执并按退避重试` | 离线缓存与重试 |
| CloudExecutorNodeContractTest | `本地闭环可重试失败会给出重试计划和错误载荷` | 重试计划 |

### 5.2 验证结论
✅ **6个单元测试全部通过**

## 6. 本地闭环组件验证

### 6.1 CloudTaskReceiptManager (回执管理器)

| 功能 | 验证状态 |
|------|---------|
| 状态流折叠为回执 | ✅ |
| 离线缓存管理 | ✅ |
| 模拟云端载荷生成 | ✅ |
| 经历上报载荷生成 | ✅ |
| 执行证据日志 | ✅ |

### 6.2 LocalClosedLoopSampler (样例生成器)

| 样例场景 | 验证状态 |
|---------|---------|
| 成功执行 | ✅ |
| 可重试失败 | ✅ |
| 不可重试失败 | ✅ |
| 执行超时 | ✅ |
| 权限缺失 | ✅ |
| 离线缓存 | ✅ |

## 7. 401刷新逻辑验证

### 7.1 CloudClient.kt (已跟踪文件)
```kotlin
// 401处理逻辑 (line 88-91)
if (response.code == 401) {
    XLog.w(TAG, "收到 401 未认证响应，需要刷新Token")
    _connectionState.value = ConnectionState.AUTH_FAILED
}
```

### 7.2 RetrofitDeviceCloudClient.kt (新增文件)
```kotlin
// Token刷新实现 (line 209-252)
override suspend fun refreshTokenIfNeeded(nowMillis: Long): Boolean {
    if (!tokenStore.shouldRefreshToken(nowMillis)) {
        return true
    }
    // ... 刷新逻辑，处理401清除Token
}
```

## 8. 结论与建议

### 8.1 验证结论

| 检查项 | 状态 |
|--------|------|
| Token安全存储 (Keystore加密) | ✅ 通过 |
| DTO字段与OpenAPI对齐 | ✅ 通过 |
| 端点路径正确 | ✅ 通过 |
| 401刷新逻辑 | ✅ 通过 |
| 单元测试覆盖 | ✅ 通过 |
| 本地闭环功能 | ✅ 通过 |

### 8.2 建议操作

1. **提交新增文件**: 4个新增Kotlin文件应纳入源码
2. **提交修改文件**: 4个已跟踪文件的变更可提交
3. **忽略artifacts和__pycache__**: 已通过.gitignore配置

### 8.3 风险提示

- 真实云端联调仍依赖后端服务恢复 (DYQ-3/DYQ-10/DYQ-25)
- Mock服务已验证可用，可用于端侧独立测试

---
**验证人**: 端侧工程师阿甲  
**验证时间**: 2026-05-23
