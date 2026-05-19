# PokeClaw设备API联调清单

## 生成信息

| 项目 | 值 |
|:---|:---|
| 生成时间 | 2026-05-17 |
| OpenAPI规范 | /mnt/e/code/dyq/api-contracts/device.openapi.yaml |
| 输出目录 | /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/generated |
| 生成器 | OpenAPI Generator 7.22.0 |
| 目标平台 | Kotlin + Retrofit2 + Gson |

## 生成的DTO模型

| 文件 | 说明 |
|:---|:---|
| DeviceRegisterRequest.kt | 设备注册请求 |
| DeviceRegisterResponse.kt | 设备注册响应（含deviceToken/refreshToken） |
| DeviceHeartbeatRequest.kt | 心跳请求（电量、网络状态） |
| DeviceHeartbeatResponse.kt | 心跳响应（待处理任务数） |
| PendingTaskItem.kt | 待处理任务项 |
| TaskResultRequest.kt | 任务结果上报请求 |
| TokenRefreshRequest.kt | Token刷新请求 |
| TokenRefreshResponse.kt | Token刷新响应 |

## 生成的API接口

| 接口方法 | HTTP | 路径 | 说明 |
|:---|:---|:---|:---|
| deviceRegister() | POST | /api/claw-device/register | 设备注册（无需认证） |
| deviceHeartbeat() | POST | /api/claw-device/heartbeat | 设备心跳（需deviceToken） |
| getPendingTasks() | GET | /api/claw-device/devices/{deviceId}/pending-tasks | 拉取待处理任务 |
| submitTaskResult() | POST | /api/claw-device/tasks/{taskUuid}/result | 提交任务结果 |
| refreshDeviceToken() | POST | /api/claw-device/token/refresh | 刷新Token（无需认证） |

## Token管理策略

1. **deviceToken**: JWT短期令牌，有效期7天，存储于Android Keystore
2. **refreshToken**: JWT长期令牌，有效期30天，存储于Android Keystore
3. **刷新时机**: deviceToken即将过期时调用refreshDeviceToken()
4. **认证方式**: HTTP Bearer Token（Authorization: Bearer {deviceToken}）

## 离线降级逻辑

| 场景 | 处理策略 |
|:---|:---|
| 网络断开 | 本地缓存任务，网络恢复后批量上报 |
| Token过期 | 使用refreshToken获取新deviceToken |
| refreshToken过期 | 重新调用deviceRegister()注册 |
| 云端不可用 | 降级到本地Gemma 4模型执行 |

## 集成步骤

### 步骤1: 移动生成的代码到正确位置
```bash
# 创建目标目录结构
mkdir -p app/src/main/java/io/agents/pokeclaw/cloud/{api,model,infrastructure}

# 移动DTO模型
cp app/src/main/java/io/agents/pokeclaw/cloud/generated/src/main/kotlin/io/agents/pokeclaw/cloud/model/*.kt \
   app/src/main/java/io/agents/pokeclaw/cloud/model/

# 移动API接口
cp app/src/main/java/io/agents/pokeclaw/cloud/generated/src/main/kotlin/io/agents/pokeclaw/cloud/api/*.kt \
   app/src/main/java/io/agents/pokeclaw/cloud/api/

# 移动基础设施
cp app/src/main/java/io/agents/pokeclaw/cloud/generated/src/main/kotlin/org/openapitools/client/infrastructure/*.kt \
   app/src/main/java/io/agents/pokeclaw/cloud/infrastructure/
```

### 步骤2: 添加Gradle依赖
```kotlin
// app/build.gradle.kts
dependencies {
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

### 步骤3: 创建CloudClient
```kotlin
// app/src/main/java/io/agents/pokeclaw/cloud/CloudClient.kt
package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.api.DefaultApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CloudClient {
    private const val BASE_URL = "http://192.168.250.3:8080"
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val api: DefaultApi = retrofit.create(DefaultApi::class.java)
}
```

### 步骤4: Token管理器（Android Keystore）
```kotlin
// app/src/main/java/io/agents/pokeclaw/cloud/TokenManager.kt
package io.agents.pokeclaw.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenManager(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_tokens", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    companion object {
        private const val KEY_ALIAS = "cloud_token_key"
        private const val PREFS_DEVICE_TOKEN = "device_token_encrypted"
        private const val PREFS_REFRESH_TOKEN = "refresh_token_encrypted"
    }
    
    // 存储加密Token
    fun saveDeviceToken(token: String) {
        prefs.edit().putString(PREFS_DEVICE_TOKEN, encrypt(token)).apply()
    }
    
    fun saveRefreshToken(token: String) {
        prefs.edit().putString(PREFS_REFRESH_TOKEN, encrypt(token)).apply()
    }
    
    // 获取解密Token
    fun getDeviceToken(): String? = prefs.getString(PREFS_DEVICE_TOKEN, null)?.let { decrypt(it) }
    fun getRefreshToken(): String? = prefs.getString(PREFS_REFRESH_TOKEN, null)?.let { decrypt(it) }
    
    // 清除Token
    fun clearTokens() {
        prefs.edit().remove(PREFS_DEVICE_TOKEN).remove(PREFS_REFRESH_TOKEN).apply()
    }
    
    private fun encrypt(plaintext: String): String {
        // 实现基于Android Keystore的AES-GCM加密
        // ...
        return plaintext // 占位符
    }
    
    private fun decrypt(ciphertext: String): String {
        // 实现解密
        // ...
        return ciphertext // 占位符
    }
}
```

### 步骤5: 设备注册服务
```kotlin
// app/src/main/java/io/agents/pokeclaw/cloud/DeviceRegistrationService.kt
package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRegistrationService(private val tokenManager: TokenManager) {
    
    suspend fun registerDevice(
        deviceId: String,
        deviceName: String? = null,
        deviceModel: String? = null,
        androidVersion: String? = null,
        appVersion: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = DeviceRegisterRequest(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceModel = deviceModel,
                androidVersion = androidVersion,
                appVersion = appVersion,
                publicKey = null // 可选：用于JWT签名验证
            )
            
            val response = CloudClient.api.deviceRegister(request)
            if (response.isSuccessful) {
                response.body()?.data?.let { data ->
                    tokenManager.saveDeviceToken(data.deviceToken)
                    tokenManager.saveRefreshToken(data.refreshToken)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("注册失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 步骤6: 心跳管理器
```kotlin
// app/src/main/java/io/agents/pokeclaw/cloud/HeartbeatManager.kt
package io.agents.pokeclaw.cloud

import androidx.work.*
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class HeartbeatManager(context: Context) {
    private val workManager = WorkManager.getInstance(context)
    
    fun startHeartbeat(deviceId: String) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(30, TimeUnit.SECONDS)
            .setInputData(workDataOf("device_id" to deviceId))
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "device_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
    
    fun stopHeartbeat() {
        workManager.cancelUniqueWork("device_heartbeat")
    }
}

class HeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deviceId = inputData.getString("device_id") ?: return Result.failure()
        
        return try {
            val batteryLevel = getBatteryLevel()
            val isCharging = isCharging()
            val networkType = getNetworkType()
            
            val request = DeviceHeartbeatRequest(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                networkType = networkType
            )
            
            val response = CloudClient.api.deviceHeartbeat(request)
            if (response.isSuccessful) {
                response.body()?.data?.let { data ->
                    if (data.pendingTaskCount > 0) {
                        // 触发任务拉取
                        TaskFetcher.fetchPendingTasks(deviceId)
                    }
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

## 联调检查项

- [ ] DTO模型与后端字段对齐验证
- [ ] 设备注册接口联调
- [ ] Token获取与存储验证
- [ ] 心跳接口联调
- [ ] 任务拉取接口联调
- [ ] 任务结果上报接口联调
- [ ] Token刷新流程验证
- [ ] 离线降级逻辑验证
- [ ] 网络异常重试机制验证
- [ ] 并发场景安全验证

## 当前状态 (2026-05-18)

### 已完成项

| 事项 | 状态 | 说明 |
|:---|:---|:---|
| Kotlin DTO模型 | ✅ 完成 | CloudModels.kt 已对齐 device.openapi.yaml |
| Retrofit API接口 | ✅ 完成 | CloudDeviceApi.kt 已实现5个核心接口 |
| Token管理(Keystore) | ✅ 完成 | CloudDeviceTokenStore.kt 已实现AES-GCM加密存储 |
| 离线降级逻辑 | ✅ 完成 | CloudEventQueue.kt 已实现指数退避重试队列 |
| WorkManager依赖 | ✅ 完成 | app/build.gradle.kts 已添加 androidx.work:work-runtime |
| 端侧编排器 | ✅ 完成 | CloudNodeOrchestrator.kt 已实现注册→心跳→任务→上报闭环 |
| 测试脚本 | ✅ 完成 | scripts/device-api-integration-test.sh 已创建 |

### 阻塞事项

| 事项 | 状态 | 说明 |
|:---|:---|:---|
| 后端编译 | 🟡 阻塞 | mvn compile 超时，需排查依赖或内存问题 |
| 环境部署 | 🟡 阻塞 | 后端服务未启动，联调测试待后端就绪后进行 |
| API联调测试 | 🟡 待执行 | 等待后端服务启动后执行测试脚本验证 |

### 下一步行动

1. **后端修复**: 排查 dyq hermes 分支编译超时问题
2. **服务启动**: 启动后端后执行 `./scripts/device-api-integration-test.sh`
3. **验证清单**: 
   - [ ] 设备注册接口返回 deviceToken/refreshToken
   - [ ] 心跳接口响应 pendingTaskCount/skillVersion/serverTime
   - [ ] 任务拉取接口返回任务列表
   - [ ] 结果上报接口返回成功状态
   - [ ] Token刷新接口返回新 deviceToken

## 产出文件清单

1. `/mnt/e/code/PokeClaw/scripts/generate-device-api.sh` - 代码生成脚本
2. `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/generated/` - 生成的Kotlin代码
3. `/mnt/e/code/PokeClaw/docs/product/INTEGRATION.md` - 联调清单（本文件）
