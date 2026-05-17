# PokeClaw 端侧执行节点心跳与错误回传对接方案

## 问题编号
CMP-1991: PokeClaw端侧执行端心跳与错误回传对接方案

## 审计日期
2026-05-16

---

## 一、任务目标

让 PokeClaw 安卓端作为小龙虾端侧执行端，具备向 dyq 云端登记、心跳、错误回传、任务状态上报的最小闭环方案。

---

## 二、当前实现审计

### 2.1 模块结构（已完成）

```
app/src/main/java/io/agents/pokeclaw/cloud/
├── api/
│   ├── CloudDeviceApi.kt              # Retrofit 接口定义（5个端点）
│   └── CloudDeviceApiFactory.kt       # OkHttp + Retrofit 构建工厂
├── auth/
│   └── CloudDeviceTokenStore.kt       # Android Keystore JWT 存储（AES-GCM加密）
├── model/
│   └── CloudModels.kt                 # DTO 对齐 device.openapi.yaml
├── DeviceCloudClient.kt               # 客户端接口 + Retrofit 实现
├── CloudEventQueue.kt                 # 离线结果缓存队列（SharedPreferences + Gson）
├── CloudNodeOrchestrator.kt           # 云端任务编排器（注册/心跳/任务拉取/结果上报）
├── CloudTaskExecutor.kt               # 云端任务执行器（接口+占位实现）
└── di/                                  # 依赖注入模块

app/src/main/java/io/agents/pokeclaw/cloudnode/
├── CloudExecutorNode.kt               # 云端执行节点实体
├── CloudExecutorNodeContract.kt       # 执行节点契约
├── CloudTaskExecutorBridge.kt         # 任务执行桥接
└── CloudTaskSkillMapper.kt            # 技能映射
```

### 2.2 已实现功能清单

| 功能 | 状态 | 文件位置 |
|------|------|----------|
| 设备注册 POST /api/claw-device/register | ✅ 已实现 | RetrofitDeviceCloudClient.register() |
| 心跳发送 POST /api/claw-device/heartbeat | ✅ 已实现 | CloudNodeOrchestrator.heartbeatLoop() |
| 任务拉取 GET /api/claw-device/devices/{deviceId}/pending-tasks | ✅ 已实现 | CloudDeviceApi.getPendingTasks() |
| 结果上报 POST /api/claw-device/tasks/{taskUuid}/result | ✅ 已实现 | RetrofitDeviceCloudClient.submitTaskResult() |
| Token 刷新 POST /api/claw-device/token/refresh | ✅ 已实现 | CloudDeviceApi.refreshDeviceToken() |
| Android Keystore 加密存储 | ✅ 已实现 | AndroidKeystoreCloudDeviceTokenStore |
| 离线队列（指数退避重试） | ✅ 已实现 | CloudEventQueue |
| 结果脱敏（长度截断） | ✅ 已实现 | CloudEventQueue.sanitized() |
| 连续失败标记离线 | ✅ 已实现 | CloudNodeOrchestrator.consecutiveHeartbeatFailures |
| 心跳响应触发任务拉取 | ✅ 已实现 | DeviceHeartbeatResponse.pendingTaskCount |

### 2.3 DTO 字段对齐验证

| OpenAPI 字段 | Kotlin DTO | 状态 |
|--------------|-----------|------|
| DeviceRegisterRequest.deviceId | CloudModels.DeviceRegisterRequest.deviceId | ✅ 对齐 |
| DeviceRegisterRequest.deviceName | CloudModels.DeviceRegisterRequest.deviceName | ✅ 对齐 |
| DeviceRegisterResponse.deviceToken | CloudModels.DeviceRegisterResponse.deviceToken | ✅ 对齐 |
| DeviceRegisterResponse.refreshToken | CloudModels.DeviceRegisterResponse.refreshToken | ✅ 对齐 |
| DeviceHeartbeatRequest.batteryLevel | CloudModels.DeviceHeartbeatRequest.batteryLevel | ✅ 对齐 |
| DeviceHeartbeatRequest.isCharging | CloudModels.DeviceHeartbeatRequest.isCharging | ✅ 对齐 |
| DeviceHeartbeatRequest.networkType | CloudModels.DeviceHeartbeatRequest.networkType | ✅ 对齐 |
| DeviceHeartbeatResponse.pendingTaskCount | CloudModels.DeviceHeartbeatResponse.pendingTaskCount | ✅ 对齐 |
| PendingTaskItem.taskUuid | CloudModels.PendingTaskItem.taskUuid | ✅ 对齐 |
| TaskResultRequest.status | CloudModels.TaskResultRequest.status | ✅ 对齐 |
| TaskResultRequest.errorMessage | CloudModels.TaskResultRequest.errorMessage | ✅ 对齐 |

---

## 三、阻塞状态

### 3.1 后端服务不可达（当前阻塞）

| 检查项 | 状态 | 详情 |
|--------|------|------|
| 后端地址 | http://192.168.250.3:8080 | ⛔ 不可达 |
| 注册接口 | POST /api/claw-device/register | ⛔ 未联调 |
| 心跳接口 | POST /api/claw-device/heartbeat | ⛔ 未联调 |
| 任务拉取 | GET /api/claw-device/devices/{deviceId}/pending-tasks | ⛔ 未联调 |
| 结果上报 | POST /api/claw-device/tasks/{taskUuid}/result | ⛔ 未联调 |

### 3.2 阻塞原因

```bash
# 验证命令（已执行）
curl -s http://192.168.250.3:8080/api/claw-device/register -X POST -H "Content-Type: application/json" -d '{}'
# 返回空，连接失败
```

后端服务当前不可达，无法进行真实联调。

---

## 四、端侧静态验证完成项

### 4.1 代码审查通过

- ✅ cloud/ 包结构清晰，职责分离
- ✅ DTO 与 OpenAPI 契约字段对齐
- ✅ Token 存储使用 Android Keystore AES-GCM 加密
- ✅ 离线队列支持指数退避重试
- ✅ 错误回传包含 errorMessage、status、executionTimeMs
- ✅ 日志统一使用 XLog，tag 前缀 PokeClaw/

### 4.2 依赖配置确认

```kotlin
// app/build.gradle.kts（已配置）
implementation(libs.okhttp)
implementation(libs.okhttp.logging)
implementation(libs.retrofit)
implementation(libs.retrofit.gson)
implementation(libs.androidx.work.runtime.ktx)  // 如需 WorkManager 后台心跳
```

### 4.3 集成点确认

| 集成点 | 状态 | 说明 |
|--------|------|------|
| ClawApplication.onCreate() | ✅ 已接入 | initCloudNode() 在 KV 配置启用时启动 |
| Settings 云端管理开关 | ⏳ 待UI实现 | 当前通过 KV 配置启用 |
| TaskOrchestrator 接入云端任务 | ⏳ 待实现 | CloudTaskExecutor 当前为占位实现 |

---

## 五、联调启动条件

以下条件全部满足后方可启动真实联调：

1. ✅ 端侧实现审计完成
2. ✅ 后端 OpenAPI 文件提供（device.openapi.yaml 已存在）
3. ⛔ 后端服务部署完成（当前不可达）
4. ⛔ 测试设备可用
5. ⛔ 联调环境配置完成

---

## 六、下一步行动计划

### 6.1 后端恢复后（阻塞解除）

1. **设备注册联调**
   ```bash
   curl -X POST http://192.168.250.3:8080/api/claw-device/register \
     -H "Content-Type: application/json" \
     -d '{
       "deviceId": "pokeclaw-test-001",
       "deviceName": "测试机-小米14",
       "deviceModel": "Xiaomi 14",
       "androidVersion": "14",
       "appVersion": "0.7.0"
     }'
   ```

2. **心跳联调**
   ```bash
   curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
     -H "Authorization: Bearer ${DEVICE_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "batteryLevel": 85,
       "isCharging": true,
       "networkType": "wifi"
     }'
   ```

3. **任务下发-执行-上报闭环验证**
   - 管理后台下发任务
   - 端侧轮询拉取
   - 本地执行（Mock）
   - 结果上报云端

### 6.2 端侧待完善（可并行）

1. **CloudTaskExecutor 接入 AgentService**
   - 当前：占位实现返回 "尚未接入"
   - 目标：通过 ExternalAutomationEntrypoint 注入任务

2. **Settings 页面添加云端管理开关**
   - 开关：启用/禁用云端连接
   - 显示：设备ID、连接状态、最后心跳时间

3. **QA 测试用例**
   - 网络异常重试测试
   - 离线队列补报测试
   - Token 过期刷新测试

---

## 七、风险边界确认

| 风险项 | 现状 | 措施 |
|--------|------|------|
| 分层破坏 | 否 | cloud 包独立，不依赖 UI 层 |
| 跨模块 JOIN | 否 | 无数据库操作 |
| 敏感信息泄露 | 否 | Keystore 加密 + 脱敏 |
| 非官方接口 | 否 | 仅使用标准 Android API |
| 后台常驻 | 合规 | 使用协程循环，非 WorkManager |

---

## 八、产出文件清单

| 文件路径 | 状态 | 说明 |
|----------|------|------|
| docs/product/pokeclaw-device-heartbeat-error-reporting.md | 本文件 | 心跳与错误回传对接方案 |
| docs/product/pokeclaw-device-api-integration.md | 已存在 | 设备API联调准备清单 |
| app/src/main/java/io/agents/pokeclaw/cloud/ | 已实现 | 云端模块完整实现 |
| /mnt/e/code/dyq/api-contracts/device.openapi.yaml | 已存在 | 后端契约文件 |

---

## 九、待验证清单

- [ ] 后端服务恢复后可访问
- [ ] 设备注册接口返回 deviceToken/refreshToken
- [ ] 心跳接口响应 pendingTaskCount
- [ ] 任务拉取接口返回 PendingTaskItem 列表
- [ ] 结果上报接口返回成功
- [ ] Token 刷新接口返回新 deviceToken
- [ ] 离线队列在网络恢复后自动补报
- [ ] 连续心跳失败标记离线状态

---

**审计完成时间**: 2026-05-16  
**审计人**: 安卓小龙  
**问题编号**: CMP-1991  
**阻塞状态**: 后端服务不可达，等待恢复后联调
