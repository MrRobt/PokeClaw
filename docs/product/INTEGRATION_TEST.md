# PokeClaw设备API联调验证用例

## 验证环境

| 组件 | 地址 | 状态 |
|:---|:---|:---|
| DYQ后端 | http://192.168.250.3:8080 | 待确认 |
| PokeClaw | /mnt/e/code/PokeClaw | main分支 |
| 设备API契约 | /mnt/e/code/dyq/api-contracts/device.openapi.yaml | ✅ 已存在 |

## 用例1: 设备注册

### 测试目的
验证设备注册接口可用，正确返回deviceToken和refreshToken。

### 请求
```bash
curl -X POST http://192.168.250.3:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "pokeclaw-test-$(date +%s)",
    "deviceName": "测试设备",
    "deviceModel": "Test Model",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'
```

### 期望响应
```json
{
  "code": 0,
  "data": {
    "deviceToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 604800
  }
}
```

### Android端调用示例
```kotlin
val request = DeviceRegisterRequest(
    deviceId = "pokeclaw-test-${System.currentTimeMillis()}",
    deviceName = "测试设备",
    deviceModel = Build.MODEL,
    androidVersion = Build.VERSION.RELEASE,
    appVersion = BuildConfig.VERSION_NAME
)
val response = CloudClient.api.deviceRegister(request)
```

---

## 用例2: 设备心跳

### 测试目的
验证心跳接口可用，正确返回待处理任务数。

### 前置条件
- 已获取deviceToken

### 请求
```bash
curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {deviceToken}" \
  -d '{
    "batteryLevel": 85,
    "isCharging": true,
    "networkType": "wifi"
  }'
```

### 期望响应
```json
{
  "code": 0,
  "data": {
    "pendingTaskCount": 0,
    "skillVersion": 1,
    "serverTime": 1715913600000
  }
}
```

### Android端调用示例
```kotlin
val request = DeviceHeartbeatRequest(
    batteryLevel = batteryManager.getBatteryLevel(),
    isCharging = batteryManager.isCharging(),
    networkType = getNetworkType()
)
val response = CloudClient.api.deviceHeartbeat(request)
if (response.body()?.data?.pendingTaskCount ?: 0 > 0) {
    // 拉取待处理任务
}
```

---

## 用例3: 拉取待处理任务

### 测试目的
验证任务拉取接口可用。

### 前置条件
- 已获取deviceToken
- deviceId已注册

### 请求
```bash
curl -X GET "http://192.168.250.3:8080/api/claw-device/devices/{deviceId}/pending-tasks" \
  -H "Authorization: Bearer {deviceToken}"
```

### 期望响应
```json
{
  "code": 0,
  "data": [
    {
      "taskUuid": "task-uuid-123",
      "command": "打开微信",
      "mode": "TASK",
      "createdAt": 1715913600000,
      "priority": "normal"
    }
  ]
}
```

---

## 用例4: 提交任务结果

### 测试目的
验证任务结果上报接口可用。

### 前置条件
- 已获取deviceToken
- 已有taskUuid

### 请求
```bash
curl -X POST "http://192.168.250.3:8080/api/claw-device/tasks/{taskUuid}/result" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {deviceToken}" \
  -d '{
    "status": "SUCCESS",
    "result": "微信已打开",
    "executionTimeMs": 1500,
    "toolCalls": "[{\"tool\":\"tap\",\"x\":100,\"y\":200}]",
    "modelUsed": "local"
  }'
```

### 期望响应
```json
{
  "code": 0,
  "data": {
    "message": "ok"
  }
}
```

### Android端调用示例
```kotlin
val request = TaskResultRequest(
    status = TaskResultRequest.Status.SUCCESS,
    result = "微信已打开",
    executionTimeMs = 1500,
    toolCalls = "[{\"tool\":\"tap\",\"x\":100,\"y\":200}]"
)
val response = CloudClient.api.submitTaskResult(taskUuid, request)
```

---

## 用例5: Token刷新

### 测试目的
验证Token刷新接口可用。

### 前置条件
- 已获取refreshToken

### 请求
```bash
curl -X POST http://192.168.250.3:8080/api/claw-device/token/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "{refreshToken}"
  }'
```

### 期望响应
```json
{
  "code": 0,
  "data": {
    "deviceToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 604800
  }
}
```

---

## 用例6: Token失效场景

### 测试目的
验证使用无效Token调用接口时的错误响应。

### 请求
```bash
curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid_token" \
  -d '{"batteryLevel": 50}'
```

### 期望响应
```json
{
  "code": 401,
  "msg": "无效的Token"
}
```

---

## 用例7: 离线降级验证

### 测试目的
验证网络异常时Android端的降级行为。

### 测试步骤
1. 断开网络连接
2. 调用deviceHeartbeat()
3. 观察应用行为

### 期望行为
- 捕获网络异常
- 记录到本地队列
- 网络恢复后重试

---

## 联调执行计划

| 步骤 | 任务 | 负责人 | 状态 |
|:---|:---|:---|:---|
| 1 | 确认后端服务已启动 | 后端阿诚 | 🟡 待确认 |
| 2 | 执行用例1：设备注册 | 安卓小龙 | 🔵 待执行 |
| 3 | 执行用例2：心跳测试 | 安卓小龙 | 🔵 待执行 |
| 4 | 执行用例3：任务拉取 | 安卓小龙 | 🔵 待执行 |
| 5 | 执行用例4：结果上报 | 安卓小龙 | 🔵 待执行 |
| 6 | 执行用例5：Token刷新 | 安卓小龙 | 🔵 待执行 |
| 7 | 执行用例6：异常处理 | 安卓小龙 | 🔵 待执行 |
| 8 | 执行用例7：离线降级 | 安卓小龙 | 🔵 待执行 |

## 阻塞事项

| ID | 事项 | 阻塞原因 | 解决方案 |
|:---|:---|:---|:---|
| BLOCK-1 | 后端编译未完成 | mvn compile超时 | 确认是否已有可运行jar |
| BLOCK-2 | 后端服务状态未知 | 不清楚192.168.250.3:8080是否可访问 | 需要后端阿诚确认 |
| BLOCK-3 | Android端Token存储 | Android Keystore实现未完整 | 后续任务完成 |

## 产出文件

1. `/mnt/e/code/PokeClaw/docs/product/INTEGRATION.md` - 联调清单
2. `/mnt/e/code/PokeClaw/docs/product/INTEGRATION_TEST.md` - 本文件
3. `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/generated/` - 生成的Kotlin代码
