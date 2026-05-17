# PokeClaw 设备 API 联调清单

> 对应 Issue: CMP-137 【Android】PokeClaw端侧对接 — 设备API联调准备
> 生成时间: 2026-05-17
> 端侧仓库: /mnt/e/code/PokeClaw (main分支)
> 后端仓库: /mnt/e/code/dyq (hermes分支)

## 一、联调前提验证

### 1.1 后端编译状态

| 项目 | 状态 | 备注 |
|------|------|------|
| dyq 后端编译 | ⚠️ 待修复 | `ClawTaskOrchestratorServiceImpl.java:298` 存在语法错误（已修复） |
| 设备 API 契约 | ✅ 已完成 | `/api-contracts/device.openapi.yaml` |

**已修复问题:**
- 文件: `dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/service/orchestration/ClawTaskOrchestratorServiceImpl.java`
- 错误: 第298行日志字符串换行处缺少闭合引号
- 修复: `"重试任务失败: 已超过最大重试次数, taskUuid={}, retryCount={}/{},` → `"重试任务失败: 已超过最大重试次数, taskUuid={}, retryCount={}/{}",`

### 1.2 端侧代码完整性

| 模块 | 文件 | 状态 |
|------|------|------|
| Retrofit API 接口 | `cloud/api/CloudDeviceApi.kt` | ✅ 已对齐契约 |
| API 工厂 | `cloud/api/CloudDeviceApiFactory.kt` | ✅ 完成 |
| DTO 模型 | `cloud/model/CloudModels.kt` | ✅ 已对齐 device.openapi.yaml |
| Token 存储 | `cloud/auth/CloudDeviceTokenStore.kt` | ✅ Android Keystore 加密 |
| 云端客户端 | `cloud/DeviceCloudClient.kt` | ✅ 完成 |
| 心跳管理器 | `cloud/CloudHeartbeatManager.kt` | ✅ 完成 |
| 离线队列 | `cloud/CloudEventQueue.kt` | ✅ 完成 |
| 执行节点 | `cloudnode/CloudExecutorNode.kt` | ✅ 完成 |

## 二、API 字段映射对照表

### 2.1 设备注册 (/api/claw-device/register)

| 字段 | Android DTO | OpenAPI 契约 | 说明 |
|------|-------------|--------------|------|
| deviceId | `DeviceRegisterRequest.deviceId` | ✅ 对齐 | 设备唯一标识 |
| deviceName | `DeviceRegisterRequest.deviceName` | ✅ 对齐 | 设备名称 |
| deviceModel | `DeviceRegisterRequest.deviceModel` | ✅ 对齐 | 设备型号 |
| androidVersion | `DeviceRegisterRequest.androidVersion` | ✅ 对齐 | Android版本 |
| appVersion | `DeviceRegisterRequest.appVersion` | ✅ 对齐 | App版本 |
| publicKey | `DeviceRegisterRequest.publicKey` | ✅ 对齐 | 公钥（可选） |

**响应字段:**
| 字段 | Android DTO | OpenAPI 契约 |
|------|-------------|--------------|
| deviceToken | `DeviceRegisterResponse.deviceToken` | ✅ 对齐 |
| refreshToken | `DeviceRegisterResponse.refreshToken` | ✅ 对齐 |
| expiresIn | `DeviceRegisterResponse.expiresIn` | ✅ 对齐 |

### 2.2 心跳 (/api/claw-device/heartbeat)

| 请求字段 | Android DTO | OpenAPI 契约 |
|----------|-------------|--------------|
| batteryLevel | `DeviceHeartbeatRequest.batteryLevel` | ✅ 对齐 |
| isCharging | `DeviceHeartbeatRequest.isCharging` | ✅ 对齐 |
| networkType | `DeviceHeartbeatRequest.networkType` | ✅ 对齐 (wifi/cellular/offline) |

**响应字段:**
| 字段 | Android DTO | OpenAPI 契约 | 用途 |
|------|-------------|--------------|------|
| pendingTaskCount | `DeviceHeartbeatResponse.pendingTaskCount` | ✅ 对齐 | 触发任务拉取 |
| skillVersion | `DeviceHeartbeatResponse.skillVersion` | ✅ 对齐 | 技能版本同步 |
| serverTime | `DeviceHeartbeatResponse.serverTime` | ✅ 对齐 | 时间校准 |

### 2.3 任务轮询 (/api/claw-device/devices/{deviceId}/pending-tasks)

| 字段 | Android DTO | OpenAPI 契约 |
|------|-------------|--------------|
| taskUuid | `PendingTaskItem.taskUuid` | ✅ 对齐 |
| command | `PendingTaskItem.command` | ✅ 对齐 |
| mode | `PendingTaskItem.mode` | ✅ 对齐 |
| createdAt | `PendingTaskItem.createdAt` | ✅ 对齐 |
| priority | `PendingTaskItem.priority` | ✅ 对齐 |

### 2.4 结果上报 (/api/claw-device/tasks/{taskUuid}/result)

| 字段 | Android DTO | OpenAPI 契约 | 说明 |
|------|-------------|--------------|------|
| status | `TaskResultRequest.status` | ✅ 对齐 | SUCCESS/FAILED/RUNNING/CANCELLED |
| result | `TaskResultRequest.result` | ✅ 对齐 | 执行结果文本 |
| errorMessage | `TaskResultRequest.errorMessage` | ✅ 对齐 | 错误信息 |
| executionTimeMs | `TaskResultRequest.executionTimeMs` | ✅ 对齐 | 执行耗时 |
| toolCalls | `TaskResultRequest.toolCalls` | ✅ 对齐 | 工具调用记录 |
| evidenceUrls | `TaskResultRequest.evidenceUrls` | ✅ 对齐 | 证据URL |
| modelUsed | `TaskResultRequest.modelUsed` | ✅ 对齐 | 使用模型 |

**扩展字段（失败回传）:**
| 字段 | Android DTO | 说明 |
|------|-------------|------|
| errorCategory | `TaskResultRequest.errorCategory` | 错误大类 |
| errorCode | `TaskResultRequest.errorCode` | 错误码 |
| errorDetail | `TaskResultRequest.errorDetail` | 详细错误 |
| recoverable | `TaskResultRequest.recoverable` | 是否可重试 |
| suggestedAction | `TaskResultRequest.suggestedAction` | 建议操作 |
| screenshotBase64 | `TaskResultRequest.screenshotBase64` | 截图（可选） |
| logSnippet | `TaskResultRequest.logSnippet` | 日志片段 |

### 2.5 Token 刷新 (/api/claw-device/token/refresh)

| 请求字段 | Android DTO | OpenAPI 契约 |
|----------|-------------|--------------|
| refreshToken | `TokenRefreshRequest.refreshToken` | ✅ 对齐 |

**响应字段:**
| 字段 | Android DTO | OpenAPI 契约 |
|------|-------------|--------------|
| deviceToken | `TokenRefreshResponse.deviceToken` | ✅ 对齐 |
| expiresIn | `TokenRefreshResponse.expiresIn` | ✅ 对齐 |

## 三、Token 管理策略

### 3.1 Android Keystore 存储

```kotlin
// CloudDeviceTokenStore.kt 关键实现
- 加密算法: AES/GCM/NoPadding
- KeyStore: AndroidKeyStore
- 存储位置: SharedPreferences（仅保存加密后的密文+IV）
- 刷新窗口: 默认10分钟（expiresAt - now <= 10min 时触发刷新）
```

### 3.2 自动刷新机制

```kotlin
// RetrofitDeviceCloudClient.refreshTokenIfNeeded()
- 条件: token.shouldRefresh() = true
- 接口: POST /api/claw-device/token/refresh
- 失败处理: 清除本地令牌，下次重新注册
```

### 3.3 鉴权拦截器

```kotlin
// CloudDeviceAuthInterceptor
- 注册/刷新接口: 不强制注入 Authorization
- 其他接口: 自动注入 Bearer Token
- Token来源: CloudDeviceTokenStore.snapshot()
```

## 四、离线降级策略

### 4.1 离线队列 (CloudEventQueue)

| 属性 | 配置 |
|------|------|
| 最大缓存数 | 100 条 |
| 单次补报上限 | 10 条 |
| 重试退避 | 指数退避 (1s, 2s, 4s, 8s, 16s, 32s) |
| 存储位置 | SharedPreferences (JSON序列化) |

### 4.2 降级触发条件

1. 缺少有效 deviceToken
2. 网络异常（IOException）
3. 云端返回非成功状态码

### 4.3 降级处理流程

```
任务执行完成 → 尝试上报结果
    ↓ 失败/无令牌
进入离线队列 → 持久化到 SharedPreferences
    ↓ 下次心跳成功
flushOfflineQueue() → 批量补报 → 成功后移除
```

## 五、联调步骤

### 5.1 环境准备

1. **启动后端服务**
   ```bash
   cd /mnt/e/code/dyq
   mvn clean compile -DskipTests
   # 启动应用，确认 /api/claw-device/* 接口可访问
   ```

2. **确认网络连通性**
   ```bash
   # 从测试设备或模拟器访问
   curl http://192.168.250.3:8080/api/claw-device/register \
     -X POST -H "Content-Type: application/json" \
     -d '{"deviceId":"test-device-001"}'
   ```

### 5.2 接口联调顺序

| 顺序 | 接口 | 验证点 |
|------|------|--------|
| 1 | POST /register | 能获取 deviceToken + refreshToken |
| 2 | POST /heartbeat | 带 Bearer Token 能成功，返回 pendingTaskCount |
| 3 | GET /pending-tasks | 能拉取到任务列表 |
| 4 | POST /tasks/{uuid}/result | 能上报任务结果 |
| 5 | POST /token/refresh | 用 refreshToken 能换取新 deviceToken |

### 5.3 Android 端验证命令

```bash
# 1. 运行单元测试
./gradlew :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloud.api.CloudDeviceApiContractTest"

# 2. 检查云端模型字段对齐
./gradlew :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest"

# 3. 完整构建
./gradlew :app:assembleDebug
```

## 六、测试用例清单

### 6.1 单元测试覆盖

| 测试类 | 路径 | 覆盖点 |
|--------|------|--------|
| CloudDeviceApiContractTest.kt | `app/src/test/java/.../cloud/api/` | API 路径、地址规范化、Bearer前缀、令牌快照、响应成功判断 |
| CloudExecutorNodeContractTest.kt | `app/src/test/java/.../cloudnode/` | 执行节点协议、状态机、上报字段 |

### 6.2 E2E 测试场景

| 场景 | 步骤 | 预期结果 |
|------|------|----------|
| 首次注册 | 清理本地存储 → 启动App → 调用 register() | 获得有效 deviceToken，存入 Keystore |
| 心跳保活 | 注册成功后等待心跳调度 | 每1分钟发送一次心跳，状态=online |
| 任务拉取 | 后端创建任务 → 等待心跳响应 | 心跳返回 pendingTaskCount>0 → 触发拉取 |
| 结果上报 | 执行任务 → 调用 submitTaskResult() | 云端收到结果，任务状态更新 |
| 离线缓存 | 断网 → 执行任务 → 恢复网络 → 心跳 | 离线期间结果缓存，恢复后补报 |
| Token刷新 | 等待token临近过期 → 发送请求 | 自动调用 refresh，获取新token |

## 七、风险与待确认项

| 风险项 | 状态 | 说明 |
|--------|------|------|
| 后端编译阻塞 | 🔄 已修复待验证 | 语法错误已修复，需重新编译验证 |
| Token有效期 | ⚠️ 待确认 | 后端配置 deviceToken=7天，refreshToken=30天 |
| 心跳间隔 | ⚠️ 待调优 | 当前1分钟，实际环境可调整 |
| 离线队列上限 | ✅ 可控 | 100条，超限时丢弃旧数据 |
| 敏感信息脱敏 | ✅ 已处理 | screenshotBase64/logSnippet 可选，队列有长度截断 |

## 八、产出文件清单

| 文件 | 路径 | 说明 |
|------|------|------|
| CloudDeviceApi.kt | `app/src/main/java/io/agents/pokeclaw/cloud/api/` | Retrofit API 接口 |
| CloudDeviceApiFactory.kt | `app/src/main/java/io/agents/pokeclaw/cloud/api/` | API 工厂与鉴权拦截器 |
| CloudModels.kt | `app/src/main/java/io/agents/pokeclaw/cloud/model/` | DTO 数据模型 |
| CloudDeviceTokenStore.kt | `app/src/main/java/io/agents/pokeclaw/cloud/auth/` | Keystore Token 存储 |
| DeviceCloudClient.kt | `app/src/main/java/io/agents/pokeclaw/cloud/` | 云端客户端实现 |
| CloudHeartbeatManager.kt | `app/src/main/java/io/agents/pokeclaw/cloud/` | 心跳管理器 |
| CloudEventQueue.kt | `app/src/main/java/io/agents/pokeclaw/cloud/` | 离线事件队列 |
| CloudExecutorNode.kt | `app/src/main/java/io/agents/pokeclaw/cloudnode/` | 执行节点引擎 |
| 本联调清单 | `.planning/pokeclaw-device-api-integration-checklist.md` | 本文档 |

## 九、下一步行动

1. **后端**: 重新编译验证修复后的代码
2. **端侧**: 运行单元测试确认契约对齐
3. **联调**: 部署后端到测试环境，进行端到端验证
4. **QA**: 按 6.2 节 E2E 场景执行测试

---

**实际检查文件:**
- /mnt/e/code/dyq/api-contracts/device.openapi.yaml
- /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt
- /mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt
- /mnt/e/code/PokeClaw/app/src/test/java/io/agents/pokeclaw/cloud/api/CloudDeviceApiContractTest.kt

**实际执行命令:**
- cd /mnt/e/code/dyq && mvn compile -pl dyq-module-claw/dyq-module-claw-biz -am -DskipTests
- git status / git branch --show-current

**改动摘要:**
- 修复后端 ClawTaskOrchestratorServiceImpl.java:298 语法错误（未闭合字符串）

**待验证清单:**
- [ ] 后端重新编译通过
- [ ] 单元测试 CloudDeviceApiContractTest 通过
- [ ] 单元测试 CloudExecutorNodeContractTest 通过
- [ ] E2E 注册接口调用成功
- [ ] E2E 心跳接口调用成功
- [ ] E2E 任务轮询与上报链路完整
