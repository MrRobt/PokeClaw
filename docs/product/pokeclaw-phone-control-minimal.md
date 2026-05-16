# PokeClaw 手机控制任务最小执行器设计

> 文档编号: CMP-1986-EXECUTOR
> 关联 Issue: CMP-1986, CMP-1940, CMP-1964
> 作者: 安卓小龙
> 日期: 2026-05-16

## 1. 目标

定义 PokeClaw 作为小龙虾端侧执行端，承接 dyq 云端下发的简单手机控制任务的最小闭环方案。

**核心原则：**
- 只做简单执行，不做复杂云端决策
- 端侧专注"怎么做"，云端决定"做什么"
- 失败可感知、可上报、可重试

## 2. 现有基础

### 2.1 云端通信层 (cloud/)
| 组件 | 路径 | 状态 |
|------|------|------|
| DeviceCloudClient | `cloud/DeviceCloudClient.kt` | ✅ 完整实现 |
| CloudNodeOrchestrator | `cloud/CloudNodeOrchestrator.kt` | ✅ 完整实现 |
| CloudEventQueue | `cloud/CloudEventQueue.kt` | ✅ 离线队列 |
| Token 管理 | `cloud/auth/CloudDeviceTokenStore.kt` | ✅ Keystore 存储 |

### 2.2 手机控制能力 (service/)
| 能力 | 实现位置 | 说明 |
|------|----------|------|
| 点击/长按 | `ClawAccessibilityService.performTap()` | 坐标级手势 |
| 滑动 | `ClawAccessibilityService.performSwipe()` | 路径手势 |
| 查找节点 | `ClawAccessibilityService.findNodesByText()` | 文本匹配 |
| 节点点击 | `ClawAccessibilityService.clickNode()` | 带降级策略 |
| 输入文字 | `ClawAccessibilityService.setNodeText()` | EditText 专用 |
| 屏幕树 | `ClawAccessibilityService.getScreenTree()` | 供 AI 分析 |

### 2.3 Agent 执行层 (agent/)
| 组件 | 路径 | 状态 |
|------|------|------|
| DefaultAgentService | `agent/DefaultAgentService.kt` | ✅ 工具调用循环 |
| ToolRegistry | `tool/ToolRegistry.kt` | ✅ 工具注册表 |
| CloudTaskExecutor | `cloud/CloudTaskExecutor.kt` | ⚠️ 占位实现 |

## 3. 最小动作样例定义

### 3.1 样例一: 打开应用 (open_app)

```yaml
动作名称: open_app
云端指令示例:
  command: '{"action":"open_app","package":"com.tencent.mm","wait_ms":3000}'
本地执行:
  1. 解析 package 和 wait_ms
  2. 调用工具 open_app(package="com.tencent.mm")
  3. 等待 wait_ms (默认 2000ms)
  4. 调用 get_screen_info 捕获屏幕状态
  5. 返回结果: {success: true, screenshot: "base64...", screen_tree: "..."}
失败场景:
  - 应用未安装 → errorCode: APP_NOT_INSTALLED
  - 启动失败 → errorCode: LAUNCH_FAILED
  - 无障碍服务未就绪 → errorCode: PERMISSION_MISSING
```

### 3.2 样例二: 点击元素 (click_element)

```yaml
动作名称: click_element
云端指令示例:
  command: '{"action":"click_element","strategy":"text","target":"通讯录","retry":3}'
本地执行:
  1. 解析 strategy (text/id/coordinate)
  2. 按策略查找元素
     - text: findNodesByText(target)
     - id: findNodesById(target)
     - coordinate: 直接解析 x,y
  3. 找到后 clickNode(node)
  4. 等待 500ms UI 稳定
  5. 返回屏幕状态作为证据
失败场景:
  - 未找到元素 → errorCode: ELEMENT_NOT_FOUND
  - 找到但点击失败 → errorCode: CLICK_FAILED (重试)
  - 重试耗尽 → errorCode: MAX_RETRY_EXCEEDED
```

### 3.3 样例三: 输入文字 (input_text)

```yaml
动作名称: input_text
云端指令示例:
  command: '{"action":"input_text","text":"你好，世界","pre_click":true}'
本地执行:
  1. 若 pre_click=true，先聚焦输入框 (点击一次)
  2. 等待 200ms
  3. 调用 input_text(text) 或 setNodeText(node, text)
  4. 返回: {success: true, input_length: 5}
失败场景:
  - 未找到输入框 → errorCode: ELEMENT_NOT_FOUND
  - 输入失败 → errorCode: INPUT_FAILED
```

## 4. 任务状态机

```
┌──────────┐     start      ┌──────────┐
│  PENDING   │──────────────▶│ RUNNING  │
│ (云端派发)│               │ (端侧执行)│
└──────────┘               └────┬─────┘
     ▲                          │
     │                          ▼
     │                    ┌──────────┐
     │         执行中异常  │ FAILED   │
     │         (可重试)   │ (失败)   │
     │                    └────┬─────┘
     │                         │ retry
     │                         ▼
     │                    ┌──────────┐
     │    重试次数耗尽或   │ RETRYING │────┐
     │    不可重试错误    │ (重试中)  │◀───┘
     │                    └──────────┘    │
     │                         │ success
     │                         ▼
     │                    ┌──────────┐
     └────────────────────│ SUCCESS  │
          上报结果         │ (成功)   │
                          └──────────┘

状态迁移说明:
- PENDING → RUNNING: 端侧收到任务开始执行
- RUNNING → SUCCESS: 执行完成且无错误
- RUNNING → FAILED: 执行异常且不可重试
- RUNNING → RETRYING: 执行失败但可重试
- RETRYING → RUNNING: 重试执行
- RETRYING → FAILED: 重试次数耗尽
- SUCCESS/FAILED → PENDING: 结果上报成功后云端确认
```

## 5. 错误码映射

| 错误码 | 来源 | 说明 | 可重试 | 上报字段 |
|--------|------|------|--------|----------|
| SUCCESS | - | 执行成功 | - | status: SUCCESS |
| UNKNOWN | 未知异常 | 未分类错误 | ✅ | errorMessage |
| TASK_REJECTED | 本地校验 | 指令格式错误 | ❌ | errorMessage |
| PERMISSION_MISSING | 无障碍服务 | 服务未启用 | ✅ | errorMessage |
| APP_NOT_INSTALLED | open_app | 目标应用不存在 | ❌ | errorMessage |
| LAUNCH_FAILED | open_app | 启动失败 | ✅ | errorMessage |
| ELEMENT_NOT_FOUND | click/input | 未找到目标元素 | ✅ | errorMessage |
| CLICK_FAILED | click_element | 点击操作失败 | ✅ | errorMessage |
| INPUT_FAILED | input_text | 输入操作失败 | ✅ | errorMessage |
| NETWORK_ERROR | 云端通信 | 网络异常 | ✅ | 进入离线队列 |
| TIMEOUT | 执行超时 | 超过最大执行时间 | ❌ | errorMessage |
| MAX_RETRY_EXCEEDED | 重试逻辑 | 重试次数耗尽 | ❌ | errorMessage |

## 6. 执行器实现方案

### 6.1 CloudTaskExecutor 改造

```kotlin
// cloud/CloudTaskExecutor.kt

interface CloudTaskExecutor {
    suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult
    fun getModelName(): String
}

/**
 * 手机控制任务执行器
 * 将云端 command JSON 转化为本地 Agent 可执行的 prompt
 */
class PhoneControlTaskExecutor(
    private val context: Context,
    private val agentService: AgentService,
    private val maxRetries: Int = 3,
    private val timeoutMs: Long = 60_000L,
) : CloudTaskExecutor {

    override suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult {
        return try {
            // 1. 解析云端指令
            val command = parseCommand(task.command)
            
            // 2. 构建本地 Agent prompt
            val prompt = buildPrompt(command)
            
            // 3. 执行（带超时）
            val result = withTimeout(timeoutMs) {
                executeWithAgent(prompt)
            }
            
            // 4. 转化结果为 CloudTaskExecutionResult
            CloudTaskExecutionResult.success(
                message = result.summary,
                evidence = result.screenshot,
            )
        } catch (e: TimeoutCancellationException) {
            CloudTaskExecutionResult.failure(
                message = "执行超时",
                errorCode = CloudTaskErrorCode.TIMEOUT,
                retryable = false,
            )
        } catch (e: Exception) {
            CloudTaskExecutionResult.failure(
                message = e.message ?: "执行异常",
                errorCode = CloudTaskErrorCode.UNKNOWN,
                retryable = true,
            )
        }
    }
    
    private fun buildPrompt(command: PhoneCommand): String {
        return when (command.action) {
            "open_app" -> "打开应用 ${command.packageName}，等待 ${command.waitMs}ms，然后告诉我当前屏幕状态"
            "click_element" -> "点击屏幕上显示"${command.target}"的元素"
            "input_text" -> "在输入框中输入"${command.text}""
            else -> throw IllegalArgumentException("未知动作: ${command.action}")
        }
    }
}
```

### 6.2 离线缓存策略

```kotlin
// cloud/CloudEventQueue.kt 已有能力

/**
 * 离线任务结果队列
 * 
 * 策略:
 * 1. 网络异常时自动入队 (submitTaskResult 失败)
 * 2. 心跳响应时批量补报 (flushOfflineQueue)
 * 3. 单个事件最大重试 3 次
 * 4. 超过 24h 未上报的事件自动丢弃
 * 5. 队列上限 1000 条，超出时丢弃最旧
 */
```

## 7. Kotlin 文件改动清单

| 文件 | 动作 | 说明 |
|------|------|------|
| `cloud/CloudTaskExecutor.kt` | 修改 | 实现 PhoneControlTaskExecutor |
| `cloud/CloudNodeOrchestrator.kt` | 修改 | 注入 PhoneControlTaskExecutor |
| `cloud/model/CloudModels.kt` | 新增 | PhoneCommand 数据类 |
| `tool/impl/OpenAppTool.kt` | 验证 | 确保 open_app 工具可用 |
| `tool/impl/ClickElementTool.kt` | 验证 | 确保点击工具可用 |
| `tool/impl/InputTextTool.kt` | 验证 | 确保输入工具可用 |
| `ClawApplication.kt` | 修改 | 初始化时启动 CloudNodeOrchestrator |

## 8. 接口字段映射

### 8.1 云端 → 端侧 (任务下发)

| 云端字段 | 端侧字段 | 说明 |
|----------|----------|------|
| taskUuid | taskUuid | 任务唯一编号 |
| command | command | JSON 指令串 |
| priority | priority | 优先级 (high/normal/low) |
| createdAt | createdAt | 创建时间戳 |

### 8.2 端侧 → 云端 (结果上报)

| 端侧字段 | 云端字段 | 说明 |
|----------|----------|------|
| status | status | SUCCESS/FAILED/RUNNING |
| result | result | 执行结果摘要 |
| errorMessage | errorMessage | 错误信息 |
| executionTimeMs | executionTimeMs | 执行耗时 |
| modelUsed | modelUsed | 使用的模型 |
| evidenceUrls | evidenceUrls | 截图/日志附件 |

## 9. 验证样例

### 9.1 单元测试

```kotlin
// test/java/io/agents/pokeclaw/cloud/PhoneControlTaskExecutorTest.kt

@Test
fun `open_app 指令解析正确`() {
    val json = """{"action":"open_app","package":"com.example.app"}"""
    val command = PhoneCommand.fromJson(json)
    assertEquals("open_app", command.action)
    assertEquals("com.example.app", command.packageName)
}

@Test
fun `点击元素重试策略`() = runTest {
    val executor = PhoneControlTaskExecutor(mockContext, mockAgent, maxRetries = 3)
    // 模拟前两次失败，第三次成功
    // 验证重试次数和最终状态
}
```

### 9.2 联调步骤

```bash
# 1. 构建 APK
./gradlew :app:assembleDebug

# 2. 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. 启动应用并启用无障碍服务
adb shell am start -n io.agents.pokeclaw/.ui.main.MainActivity
adb shell settings put secure enabled_accessibility_services io.agents.pokeclaw/io.agents.pokeclaw.service.ClawAccessibilityService

# 4. 触发云端任务 (模拟)
adb shell am broadcast -a io.agents.pokeclaw.CLOUD_TASK -e command '{"action":"open_app","package":"com.android.settings"}'

# 5. 查看日志验证
adb logcat -s "PokeClaw/CloudNodeOrchestrator" "PokeClaw/PhoneControlTaskExecutor" -v time
```

## 10. 待验证清单

- [ ] PhoneCommand JSON 解析器实现并测试
- [ ] PhoneControlTaskExecutor.execute() 完成三种动作样例
- [ ] 与 AgentService.executeTask() 成功对接
- [ ] 错误码正确映射到 CloudTaskErrorCode
- [ ] 重试逻辑按策略执行
- [ ] 超时机制生效
- [ ] 离线队列在网络恢复后补报成功
- [ ] CloudNodeOrchestrator 在 Application.onCreate() 中启动
- [ ] 端到端联调通过 (云端下发 → 端侧执行 → 结果上报)

## 11. 下一步开发任务

1. **高优**: 实现 PhoneCommand 数据类和 JSON 解析
2. **高优**: 实现 PhoneControlTaskExecutor 基础框架
3. **中优**: 对接 AgentService 执行实际动作
4. **中优**: 补充单元测试和联调脚本
5. **低优**: 添加更多动作样例 (滑动、查找等)

---

**文档产出路径**: `/mnt/e/code/PokeClaw/docs/product/pokeclaw-phone-control-minimal.md`
**关联代码路径**: `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/`
