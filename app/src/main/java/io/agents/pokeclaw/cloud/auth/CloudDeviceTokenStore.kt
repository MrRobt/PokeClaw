// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 设备端 JWT 令牌安全存储：密钥在 Android Keystore，磁盘只保存加密载荷。

package io.agents.pokeclaw.cloud.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import io.agents.pokeclaw.utils.XLog
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 端云设备令牌快照。 */
data class CloudDeviceTokenSnapshot(
    val deviceToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
) {
    fun hasDeviceToken(nowMillis: Long = System.currentTimeMillis()): Boolean =
        deviceToken.isNotBlank() && expiresAtMillis > nowMillis

    fun shouldRefresh(nowMillis: Long = System.currentTimeMillis(), refreshWindowMillis: Long = 10 * 60 * 1000L): Boolean =
        refreshToken.isNotBlank() && expiresAtMillis - nowMillis <= refreshWindowMillis
}

/**
 * 给定 token 字符串，补齐一次 Bearer 前缀；已有前缀则原样返回。
 *
 * 用法：`"abc-123".asBearerToken()` → `"Bearer abc-123"`；
 *      `"Bearer abc-123".asBearerToken()` → `"Bearer abc-123"`。
 */
fun String.asBearerToken(): String =
    if (startsWith("Bearer ")) this else "Bearer $this"

/** 令牌存储抽象，方便后续替换为硬件强安全实现或联调内存实现。 */
interface CloudDeviceTokenStore {
    fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int, nowMillis: Long = System.currentTimeMillis())
    fun updateDeviceToken(deviceToken: String, expiresInSeconds: Int, nowMillis: Long = System.currentTimeMillis())
    fun snapshot(): CloudDeviceTokenSnapshot?
    fun clear()
    /**
     * Force-invalidate all stored tokens + expiresAt.
     *
     * Caller is responsible for re-registering the device.
     * 与 [clear] 的区别：invalidate 用于认证失败后强制清除，logging 更醒目。
     */
    fun invalidate()
}

/**
 * Android Keystore 版本的设备令牌存储。
 *
 * 这里不把 token 明文写入 SharedPreferences；SharedPreferences 中只保存 AES-GCM 的 IV 与密文。
 */
class AndroidKeystoreCloudDeviceTokenStore(
    context: Context,
) : CloudDeviceTokenStore {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int, nowMillis: Long) {
        require(deviceToken.isNotBlank()) { "设备令牌不能为空" }
        require(refreshToken.isNotBlank()) { "刷新令牌不能为空" }
        val expiresAt = nowMillis + expiresInSeconds.coerceAtLeast(0) * 1000L
        prefs.edit()
            .putString(KEY_DEVICE_TOKEN, encrypt(deviceToken))
            .putString(KEY_REFRESH_TOKEN, encrypt(refreshToken))
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
        XLog.i(TAG, "saveTokens: device token saved, expiresAt=$expiresAt")
    }

    override fun updateDeviceToken(deviceToken: String, expiresInSeconds: Int, nowMillis: Long) {
        require(deviceToken.isNotBlank()) { "设备令牌不能为空" }
        val expiresAt = nowMillis + expiresInSeconds.coerceAtLeast(0) * 1000L
        prefs.edit()
            .putString(KEY_DEVICE_TOKEN, encrypt(deviceToken))
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
        XLog.i(TAG, "updateDeviceToken: device token refreshed, expiresAt=$expiresAt")
    }

    override fun snapshot(): CloudDeviceTokenSnapshot? {
        val encryptedDeviceToken = prefs.getString(KEY_DEVICE_TOKEN, null) ?: return null
        val encryptedRefreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return try {
            CloudDeviceTokenSnapshot(
                deviceToken = decrypt(encryptedDeviceToken),
                refreshToken = decrypt(encryptedRefreshToken),
                expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
            )
        } catch (e: Exception) {
            XLog.e(TAG, "snapshot: failed to decrypt cloud device token, clearing invalid storage", e)
            clear()
            null
        }
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
        XLog.i(TAG, "clear: cloud device tokens cleared")
    }

    override fun invalidate() {
        prefs.edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
        XLog.w(TAG, "invalidate: cloud device tokens FORCE-INVALIDATED — caller must re-register")
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return encode(iv) + ":" + encode(cipherText)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2) { "加密载荷格式错误" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, decode(parts[0])))
        return String(cipher.doFinal(decode(parts[1])), StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance("AES", ANDROID_KEY_STORE)
        val builder = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val TAG = "PokeClaw/CloudDeviceTokenStore"
        private const val PREFS_NAME = "pokeclaw_cloud_device_tokens"
        private const val KEY_DEVICE_TOKEN = "device_token_encrypted"
        private const val KEY_REFRESH_TOKEN = "refresh_token_encrypted"
        private const val KEY_EXPIRES_AT = "expires_at_millis"
        private const val KEY_ALIAS = "pokeclaw_cloud_device_token_aes"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
