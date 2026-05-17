# PokeClaw 端侧设备 API 联调准备清单

> 关联任务：CMP-137【Android】PokeClaw端侧对接 — 设备API联调准备  
> 文档路径：/mnt/e/code/PokeClaw/docs/product/pokeclaw-device-api-integration.md  
> 对齐契约：/mnt/e/code/dyq/api-contracts/device.openapi.yaml

---

## 一、已完成工作

### 1.1 Kotlin DTO 模型（已存在）

| 文件路径 | 说明 |
|---------|------|
| `cloud/model/DeviceRegisterRequest.kt` | 设备注册请求 |
| `cloud/model/DeviceRegisterResponse.kt` | 设备注册响应（含 deviceToken/refreshToken） |
| `cloud/model/DeviceHeartbeatRequest.kt` | 心跳请求（batteryLevel/isCharging/networkType） |
| `cloud/model/DeviceHeartbeatResponse.kt` | 心跳响应（pendingTaskCount/skillVersion/serverTime） |
| `cloud/model/PendingTaskItem.kt` | 待处理任务项 |
| `cloud/model/TaskResultRequest.kt` | 任务结果上报请求 |
| `cloud/model/TokenRefreshRequest.kt` | Token刷新请求 |
| `cloud/model/TokenRefreshResponse.kt` | Token刷新响应 |
| `cloud/model/CloudModels.kt` | 统一模型定义（含枚举） |

### 1.2 Retrofit API 接口（已存在）

| 文件路径 | 说明 |
|---------|------|
| `cloud/api/CloudDeviceApi.kt` | 设备端API定义（5个端点） |
| `cloud/api/CloudDeviceApiFactory.kt` | API工厂（OkHttp+Retrofit配置） |

### 1.3 Token 管理（Android Keystore）

| 文件路径 | 说明 |
|---------|------|
| `cloud/auth/CloudDeviceTokenStore.kt` | 安全令牌存储（AES-GCM加密） |

### 1.4 云端客户端实现

| 文件路径 | 说明 |
|---------|------|
| `cloud/DeviceCloudClient.kt` | 设备云端客户端接口+Retrofit实现 |
| `cloud/CloudHeartbeatManager.kt` | WorkManager心跳调度 |
| `cloud/CloudEventQueue.kt` | 离线事件队列（Room数据库） |

### 1.5 本修复：模型对齐修正

**修复1：NetworkType 枚举对齐 device.openapi.yaml**
- 原：WIFI/CELLULAR/OFFLINE/UNKNOWN（4个值）
- 新：WIFI/CELLULAR/OFFLINE（3个值，对齐契约）
- 文件：`cloud/model/CloudModels.kt` 第196-201行

**修复2：getNetworkType() 方法修正**
- 原：返回 UNKNOWN 兜底，类型为 String?
- 新：异常或未知网络返回 OFFLINE，类型为 CloudNetworkType?
- 文件：`cloud/CloudHeartbeatManager.kt` 第283-300行

---

## 二、API 端点映射

| 操作 | 端点 | 认证 | 请求体 | 响应体 |
|-----|------|------|--------|--------|
| 设备注册 | POST /api/claw-device/register | 无 | DeviceRegisterRequest | DeviceRegisterResponse |
| 心跳保活 | POST /api/claw-device/heartbeat | Bearer JWT | DeviceHeartbeatRequest | DeviceHeartbeatResponse |
| 任务拉取 | GET /api/claw-device/devices/{deviceId}/pending-tasks | Bearer JWT | - | List<PendingTaskItem> |
| 结果上报 | POST /api/claw-device/tasks/{taskUuid}/result | Bearer JWT | TaskResultRequest | {message: "ok"} |
| Token刷新 | POST /api/claw-device/token/refresh | 无 | TokenRefreshRequest | TokenRefreshResponse |

---

## 三、Token 管理策略

### 3.1 存储安全
- **设备Token**：存 Android Keystore，AES-GCM加密后写入 SharedPreferences
- **刷新Token**：同上
- **过期时间**：存 SharedPreferences，纯数值（毫秒时间戳）

### 3.2 刷新策略
- 有效期7天，刷新窗口10分钟
- `shouldRefresh()` 在过期前10分钟触发刷新
- 刷新失败（401）时清除本地令牌，下次重新注册

### 3.3 降级策略
- Token为空 → 尝试注册（需要deviceId已生成）
- 注册失败 → 静默重试（指数退避）
- 不阻塞本地功能

---

## 四、离线降级逻辑

### 4.1 离线判定
- 连续3次心跳失败 → 标记离线
- 网络异常 → 标记离线
- 401/403 → 尝试刷新Token，失败则重新注册

### 4.2 离线缓存
- 任务结果 → 写入 CloudEventQueue（Room数据库）
- 心跳失败 → 记录失败次数，不重试（下次心跳周期再试）

### 4.3 恢复上报
- 网络恢复后，flushOfflineQueue() 批量上报
- 失败重试3次，仍失败则保留在队列

---

## 五、联调步骤

### 5.1 后端准备
```bash
# 1. 确认后端运行在 192.168.250.3:8080
# 2. 确认 device.openapi.yaml 已部署
# 3. 确认数据库表 claw_device 存在

# 验证API
 curl -X POST http://192.168.250.3:8080/api/claw-device/register \
   -H "Content-Type: application/json" \
   -d '{"deviceId":"test-device-001","deviceName":"测试设备"}'
```

### 5.2 Android端配置
```kotlin
// 在 ClawApplication 或设置中配置
val baseUrl = "http://192.168.250.3:8080"
val tokenStore = AndroidKeystoreCloudDeviceTokenStore(context)
val offlineQueue = RoomCloudEventQueue(context)
val client = RetrofitDeviceCloudClient.create(baseUrl, tokenStore, offlineQueue)
```

### 5.3 联调检查清单

| 步骤 | 操作 | 预期结果 |
|-----|------|---------|
| 1 | 首次启动，调用 register() | 返回 deviceToken + refreshToken，存入 Keystore |
| 2 | 观察日志：token存储成功 | XLog 显示 "saveTokens: device token saved" |
| 3 | 等待心跳周期（默认1分钟） | 发送心跳请求，携带 Bearer Token |
| 4 | 心跳响应检查 | 返回 pendingTaskCount/skillVersion/serverTime |
| 5 | 后端下发任务 | Android端收到广播 ACTION_PENDING_TASKS |
| 6 | 执行任务，调用 submitTaskResult() | 上报成功，XLog 显示 "结果上报成功" |
| 7 | 断开网络，执行任务 | 结果进入离线队列，XLog 显示 "结果已缓存" |
| 8 | 恢复网络 | 自动flush离线队列，补报结果 |
| 9 | 等待7天（或篡改过期时间） | 触发Token刷新，XLog 显示 "设备令牌已刷新" |
| 10 | 刷新Token失败（401） | 清除本地令牌，下次重新注册 |

---

## 六、已知问题与阻塞

### 6.1 当前阻塞
- **后端编译状态**：需确认 dyq 后端 device API 实现已部署到 192.168.250.3:8080
- **测试设备**：需真机或模拟器运行 PokeClaw APK

### 6.2 待验证问题
1. 设备注册时 deviceId 生成策略（UUID/IMEI/自定义）
2. 心跳间隔在生产环境是否可调（当前默认1分钟）
3. 离线队列容量上限（当前未设限）
4. 截图上报的 Base64 大小限制

---

## 七、文件改动摘要

| 文件 | 改动类型 | 说明 |
|-----|---------|------|
| `cloud/model/CloudModels.kt` | 修复 | NetworkType 枚举移除 UNKNOWN，对齐 device.openapi.yaml |
| `cloud/CloudHeartbeatManager.kt` | 修复 | getNetworkType() 方法修正返回值和异常处理 |
| `docs/product/pokeclaw-device-api-integration.md` | 新增 | 本文档 |

---

## 八、下一步行动

1. **后端确认**：确认 /api/claw-device/* 接口已部署并可访问
2. **联调测试**：按第5节步骤逐项验证
3. **问题修复**：联调中发现问题在此Issue下跟进
4. **E2E QA**：按 QA_CHECKLIST.md 执行端云联调测试

---

**更新记录：**
- 2025-05-17 小黑：创建文档，修复 NetworkType 枚举对齐问题
