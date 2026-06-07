// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云 Retrofit 工厂 — 构造 OkHttp + Retrofit + DeviceApi。
// 与 CloudClient.kt 拆开：CloudClient 用于聊天通道；本工厂专用于设备云端 5 端点。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
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

    fun buildDeviceApi(baseUrl: String, tokenStore: CloudDeviceTokenStore): DeviceApi {
        val okHttpClient = buildOkHttpClient(tokenStore)
        val retrofit = Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(DeviceApi::class.java)
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
        val url = request.url().toString()
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
