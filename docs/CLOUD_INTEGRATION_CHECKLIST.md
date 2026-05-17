# PokeClaw 端云设备对接联调清单

> 本文档对齐 CMP-1940 任务目标，涵盖设备注册、任务下发、结果回传的端云协同联调步骤。
> 对应后端接口定义：`/mnt/e/code/dyq/api-contracts/device.openapi.yaml`

---

## 一、接口字段映射

### 1. 设备注册接口

| 方向 | 端点 | 方法 | 说明 |
|:---|:---|:---|:---|
| 端→云 | `/api/claw-device/register` | POST | 设备首次注册或重连 |

**请求字段映射（Android → DYQ）**

| Android 字段 (DeviceRegisterRequest) | DYQ 字段 | 类型 | 来源 |
|:---|:---|:---|:---|
| deviceId | deviceId | String | UUID自动生成，格式 `pokeclaw-{uuid}` |
| deviceName | deviceName | String? | Build.MODEL |
| deviceModel | deviceModel | String? | Build.MODEL |
| androidVersion | androidVersion | String? | Build.VERSION.RELEASE |
| appVersion | appVersion | String? | BuildConfig.VERSION_NAME |
| publicKey | publicKey | String? | 预留，暂未使用 |

**响应字段映射（DYQ → Android）**

| DYQ 字段 | Android 字段 (DeviceRegisterResponse) | 类型 |
|:---|:---|:---|
| deviceToken | deviceToken | String (JWT，有效期7天) |
| refreshToken | refreshToken | String (JWT，有效期30天) |
| expiresIn | expiresIn | Int (秒) |

### 2. 设备心跳接口

| 方向 | 端点 | 方法 | 认证 |
|:---|:---|:---|:---|
| 端→云 | `/api/claw-device/heartbeat` | POST | Bearer {deviceToken} |

**请求字段映射**

| Android 字段 (DeviceHeartbeatRequest) | DYQ 字段 | 类型 | 来源 |
|:---|:---|:---|:---|
| batteryLevel | batteryLevel | Int? | BatteryManager |
| isCharging | isCharging | Boolean? | BatteryManager |
| networkType | networkType | String? | wifi/cellular/offline |

**响应字段映射**

| DYQ 字段 | Android 字段 (DeviceHeartbeatResponse) | 说明 |
|:---|:---|:---|
| pendingTaskCount | pendingTaskCount | 待处理任务数，>0时触发任务拉取 |
| skillVersion | skillVersion | 云端技能版本号 |
| serverTime | serverTime | 服务器时间戳（毫秒） |

### 3. 任务轮询接口

| 方向 | 端点 | 方法 | 认证 |
|:---|:---|:---|:---|
| 端→云 | `/api/claw-device/devices/{deviceId}/pending-tasks` | GET | Bearer {deviceToken} |

**响应字段映射**

| DYQ 字段 | Android 字段 (PendingTaskItem) | 类型 | 说明 |
|:---|:---|:---|:---|
| taskUuid | taskUuid | String | 任务唯一标识 |
| command | command | String | 执行指令 |
| mode | mode | String? | TASK/INTERACTIVE |
| createdAt | createdAt | Long | 创建时间戳 |
| priority | priority | String? | 优先级 normal/high/low |

### 4. 结果上报接口

| 方向 | 端点 | 方法 | 认证 |
|:---|:---|:---|:---|
| 端→云 | `/api/claw-device/tasks/{taskUuid}/result` | POST | Bearer {deviceToken} |

**请求字段映射**

| Android 字段 (TaskResultRequest) | DYQ 字段 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|:---|
| status | status | String | ✅ | SUCCESS/FAILED/RUNNING/CANCELLED |
| result | result | String? | ❌ | 执行结果文本 |
| errorMessage | errorMessage | String? | ❌ | 错误信息（用户可读） |
| executionTimeMs | executionTimeMs | Long? | ❌ | 执行耗时 |
| toolCalls | toolCalls | String? | ❌ | 工具调用记录（JSON） |
| evidenceUrls | evidenceUrls | String? | ❌ | 证据URL列表（JSON） |
| modelUsed | modelUsed | String? | ❌ | 使用的模型 |
| errorCategory | errorCategory | String? | ❌ | 错误大类 |
| errorCode | errorCode | String? | ❌ | 错误码 |
| errorDetail | errorDetail | String? | ❌ | 详细错误信息 |
| recoverable | recoverable | Boolean? | ❌ | 是否可重试 |
| suggestedAction | suggestedAction | String? | ❌ | 建议用户操作 |
| screenshotBase64 | screenshotBase64 | String? | ❌ | 失败截图（Base64） |
| logSnippet | logSnippet | String? | ❌ | 相关日志片段 |

### 5. Token 刷新接口

| 方向 | 端点 | 方法 | 认证 |
|:---|:---|:---|:---|
| 端→云 | `/api/claw-device/token/refresh` | POST | 无（需 refreshToken） |

---

## 二、联调步骤

### Step 1: 后端服务启动验证

```bash
# 在 DYQ 后端机器上执行
cd /mnt/e/code/dyq
curl -s http://192.168.250.3:8080/api/health | grep -o '"status":"[^"]*"'
# 预期返回: "status":"ok"
```

### Step 2: 设备注册联调

**cURL 测试命令：**

```bash
# 测试设备注册（无认证）
curl -X POST http://192.168.250.3:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "pokeclaw-test-001",
    "deviceName": "测试设备-001",
    "deviceModel": "Xiaomi 14",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'

# 预期响应:
{
  "code": 0,
  "data": {
    "deviceToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 604800
  }
}
```

**Android 端验证：**
- 检查 logcat 日志过滤 `PokeClaw/DeviceCloudClient`
- 预期输出：`register: 注册成功，deviceId=xxx`

### Step 3: 心跳联调

**cURL 测试命令：**

```bash
# 使用 Step 2 获取的 deviceToken
DEVICE_TOKEN="your_device_token_here"

curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" \
  -d '{
    "batteryLevel": 85,
    "isCharging": true,
    "networkType": "wifi"
  }'

# 预期响应:
{
  "code": 0,
  "data": {
    "pendingTaskCount": 0,
    "skillVersion": 0,
    "serverTime": 1747471234567
  }
}
```

**Android 端验证：**
- logcat 过滤 `PokeClaw/CloudNodeOrchestrator`
- 预期输出：`heartbeatLoop: 启动心跳循环，间隔=30000ms`
- 每30秒输出：`sendHeartbeat: battery=xx, network=wifi`

### Step 4: 任务轮询联调

**cURL 测试命令：**

```bash
# 查询待处理任务
curl -X GET "http://192.168.250.3:8080/api/claw-device/devices/pokeclaw-test-001/pending-tasks" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}"

# 初始预期响应（无任务）:
{
  "code": 0,
  "data": []
}
```

**Android 端验证：**
- 心跳响应中 `pendingTaskCount > 0` 时触发任务拉取
- logcat 预期输出：`getPendingTasks: 拉取到 N 个待处理任务`

### Step 5: 任务下发（管理端）

**通过管理后台下发任务：**

```bash
# 管理员登录后获取 token（此处略）

# 向指定设备下发任务
curl -X POST http://192.168.250.3:8080/claw/device/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "deviceId": "pokeclaw-test-001",
    "command": "检查手机剩余存储空间",
    "mode": "TASK"
  }'
```

**Android 端预期行为：**
- 下次心跳响应中 `pendingTaskCount` 变为 1
- 自动调用 `getPendingTasks` 拉取任务
- 执行任务（调用本地 Agent）
- 上报结果到 `/api/claw-device/tasks/{taskUuid}/result`

### Step 6: 结果回传联调

**cURL 测试命令：**

```bash
# 模拟上报任务结果
curl -X POST "http://192.168.250.3:8080/api/claw-device/tasks/test-task-001/result" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${DEVICE_TOKEN}" \
  -d '{
    "status": "SUCCESS",
    "result": "存储空间检查完成：剩余 45.2GB",
    "executionTimeMs": 1250,
    "modelUsed": "local"
  }'

# 预期响应:
{
  "code": 0,
  "data": {
    "message": "ok"
  }
}
```

**Android 端验证：**
- logcat 预期输出：`submitTaskResult: 结果上报成功，taskUuid=xxx`

---

## 三、关键代码文件清单

| 文件路径 | 职责 |
|:---|:---|
| `cloud/DeviceCloudClient.kt` | 云端客户端接口定义 |
| `cloud/RetrofitDeviceCloudClient.kt` | Retrofit 实现（注册/心跳/任务/上报） |
| `cloud/CloudNodeOrchestrator.kt` | 编排器（注册→心跳→任务→执行→上报闭环） |
| `cloud/api/CloudDeviceApi.kt` | Retrofit API 定义（对齐 device.openapi.yaml） |
| `cloud/api/CloudDeviceApiFactory.kt` | API 客户端工厂（含认证拦截器） |
| `cloud/model/CloudModels.kt` | 数据模型（请求/响应 DTO） |
| `cloud/auth/CloudDeviceTokenStore.kt` | Token 存储接口 |
| `cloud/auth/AndroidKeystoreCloudDeviceTokenStore.kt` | Android Keystore 实现 |
| `cloud/CloudEventQueue.kt` | 离线事件队列 |
| `ClawApplication.kt` | Application 初始化云端节点 |

---

## 四、配置项

| 配置键 | 默认值 | 说明 |
|:---|:---|:---|
| `cloud_base_url` | `http://192.168.250.3:8080` | DYQ 后端地址 |
| `cloud_enabled` | `false` | 云端节点开关（需在设置中开启） |

---

## 五、常见问题排查

### 问题 1: 注册返回 500

**排查步骤：**
1. 检查 DYQ 后端是否启动：`curl http://192.168.250.3:8080/api/health`
2. 检查 `claw_device` 表是否存在
3. 检查后端日志是否有 NullPointerException

### 问题 2: 心跳返回 401

**排查步骤：**
1. 确认 deviceToken 已正确存储（检查 KVUtils）
2. 确认 Authorization 头格式为 `Bearer {token}`
3. 检查 token 是否过期（7天有效期）

### 问题 3: 任务下发后未收到

**排查步骤：**
1. 确认设备已注册且心跳正常
2. 检查管理端调用的设备 ID 是否与端侧一致
3. 检查 `claw_device_task` 表中任务状态是否为 PENDING
4. 检查设备是否正在执行其他任务（_state == EXECUTING 时会跳过）

### 问题 4: 结果上报失败

**排查步骤：**
1. 检查网络连接
2. 查看 logcat 中 `submitTaskResult` 日志
3. 失败结果会自动进入离线队列，网络恢复后补报

---

## 六、验收标准

- [ ] 设备注册：Android 端调用注册接口成功，获取 deviceToken
- [ ] 心跳上报：每30秒发送心跳，响应中包含 pendingTaskCount
- [ ] 任务轮询：心跳响应 pendingTaskCount > 0 时自动拉取任务
- [ ] 任务执行：本地 Agent 执行云端下发的指令
- [ ] 结果回传：任务执行完成后上报结果到云端
- [ ] 离线缓存：网络异常时结果进入队列，恢复后补报
- [ ] Token 刷新：deviceToken 过期前自动刷新

---

## 七、待验证清单（QA）

| 测试项 | 命令/操作 | 预期结果 |
|:---|:---|:---|
| 设备注册 | `adb logcat -s PokeClaw/DeviceCloudClient` | 看到 "注册成功" 日志 |
| 心跳间隔 | 同上，持续观察 | 每30秒一次心跳日志 |
| 任务下发 | 管理后台下发任务后观察 | Android 端收到并执行任务 |
| 离线缓存 | 断网后执行任务，恢复网络 | 结果自动补报 |
| Token 过期 | 等待7天或手动篡改token | 自动刷新并恢复 |

---

**文档版本**: 2025-05-17  
**对应 Issue**: CMP-1940  
**作者**: 安卓小龙
