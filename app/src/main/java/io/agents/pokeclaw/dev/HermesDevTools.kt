package io.agents.pokeclaw.dev

import android.util.Log
import io.agents.pokeclaw.BuildConfig
import io.agents.pokeclaw.utils.XLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hermes开发工具类
 * 用于开发期间的调试和状态监控
 */
object HermesDevTools {
    
    private const val TAG = "HermesDev"
    private const val HERMES_MARKER = "[HERMES]"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * 记录开发日志
     */
    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = dateFormat.format(Date())
        val fullMessage = "$HERMES_MARKER [$timestamp] $message"
        
        when (level) {
            LogLevel.VERBOSE -> XLog.v(TAG, fullMessage)
            LogLevel.DEBUG -> XLog.d(TAG, fullMessage)
            LogLevel.INFO -> XLog.i(TAG, fullMessage)
            LogLevel.WARN -> XLog.w(TAG, fullMessage)
            LogLevel.ERROR -> XLog.e(TAG, fullMessage)
        }
    }
    
    /**
     * 记录函数执行时间
     */
    inline fun <T> measureTime(label: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block().also {
                val duration = System.currentTimeMillis() - start
                log("$label 执行耗时: ${duration}ms")
            }
        } catch (e: Exception) {
            log("$label 执行失败: ${e.message}", LogLevel.ERROR)
            throw e
        }
    }
    
    /**
     * 获取构建信息
     */
    fun getBuildInfo(): BuildInfo {
        return BuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = if (BuildConfig.DEBUG) "debug" else "release",
            buildTime = dateFormat.format(Date(BuildConfig.BUILD_TIMESTAMP)),
            gitInfo = BuildConfig.VERSION_INFO
        )
    }
    
    /**
     * 检查是否为开发模式
     */
    fun isDevMode(): Boolean {
        return BuildConfig.DEBUG
    }
    
    /**
     * 打印应用状态摘要
     */
    fun printAppStatusSummary() {
        log("=== PokeClaw 状态摘要 ===")
        log("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        log("构建: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
        log("Git: ${BuildConfig.VERSION_INFO}")
        log("========================")
    }
    
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    data class BuildInfo(
        val versionName: String,
        val versionCode: Int,
        val buildType: String,
        val buildTime: String,
        val gitInfo: String
    )
}
