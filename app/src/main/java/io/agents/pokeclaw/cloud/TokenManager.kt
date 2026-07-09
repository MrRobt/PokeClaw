package io.agents.pokeclaw.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import io.agents.pokeclaw.utils.XLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Token管理器
 * 负责 deviceToken 和 refreshToken 的安全存储与自动刷新
 * Token 加密存储在 Android Keystore 中
 */
class TokenManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PokeClaw/TokenManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pokeclaw_device_token_key"
        private const val PREFS_NAME = "pokeclaw_token_prefs"
        private const val PREFS_KEY_DEVICE_TOKEN = "encrypted_device_token"
        private const val PREFS_KEY_REFRESH_TOKEN = "encrypted_refresh_token"
        private const val PREFS_KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val PREFS_KEY_DEVICE_ID = "device_id"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // 内存中缓存的解密后token
    @Volatile
    private var cachedDeviceToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    /**
     * 初始化加密密钥
     */
    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    /**
     * 生成AES-GCM密钥
     */
    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        keyGenerator.generateKey()
        XLog.i(TAG, "密钥生成成功")
    }

    /**
     * 获取密钥
     */
    private fun getSecretKey(): SecretKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    /**
     * 加密数据
     */
    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // IV + ciphertext 拼接后 Base64 编码
        return android.util.Base64.encodeToString(iv + ciphertext, android.util.Base64.DEFAULT)
    }

    /**
     * 解密数据
     */
    private fun decrypt(encrypted: String): String? {
        try {
            val combined = android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT)
            // 提取 IV (前12字节) 和 ciphertext
            val iv = combined.slice(0..11).toByteArray()
            val ciphertext = combined.slice(12 until combined.size).toByteArray()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            XLog.e(TAG, "解密失败", e)
            return null
        }
    }

    /**
     * 原子保存 deviceToken 和 refreshToken
     * 首次注册时必须使用此方法同时保存两个 token
     */
    fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int) {
        require(deviceToken.isNotBlank()) { "设备令牌不能为空" }
        require(refreshToken.isNotBlank()) { "刷新令牌不能为空" }

        val encryptedDeviceToken = encrypt(deviceToken)
        val encryptedRefreshToken = encrypt(refreshToken)
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000

        prefs.edit {
            putString(PREFS_KEY_DEVICE_TOKEN, encryptedDeviceToken)
            putString(PREFS_KEY_REFRESH_TOKEN, encryptedRefreshToken)
            putLong(PREFS_KEY_TOKEN_EXPIRES_AT, expiresAt)
        }
        cachedDeviceToken = deviceToken
        cachedRefreshToken = refreshToken
        XLog.i(TAG, "Tokens 已原子保存，过期时间: ${expiresInSeconds}s")
    }

    /**
     * 更新 deviceToken（Token 刷新时使用）
     * 保留原 refreshToken 不变
     *
     * @param deviceToken 新的设备令牌
     * @param expiresInSeconds 过期时间（秒）
     * @param nowMillis 当前时间戳（毫秒），用于测试时可注入
     */
    fun updateDeviceToken(deviceToken: String, expiresInSeconds: Int, nowMillis: Long = System.currentTimeMillis()) {
        require(deviceToken.isNotBlank()) { "设备令牌不能为空" }
        // 必须有历史 refreshToken 才能只更新 deviceToken
        val existingRefreshToken = getRefreshToken()
            ?: throw IllegalStateException("更新 deviceToken 前必须先完成注册，调用 saveTokens()")

        val encryptedDeviceToken = encrypt(deviceToken)
        val expiresAt = nowMillis + expiresInSeconds * 1000

        prefs.edit {
            putString(PREFS_KEY_DEVICE_TOKEN, encryptedDeviceToken)
            // 保留原 refreshToken 不变
            putLong(PREFS_KEY_TOKEN_EXPIRES_AT, expiresAt)
        }
        cachedDeviceToken = deviceToken
        XLog.i(TAG, "DeviceToken 已更新（refreshToken 保持不变），过期时间: ${expiresInSeconds}s")
    }

    /**
     * 保存设备Token（兼容方法，有历史记录时保留原 refreshToken）
     * 注意：首次注册时必须使用 saveTokens()
     */
    fun saveDeviceToken(token: String, expiresInSeconds: Int) {
        val existingRefreshToken = getRefreshToken()
        if (existingRefreshToken == null) {
            throw IllegalStateException("首次保存令牌时必须使用 saveTokens(deviceToken, refreshToken, ...) 同时保存两个令牌")
        }
        // 有历史记录时，使用原子保存（保留原 refreshToken）
        saveTokens(token, existingRefreshToken, expiresInSeconds)
    }

    /**
     * 保存刷新Token（兼容方法，有历史记录时保留原 deviceToken）
     * 注意：首次注册时必须使用 saveTokens()
     */
    fun saveRefreshToken(token: String) {
        val existingDeviceToken = getDeviceToken()
        val expiresAt = prefs.getLong(PREFS_KEY_TOKEN_EXPIRES_AT, 0)
        val expiresInSeconds = ((expiresAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)

        if (existingDeviceToken == null) {
            throw IllegalStateException("首次保存令牌时必须使用 saveTokens(deviceToken, refreshToken, ...) 同时保存两个令牌")
        }
        // 有历史记录时，使用原子保存（保留原 deviceToken）
        saveTokens(existingDeviceToken, token, expiresInSeconds)
    }

    /**
     * 保存设备ID
     */
    fun saveDeviceId(deviceId: String) {
        prefs.edit {
            putString(PREFS_KEY_DEVICE_ID, deviceId)
        }
        XLog.i(TAG, "DeviceId 已保存: $deviceId")
    }

    /**
     * 获取设备ID
     */
    fun getDeviceId(): String? {
        return prefs.getString(PREFS_KEY_DEVICE_ID, null)
    }

    /**
     * 获取设备Token（自动解密）
     */
    fun getDeviceToken(): String? {
        // 优先使用内存缓存
        cachedDeviceToken?.let { return it }

        // 从存储中读取并解密
        val encrypted = prefs.getString(PREFS_KEY_DEVICE_TOKEN, null)
        return encrypted?.let {
            decrypt(it).also { decrypted ->
                cachedDeviceToken = decrypted
            }
        }
    }

    /**
     * 获取刷新Token
     */
    fun getRefreshToken(): String? {
        cachedRefreshToken?.let { return it }

        val encrypted = prefs.getString(PREFS_KEY_REFRESH_TOKEN, null)
        return encrypted?.let {
            decrypt(it).also { decrypted ->
                cachedRefreshToken = decrypted
            }
        }
    }

    /**
     * 检查Token是否过期
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = prefs.getLong(PREFS_KEY_TOKEN_EXPIRES_AT, 0)
        // 提前5分钟认为过期，留足刷新时间
        return System.currentTimeMillis() + 5 * 60 * 1000 >= expiresAt
    }

    /**
     * 是否需要刷新Token
     */
    fun shouldRefreshToken(): Boolean {
        return isTokenExpired() && getRefreshToken() != null
    }

    /**
     * 清除所有Token
     */
    fun clearTokens() {
        prefs.edit {
            remove(PREFS_KEY_DEVICE_TOKEN)
            remove(PREFS_KEY_REFRESH_TOKEN)
            remove(PREFS_KEY_TOKEN_EXPIRES_AT)
        }
        cachedDeviceToken = null
        cachedRefreshToken = null
        XLog.i(TAG, "Token 已清除")
    }

    /**
     * 检查是否已注册
     */
    fun isRegistered(): Boolean {
        return getDeviceToken() != null && getDeviceId() != null
    }
}
