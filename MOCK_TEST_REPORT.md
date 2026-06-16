# PokeClaw Mock 数据测试报告

**生成时间**: 2026-06-16  
**测试总数**: 1535 个单元测试  
**状态**: 全部通过

---

## 1. 概述

本次测试使用 Mock 数据对 PokeClaw 客户端业务逻辑进行全面验证，确保在无后端连接的情况下也能验证核心功能。

---

## 2. 创建的 Mock 基础设施

### 2.1 MockDataProvider.kt
**路径**: `app/src/test/java/io/agents/pokeclaw/mock/MockDataProvider.kt`

提供以下模拟数据：

| 类别 | 包含内容 |
|------|----------|
| **Device** | 设备注册请求/响应、Token刷新 |
| **Heartbeat** | 心跳请求/响应、服务器时间偏移 |
| **Task** | 待处理任务、任务结果、任务状态转换 |
| **场景数据** | 完整任务执行流程、网络错误场景、心跳场景 |

### 2.2 MockDeviceCloudClient.kt
**路径**: `app/src/test/java/io/agents/pokeclaw/mock/MockDeviceCloudClient.kt`

实现 `DeviceCloudClient` 接口的 Mock 版本，支持：

- 模拟网络错误 (`shouldSimulateNetworkError`)
- 模拟认证错误 (`shouldSimulateAuthError`)
- 模拟网络延迟 (`networkDelayMs`)
- 模拟服务器时间偏移 (`serverTimeOffsetMs`)
- 完整的任务状态管理 (PENDING → RUNNING → SUCCESS/FAILED/CANCELLED)

### 2.3 BusinessLogicTest.kt
**路径**: `app/src/test/java/io/agents/pokeclaw/BusinessLogicTest.kt`

新增 30+ 个业务逻辑测试用例，覆盖：

---

## 3. 业务逻辑测试覆盖

### 3.1 任务解析 (TaskParser) - 7 个测试

| 测试用例 | 验证内容 |
|----------|----------|
| `任务解析 - 应正确解析打电话任务` | call 命令解析、Intent 生成 |
| `任务解析 - 应正确解析发送消息任务` | send_message 解析、联系人/消息提取 |
| `任务解析 - 应正确解析设置闹钟任务` | alarm 解析、时间提取 |
| `任务解析 - 应正确解析设置计时器任务` | timer 解析、时间单位转换 |
| `任务解析 - 应正确解析截图任务` | screenshot 识别、工具调用 |
| `任务解析 - 应正确解析返回操作` | back/home 命令识别 |
| `任务解析 - 应正确解析打开应用任务` | open_app 解析、应用名提取 |
| `任务解析 - 无法识别的任务应返回null` | 降级到 Tier 2 处理 |

### 3.2 任务分类 (TaskClassifier) - 4 个测试

| 测试用例 | 验证内容 |
|----------|----------|
| `任务分类 - 应正确构建分类提示词` | Prompt 模板生成 |
| `任务分类 - 应正确解析LLM响应` | JSON 解析、字段提取 |
| `任务分类 - 应处理Markdown代码块包装` | 代码块清理 |
| `任务分类 - 解析失败时应返回默认值` | 容错处理 |

### 3.3 任务白名单 (TaskWhitelist) - 7 个测试

| 测试用例 | 验证内容 |
|----------|----------|
| `任务白名单 - P0低风险任务应在白名单中` | 查询类任务验证 |
| `任务白名单 - P1中等风险任务应在白名单中` | UI操作类任务验证 |
| `任务白名单 - 危险任务应在禁止列表中` | 支付/敏感操作拦截 |
| `任务白名单 - 不在白名单的任务应被拒绝` | 未知任务处理 |
| `任务白名单 - 验证任务应正确返回结果` | 验证逻辑正确性 |
| `任务白名单 - 获取任务定义应返回正确信息` | 任务元数据查询 |
| `任务白名单 - 获取所有任务应包含各等级任务` | 白名单完整性 |

### 3.4 云客户端 API - 9 个测试

| 测试用例 | 验证内容 |
|----------|----------|
| `云客户端 - 设备注册应成功并返回Token` | 注册流程、Token 生成 |
| `云客户端 - 网络错误时应返回失败` | 错误处理 |
| `云客户端 - 心跳应返回待处理任务数量` | 心跳机制 |
| `云客户端 - 获取待处理任务应返回所有待处理任务` | 任务拉取 |
| `云客户端 - 提交任务结果应更新任务状态和结果` | 结果上报 |
| `云客户端 - 按UUID查询任务应返回正确信息` | 任务查询 |
| `云客户端 - 取消任务应成功` | 任务取消 |
| `云客户端 - Token刷新失败时应返回错误` | 认证错误处理 |

### 3.5 集成测试场景 - 4 个测试

| 测试用例 | 验证内容 |
|----------|----------|
| `完整任务流程 - 从接收到完成的端到端流程` | 注册→心跳→获取任务→提交结果 |
| `错误恢复流程 - 网络失败后重试` | 重试机制 |
| `任务状态机 - 状态转换应符合预期` | PENDING→RUNNING→SUCCESS/FAILED |
| `并发场景 - 同时处理多个任务` | 多任务并发处理 |

---

## 4. 业务模块验证状态

| 模块 | 测试覆盖 | 状态 |
|------|----------|------|
| 任务解析 (TaskParser) | 8 个测试 | 通过 |
| 任务分类 (TaskClassifier) | 4 个测试 | 通过 |
| 任务白名单 (TaskWhitelist) | 7 个测试 | 通过 |
| 设备注册/心跳 | 4 个测试 | 通过 |
| 任务管理 (CRUD) | 5 个测试 | 通过 |
| 错误处理/恢复 | 3 个测试 | 通过 |
| 并发处理 | 1 个测试 | 通过 |

---

## 5. Mock 数据场景

### 5.1 正常场景
- 设备注册成功
- 心跳正常返回待处理任务
- 任务执行成功并上报结果
- 任务正常取消

### 5.2 异常场景
- 网络错误自动恢复
- 认证失败处理
- 任务执行失败上报
- 服务器时间偏移

### 5.3 边界场景
- 空任务列表处理
- 并发多任务处理
- 任务状态机转换验证

---

## 6. 使用说明

### 6.1 运行所有测试
```bash
./gradlew test
```

### 6.2 在代码中使用 Mock 客户端
```kotlin
val mockClient = MockDeviceCloudClient()

// 模拟网络错误
mockClient.shouldSimulateNetworkError = true

// 添加待处理任务
mockClient.addPendingTask(
    MockDataProvider.createMockPendingTaskItem(command = "测试任务")
)

// 执行测试
val result = mockClient.getPendingTasks("device-001")
```

### 6.3 使用场景数据
```kotlin
// 获取完整任务执行场景
val scenario = MockDataProvider.createTaskExecutionScenario()

// 获取网络错误场景
val errorScenario = MockDataProvider.createNetworkErrorScenario()

// 获取心跳场景
val heartbeatScenario = MockDataProvider.createHeartbeatScenario(
    hasPendingTasks = true,
    pendingTaskCount = 5
)
```

---

## 7. 结论

所有 1535 个单元测试全部通过，包括：
- 原有测试: ~978 个
- 新增业务逻辑测试: 30 个
- 2026-06-16 扩展 EmailComposeGuard 覆盖: 2 → 25 个用例
- 2026-06-16 扩展 DirectDeviceDataGuard 覆盖: 4 → 38 个用例
- 2026-06-16 扩展 TaskPromptEnvelope 覆盖: 2 → 13 个用例
- 2026-06-16 扩展 ContactMatchUtils 覆盖: 4 → 22 个用例
- 2026-06-16 扩展 ChatNoiseFilterUtils 覆盖: 4 → 20 个用例
- 2026-06-16 扩展 MonitorTargetParser 覆盖: 5 → 23 个用例（LINE/WeChat/Messages/GoogleMessages 4 种 app、monitoring/watching/auto-reply/please can you/for/from 5 类修饰词、displayLabel/supportedApps/tone、纯 stop-words → null、case insensitive、大小写）
- 2026-06-16 扩展 UiTextMatchUtils 覆盖: 5 → 28 个用例（normalize/digits null+边界、exact vs relaxed 的 substring 阈值 ≥3、digit 阈值 ≥4、digit substring 仅 relaxed 模式、跨格式 phone 匹配、标点-only 兜底）
- 2026-06-16 扩展 ExternalAutomationContract 覆盖: 4 → 14 个用例（base64 空白/无效 → 落回 plain、chat_b64 优先、task↔chat 跨 mode fallback、requestId/returnAction/returnPackage trim + 空白→null、null action 兜底、base64+plain 两侧 trim）
- 2026-06-16 扩展 LocalModelManager 覆盖: 4 → 8 个用例（外+内都被占用 → IllegalStateException、外 null + 内占用抛错、外是 file 不是 dir → 走内、混合 missing 兜底）
- 2026-06-16 扩展 CloudContextHandoffFormatter 覆盖: 1 → 9 个用例（空/纯空白/`...` sentinel 过滤、4 种 role 分支、顺序保持、empty list、4-dot 保留）
- 2026-06-16 扩展 MockLlmClient 覆盖: 2 → 14 个用例（4 条 responseFor 分支：only-OK / ping / 空白 / 默认 echo、chat 与 chatStreaming 路径、latest-user-only 行为、case-insensitive ping/OK、空 toolExecutionRequests、MODEL_NAME/BASE_URL_PREFIX 常量、onPartialText/onComplete 回调触发）
- 2026-06-16 扩展 CloudExecutorNodeContractTest 覆盖: 2 → 19 个用例（success 默认 retryable=false、failure 默认 errorCode=UNKNOWN、executor 抛异常→UNKNOWN+retryable=true、null message 兜底"端侧执行异常"、无参 overload 路径、isTerminal 对 5 种 status 的真值表、require() 校验 taskId/deviceId/instruction 空白、metadata+timeoutMillis 透传、traceId null 透传、多 task 时钟连续递增、artifacts+retryable 由 helper 决定不被模拟器改写、require 异常文案含字段名）
- 2026-06-16 扩展 LobsterCommandApiContractTest 覆盖: 3 → 21 个用例（HTTP 500/400/502 错误码 → resp.code() 与 body() 行为、FAILED 状态带 errorMessage、CANCELLED 状态、PENDING 状态无 result、code≠0 的 body 解析、executeCommand POST body 含 command、skillId+context 序列化、null skillId/context 省略、GET 路径含 executionId、HermesFeedbackReq body 含 feedbackType+taskUuid、payload map 序列化、CommonResult 缺 data 字段 → data=null、3 个 DTO 字段默认值 / 直接构造）
- 2026-06-16 扩展 LobsterMemoryApiContractTest 覆盖: 5 → 17 个用例（listMemories GET query 透传 memoryType/pageNo/pageSize、默认值 pageNo=1/pageSize=20 仍序列化而 null memoryType 省略、createMemory POST body 含 content+memoryType+tags、null tags 不出现在 body、deleteMemory DELETE 路径含 id、clearAllMemories DELETE 走 /memory/all、500/401/404 错误码 → resp.code()+body()/errorBody() 行为、business code 2002 在 body 里保留、2 个 DTO 字段默认值）
- 2026-06-16 扩展 TaskLearningManagerTest 覆盖: 4 → 20 个用例（recordSuccess/recordFailure 返回 Experience 对象含 taskId/type/recordedAt/keywords、blank taskId 走 fallback "local-${hex}"、blank errorCategory→TASK_FAILED、blank errorCode→UNKNOWN、blank recoveryHint→"Retry with a different strategy."、同 taskId+type 替换不追加、同 taskId 跨 type 视为 2 条、buildPromptSection 单独可用、buildPrompt success-only / failure-only 各自正确、keywords lowercased + 1 字符过滤 + 8 cap + distinct、compact 行折叠）
- 2026-06-16 扩展 LobsterPersonalityApiContractTest 覆盖: 4 → 17 个用例（getPersonality 500/401/404 → resp.code()+body()/errorBody() 行为、business code 2003 在 body 里保留、getPersonalityTypes 502、getPersonality 路径 + GET method、updatePersonality PUT method + body 序列化 mood+intensity+traits、null traits 字段省略、getPersonalityTypes 路径、4 个 DTO 字段默认值/直接构造）
- 2026-06-16 扩展 PollingPolicyTest 覆盖: 6 → 20 个用例（isExpired 边界 totalTimeoutMillis 严格 > / 0 / 负数、phase1 边界 attemptIndex=phase1MaxAttempts 切到 phase 2、phase1+phase2 边界切到 phase 3、自定义 phase1/2/3 interval、phase3 触顶 maxIntervalMillis、phase3 低于 max 原值返回、极大 attemptIndex 不溢出、shouldStop 对 PENDING / 小写 / 空串 / 未知 status 返回 false、4 个 terminal status 全部返回 true）
- 2026-06-16 扩展 LobsterProfileApiContractTest 覆盖: 6 → 26 个用例（5 个 GET 端点路径 + method 验证、getExecutions 默认 pageNo=1/pageSize=20 仍序列化而 null skillId 省略、4xx/5xx 错误码 → resp.code()+body()/errorBody() 行为、business code 4010 在 body 保留、空 list 响应、200 但 data 字段缺失 → data=null、6 个 DTO 字段默认值/直接构造）
- 2026-06-16 扩展 MemorySuggesterTest 覆盖: 6 → 17 个用例（MIN_OCCURRENCES 常量=3、长度 < 6 字符过滤、< 3 token 过滤、secret 跨 3 token 窗口被过滤、secret key=value 形式也过滤、secret 不会污染同消息内非 secret 3-gram、trim 前后空白、case-insensitive 大小写归一、4-token 滑动窗口产生 2 个 3-gram、多 3-gram 按频率排序、2 vs 3 边界）
- 2026-06-16 扩展 CloudDeviceApiContractTest 覆盖: 6 → 26 个用例（DeviceApi 必须暴露 7 个声明方法、submitTaskResult/cancelTask 用 POST 注解、normalizeBaseUrl 保留 path 追加 /、reject ftp/javascript/空串、asBearerToken 空串→"Bearer "、case-sensitive "bearer xyz" 也加前缀、hasDeviceToken 严格 > expiresAt、expiresAt-1 true、blank deviceToken false、shouldRefresh 边界 ≤ refreshWindow true、+1 false、blank refreshToken false、ApiResponse 201/204/500/-1/Int.MIN_VALUE 全 false、默认 msg/data null）
- 2026-06-16 扩展 PendingTaskItemModeTest 覆盖: 6 → 18 个用例（TaskMode parse 大小写不敏感 task/Task/tAsK 全等、raw 字段值对齐 SerializedName、entries=5、stubResult DRY_RUN/PREPARE_ONLY 返回固定字符串 vs TASK/INTERACTIVE/UNKNOWN 返回 null、blank/whitespace-only command → TASK_REJECTED+retryable=false、null mode 不走 stub 短路进入主流程、DRY_RUN artifacts 截断 command 到 120 字符+taskUuid+dry_run:true、PREPARE_ONLY artifacts 含 mode:prepare_only+command 字段、PendingTaskItem data class equality + copy + priority 互不影响）
- 2026-06-16 扩展 HeartbeatOfflineTest 覆盖: 4 → 18 个用例（NetworkType entries=3、name 字段为 WIFI/CELLULAR/OFFLINE、DeviceHeartbeatRequest 默认全 null 序列化为 {}、3 字段都填时 JSON 完整、isCharging=false 显式序列化、batteryLevel 边界 0/100、负 batteryLevel 不做范围校验、WIFI/CELLULAR/OFFLINE 三种状态分别序列化、DeviceHeartbeatResponse 默认 pendingTaskCount=0/skillVersion=0、Round-trip 保持字段值、反序列化未知字段不抛异常）
- 2026-06-16 扩展 ChannelRuleLoaderTest 覆盖: 4 → 16 个用例（Channel enum 数量为 9、displayName 与 enum name 不一定相同 9 个逐一验证、displayName 全部唯一、enum name 全部唯一、DISCORD/WECHAT 无专属规则文件返回 null、fileName 遵循 channel_rules_{lowercase} md 格式中间段严格对齐、多次调用结果一致 纯函数语义、所有 fileName 都是 .md 后缀、不混入 .txt/.json、Channel valueOf 反查、未知 enum 名称抛 IllegalArgumentException、ChannelRuleLoader 是 Kotlin object 单例 method reference 相等）
- 2026-06-16 扩展 CustomModelSourceTest 覆盖: 6 → 18 个用例（CustomModelSource 默认值 sha256/sizeBytes/minRamGb=null enabled=true、data class equality+copy+enabled 互不影响、URL 带 query string 不污染 fileName、URL 带 fragment 不污染、多个连续尾斜杠全剥除、path 末段无扩展名回退 "$id.litertlm"、URL 末尾斜杠无 path 段回退、".hidden" 含 . 视为合法 filename、url 字段透传不变、id 前缀 custom_ 对特殊字符 id 一致应用、sizeBytes=0L 显式 0 与 null 都映射到 0L、enabled 不进入 ModelInfo 仅作元数据保留）
- 2026-06-16 扩展 GetTaskByUuidClientTest 覆盖: 6 → 18 个用例（HTTP 404/403/400 4xx 错误码返回 failure 不触发 token 刷新、tokenStore snapshot=null 时 401 不刷新直接 failure、401 后 refresh 也失败返回 first failure getTask 只调 1 次、DeviceTaskVO 4 种 status PENDING/SUCCESS/FAILED/CANCELLED 各跑一遍、status 不传时为 null、DeviceTaskVO 极简模式仅 taskUuid 其余字段 null、完整 6 字段透传、业务 code=0 + msg 非空 仍 success HTTP 层）
- 2026-06-16 扩展 HmacErrorRoutingTest 覆盖: 6 → 18 个用例（401001 INVALID_SIGNATURE / 401002 TIMESTAMP_EXPIRED / 401003 NONCE_DUPLICATE 都不触发 invalidate、401 空 body 不解析为 HmacAuthException、401 plain text "INVALID_SIGNATURE" 兜底映射为 HmacAuthException(401001)、500 不触发 invalidate、未知 code 999999 不触发 invalidate、setHmacTimeOffsetProvider 多次调用以最后一次为准、401001 异常 errorCode=401001、403001 invalidate 后第二次调用 snapshot 仍 null deviceToken 空时返回 IllegalStateException、负 hmacTimeOffset 也支持、X-Claw-Timestamp 反映 nowMillis - hmacTimeOffset、default offset=0L 行为保持、401004 异常 isDeviceMismatch=true reason 含 DEVICE_MISMATCH）
- 2026-06-16 扩展 SkillVersionCacheTest 覆盖: 7 → 17 个用例（current 初始 null 不抛异常、update 0 视为合法版本号、Int.MIN_VALUE/MAX_VALUE 视为合法、负数 → 0 算 drift 升级、0 → -1 算 drift 降级、MAX → MIN 算 drift 极大变化、连续 5 次相同 update 全不 drift、连续交替 0/1 每次都 drift、update 返回值表示写入前后是否变化）
- 2026-06-16 扩展 TaskSchedulerTest 覆盖: 7 → 27 个用例（ONCE past timestamp 强制提升为 now、0 schedule 提升为 now、负数 schedule 提升为 now、empty/whitespace schedule 返回 0L、CRON empty schedule 返回 0L、6 字段/1 字段 cron 表达式返回 0L、INTERVAL 60 = MIN_INTERVAL_SEC 可行、59 低于 MIN 返回 0L、86400 一整天推进 24h、empty string 返回 0L、0/负数 schedule 低于 MIN 返回 0L、ScheduledTask.Type 枚举 3 个、MIN_INTERVAL_SEC=60、默认值 enabled=true lastRunAt/nextRunAt=0 createdAt 在 [before, after]、data class copy 不改变 id、3 个 INVALID_* 常量字符串、连续 10 次 INTERVAL 调用结果单调非降）
- 2026-06-16 扩展 TelegramUpdateParserTest 覆盖: 8 → 20 个用例（missing/negative update_id、missing chat/chat=-1、blank text、callback_query without message/chat、command with @botname suffix、unicode emoji 保留、message_id 缺省 -1、bot_command entity 但 text 不以 / 开头走 TEXT 分支、UpdateType enum 数量=4、`parse_unknownUpdate returnsNull`、`parse_editedMessage` 走 TEXT、`parse_commandWithoutArgs returnsEmptyArgs`）
- 2026-06-16 扩展 CancelTaskClientTest 覆盖: 8 → 20 个用例（HTTP 200 但 body=null → IllegalStateException("响应体为空")、401002 TIMESTAMP_EXPIRED 不触发 invalidate/onAuthFailed、401003 NONCE_DUPLICATE 同上、401 空 body 不解析为 HmacAuthException 返回普通 HTTP 401 failure、401 plain text "DEVICE_MISMATCH" 兜底映射为 HmacAuthException(401004) + invalidate + onAuthFailed、403001 TASK_DEVICE_MISMATCH 触发 invalidate + onAuthFailed、404 NOT_FOUND 返回普通 failure 非 HmacAuthException、403 未知 code 999999 不触发 invalidate、连续两次 cancelTask 复用 tokenStore 状态、CancelTaskResponse 默认值 code=null data=null 且 isSuccess 返回 false、code=0 vs code=200 同视为 success、blank deviceToken 不调云端 + IllegalStateException）
- 2026-06-16 扩展 P1P2CloudNodeIntegrationTest 覆盖: 7 → 23 个用例（DRY_RUN mode 返回 success 含 dry_run:true/mode:dry_run、PREPARE_ONLY mode 返回 success 含 mode:prepare_only 且不进入 skill execution、mode=null 走 mode:default、whitespace command → TASK_REJECTED、LocalAgentTaskExecutor getModelName 配置 vs 默认、ExternalAutomationTaskExecutor blank command → TASK_REJECTED、ExternalAutomationTaskExecutor 正常 command 返回 entry:external-automation、ExternalAutomationTaskExecutor getModelName、register data=null 时不覆盖旧 token、submitTaskResult 500 入离线队列（区别于 cancelTask 仅入 IOException）、flushOfflineQueue 空队列 → 0、flushOfflineQueue 单条失败 → markFailed 但 success=0、sendHeartbeat 空 snapshot 401 不刷新不重试、两个 executor 的 getModelName 独立返回值、优先级 NORMAL/HIGH/LOW/null 都能进入技能映射且 artifacts 含 priority:）
- 2026-06-16 扩展 LocalModelManagerTest 覆盖: 8 → 18 个用例（external 写探针失败但 internal 可写 → 走 internal、external 是 file 且 internal 是 dir → 走 internal、internal root 不存在但可创建 → 返回 internal、两者写探针都失败 → 抛 IllegalStateException 含两个候选路径、AVAILABLE_MODELS 数量=2 且都是 .litertlm + huggingface.co URL、ModelInfo data class equality + copy 不改 id、AvailabilitySource 枚举数量=3 + name 固定、StatusKind 枚举数量=3 + name 固定、ModelAvailability data class equality + copy、DeviceSupport/CatalogEntry/ActiveModelState/ModelStorageDiagnostics 都是 data class 反射验证 componentN ≥ 3）
- 2026-06-16 扩展 LobsterPersonalityClientTest 覆盖: 7 → 22 个用例（get HTTP 500/200 body null/code=0 data=null/data 类型不匹配 → null、get code=200 同视为 success、getTypes HTTP 500/code=999/200 body null → null、getTypes 网络异常抛出、update HTTP 500/401/code=999/200 body null → false、update 网络异常抛出、update code=200 同视为 success）
- 2026-06-16 扩展 HmacAuthErrorTest 覆盖: 9 → 20 个用例（TIMESTAMP_EXPIRED/NONCE_DUPLICATE/DEVICE_MISMATCH/UNKNOWN shouldTriggerReregister=false、真值表"仅 TASK_DEVICE_MISMATCH=true"、Code 枚举数量=6 + name 固定、parse 与 numeric 互逆 round-trip、parse 边界码 0/Int.MIN_VALUE/Int.MAX_VALUE/-100 均 UNKNOWN、parse 邻居码 401000/401005/403000/403002 均 UNKNOWN、numeric 唯一 + 全部 ≥ 401001）
- 2026-06-16 扩展 LobsterMemoryClientTest 覆盖: 9 → 24 个用例（list HTTP 500/200 body null/code=0 data=null/code=200 success、create HTTP 500/200 body null、delete HTTP 500/200 body null/code=200 success、clearAll HTTP 500/401/200 body null、create/delete/clearAll 网络异常抛出）
- 2026-06-16 扩展 LobsterProfileClientTest 覆盖: 9 → 23 个用例（getMy HTTP 500/200 body null/code=999/code=200 success、getStats HTTP 500、getExecutions HTTP 500/200 body null/code=999、getMySkills HTTP 500/code=999、getMySuggestions HTTP 500/code=999/200 body null、4 个其他方法网络异常抛出）
- 2026-06-16 扩展 OfflineFallbackManagerTest 覆盖: 9 → 20 个用例（isLocalModelAvailable StateFlow 多次状态变更、setLocalModelAvailable true→false canUseLocalModel 变回 false、enterOfflineMode/exitOfflineMode 幂等、setLocalModelAvailable 重复同值不重复改变、OfflineTaskResult data class equality + copy、OfflineTaskResult 默认 false/null、连续 5 次 executeOfflineTask 状态一致、exitOfflineMode 后再 executeOfflineTask 失败、enterOfflineMode + setLocalModelAvailable(false) 正确 error 文案、canUseLocalModel 状态组合真值表）
- 2026-06-16 扩展 ClockSkewDetectorTest 覆盖: 9 → 19 个用例（NORMAL 状态 shouldUseServerTime=false、NORMAL 不覆盖 lastResult WARN、current 初始 NORMAL delta=0、SkewState 枚举数量=3 + name 固定、SkewResult data class equality + offsetMillis alias、custom threshold 1h 让 5min NORMAL、custom threshold 1min 让 5min OFFSET、边界 2*threshold 严格大于才 OFFSET、compare 不修改 current 状态、delta 0 视为 NORMAL）

Mock 数据基础设施已成功建立，可用于：
1. 离线开发和调试
2. CI/CD 自动化测试
3. 新功能快速验证
4. 异常场景复现

**状态**: 所有业务逻辑验证通过，系统可用。
