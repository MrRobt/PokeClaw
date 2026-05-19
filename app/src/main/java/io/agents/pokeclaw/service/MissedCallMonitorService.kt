// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.agents.pokeclaw.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 漏接来电监听服务
 * 
 * 功能：
 * 1. 监听电话状态变化
 * 2. 检测漏接来电事件
 * 3. 触发自动跟进流程
 * 4. 提供状态通知
 */
class MissedCallMonitorService(
    private val context: Context
) {
    companion object {
        private const val TAG = "MissedCallMonitor"
        private const val NOTIFICATION_CHANNEL_ID = "missed_call_channel"
        private const val NOTIFICATION_ID = 9001
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 存储正在进行的通话
    private val activeCalls = ConcurrentHashMap<String, CallSession>()
    
    // 漏接来电处理器
    private var missedCallHandler: MissedCallHandler? = null
    
    // 配置
    private var config: FollowUpConfig = FollowUpConfig()
    
    // 状态流
    private val _monitoringState = MutableStateFlow<MonitoringState>(MonitoringState.Idle)
    val monitoringState: StateFlow<MonitoringState> = _monitoringState
    
    // 漏接来电事件流
    private val _missedCallEvents = MutableStateFlow<MissedCallEvent?>(null)
    val missedCallEvents: StateFlow<MissedCallEvent?> = _missedCallEvents

    /**
     * 监控状态
     */
    sealed class MonitoringState {
        object Idle : MonitoringState()
        object Monitoring : MonitoringState()
        data class Error(val message: String) : MonitoringState()
    }

    /**
     * 通话会话
     */
    private data class CallSession(
        val callId: String,
        val phoneNumber: String,
        val startTime: Long,
        val callerName: String? = null,
        var state: CallState = CallState.RINGING
    ) {
        enum class CallState {
            RINGING, CONNECTED, DISCONNECTED, MISSED
        }
    }

    /**
     * 电话状态监听器
     */
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            handleCallStateChanged(state, phoneNumber)
        }
    }

    /**
     * 启动监控
     */
    fun startMonitoring(handler: MissedCallHandler, config: FollowUpConfig) {
        if (_monitoringState.value == MonitoringState.Monitoring) {
            android.util.Log.d(TAG, "已经在监控中")
            return
        }

        // 检查权限
        if (!hasRequiredPermissions()) {
            _monitoringState.value = MonitoringState.Error("缺少必要权限: READ_PHONE_STATE")
            return
        }

        this.missedCallHandler = handler
        this.config = config

        try {
            // 注册电话状态监听
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            
            _monitoringState.value = MonitoringState.Monitoring
            android.util.Log.i(TAG, "漏接来电监控已启动")
            
            showNotification("漏接来电监控已开启", "将自动检测漏接来电并发送跟进消息")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "启动监控失败", e)
            _monitoringState.value = MonitoringState.Error("启动失败: ${e.message}")
        }
    }

    /**
     * 停止监控
     */
    fun stopMonitoring() {
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            _monitoringState.value = MonitoringState.Idle
            android.util.Log.i(TAG, "漏接来电监控已停止")
            
            cancelNotification()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "停止监控失败", e)
        }
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: FollowUpConfig) {
        this.config = newConfig
        android.util.Log.d(TAG, "配置已更新: enabled=${newConfig.enabled}")
    }

    /**
     * 处理电话状态变化
     */
    private fun handleCallStateChanged(state: Int, phoneNumber: String?) {
        val normalizedNumber = phoneNumber?.let { normalizePhoneNumber(it) }
        
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // 来电响铃
                normalizedNumber?.let { number ->
                    val callId = UUID.randomUUID().toString()
                    val session = CallSession(
                        callId = callId,
                        phoneNumber = number,
                        startTime = System.currentTimeMillis(),
                        callerName = lookupContactName(number)
                    )
                    activeCalls[callId] = session
                    android.util.Log.d(TAG, "来电响铃: $number, callId=$callId")
                }
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // 通话中（接听或拨出）
                // 将最早的一个响铃通话标记为已连接
                activeCalls.values
                    .filter { it.state == CallSession.CallState.RINGING }
                    .minByOrNull { it.startTime }
                    ?.let { session ->
                        session.state = CallSession.CallState.CONNECTED
                        android.util.Log.d(TAG, "通话已连接: ${session.phoneNumber}")
                    }
            }
            
            TelephonyManager.CALL_STATE_IDLE -> {
                // 空闲状态（通话结束）
                handleCallEnded()
            }
        }
    }

    /**
     * 处理通话结束
     */
    private fun handleCallEnded() {
        val now = System.currentTimeMillis()
        
        // 检查所有通话会话
        activeCalls.values.forEach { session ->
            when (session.state) {
                CallSession.CallState.RINGING -> {
                    // 响铃中结束 = 漏接
                    session.state = CallSession.CallState.MISSED
                    val ringDuration = now - session.startTime
                    
                    // 过滤太短的响铃（可能是骚扰电话或误拨）
                    if (ringDuration >= 2000) {
                        val event = MissedCallEvent(
                            phoneNumber = session.phoneNumber,
                            callerName = session.callerName,
                            callTime = session.startTime,
                            ringDurationMs = ringDuration,
                            missedCallId = session.callId
                        )
                        
                        // 检查是否应该处理
                        if (shouldProcessMissedCall(event)) {
                            serviceScope.launch {
                                _missedCallEvents.value = event
                                missedCallHandler?.onMissedCall(event)
                            }
                        }
                    }
                }
                CallSession.CallState.CONNECTED -> {
                    // 正常通话结束
                    android.util.Log.d(TAG, "通话正常结束: ${session.phoneNumber}")
                }
                else -> { /* 忽略 */ }
            }
        }
        
        // 清理会话
        activeCalls.clear()
    }

    /**
     * 判断是否应该处理漏接来电
     */
    private fun shouldProcessMissedCall(event: MissedCallEvent): Boolean {
        // 检查功能是否启用
        if (!config.enabled) {
            android.util.Log.d(TAG, "功能未启用，忽略漏接: ${event.phoneNumber}")
            return false
        }
        
        // 检查业务时间
        if (!config.isBusinessHours()) {
            android.util.Log.d(TAG, "非业务时间，忽略漏接: ${event.phoneNumber}")
            return false
        }
        
        // 检查排除列表
        if (config.excludeContacts.contains(event.phoneNumber) || 
            config.excludeContacts.contains(event.callerName)) {
            android.util.Log.d(TAG, "在排除列表中，忽略漏接: ${event.phoneNumber}")
            return false
        }
        
        return true
    }

    /**
     * 检查必要权限
     */
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, 
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 规范化电话号码
     */
    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^+0-9]"), "")
    }

    /**
     * 查询联系人名称
     */
    private fun lookupContactName(phoneNumber: String): String? {
        // TODO: 实现联系人查询
        // 需要 READ_CONTACTS 权限
        return null
    }

    /**
     * 显示通知
     */
    private fun showNotification(title: String, content: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 取消通知
     */
    private fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * 释放资源
     */
    fun release() {
        stopMonitoring()
        serviceScope.cancel()
    }
}
