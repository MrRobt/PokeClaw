// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云 Retrofit 工厂 — 构造 OkHttp + Retrofit + DeviceApi。
// 与 CloudClient.kt 拆开：CloudClient 用于聊天通道；本工厂专用于设备云端 5 端点。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.lobster.api.LobsterCommandApi
import io.agents.pokeclaw.cloud.lobster.api.LobsterMemoryApi
import io.agents.pokeclaw.cloud.lobster.api.LobsterPersonalityApi
import io.agents.pokeclaw.cloud.lobster.api.LobsterProfileApi
import io.agents.pokeclaw.cloud.lobster.api.LobsterSkillMarketplaceApi
import io.agents.pokeclaw.utils.XLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 设备云端 Retrofit 客户端工厂。
 *
 * 负责：
 * 1. 构造 OkHttp（带 deviceToken 拦截器：除 /register 与 /token/refresh 之外全部加 Authorization 头）
 * 2. 构造 Retrofit + Gson
 * 3. 暴露 [DeviceApi] 给上层使用
 *
 * 401 不在拦截器中处理 — 由 [RetrofitDeviceCloudClient.runWithAuthRetry] 集中管理，
 * 避免在拦截器里触发再次异步请求的复杂性。
 */
object CloudClientFactory {

    private const val TAG = "PokeClaw/CloudClientFactory"
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 60L

    /**
     * 规范化 baseUrl：补齐结尾斜杠，并校验必须以 http:// 或 https:// 开头。
     *
     * @throws IllegalArgumentException 当 scheme 不合法
     */
    fun normalizeBaseUrl(raw: String): String {
        require(raw.startsWith("http://") || raw.startsWith("https://")) {
            "baseUrl 必须以 http:// 或 https:// 开头：$raw"
        }
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    fun buildDeviceApi(baseUrl: String, tokenStore: CloudDeviceTokenStore): DeviceApi {
        val okHttpClient = buildOkHttpClient(tokenStore)
        val retrofit = buildRetrofit(baseUrl, okHttpClient)
        return retrofit.create(DeviceApi::class.java)
    }

    /**
     * US-D-038 主人指令通道客户端构造。
     *
     * 复用设备层 [DeviceTokenInterceptor]：lobster/command 与 hermes/feedback 端点
     * 在 dyq v1.1.0 同样要求 deviceToken 鉴权（共享同一 deviceToken 命名空间）。
     */
    fun buildLobsterCommandApi(baseUrl: String, tokenStore: CloudDeviceTokenStore): LobsterCommandApi {
        val okHttpClient = buildOkHttpClient(tokenStore)
        val retrofit = buildRetrofit(baseUrl, okHttpClient)
        return retrofit.create(LobsterCommandApi::class.java)
    }

    /**
     * US-D-039 Skill Marketplace 客户端构造。
     *
     * 复用设备层 [DeviceTokenInterceptor]：lobster/skill 端点同样要求 deviceToken 鉴权。
     */
    fun buildSkillMarketplaceApi(baseUrl: String, tokenStore: CloudDeviceTokenStore): LobsterSkillMarketplaceApi {
        val okHttpClient = buildOkHttpClient(tokenStore)
        val retrofit = buildRetrofit(baseUrl, okHttpClient)
        return retrofit.create(LobsterSkillMarketplaceApi::class.java)
    }

    /**
     * US-D-040 记忆 API 构造。
     */
    fun buildLobsterMemoryApi(baseUrl: String, tokenStore: CloudDeviceTokenStore): LobsterMemoryApi {
        val okHttpClient = buildOkHttpClient(tokenStore)
        val retrofit = buildRetrofit(baseUrl, okHttpClient)
        return retrofit.create(LobsterMemoryApi::class.java)
    }

    /**
     * US-D-040 人格 API 构造。
     */
    fun buildLobsterPersonalityApi(baseUrl: String, tokenStore: CloudDeviceTokenStore): LobsterPersonalityApi {
        val okHttpClient = buildOkHttpClient(tokenStore)
        val retrofit = buildRetrofit(baseUrl, okHttpClient)
        return retrofit.create(LobsterPersonalityApi::class.java)
    }

    /**
     * US-D-041 Profile API 构造。
     *
     * 复用设备层 [DeviceTokenInterceptor]：lobster/my 端点同样要求 deviceToken 鉴权。
     */
    fun buildLobsterProfileApi(baseUrl: String, tokenStore: CloudDeviceTokenStore): LobsterProfileApi {
        val okHttpClient = buildOkHttpClient(tokenStore)
        val retrofit = buildRetrofit(baseUrl, okHttpClient)
        return retrofit.create(LobsterProfileApi::class.java)
    }

    private fun buildRetrofit(baseUrl: String, okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun buildOkHttpClient(tokenStore: CloudDeviceTokenStore): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(DeviceTokenInterceptor(tokenStore))
            .build()
    }
}

/**
 * 设备 token 拦截器：除 /register 与 /token/refresh 外，所有请求自动注入 Bearer token。
 * 401 由调用层处理（refresh + retry），拦截器只负责注入。
 */
class DeviceTokenInterceptor(
    private val tokenStore: CloudDeviceTokenStore,
) : Interceptor {

    companion object {
        private const val TAG = "PokeClaw/DeviceTokenInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        val url = request.url.toString()
        if (shouldAddAuth(url)) {
            val token = tokenStore.snapshot()?.deviceToken
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
                XLog.d(TAG, "intercept: 已注入 deviceToken (前 8 位=${token.take(8)}...)")
            }
        }
        return chain.proceed(builder.build())
    }

    private fun shouldAddAuth(url: String): Boolean {
        return !url.contains("/register") && !url.contains("/token/refresh")
    }
}
