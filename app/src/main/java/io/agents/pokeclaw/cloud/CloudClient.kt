package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 云端客户端管理器
 * 负责 Retrofit 客户端创建、Token 注入、离线降级处理
 */
class CloudClient private constructor(context: Context) {

    companion object {
        private const val TAG = "PokeClaw/CloudClient"
        private const val BASE_URL = "http://192.168.250.3:8080/" // 开发环境
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L

        @Volatile
        private var instance: CloudClient? = null

        fun getInstance(context: Context): CloudClient {
            return instance ?: synchronized(this) {
                instance ?: CloudClient(context.applicationContext).also { instance = it }
            }
        }
    }

    private val tokenManager = TokenManager.getInstance(context)

    // 网络连接状态
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // 懒加载 Retrofit 客户端
    private val retrofit: Retrofit by lazy { createRetrofit() }

    // API 接口
    val deviceApi: DeviceApi by lazy { retrofit.create(DeviceApi::class.java) }

    /**
     * 创建 Retrofit 客户端
     */
    private fun createRetrofit(): Retrofit {
        val client = createOkHttpClient()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * 创建 OkHttpClient
     */
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // 日志级别：DEBUG构建时输出BODY，否则只输出BASIC
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()

                // 添加 Device Token（如果存在）
                val token = tokenManager.getDeviceToken()
                if (token != null && shouldAddAuth(request.url.toString())) {
                    builder.addHeader("Authorization", "Bearer $token")
                    XLog.d(TAG, "添加认证头: Bearer ${token.take(20)}...")
                }

                val newRequest = builder.build()
                val response = chain.proceed(newRequest)

                // 处理 401 未认证
                if (response.code == 401) {
                    XLog.w(TAG, "收到 401 未认证响应，需要刷新Token")
                    _connectionState.value = ConnectionState.AUTH_FAILED
                }

                response
            }
            .build()
    }

    /**
     * 判断是否需要添加认证头
     * 注册和刷新Token接口不需要认证
     */
    private fun shouldAddAuth(url: String): Boolean {
        return !url.contains("/register") && !url.contains("/token/refresh")
    }

    /**
     * 尝试刷新Token
     */
    suspend fun tryRefreshToken(): Boolean {
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken == null) {
            XLog.w(TAG, "没有 refreshToken，无法刷新")
            return false
        }

        return try {
            val response = deviceApi.refreshToken(
                io.agents.pokeclaw.cloud.model.TokenRefreshRequest(refreshToken)
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    tokenManager.saveDeviceToken(
                        body.data.deviceToken,
                        body.data.expiresIn ?: 604800
                    )
                    XLog.i(TAG, "Token 刷新成功")
                    _connectionState.value = ConnectionState.CONNECTED
                    true
                } else {
                    XLog.e(TAG, "Token 刷新响应为空")
                    false
                }
            } else {
                XLog.e(TAG, "Token 刷新失败: ${response.code()}")
                if (response.code() == 401) {
                    // refreshToken 也过期了，需要重新注册
                    tokenManager.clearTokens()
                }
                false
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Token 刷新异常", e)
            _connectionState.value = ConnectionState.OFFLINE
            false
        }
    }

    /**
     * 更新连接状态
     */
    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}

/**
 * 连接状态枚举
 */
enum class ConnectionState {
    DISCONNECTED,   // 未连接
    CONNECTING,     // 连接中
    CONNECTED,      // 已连接
    OFFLINE,        // 离线（网络不可用）
    AUTH_FAILED     // 认证失败
}
