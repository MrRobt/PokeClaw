package io.agents.pokeclaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import io.agents.pokeclaw.R
import io.agents.pokeclaw.ui.chat.ComposeChatActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 未接来电自动跟进服务
 * 监听电话状态变化，检测未接来电并自动发送跟进消息
 * 
 * @author Hermes Dev Team
 * @since 2026-05-20
 */
class MissedCallService : Service() {

    companion object {
        private const val TAG = "MissedCallService"
        private const val CHANNEL_ID = "missed_call_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "io.agents.pokeclaw.action.START_MISSED_CALL_SERVICE"
        private const val ACTION_STOP = "io.agents.pokeclaw.action.STOP_MISSED_CALL_SERVICE"

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, MissedCallService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, MissedCallService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var telephonyManager: TelephonyManager
    private var phoneStateListener: PhoneStateListener? = null
    private val callHistory = ConcurrentHashMap<String, CallInfo>()
    private var lastState = TelephonyManager.CALL_STATE_IDLE

    data class CallInfo(
        val number: String,
        val startTime: Long,
        var endTime: Long? = null,
        var wasAnswered: Boolean = false,
        var followUpSent: Boolean = false
    )

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
        HermesDevTools.log("MissedCallService", "Service created")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startCallMonitoring()
                HermesDevTools.log("MissedCallService", "Service started")
            }
            ACTION_STOP -> {
                stopCallMonitoring()
                stopSelf()
                HermesDevTools.log("MissedCallService", "Service stopped")
            }
        }
        return START_STICKY
    }

    /**
     * 开始监听电话状态
     */
    private fun startCallMonitoring() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallStateChanged(state, phoneNumber)
            }
        }

        phoneStateListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_CALL_STATE)
        }

        // 注册额外的广播接收器作为备份
        registerCallReceiver()
    }

    /**
     * 停止监听电话状态
     */
    private fun stopCallMonitoring() {
        phoneStateListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneStateListener = null

        try {
            unregisterReceiver(callReceiver)
        } catch (e: Exception) {
            // 接收器可能未注册
        }

        serviceScope.cancel()
    }

    /**
     * 处理电话状态变化
     */
    private fun handleCallStateChanged(state: Int, phoneNumber: String?) {
        val normalizedNumber = phoneNumber?.replace("[^0-9+]".toRegex(), "") ?: "unknown"
        
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // 来电响铃
                HermesDevTools.log("MissedCallService", "Incoming call from: $normalizedNumber")
                callHistory[normalizedNumber] = CallInfo(
                    number = normalizedNumber,
                    startTime = System.currentTimeMillis()
                )
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // 通话中（接听）
                callHistory[normalizedNumber]?.wasAnswered = true
                HermesDevTools.log("MissedCallService", "Call answered: $normalizedNumber")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // 空闲状态（通话结束）
                handleCallEnded(normalizedNumber)
            }
        }
        
        lastState = state
    }

    /**
     * 处理通话结束
     */
    private fun handleCallEnded(phoneNumber: String) {
        val callInfo = callHistory[phoneNumber] ?: return
        callInfo.endTime = System.currentTimeMillis()

        // 判断是否未接来电
        if (!callInfo.wasAnswered && !callInfo.followUpSent) {
            val duration = callInfo.endTime!! - callInfo.startTime
            
            // 响铃时间超过3秒才算有效未接来电
            if (duration > 3000) {
                HermesDevTools.log("MissedCallService", "Missed call detected: $phoneNumber (duration: ${duration}ms)")
                handleMissedCall(callInfo)
            }
        }
    }

    /**
     * 处理未接来电 - 自动跟进
     */
    private fun handleMissedCall(callInfo: CallInfo) {
        serviceScope.launch {
            try {
                // 显示通知
                showMissedCallNotification(callInfo)
                
                // 获取联系人名称（如果有权限）
                val contactName = getContactName(callInfo.number)
                
                // 延迟5秒后发送自动跟进消息
                delay(5000)
                
                // 通过AgentService发送跟进消息
                sendFollowUpMessage(callInfo.number, contactName)
                
                callInfo.followUpSent = true
                
                // 发送广播通知UI层
                broadcastMissedCallHandled(callInfo)
                
            } catch (e: Exception) {
                HermesDevTools.log("MissedCallService", "Error handling missed call: ${e.message}")
            }
        }
    }

    /**
     * 发送跟进消息
     */
    private suspend fun sendFollowUpMessage(phoneNumber: String, contactName: String?) {
        val displayName = contactName ?: phoneNumber
        val message = buildFollowUpMessage(displayName)
        
        HermesDevTools.log("MissedCallService", "Sending follow-up to: $displayName")
        
        // 这里通过TaskOrchestrator启动一个任务来发送消息
        // 优先使用SMS API，而不是WhatsApp自动化
        try {
            // 创建任务Intent
            val taskIntent = Intent(this, ComposeChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("missed_call_number", phoneNumber)
                putExtra("missed_call_name", displayName)
                putExtra("auto_follow_up", true)
                putExtra("follow_up_message", message)
            }
            
            // 发送广播通知AgentService
            val broadcastIntent = Intent("io.agents.pokeclaw.MISSED_CALL_FOLLOW_UP").apply {
                putExtra("phone_number", phoneNumber)
                putExtra("contact_name", displayName)
                putExtra("message", message)
                putExtra("timestamp", System.currentTimeMillis())
            }
            sendBroadcast(broadcastIntent)
            
        } catch (e: Exception) {
            HermesDevTools.log("MissedCallService", "Failed to send follow-up: ${e.message}")
        }
    }

    /**
     * 构建跟进消息内容
     */
    private fun buildFollowUpMessage(contactName: String): String {
        val templates = listOf(
            "您好，刚刚错过了您的来电。请问有什么可以帮助您的吗？",
            "抱歉刚才没接到您的电话，请问有什么急事吗？",
            "刚刚在忙没接到您的来电，看到请回复。",
            "Hi, I missed your call just now. Is there anything urgent?"
        )
        
        // 可以添加更智能的消息生成逻辑
        return templates.random()
    }

    /**
     * 获取联系人名称
     */
    private fun getContactName(phoneNumber: String): String? {
        // TODO: 实现联系人查询
        return null
    }

    /**
     * 显示未接来电通知
     */
    private fun showMissedCallNotification(callInfo: CallInfo) {
        val intent = Intent(this, ComposeChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("missed_call_number", callInfo.number)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("未接来电")
            .setContentText("来自 ${callInfo.number} 的未接来电")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_send, "发送跟进", createFollowUpPendingIntent(callInfo))
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(callInfo.number.hashCode(), notification)
    }

    /**
     * 创建发送跟进消息的PendingIntent
     */
    private fun createFollowUpPendingIntent(callInfo: CallInfo): PendingIntent {
        val intent = Intent("io.agents.pokeclaw.ACTION_SEND_FOLLOW_UP").apply {
            putExtra("phone_number", callInfo.number)
        }
        return PendingIntent.getBroadcast(
            this, callInfo.number.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 广播未接来电处理完成
     */
    private fun broadcastMissedCallHandled(callInfo: CallInfo) {
        val intent = Intent("io.agents.pokeclaw.MISSED_CALL_HANDLED").apply {
            putExtra("phone_number", callInfo.number)
            putExtra("follow_up_sent", callInfo.followUpSent)
            putExtra("timestamp", System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "未接来电自动跟进",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "监听未接来电并自动发送跟进消息"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, ComposeChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("未接来电监听服务")
            .setContentText("正在监听未接来电...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 额外的广播接收器作为备份
     */
    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    
                    when (state) {
                        TelephonyManager.EXTRA_STATE_RINGING -> {
                            HermesDevTools.log("MissedCallService", "Broadcast: Ringing from $number")
                        }
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                            HermesDevTools.log("MissedCallService", "Broadcast: Offhook")
                        }
                        TelephonyManager.EXTRA_STATE_IDLE -> {
                            HermesDevTools.log("MissedCallService", "Broadcast: Idle")
                        }
                    }
                }
            }
        }
    }

    private fun registerCallReceiver() {
        val filter = IntentFilter().apply {
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        }
        registerReceiver(callReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCallMonitoring()
        HermesDevTools.log("MissedCallService", "Service destroyed")
    }
}
