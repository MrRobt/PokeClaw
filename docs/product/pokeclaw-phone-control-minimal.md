# PokeClaw 手机控制任务最小执行器设计与样例

## 问题编号
CMP-1986: 自动派活：PokeClaw手机控制任务最小执行器设计与样例

## 日期
2026-05-16

## 负责人
安卓小龙

---

## 一、任务目标

让 PokeClaw 作为小龙虾端侧手下/执行端，能承接 dyq 下发的简单手机控制任务并回传结果。

---

## 二、前提假设验证

### 2.1 仓库路径验证 ✅
- 实际路径：`/mnt/e/code/PokeClaw/`
- 当前分支：`main`
- 仓库状态：真实 PokeClaw 安卓端仓库，非 zeroclaw/metaclaw

### 2.2 相关文件确认 ✅
| 检查项 | 路径 | 状态 |
|--------|------|------|
| 云端执行节点引擎 | `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudExecutorNode.kt` | ✅ 存在 |
| 任务技能映射器 | `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudTaskSkillMapper.kt` | ✅ 存在 |
| 云端数据模型 | `app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | ✅ 存在 |
| 云端任务执行器接口 | `app/src/main/java/io/agents/pokeclaw/cloud/CloudTaskExecutor.kt` | ✅ 存在 |
| 设备API联调文档 | `docs/product/pokeclaw-device-api-integration.md` | ✅ 存在 |
| 设备节点方案文档 | `docs/product/pokeclaw-device-node-integration.md` | ✅ 存在 |

### 2.3 现有能力清单 ✅
- 设备注册：POST /api/claw-device/register
- 心跳上报：POST /api/claw-device/heartbeat
- 任务拉取：GET /api/claw-device/devices/{deviceId}/pending-tasks
- 结果上报：POST /api/claw-device/tasks/{taskUuid}/result
- Token刷新：POST /api/claw-device/token/refresh
- 离线队列：SharedPreferences + Gson 缓存
- Android Keystore：JWT Token 加密存储

---

## 三、方案选择

### 方案A：扩展现有 CloudTaskExecutor 实现
直接在 `CloudTaskExecutor.kt` 中接入 `AgentService` 或 `ExternalAutomationEntrypoint`。

**优点**：改动小，直接复用现有架构  
**缺点**：需要依赖完整的 Agent 运行时，测试复杂

### 方案B：设计独立的最小执行器契约
先定义最小执行器的接口、状态机和样例，再逐步实现。

**优点**：边界清晰，可独立测试，文档先行  
**缺点**：需要额外设计文档

**选择方案B**，理由：符合"设计先行"原则，CMP-1986 重点是产出设计文档而非直接编码。

---

## 四、最小执行器设计

### 4.1 定义域模型

```kotlin
// 最小手机控制动作枚举
enum class PhoneControlAction {
    LAUNCH_APP,        // 打开应用
    FIND_AND_TAP,      // 查找并点击
    INPUT_TEXT,        // 输入文本
    GO_BACK,           // 返回上一页
    SEARCH_IN_APP,     // 应用内搜索
    COPY_SCREEN_TEXT,  // 复制屏幕文字（截图+OCR）
    ACCEPT_PERMISSION, // 接受权限请求
    DISMISS_POPUP,     // 关闭弹窗
    SWIPE_GESTURE      // 滑动手势
}

// 最小任务定义
data class MinimalTask(
    val taskId: String,           // 云端任务编号
    val action: PhoneControlAction, // 动作类型
    val params: Map<String, String>,  // 动作参数
    val timeoutSec: Int = 30,     // 超时秒数
    val requireUserVisible: Boolean = true // 是否需要用户可见
)

// 最小任务结果
data class MinimalTaskResult(
    val taskId: String,
    val status: TaskResultStatus,  // SUCCESS, FAILED, TIMEOUT, CANCELLED
    val output: String?,           // 执行输出（如屏幕文字）
    val errorCode: String?,        // 错误码
    val errorMessage: String?,     // 错误信息
    val executionTimeMs: Long,     // 执行耗时
    val screenshotPath: String?      // 截图路径（可选）
)

enum class TaskResultStatus {
    SUCCESS,   // 成功
    FAILED,    // 失败
    TIMEOUT,   // 超时
    CANCELLED, // 取消
    RUNNING    // 进行中（用于状态上报）
}
```

### 4.2 任务状态机

```
[RECEIVED] → validate() → [VALIDATED] → execute() → [RUNNING]
                                                 ↓
                         ┌───────────────────────┼───────────────────────┐
                         ↓                       ↓                       ↓
                    [SUCCESS]               [FAILED]              [TIMEOUT]
                         ↓                       ↓                       ↓
                    report()               report()                report()
                         ↓                       ↓                       ↓
                    [COMPLETED]             [COMPLETED]             [COMPLETED]

[CANCELLED] 可由外部随时触发，直接进入 COMPLETED
```

### 4.3 执行器接口契约

```kotlin
/**
 * 最小手机控制任务执行器。
 * 职责单一：接收最小任务定义，执行动作，返回结果。
 */
interface MinimalPhoneControlExecutor {
    
    /**
     * 执行任务。
     * @param task 最小任务定义
     * @param callback 状态回调（可选，用于实时上报）
     * @return 执行结果
     */
    suspend fun execute(
        task: MinimalTask,
        callback: TaskStatusCallback? = null
    ): MinimalTaskResult
    
    /**
     * 检查是否支持该动作。
     */
    fun supports(action: PhoneControlAction): Boolean
    
    /**
     * 获取执行器能力列表。
     */
    fun capabilities(): List<PhoneControlAction>
}

/**
 * 任务状态回调。
 */
interface TaskStatusCallback {
    fun onStatusChange(taskId: String, status: TaskResultStatus)
    fun onProgress(taskId: String, message: String)
    fun onError(taskId: String, errorCode: String, message: String)
}
```

### 4.4 云端字段映射

| 云端字段 | 本地字段 | 说明 |
|----------|----------|------|
| `PendingTaskItem.taskUuid` | `MinimalTask.taskId` | 任务编号 |
| `PendingTaskItem.command` | `MinimalTask.action + params` | 需解析转换 |
| `TaskResultRequest.status` | `MinimalTaskResult.status.value` | 状态枚举 |
| `TaskResultRequest.result` | `MinimalTaskResult.output` | 执行结果 |
| `TaskResultRequest.errorMessage` | `MinimalTaskResult.errorMessage` | 错误信息 |
| `TaskResultRequest.executionTimeMs` | `MinimalTaskResult.executionTimeMs` | 执行耗时 |
| `TaskResultRequest.modelUsed` | `"minimal-executor"` | 固定标识 |

---

## 五、最小动作样例

### 样例1：打开应用

**云端指令**：`打开设置`

**本地任务**：
```kotlin
MinimalTask(
    taskId = "task-001",
    action = PhoneControlAction.LAUNCH_APP,
    params = mapOf("app_name" to "设置", "package_hint" to "com.android.settings"),
    timeoutSec = 10
)
```

**执行流程**：
1. 解析参数获取应用名称"设置"
2. 尝试通过包名启动 `com.android.settings`
3. 失败时回退到通过应用名称匹配启动
4. 等待应用启动完成（检查当前窗口包名）
5. 返回结果：成功/失败 + 启动耗时

**状态上报序列**：
```
RECEIVED → VALIDATED → RUNNING → SUCCESS → COMPLETED
```

---

### 样例2：查找并点击

**云端指令**：`点击"确定"按钮`

**本地任务**：
```kotlin
MinimalTask(
    taskId = "task-002",
    action = PhoneControlAction.FIND_AND_TAP,
    params = mapOf("text" to "确定", "match_type" to "exact"),
    timeoutSec = 15
)
```

**执行流程**：
1. 请求无障碍服务获取当前屏幕节点树
2. 查找文本为"确定"的可点击节点
3. 找到后执行点击动作
4. 未找到时返回失败 + 截图

**状态上报序列**：
```
RECEIVED → VALIDATED → RUNNING → SUCCESS → COMPLETED
# 或
RECEIVED → VALIDATED → RUNNING → FAILED → COMPLETED
```

---

### 样例3：截图并回传状态

**云端指令**：`查看当前屏幕状态`

**本地任务**：
```kotlin
MinimalTask(
    taskId = "task-003",
    action = PhoneControlAction.COPY_SCREEN_TEXT,
    params = mapOf("include_screenshot" to "true"),
    timeoutSec = 5
)
```

**执行流程**：
1. 执行截图
2. 使用 OCR 识别屏幕文字（或读取无障碍节点树文本）
3. 压缩截图（可选，受脱敏规则限制）
4. 返回识别的文字 + 截图路径

**状态上报序列**：
```
RECEIVED → VALIDATED → RUNNING → SUCCESS → COMPLETED
```

---

## 六、错误上报与离线缓存

### 6.1 错误码定义

```kotlin
enum class MinimalErrorCode(val code: String, val retryable: Boolean) {
    SUCCESS("E000", false),
    UNKNOWN("E001", true),
    TIMEOUT("E002", true),
    PERMISSION_DENIED("E003", false),     // 权限被拒绝，需用户处理
    ACCESSIBILITY_NOT_READY("E004", true), // 无障碍服务未就绪
    ELEMENT_NOT_FOUND("E005", false),      // 元素未找到
    APP_NOT_FOUND("E006", false),          // 应用未找到
    INVALID_PARAMS("E007", false),         // 参数无效
    NETWORK_ERROR("E008", true),           // 网络错误
    CLOUD_UNAVAILABLE("E009", true),       // 云端不可用
    EXECUTION_CANCELLED("E010", false)     // 执行被取消
}
```

### 6.2 离线缓存策略

```kotlin
// 离线事件队列
interface OfflineEventQueue {
    /**
     * 缓存事件。
     */
    fun enqueue(event: TaskResultEvent)
    
    /**
     * 获取待上报事件列表（不移除）。
     */
    fun peekAll(): List<TaskResultEvent>
    
    /**
     * 移除指定事件。
     */
    fun remove(eventId: String)
    
    /**
     * 清空队列。
     */
    fun clear()
    
    /**
     * 获取队列大小。
     */
    fun size(): Int
}

// 配置
object OfflineQueueConfig {
    const val MAX_QUEUE_SIZE = 100        // 最大缓存100条
    const val MAX_RETRY_ATTEMPTS = 3      // 单条最大重试3次
    const val RETRY_INTERVAL_MS = 30000L  // 重试间隔30秒
    const val EVENT_EXPIRY_MS = 86400000L // 事件过期时间24小时
}
```

---

## 七、Kotlin 文件改动清单

### 7.1 新增文件

| 文件路径 | 说明 |
|----------|------|
| `cloudnode/model/MinimalTask.kt` | 最小任务数据模型 |
| `cloudnode/model/MinimalTaskResult.kt` | 任务结果数据模型 |
| `cloudnode/executor/MinimalPhoneControlExecutor.kt` | 执行器接口定义 |
| `cloudnode/executor/AccessibilityBasedExecutor.kt` | 基于无障碍的实现 |
| `cloudnode/parser/CommandParser.kt` | 云端指令解析器 |
| `cloudnode/offline/OfflineEventQueue.kt` | 离线事件队列接口 |
| `cloudnode/offline/SharedPrefEventQueue.kt` | 基于 SharedPreferences 的实现 |

### 7.2 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `cloud/CloudTaskExecutor.kt` | 接入 MinimalPhoneControlExecutor |
| `cloud/CloudNodeOrchestrator.kt` | 集成离线队列上报逻辑 |

---

## 八、接口字段映射清单

### 8.1 设备注册请求

| OpenAPI 字段 | Kotlin DTO 字段 | 示例值 |
|--------------|-----------------|--------|
| deviceId | deviceId | "uuid-generated" |
| deviceName | deviceName | "小米13" |
| deviceModel | deviceModel | "2211133C" |
| androidVersion | androidVersion | "14" |
| appVersion | appVersion | "0.6.12" |

### 8.2 心跳请求

| OpenAPI 字段 | Kotlin DTO 字段 | 示例值 |
|--------------|-----------------|--------|
| batteryLevel | batteryLevel | 73 |
| isCharging | isCharging | false |
| networkType | networkType | "wifi" |

### 8.3 任务结果上报

| OpenAPI 字段 | Kotlin DTO 字段 | 示例值 |
|--------------|-----------------|--------|
| status | status | "SUCCESS" |
| result | result | "设置已打开" |
| errorMessage | errorMessage | null |
| executionTimeMs | executionTimeMs | 2345 |
| modelUsed | modelUsed | "minimal-executor" |

---

## 九、验证样例

### 9.1 本地单元测试

```kotlin
@Test
fun `打开应用任务 - 成功路径`() = runTest {
    val task = MinimalTask(
        taskId = "test-001",
        action = PhoneControlAction.LAUNCH_APP,
        params = mapOf("app_name" to "设置"),
        timeoutSec = 5
    )
    
    val result = executor.execute(task)
    
    assertEquals(TaskResultStatus.SUCCESS, result.status)
    assertNotNull(result.output)
    assertTrue(result.executionTimeMs > 0)
}

@Test
fun `元素未找到 - 失败路径`() = runTest {
    val task = MinimalTask(
        taskId = "test-002",
        action = PhoneControlAction.FIND_AND_TAP,
        params = mapOf("text" to "不存在的元素"),
        timeoutSec = 3
    )
    
    val result = executor.execute(task)
    
    assertEquals(TaskResultStatus.FAILED, result.status)
    assertEquals("E005", result.errorCode)
}
```

### 9.2 ADB 集成测试

```bash
# 触发云端任务广播（模拟）
adb shell am broadcast \
    -a io.agents.pokeclaw.CLOUD_TASK \
    --es task_id "adb-test-001" \
    --es command "打开设置" \
    -n io.agents.pokeclaw/.cloud.CloudTaskReceiver

# 检查日志
adb logcat -d | grep -E "(CloudExecutor|MinimalTask)"

# 验证结果上报（检查离线队列）
adb shell run-as io.agents.pokeclaw cat \
    /data/data/io.agents.pokeclaw/shared_prefs/cloud_offline_queue.xml
```

---

## 十、风险边界确认

| 风险项 | 现状 | 措施 |
|--------|------|------|
| 分层破坏 | 否 | cloudnode 包独立，不依赖 UI 层 |
| 跨模块 JOIN | 否 | 无数据库操作 |
| 敏感信息泄露 | 否 | 截图脱敏，不上传完整内容 |
| 非官方接口 | 否 | 仅使用标准 Android Accessibility API |
| 后台常驻 | 合规 | 使用协程循环，非 WorkManager |

---

## 十一、产出文件清单

| 文件 | 路径 | 状态 |
|------|------|------|
| 设计文档 | `docs/product/pokeclaw-phone-control-minimal.md` | ✅ 本文件 |
| Kotlin 文件清单 | 见第七节 | ⏳ 待实现 |
| 接口字段映射 | 见第八节 | ✅ 已对齐 |
| 验证样例 | 见第九节 | ⏳ 待实现 |

---

## 十二、下一步任务

1. 实现 `MinimalTask` 和 `MinimalTaskResult` 数据模型
2. 实现 `CommandParser` 云端指令解析器
3. 实现 `AccessibilityBasedExecutor` 基于无障碍的执行器
4. 接入 `CloudTaskExecutor` 现有接口
5. 编写单元测试和 ADB 集成测试
6. 更新 QA_CHECKLIST.md 新增测试项

---

## 十三、关联问题

- CMP-1940: PokeClaw端云任务下发与结果回传联调清单
- CMP-1964: PokeClaw端侧执行端心跳与错误上报方案
- CMP-2001: PokeClaw设备节点注册与云端报错回传
- POK-3: 安卓端侧对接 — cloud/包开发

---

## 文档变更日志

| 日期 | 操作 | 内容 |
|------|------|------|
| 2026-05-16 | 创建 | 初始版本，完成最小执行器设计文档 |
