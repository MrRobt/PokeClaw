// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.agents.pokeclaw.service.DefaultMissedCallHandler
import io.agents.pokeclaw.service.FollowUpConfig
import io.agents.pokeclaw.service.FollowUpHistoryManager
import io.agents.pokeclaw.service.FollowUpMessage
import io.agents.pokeclaw.service.FollowUpMessageSender
import io.agents.pokeclaw.service.FollowUpStatus
import io.agents.pokeclaw.service.MissedCallMonitorService
import kotlinx.coroutines.launch

/**
 * 漏接来电设置Fragment
 * 
 * 提供UI让用户配置：
 * 1. 启用/禁用自动跟进
 * 2. 自动发送或手动确认
 * 3. 默认消息模板
 * 4. 发送延迟时间
 * 5. 发送渠道偏好
 * 6. 业务时间设置
 * 7. 查看历史记录
 */
class MissedCallSettingsFragment : Fragment() {

    private var monitorService: MissedCallMonitorService? = null
    private var handler: DefaultMissedCallHandler? = null
    private val historyManager = FollowUpHistoryManager()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MissedCallSettingsScreen(
                    historyManager = historyManager,
                    onStartMonitoring = { startMonitoring() },
                    onStopMonitoring = { stopMonitoring() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorService?.release()
        handler?.release()
    }

    private fun startMonitoring() {
        // 检查权限
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS
        )
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            return
        }

        // 初始化服务
        monitorService = MissedCallMonitorService(requireContext())
        handler = DefaultMissedCallHandler(
            context = requireContext(),
            messageSender = createMessageSender(),
            historyManager = historyManager
        )

        val config = FollowUpConfig(
            enabled = true,
            autoSend = false,  // 默认需要用户确认
            defaultMessage = "抱歉，刚才在忙没有接到您的电话，请问有什么可以帮您？"
        )

        handler?.updateConfig(config)
        monitorService?.startMonitoring(handler!!, config)
    }

    private fun stopMonitoring() {
        monitorService?.stopMonitoring()
    }

    private fun createMessageSender(): FollowUpMessageSender {
        return object : FollowUpMessageSender {
            override suspend fun sendSms(phoneNumber: String, message: String): Boolean {
                // 实际发送逻辑在handler中处理
                return true
            }

            override suspend fun sendViaWhatsApp(phoneNumber: String, message: String): Boolean {
                // 实际发送逻辑在handler中处理
                return true
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissedCallSettingsScreen(
    historyManager: FollowUpHistoryManager,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var autoSend by remember { mutableStateOf(false) }
    var defaultMessage by remember { mutableStateOf("抱歉，刚才在忙没有接到您的电话，请问有什么可以帮您？") }
    var delaySeconds by remember { mutableFloatStateOf(3f) }
    var smsPreferred by remember { mutableStateOf(true) }
    var businessHoursOnly by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    
    val messages by historyManager.allMessages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("漏接来电自动跟进") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 主开关
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "漏接来电自动跟进",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "检测漏接来电并自动发送跟进消息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            if (it) onStartMonitoring() else onStopMonitoring()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 设置选项
            if (enabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 自动发送开关
                        SettingSwitchItem(
                            title = "自动发送消息",
                            subtitle = "关闭时需要手动确认每条消息",
                            checked = autoSend,
                            onCheckedChange = { autoSend = it }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 消息模板
                        OutlinedTextField(
                            value = defaultMessage,
                            onValueChange = { defaultMessage = it },
                            label = { Text("默认消息模板") },
                            placeholder = { Text("输入自动发送的消息内容") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )

                        Text(
                            "可用变量: {name} - 联系人名称, {time} - 来电时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        // 发送延迟
                        Text(
                            "发送延迟: ${delaySeconds.toInt()}秒",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = delaySeconds,
                            onValueChange = { delaySeconds = it },
                            valueRange = 0f..30f,
                            steps = 29
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 发送渠道
                        SettingSwitchItem(
                            title = "优先使用SMS",
                            subtitle = "使用系统短信发送，更可靠",
                            checked = smsPreferred,
                            onCheckedChange = { smsPreferred = it }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 业务时间
                        SettingSwitchItem(
                            title = "仅工作时间发送",
                            subtitle = "9:00 - 18:00 之外不自动发送",
                            checked = businessHoursOnly,
                            onCheckedChange = { businessHoursOnly = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 历史记录按钮
                OutlinedButton(
                    onClick = { showHistory = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看历史记录 (${messages.size})")
                }
            }

            // 权限提示
            if (!enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "所需权限",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• READ_PHONE_STATE - 监听电话状态")
                        Text("• SEND_SMS - 发送跟进短信")
                        Text("• READ_CONTACTS - 识别联系人名称（可选）")
                    }
                }
            }
        }
    }

    // 历史记录对话框
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("跟进历史") },
            text = {
                if (messages.isEmpty()) {
                    Text("暂无记录")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        messages.take(20).forEach { msg ->
                            HistoryItem(message = msg)
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun HistoryItem(message: FollowUpMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val statusColor = when (message.status) {
            FollowUpStatus.SENT -> MaterialTheme.colorScheme.primary
            FollowUpStatus.FAILED -> MaterialTheme.colorScheme.error
            FollowUpStatus.SENDING -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outline
        }
        
        val statusText = when (message.status) {
            FollowUpStatus.SENT -> "已发送"
            FollowUpStatus.FAILED -> "失败"
            FollowUpStatus.SENDING -> "发送中"
            FollowUpStatus.PENDING -> "待发送"
            FollowUpStatus.CANCELLED -> "已取消"
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                message.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                message.messageContent.take(30) + if (message.messageContent.length > 30) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
    }
}
