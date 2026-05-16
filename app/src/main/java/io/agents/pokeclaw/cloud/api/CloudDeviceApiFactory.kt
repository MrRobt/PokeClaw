// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云设备 API 客户端工厂与鉴权拦截器。

package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.utils.XLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 给设备端 API 自动注入 Bearer Token。
 *
 * 注册和刷新令牌请求不会强制补令牌；如果调用方已显式设置 Authorization，则保持调用方传入值。
 */
class CloudDeviceAuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header("Authorization") != null) {
            return chain.proceed(original)
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
        if (token == null) {
            XLog.d(TAG, "intercept: no cloud device token for ${original.url.encodedPath}")
            return chain.proceed(original)
        }

        val authed = original.newBuilder()
            .header("Authorization", token.asBearerToken())
            .build()
        XLog.d(TAG, "intercept: Authorization header injected for ${original.url.encodedPath}")
        return chain.proceed(authed)
    }

    companion object {
        private const val TAG = "PokeClaw/CloudDeviceAuth"
    }
}

object CloudDeviceApiFactory {
    fun create(
        baseUrl: String,
        tokenProvider: () -> String? = { null },
        extraInterceptors: List<Interceptor> = emptyList(),
    ): CloudDeviceApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        XLog.i(TAG, "create: cloud device api baseUrl=$normalizedBaseUrl")

        val logging = HttpLoggingInterceptor { message ->
            // 日志拦截器可能包含 URL 和状态码；避免记录 Authorization 或 token 正文。
            if (!message.contains("Authorization", ignoreCase = true)) {
                XLog.d(TAG, "http: $message")
            }
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(CloudDeviceAuthInterceptor(tokenProvider))
            .apply { extraInterceptors.forEach { addInterceptor(it) } }
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudDeviceApi::class.java)
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "云端地址必须以 http:// 或 https:// 开头" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private const val TAG = "PokeClaw/CloudDeviceApiFactory"
}
