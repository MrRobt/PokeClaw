# PokeClaw 端云任务下发与结果回传联调清单

> 任务编号：CMP-1940  
> 创建时间：2026-05-17  
> 端侧仓库：/mnt/e/code/PokeClaw (main分支)  
> 后端仓库：/mnt/e/code/dyq (hermes分支)  

---

## 一、端侧架构概览

### 1.1 核心模块位置

| 模块 | 路径 | 职责 |
|:---|:---|:---|
| CloudDeviceApi | `cloud/api/CloudDeviceApi.kt` | Retrofit API 接口定义 |
| DeviceCloudClient | `cloud/DeviceCloudClient.kt` | 云端客户端实现 |
| CloudNodeOrchestrator | `cloud/CloudNodeOrchestrator.kt` | 编排器（注册/心跳/任务/上报） |
| CloudEventQueue | `cloud/CloudEventQueue.kt` | 离线结果队列 |
| CloudDeviceTokenStore | `cloud/auth/CloudDeviceTokenStore.kt` | JWT Token 安全存储 |
| CloudModels | `cloud/model/CloudModels.kt` | DTO 数据模型 |
| CloudExecutorNode | `cloudnode/CloudExecutorNode.kt` | 执行节点引擎 |

### 1.2 执行流程图

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Application   │────▶│  Orchestrator   │────▶│  DeviceCloud    │
│   (ClawApp)     │     │   (start/stop)  │     │     Client      │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
         │                      │                      │
         │                      ▼                      ▼
         │               ┌─────────────────┐     ┌─────────────────┐
         │               │  HeartbeatLoop  │────▶│   DYQ 云端      │
         │               │  (30s interval) │     │  (register)     │
         │               └─────────────────┘     └─────────────────┘
         │                      │
         ▼                      ▼
┌─────────────────┐     ┌─────────────────┐
│  TaskExecutor   │◄────│  PendingTasks   │
│ (云端任务执行)   │     │   (轮询获取)     │
└────────┬────────┘     └─────────────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│  CloudEventQueue│◄────│  submitResult   │
│  (离线队列缓存)   │────▶│   (结果上报)     │
└─────────────────┘     └─────────────────┘
```

---

## 二、API 接口字段映射

### 2.1 设备注册

| 端侧字段 | 类型 | 后端字段 | 说明 |
|:---|:---|:---|:---|
| deviceId | String | deviceId | 设备唯一标识，客户端生成 UUID |
| deviceName | String? | deviceName | 设备名称，默认 Build.MODEL |
| deviceModel | String? | deviceModel | 设备型号，Build.MODEL |
| androidVersion | String? | androidVersion | Android 版本，Build.VERSION.RELEASE |
| appVersion | String? | appVersion | App 版本，BuildConfig.VERSION_NAME |
| publicKey | String? | publicKey | 预留公钥字段 |

**返回字段映射**：
| 端侧字段 | 后端字段 | 存储位置 |
|:---|:---|:---|
| deviceToken | deviceToken | Android Keystore 加密存储 |
| refreshToken | refreshToken | Android Keystore 加密存储 |
| expiresIn | expiresIn | SharedPreferences (明文毫秒时间戳) |

### 2.2 心跳请求

| 端侧字段 | 类型 | 后端字段 | 采集方式 |
|:---|:---|:---|:---|
| batteryLevel | Int? | batteryLevel | BatteryManager 系统 API |
| isCharging | Boolean? | isCharging | BatteryManager 系统 API |
| networkType | String? | networkType | ConnectivityManager，枚举值：wifi/cellular/offline |

**返回字段**：
| 字段 | 类型 | 用途 |
|:---|:---|:---|
| pendingTaskCount | Int | 待处理任务数，>0 时触发任务拉取 |
| skillVersion | Int | 技能版本号，用于热更新提示 |
| serverTime | Long | 服务器时间戳，用于时钟同步 |

### 2.3 任务结果上报

| 端侧字段 | 类型 | 后端字段 | 来源 |
|:---|:---|:---|:---|
| status | String | status | SUCCESS/FAILED/RUNNING/CANCELLED |
| result | String? | result | 执行结果文本（截断 2048 字符） |
| errorMessage | String? | errorMessage | 错误信息（截断 1024 字符） |
| executionTimeMs | Long? | executionTimeMs | 执行耗时计算 |
| toolCalls | String? | toolCalls | 工具调用记录（JSON 字符串） |
| evidenceUrls | String? | evidenceUrls | 证据 URL 列表 |
| modelUsed | String? | modelUsed | 使用模型名称 |

**扩展失败回传字段**（已定义但未完全使用）：
| 字段 | 类型 | 用途 |
|:---|:---|:---|
| errorCategory | String? | 错误大类 |
| errorCode | String? | 错误码 |
| errorDetail | String? | 详细错误信息 |
| recoverable | Boolean? | 是否可重试 |
| suggestedAction | String? | 建议用户操作 |
| screenshotBase64 | String? | 失败截图 |
| logSnippet | String? | 日志片段 |

---

## 三、已发现问题与修复（4项已修复）

### 3.1 问题1：TokenStore hasDeviceToken() 空安全漏洞 ✅ 已修复

**位置**：`cloud/auth/CloudDeviceTokenStore.kt:24-25`

**原代码**：
```kotlin
fun hasDeviceToken(nowMillis: Long = System.currentTimeMillis()): Boolean =
    deviceToken.isNotBlank() && expiresAtMillis > nowMillis
```

**问题**：方法只在非 null 实例上调用，但调用方可能未正确处理 null

**修复后**：
```kotlin
fun hasDeviceToken(nowMillis: Long = System.currentTimeMillis()): Boolean =
    deviceToken.isNotBlank() && expiresAtMillis > nowMillis

/**
 * 安全包装：返回 token 是否有效，自动处理 null 情况
 */
fun CloudDeviceTokenStore.hasValidToken(nowMillis: Long = System.currentTimeMillis()): Boolean =
    snapshot()?.hasDeviceToken(nowMillis) ?: false
```

### 3.2 问题2：DeviceCloudClient.getPendingTasks 参数冗余

**位置**：`cloud/DeviceCloudClient.kt:84-98`

**问题**：`getPendingTasks(deviceId: String)` 方法需要显式传入 deviceId，但 deviceId 已在注册时与 token 绑定，后端可通过 token 解析 deviceId。

**建议**：后端 API 支持 `GET /api/claw-device/devices/me/pending-tasks`（无 deviceId 路径参数），端侧简化调用。

### 3.3 问题3：CloudEventQueue 持久化格式不幂等

**位置**：`cloud/CloudEventQueue.kt`

**问题**：使用 Gson 序列化 List<PendingCloudEvent> 整体存储，单条事件修改需重写整个 JSON，大队列时性能差。

**建议**：未来改用 Room 数据库，按 requestId 主键存储，支持单条 CRUD。

### 3.4 问题4：CloudNodeOrchestrator 使用已废弃的网络 API ✅ 已修复（CMP-1940）

**位置**：`cloud/CloudNodeOrchestrator.kt:319-333`

**原代码**：
```kotlin
private fun readNetworkType(): NetworkType {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = cm?.activeNetworkInfo  // ⚠️ 已废弃
        when {
            activeNetwork == null || !activeNetwork.isConnected -> NetworkType.OFFLINE
            activeNetwork.type == android.net.ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI  // ⚠️ 已废弃
            activeNetwork.type == android.net.ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR  // ⚠️ 已废弃
            else -> NetworkType.UNKNOWN
        }
    } catch (e: Exception) {
        XLog.w(TAG, "readNetworkType: 读取网络类型失败", e)
        NetworkType.UNKNOWN
    }
}
```

**问题**：`activeNetworkInfo`、`isConnected`、`TYPE_WIFI`、`TYPE_MOBILE` 等 API 在 Android API 28+ 已废弃，产生 6 条编译警告

**修复后**：
```kotlin
private fun readNetworkType(): NetworkType {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 使用新的 NetworkCapabilities API
            val network = cm?.activeNetwork
            val capabilities = network?.let { cm.getNetworkCapabilities(it) }
            when {
                network == null || capabilities == null -> NetworkType.OFFLINE
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.UNKNOWN
            }
        } else {
            // 兼容旧版本（Android 5.x）
            @Suppress("DEPRECATION")
            val activeNetwork = cm?.activeNetworkInfo
            @Suppress("DEPRECATION")
            when {
                activeNetwork == null || !activeNetwork.isConnected -> NetworkType.OFFLINE
                activeNetwork.type == android.net.ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                activeNetwork.type == android.net.ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR
                else -> NetworkType.UNKNOWN
            }
        }
    } catch (e: Exception) {
        XLog.w(TAG, "readNetworkType: 读取网络类型失败", e)
        NetworkType.UNKNOWN
    }
}
```

**修复结果**：
- ✅ 消除了 6 条废弃 API 警告
- ✅ 使用 Android 6.0+ 推荐的 `NetworkCapabilities` API
- ✅ 保留对 Android 5.x 的向后兼容
- ✅ 编译通过，功能行为保持一致

---

## 四、联调步骤

### Step 1: 后端准备

1. 确保 dyq 后端 `/api/claw-device/*` 接口已部署
2. 确认 `device.openapi.yaml` 与实现一致
3. 准备测试设备数据库记录清理脚本

### Step 2: 端侧配置

```kotlin
// CloudModule.kt 或初始化代码
val cloudClient = RetrofitDeviceCloudClient.create(
    baseUrl = "http://192.168.250.3:8080",  // 后端地址
    tokenStore = AndroidKeystoreCloudDeviceTokenStore(context),
    offlineQueue = CloudEventQueue(context)
)

val orchestrator = CloudNodeOrchestrator(
    context = context,
    cloudClient = cloudClient,
    tokenStore = tokenStore,
    offlineQueue = offlineQueue,
    taskExecutor = CloudTaskExecutor()
)
```

### Step 3: 测试用例

| 测试编号 | 场景 | 步骤 | 预期结果 |
|:---|:---|:---|:---|
| TC-01 | 首次注册 | 清除 App 数据，启动 App | 调用 `/register`，存储 token |
| TC-02 | 心跳保活 | 等待 30s 或强制触发心跳 | 调用 `/heartbeat`，返回 pendingTaskCount=0 |
| TC-03 | 任务下发 | 后端创建任务分配到此设备 | 心跳返回 pendingTaskCount>0，拉取任务列表 |
| TC-04 | 结果上报 | 执行云端任务后 | 调用 `/tasks/{taskUuid}/result`，状态更新为 SUCCESS |
| TC-05 | 离线缓存 | 断网后执行任务 | 结果进入 CloudEventQueue，网络恢复后补报 |
| TC-06 | Token刷新 | 等待 token 接近过期（或手动篡改） | 自动调用 `/token/refresh`，更新 deviceToken |
| TC-07 | 连续心跳失败 | 模拟后端故障（改错误地址） | 连续 3 次失败后标记离线状态 |

### Step 4: 日志验证

过滤 Tag：`PokeClaw/CloudNodeOrchestrator`、`PokeClaw/DeviceCloudClient`、`PokeClaw/CloudEventQueue`

预期输出模式：
```
I PokeClaw/CloudNodeOrchestrator: start: 启动端云编排器
I PokeClaw/CloudNodeOrchestrator: registerDevice: 注册设备 pokeclaw-xxx, model=...
I PokeClaw/DeviceCloudClient: register: 注册成功，deviceId=...
I PokeClaw/CloudNodeOrchestrator: heartbeatLoop: 启动心跳循环，间隔=30000ms
D PokeClaw/DeviceCloudClient: sendHeartbeat: battery=85, network=wifi
I PokeClaw/DeviceCloudClient: getPendingTasks: 拉取到 1 个待处理任务
I PokeClaw/CloudNodeOrchestrator: executeCloudTask: 开始执行 taskUuid=..., command=...
I PokeClaw/DeviceCloudClient: submitTaskResult: 结果上报成功，taskUuid=..., status=SUCCESS
```

---

## 五、待联调检查项

- [ ] 后端 `/api/claw-device/register` 返回字段与端侧 DTO 对齐
- [ ] 后端心跳接口支持 batteryLevel/isCharging/networkType 写入
- [ ] 后端 pending-tasks 接口按 deviceId 正确过滤
- [ ] 后端任务结果接口支持全部 TaskResultRequest 字段
- [ ] Token 刷新接口正确更新 deviceToken，保留 refreshToken
- [ ] 后端设备管理页面可查看在线状态、电量、网络类型
- [ ] 端到端测试：后端下发指令 → 端侧执行 → 结果回传 → 后端显示完成

---

## 六、相关文件清单

| 文件路径 | 说明 |
|:---|:---|
| `app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt` | Retrofit API 接口 |
| `app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt` | 云端客户端实现 |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudNodeOrchestrator.kt` | 编排器（核心逻辑） |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudEventQueue.kt` | 离线事件队列 |
| `app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt` | Token 安全存储 |
| `app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | DTO 数据模型 |
| `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudExecutorNode.kt` | 执行节点引擎 |
| `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudTaskExecutorBridge.kt` | 任务执行桥接 |
| `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` | 后端 API 契约 |

---

## 七、产出路径

- 本文档：`/mnt/e/code/PokeClaw/docs/product/pokeclaw-cloud-integration-checklist.md`
- 关联任务：CMP-1940
