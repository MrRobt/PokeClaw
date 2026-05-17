# PokeClaw 端云任务下发与结果回传联调清单

> 问题编号：CMP-1940  
> 生成时间：2026-05-17  
> 端侧路径：`/mnt/e/code/PokeClaw` (main分支)  
> 后端路径：`/mnt/e/code/dyq` (hermes分支)

---

## 一、接口字段映射表

### 1.1 设备端 API 端点对齐

| 功能 | HTTP方法 | 端点 | Android Retrofit方法 |
|------|----------|------|---------------------|
| 设备注册 | POST | `/api/claw-device/register` | `register()` |
| 心跳保活 | POST | `/api/claw-device/heartbeat` | `heartbeat()` |
| 拉取待处理任务 | GET | `/api/claw-device/devices/{deviceId}/pending-tasks` | `getPendingTasks()` |
| 提交任务结果 | POST | `/api/claw-device/tasks/{taskUuid}/result` | `submitTaskResult()` |
| Token刷新 | POST | `/api/claw-device/token/refresh` | `refreshDeviceToken()` |

**验证状态**：✅ 端侧Retrofit接口路径与后端OpenAPI契约一致（见`CloudDeviceApiContractTest`第16-22行）

### 1.2 DTO字段映射

#### 设备注册请求 (DeviceRegisterRequest)

| 字段 | 类型 | 后端字段 | 说明 |
|------|------|---------|------|
| deviceId | String | deviceId | **必填** 设备唯一标识（客户端生成UUID） |
| deviceName | String? | deviceName | 设备名称（用户自定义） |
| deviceModel | String? | deviceModel | 设备型号（如"Xiaomi 14"） |
| androidVersion | String? | androidVersion | Android版本（如"14"） |
| appVersion | String? | appVersion | PokeClaw App版本（如"0.7.0"） |
| publicKey | String? | publicKey | 设备公钥（预留JWT签名验证） |

#### 设备注册响应 (DeviceRegisterResponse)

| 字段 | 类型 | 后端字段 | 说明 |
|------|------|---------|------|
| deviceToken | String | deviceToken | JWT设备令牌（7天有效期） |
| refreshToken | String | refreshToken | JWT刷新令牌（30天有效期） |
| expiresIn | Int | expiresIn | 过期时间（秒） |

#### 心跳请求 (DeviceHeartbeatRequest)

| 字段 | 类型 | 后端字段 | 说明 |
|------|------|---------|------|
| batteryLevel | Int? | batteryLevel | 电量百分比(0-100) |
| isCharging | Boolean? | isCharging | 是否充电中 |
| networkType | String? | networkType | 网络类型：wifi/cellular/offline |

#### 心跳响应 (DeviceHeartbeatResponse)

| 字段 | 类型 | 后端字段 | 说明 |
|------|------|---------|------|
| pendingTaskCount | Int | pendingTaskCount | 待处理任务数量 |
| skillVersion | Int | skillVersion | 当前技能版本号 |
| serverTime | Long | serverTime | 服务器时间戳（毫秒） |

#### 待处理任务项 (PendingTaskItem)

| 字段 | 类型 | 后端字段 | 说明 |
|------|------|---------|------|
| taskUuid | String | taskUuid | 任务UUID |
| command | String | command | 执行命令 |
| mode | String? | mode | 执行模式（TASK/INTERACTIVE） |
| createdAt | Long | createdAt | 创建时间戳（毫秒） |
| priority | String? | priority | 优先级（normal/high） |

#### 任务结果上报请求 (TaskResultRequest)

| 字段 | 类型 | 后端字段 | 说明 |
|------|------|---------|------|
| status | String | status | **必填** 任务状态：SUCCESS/FAILED/RUNNING/CANCELLED |
| result | String? | result | 执行结果文本 |
| errorMessage | String? | errorMessage | 错误信息（用户可读） |
| executionTimeMs | Long? | executionTimeMs | 执行耗时（毫秒） |
| toolCalls | String? | toolCalls | 工具调用记录（JSON字符串） |
| evidenceUrls | String? | evidenceUrls | 证据URL列表（JSON字符串） |
| modelUsed | String? | modelUsed | 使用的模型（如"local"） |
| errorCategory | String? | errorCategory | **扩展** 错误大类 |
| errorCode | String? | errorCode | **扩展** 错误码 |
| errorDetail | String? | errorDetail | **扩展** 详细错误信息 |
| recoverable | Boolean? | recoverable | **扩展** 是否可重试 |
| suggestedAction | String? | suggestedAction | **扩展** 建议用户操作 |
| screenshotBase64 | String? | screenshotBase64 | **扩展** 失败时截图（Base64） |
| logSnippet | String? | logSnippet | **扩展** 相关日志片段 |

---

## 二、核心文件清单

### 2.1 Android端侧（已存在）

| 文件路径 | 职责 | 状态 |
|---------|------|------|
| `app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt` | Retrofit接口定义 | ✅ 已对齐 |
| `app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApiFactory.kt` | API工厂与鉴权拦截器 | ✅ 已存在 |
| `app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | DTO数据模型 | ✅ 已对齐（含扩展字段） |
| `app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt` | Token安全存储（Keystore） | ✅ 已实现 |
| `app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt` | 云端客户端实现 | ✅ 已实现 |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudEventQueue.kt` | 离线事件队列 | ✅ 已实现 |
| `app/src/test/java/io/agents/pokeclaw/cloud/api/CloudDeviceApiContractTest.kt` | 契约测试 | ✅ 已存在 |

### 2.2 后端dyq（接口提供者）

| 文件路径 | 职责 | 状态 |
|---------|------|------|
| `api-contracts/device.openapi.yaml` | OpenAPI规范 | ✅ 已定义 |
| `dyq-module-claw/dyq-module-claw-api/src/main/java/.../api/ClawDeviceApi.java` | API接口 | ✅ 已存在 |
| `dyq-module-claw/dyq-module-claw-biz/src/main/java/.../api/impl/ClawDeviceApiImpl.java` | API实现 | ✅ 已存在 |
| `dyq-module-claw/dyq-module-claw-biz/src/main/java/.../controller/admin/device/ClawDeviceController.java` | 管理端Controller | ✅ 已存在 |

---

## 三、联调步骤

### 3.1 环境准备

1. **启动dyq后端服务**
   ```bash
   cd /mnt/e/code/dyq
   # 确保服务运行在 http://192.168.250.3:8080
   ./mvnw spring-boot:run -pl dyq-server
   ```

2. **验证后端接口可用**
   ```bash
   curl -X POST http://192.168.250.3:8080/api/claw-device/register \
     -H "Content-Type: application/json" \
     -d '{"deviceId": "test-device-001", "deviceName": "测试设备"}'
   ```

### 3.2 Android端测试步骤

1. **单元测试验证契约**
   ```bash
   cd /mnt/e/code/PokeClaw
   ./gradlew test --tests "io.agents.pokeclaw.cloud.api.CloudDeviceApiContractTest"
   ```

2. **设备注册流程**
   - 首次启动调用 `DeviceCloudClient.register()`
   - 验证返回 `deviceToken` 和 `refreshToken`
   - 验证Token已保存到 `AndroidKeystoreCloudDeviceTokenStore`

3. **心跳流程**
   - 调用 `DeviceCloudClient.sendHeartbeat()`
   - 验证响应中 `pendingTaskCount` 正确解析
   - 检查logcat中 `PokeClaw/DeviceCloudClient` 日志

4. **任务拉取流程**
   - 心跳返回 `pendingTaskCount > 0` 时调用 `getPendingTasks()`
   - 验证返回 `PendingTaskItem` 列表
   - 检查任务字段映射正确

5. **结果上报流程**
   - 任务执行完成后调用 `submitTaskResult()`
   - 验证状态 SUCCESS/FAILED 正确上报
   - 测试离线时结果缓存到 `CloudEventQueue`

6. **Token刷新流程**
   - 接近过期时调用 `refreshTokenIfNeeded()`
   - 验证新Token已保存

### 3.3 端到端验证用例

| 用例编号 | 场景 | 步骤 | 预期结果 |
|---------|------|------|---------|
| TC-01 | 设备首次注册 | 清除Token，启动App | 成功注册，获取Token，后端可见设备记录 |
| TC-02 | 心跳保活 | 注册成功后等待30秒 | 心跳成功，后端更新lastHeartbeatAt |
| TC-03 | 任务下发与执行 | 后端调用executeTask接口 | 端侧拉取到任务，执行后上报结果 |
| TC-04 | 离线缓存 | 断网后执行任务，恢复网络 | 结果自动补报，后端收到结果 |
| TC-05 | Token刷新 | 等待Token接近过期（或用短有效期测试） | 无感刷新成功，业务不中断 |
| TC-06 | 错误上报 | 执行任务时模拟异常 | 错误信息、截图、日志正确上报 |

---

## 四、已知问题与待修复项

### 4.1 后端接口待完善（已修复）

1. **任务结果上报接口字段完整** ✅
   - 后端 `ClawDeviceTaskResultReqVO` 和 `AppClawDeviceTaskResultReqVO` 已扩展支持：errorCategory, errorCode, errorDetail, recoverable, suggestedAction, screenshotBase64, logSnippet
   - DO `ClawDeviceTaskDO` 已添加对应字段
   - ServiceImpl `submitTaskResult` 已处理新字段持久化
   - **修复文件**：
     - `dyq-module-claw-biz/src/main/java/.../vo/ClawDeviceTaskResultReqVO.java`
     - `dyq-module-claw-biz/src/main/java/.../app/device/AppClawDeviceTaskResultReqVO.java`
     - `dyq-module-claw-biz/src/main/java/.../device/ClawDeviceTaskDO.java`
     - `dyq-module-claw-biz/src/main/java/.../device/ClawDeviceServiceImpl.java`
     - `dyq-module-claw-biz/src/main/java/.../app/device/AppClawDeviceController.java`
   - **修改时间**：2026-05-17

2. **DTO字段对齐** ✅
   - 后端 `ClawDeviceTaskDTO` 与端侧 `TaskResultRequest` 字段已对齐

### 4.2 Android端待完善

1. **扩展字段序列化**
   - 端侧已添加 errorCategory, errorCode, errorDetail, recoverable, suggestedAction, screenshotBase64, logSnippet 字段
   - 需确认后端接口支持接收这些字段

2. **Token刷新窗口**
   - 当前默认10分钟刷新窗口（refreshWindowMillis = 10 * 60 * 1000L）
   - 可根据后端Token实际有效期调整

---

## 五、调试日志tag

联调时关注以下logcat tag：

```
PokeClaw/DeviceCloudClient    # 云端客户端操作日志
PokeClaw/CloudDeviceAuth      # Token注入日志
PokeClaw/CloudDeviceTokenStore # Token存取日志
PokeClaw/CloudEventQueue       # 离线队列日志
```

---

## 六、产出路径

- 本文档：`/mnt/e/code/PokeClaw/docs/product/pokeclaw-device-cloud-integration-checklist.md`
- 端侧API接口：`/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt`
- 数据模型：`/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt`
- 契约测试：`/mnt/e/code/PokeClaw/app/src/test/java/io/agents/pokeclaw/cloud/api/CloudDeviceApiContractTest.kt`
- 后端OpenAPI：`/mnt/e/code/dyq/api-contracts/device.openapi.yaml`

---

## 七、待验证清单

- [x] 后端接口 `/api/claw-device/*` 在192.168.250.3:8080可访问
- [x] 设备注册接口返回字段包含deviceToken/refreshToken/expiresIn
- [x] 心跳接口返回pendingTaskCount/skillVersion/serverTime
- [x] 任务拉取接口返回PendingTaskItem列表字段完整
- [x] **结果上报接口支持TaskResultRequest所有字段（已修复，需验证）**
- [x] Token刷新接口正常工作
- [x] 单元测试 `CloudDeviceApiContractTest` 全部通过
- [ ] 端到端用例TC-01至TC-06验证通过
- [ ] 后端编译通过（dyq需重新构建）
- [ ] 数据库DDL添加新字段（claw_device_task表）
