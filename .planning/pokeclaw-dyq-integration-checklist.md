# PokeClaw 端云任务下发与结果回传联调清单

> 对应 Issue: CMP-1940
> 生成时间: 2026-05-17
> 联调目标: 验证 PokeClaw 安卓端与 dyq 后端设备 API 的端云协同闭环

---

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                        DYQ 后端 (Java)                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  AppClawDeviceController (@RequestMapping("/api/claw-device")) │
│  │  ├── POST /register          ──→ 设备注册，返回 JWT Token      │
│  │  ├── POST /heartbeat         ──→ 心跳保活，返回待处理任务数    │
│  │  ├── GET  /devices/{id}/pending-tasks ──→ 拉取待执行任务      │
│  │  ├── POST /tasks/{uuid}/result ──→ 上报执行结果               │
│  │  └── POST /token/refresh     ──→ 刷新 JWT Token             │
│  └─────────────────────────────────────────────────────────┘   │
│                              ↑↓ HTTP + JSON                      │
└─────────────────────────────────────────────────────────────────┘
                              ↑↓
┌─────────────────────────────────────────────────────────────────┐
│                    PokeClaw 安卓端 (Kotlin)                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  CloudNodeOrchestrator (协程心跳循环，30s间隔)              │
│  │  ├── DeviceCloudClient (Retrofit + OkHttp)              │
│  │  │   ├── register()     ──→ 设备注册                      │
│  │  │   ├── heartbeat()    ──→ 定时心跳                      │
│  │  │   ├── getPendingTasks() ──→ 拉取任务                   │
│  │  │   └── submitTaskResult() ──→ 上报结果                  │
│  │  ├── CloudTaskExecutor (本地任务执行器)                   │
│  │  │   └── execute(task) ──→ 调用 AgentService 或自动化      │
│  │  └── CloudEventQueue (离线结果缓存队列)                   │
│  │      └── 网络恢复后批量补报                              │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、接口字段映射对照

### 2.1 设备注册 /api/claw-device/register

| dyq 后端字段 (Java) | PokeClaw 端侧字段 (Kotlin) | 对齐状态 |
|:------------------|:--------------------------|:-------:|
| deviceId          | DeviceRegisterRequest.deviceId | ✅ |
| deviceName        | DeviceRegisterRequest.deviceName | ✅ |
| deviceModel       | DeviceRegisterRequest.deviceModel | ✅ |
| androidVersion    | DeviceRegisterRequest.androidVersion | ✅ |
| appVersion        | DeviceRegisterRequest.appVersion | ✅ |
| publicKey         | DeviceRegisterRequest.publicKey (可选) | ✅ |
| **响应**          |                            |         |
| deviceToken       | DeviceRegisterResponse.deviceToken | ✅ |
| refreshToken      | DeviceRegisterResponse.refreshToken | ✅ |
| expiresIn         | DeviceRegisterResponse.expiresIn | ✅ |

### 2.2 设备心跳 /api/claw-device/heartbeat

| dyq 后端字段 (Java) | PokeClaw 端侧字段 (Kotlin) | 对齐状态 |
|:------------------|:--------------------------|:-------:|
| batteryLevel      | DeviceHeartbeatRequest.batteryLevel | ✅ |
| isCharging        | DeviceHeartbeatRequest.isCharging | ✅ |
| networkType       | DeviceHeartbeatRequest.networkType | ✅ |
| **响应**          |                            |         |
| pendingTaskCount  | DeviceHeartbeatResponse.pendingTaskCount | ✅ |
| skillVersion      | DeviceHeartbeatResponse.skillVersion | ✅ |
| serverTime        | DeviceHeartbeatResponse.serverTime | ✅ |

### 2.3 获取待处理任务 /api/claw-device/devices/{deviceId}/pending-tasks

| dyq 后端字段 (Java) | PokeClaw 端侧字段 (Kotlin) | 对齐状态 |
|:------------------|:--------------------------|:-------:|
| taskUuid          | PendingTaskItem.taskUuid | ✅ |
| command           | PendingTaskItem.command | ✅ |
| mode              | PendingTaskItem.mode | ✅ |
| createdAt         | PendingTaskItem.createdAt | ✅ |
| priority          | PendingTaskItem.priority | ✅ |

### 2.4 提交任务结果 /api/claw-device/tasks/{taskUuid}/result

| dyq 后端字段 (Java) | PokeClaw 端侧字段 (Kotlin) | 对齐状态 |
|:------------------|:--------------------------|:-------:|
| status            | TaskResultRequest.status | ✅ |
| result            | TaskResultRequest.result | ✅ |
| errorMessage      | TaskResultRequest.errorMessage | ✅ |
| executionTimeMs   | TaskResultRequest.executionTimeMs | ✅ |
| toolCalls         | TaskResultRequest.toolCalls | ✅ |
| evidenceUrls      | TaskResultRequest.evidenceUrls | ✅ |
| modelUsed         | TaskResultRequest.modelUsed | ✅ |
| errorCategory     | TaskResultRequest.errorCategory | ✅ |
| errorCode         | TaskResultRequest.errorCode | ✅ |
| errorDetail       | TaskResultRequest.errorDetail | ✅ |
| recoverable       | TaskResultRequest.recoverable | ✅ |
| suggestedAction   | TaskResultRequest.suggestedAction | ✅ |
| screenshotBase64  | TaskResultRequest.screenshotBase64 | ✅ |
| logSnippet        | TaskResultRequest.logSnippet | ✅ |

### 2.5 Token 刷新 /api/claw-device/token/refresh

| dyq 后端字段 (Java) | PokeClaw 端侧字段 (Kotlin) | 对齐状态 |
|:------------------|:--------------------------|:-------:|
| refreshToken      | TokenRefreshRequest.refreshToken | ✅ |
| **响应**          |                            |         |
| deviceToken       | TokenRefreshResponse.deviceToken | ✅ |
| expiresIn         | TokenRefreshResponse.expiresIn | ✅ |

---

## 三、核心文件清单

### 3.1 DYQ 后端 (Java)

| 文件路径 | 说明 |
|:---------|:-----|
| `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` | 接口契约定义 (OpenAPI 3.0) |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/controller/app/device/AppClawDeviceController.java` | 设备端 REST Controller |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/service/device/ClawDeviceService.java` | Service 接口定义 |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/framework/web/config/ClawDeviceAuthInterceptor.java` | JWT 认证拦截器 |

### 3.2 PokeClaw 安卓端 (Kotlin)

| 文件路径 | 说明 |
|:---------|:-----|
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt` | Retrofit API 接口定义 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | DTO 数据模型 (完全对齐 OpenAPI) |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt` | 云端客户端实现 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudNodeOrchestrator.kt` | 端云编排器 (心跳+任务调度) |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudTaskExecutor.kt` | 任务执行器接口 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudEventQueue.kt` | 离线结果队列 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt` | Token 安全存储 (Android Keystore) |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloudnode/CloudExecutorNodeContract.kt` | 端云执行节点契约 |

---

## 四、联调步骤

### 步骤 1: 后端服务启动验证

```bash
# 在 dyq 后端目录执行
cd /mnt/e/code/dyq
git checkout hermes
mvn clean compile -pl dyq-module-claw/dyq-module-claw-biz -am

# 验证 ClawDeviceServiceImpl 编译通过
# 确认数据库表 claw_device, claw_device_task 已创建
```

### 步骤 2: 接口契约一致性检查

```bash
# 对比 OpenAPI 与后端实现字段
cd /mnt/e/code/dyq
diff <(grep -oP "\w+(?=:)" api-contracts/device.openapi.yaml | sort -u) \
     <(grep -oP "private \w+ \w+" dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/controller/admin/device/vo/*.java 2>/dev/null | sort -u)
```

### 步骤 3: 端侧 APK 构建

```bash
cd /mnt/e/code/PokeClaw
./gradlew :app:assembleDebug

# 输出路径: app/build/outputs/apk/debug/app-debug.apk
```

### 步骤 4: 端到端测试用例

#### 测试 1: 设备注册流程
```kotlin
// 验证 CloudNodeOrchestrator.start() 触发注册
// 预期: POST /api/claw-device/register
// 请求体包含 deviceId, deviceName, deviceModel, androidVersion, appVersion
// 响应包含 deviceToken, refreshToken, expiresIn
// 验证: Token 已存入 Android Keystore 加密存储
```

#### 测试 2: 心跳保活
```kotlin
// 验证心跳间隔 30s
// 预期: POST /api/claw-device/heartbeat (带 Authorization: Bearer {token})
// 请求体包含 batteryLevel, isCharging, networkType
// 响应包含 pendingTaskCount, skillVersion, serverTime
```

#### 测试 3: 任务拉取与执行
```kotlin
// 在管理后台创建任务分配给设备
// 预期: 心跳响应 pendingTaskCount > 0
// 触发 GET /api/claw-device/devices/{deviceId}/pending-tasks
// 返回 PendingTaskItem 列表
```

#### 测试 4: 结果上报
```kotlin
// 任务执行完成后
// 预期: POST /api/claw-device/tasks/{taskUuid}/result
// 请求体包含 status, result, executionTimeMs, errorMessage 等
// 响应 code = 0
```

#### 测试 5: 离线队列与补报
```kotlin
// 关闭网络后执行任务
// 预期: 结果进入 CloudEventQueue 缓存
// 恢复网络后心跳触发 flushOfflineQueue()
// 验证离线事件被批量补报
```

#### 测试 6: Token 刷新
```kotlin
// Token 接近过期时 (expiresIn - refreshWindow)
// 预期: POST /api/claw-device/token/refresh
// 请求体包含 refreshToken
// 响应包含新的 deviceToken, expiresIn
```

---

## 五、已知问题与待修复项

| 问题 | 位置 | 状态 | 修复方案 |
|:-----|:-----|:----:|:---------|
| CloudTaskExecutor 未接入真实 AgentService | CloudTaskExecutor.kt:53-64 | ⚠️ TODO | 需接入 TaskOrchestrator 或 DefaultAgentService |
| ExternalAutomationTaskExecutor 未实现 | CloudTaskExecutor.kt:90-96 | ⚠️ TODO | 需接入 ExternalAutomationEntrypoint |

---

## 六、验收标准

- [x] 接口字段与 OpenAPI 契约 100% 对齐
- [x] 设备注册流程完整 (含 Token 安全存储)
- [x] 心跳循环正常工作 (30s 间隔)
- [x] 任务拉取与结果上报链路通
- [x] 离线队列缓存与补报机制
- [x] Token 刷新机制
- [ ] **待完成**: 任务执行器接入真实 Agent 执行能力 (CMP-1940 子任务)

---

## 七、后续工作

1. **接入真实任务执行**: CloudTaskExecutor.execute() 需要接入 TaskOrchestrator 或 AgentService
2. **管理后台联调**: 验证设备列表、任务下发、执行结果查看
3. **权限白名单**: 补充 PokeClaw 权限缺失时的错误上报
4. **日志脱敏**: 确保上报的 logSnippet 不包含敏感信息

---

**文档生成**: 安卓小龙 (ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0)
**关联 Issue**: CMP-1940
