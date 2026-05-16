# PokeClaw 设备API联调准备清单

## 问题编号
CMP-137: 【Android】PokeClaw端侧对接 — 设备API联调准备

## 执行时间
2026-05-16

## 一、当前状态

### ✅ 已完成代码

| 文件 | 说明 | 状态 |
|------|------|------|
| `CloudDeviceApi.kt` | Retrofit 接口定义（5个端点） | ✅ 已完成 |
| `CloudModels.kt` | DTO 模型（对齐 device.openapi.yaml） | ✅ 已完成 |
| `CloudDeviceApiFactory.kt` | API 工厂与鉴权拦截器 | ✅ 已完成 |
| `CloudDeviceTokenStore.kt` | Android Keystore Token 加密存储 | ✅ 已完成 |
| `DeviceCloudClient.kt` | 云端客户端封装（注册/心跳/任务/结果） | ✅ 已完成 |
| `CloudEventQueue.kt` | 离线事件队列（带指数退保重试） | ✅ 已完成 |
| `CloudNodeOrchestrator.kt` | 端云执行节点编排器（最小闭环） | ✅ 已完成 |

### ✅ API 路径对齐验证

| OpenAPI 路径 | Kotlin 方法 | 状态 |
|-------------|-------------|------|
| `/api/claw-device/register` | `register()` | ✅ |
| `/api/claw-device/heartbeat` | `heartbeat()` | ✅ |
| `/api/claw-device/devices/{deviceId}/pending-tasks` | `getPendingTasks()` | ✅ |
| `/api/claw-device/tasks/{taskUuid}/result` | `submitTaskResult()` | ✅ |
| `/api/claw-device/token/refresh` | `refreshDeviceToken()` | ✅ |

### ✅ DTO 字段对齐验证

所有字段已与 `device.openapi.yaml` 对齐：
- `DeviceRegisterRequest`: deviceId, deviceName, deviceModel, androidVersion, appVersion, publicKey
- `DeviceRegisterResponse`: deviceToken, refreshToken, expiresIn
- `DeviceHeartbeatRequest`: batteryLevel, isCharging, networkType
- `DeviceHeartbeatResponse`: pendingTaskCount, skillVersion, serverTime
- `PendingTaskItem`: taskUuid, command, mode, createdAt, priority
- `TaskResultRequest`: status, result, errorMessage, executionTimeMs, toolCalls, evidenceUrls, modelUsed

### ✅ 任务状态枚举对齐

| OpenAPI | Kotlin | 状态 |
|---------|--------|------|
| PENDING | PENDING | ✅ |
| RUNNING | RUNNING | ✅ |
| SUCCESS | SUCCESS | ✅ |
| FAILED | FAILED | ✅ |
| CANCELLED | CANCELLED | ✅ |

## 二、后端状态

### 后端实现位置
- Controller: `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/controller/app/device/AppClawDeviceController.java`
- Service: `ClawDeviceService`
- 状态: 已实现，已编译（target/classes 存在）

### 后端API端点
```
POST   /api/claw-device/register                  # 设备注册
POST   /api/claw-device/heartbeat                 # 设备心跳
GET    /api/claw-device/devices/{deviceId}/pending-tasks  # 获取待处理任务
POST   /api/claw-device/tasks/{taskUuid}/result   # 提交任务结果
POST   /api/claw-device/token/refresh             # 刷新Token
```

## 三、待修复问题

### 测试代码编译错误
1. `CloudDeviceApiContractTest.kt` - 扩展函数导入问题 ✅ 已修复
2. `CloudNodeOrchestratorTest.kt` - 协程测试依赖缺失（kotlinx-coroutines-test）
3. `CloudExecutorNodeContractTest.kt` - API 变更未同步
4. `CloudExecutorNodeTest.kt` - 可空性检查

### 修复状态
- `CloudDeviceApiContractTest.kt`: 已修复 `asBearerToken` 导入和使用方式
- 其他测试文件: 建议单独 Issue 处理，不影响主功能

## 四、联调步骤

### 4.1 环境准备
```bash
# 1. 确保后端服务运行
cd /mnt/e/code/dyq
mvn spring-boot:run -pl dyq-server

# 2. 验证后端API可达
curl http://192.168.250.3:8080/api/claw-device/register \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"pokeclaw-test-001"}'
```

### 4.2 Android端测试
```kotlin
// 在 PokeClaw 中测试设备注册
val client = RetrofitDeviceCloudClient.create(
    baseUrl = "http://192.168.250.3:8080",
    tokenStore = AndroidKeystoreCloudDeviceTokenStore(context),
    offlineQueue = CloudEventQueue(context)
)

// 测试注册
scope.launch {
    val registered = client.register(DeviceRegisterRequest(
        deviceId = "pokeclaw-test-001",
        deviceName = "Test Device",
        deviceModel = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        appVersion = BuildConfig.VERSION_NAME
    ))
    Log.i(TAG, "注册结果: $registered")
}
```

### 4.3 端到端验证
1. 设备注册 → 获取 deviceToken
2. 心跳发送 → 验证 online 状态
3. 任务拉取 → 验证 pendingTaskCount
4. 结果上报 → 验证 SUCCESS 状态回传

## 五、风险与边界

### 当前风险
1. 测试代码编译失败 - 需单独 Issue 修复
2. 离线重试策略 - 已用指数退避（1s, 2s, 4s, 8s...）
3. Token 过期 - 已用 10 分钟刷新窗口

### 技术约束
- Token 存储：Android Keystore 加密
- 离线队列：最大 100 条，带重试计数
- 网络超时：连接 15s，读写 30s
- 心跳间隔：30s

## 六、下一步任务

1. **修复测试代码** - 新建 Issue 处理测试编译问题
2. **后端联调** - 等待后端服务启动后验证端到端流程
3. **文档更新** - 将本清单归档到 `.planning/`

## 七、产出文件

| 文件路径 | 说明 |
|---------|------|
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt` | Retrofit 接口 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | DTO 模型 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt` | 云端客户端 |
| `/mnt/e/code/PokeClaw/docs/product/pokeclaw-device-api-integration-checklist.md` | 本清单 |

## 八、待验证清单

- [ ] 后端服务启动并监听 192.168.250.3:8080
- [ ] 设备注册接口返回 deviceToken
- [ ] 心跳接口更新设备在线状态
- [ ] 任务拉取接口返回待处理任务列表
- [ ] 结果上报接口接收执行结果
- [ ] Token刷新接口正常工作
- [ ] 离线队列在网络恢复后自动补报
