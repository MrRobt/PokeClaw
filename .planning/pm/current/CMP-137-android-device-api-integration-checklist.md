# CMP-137: PokeClaw端侧对接 — 设备API联调准备

> 问题编号：CMP-137  
> 生成时间：2026-05-17  
> 执行人：安卓小龙  
> 仓库路径：`/mnt/e/code/PokeClaw` (main分支)

---

## 一、任务目标

基于后端 `/api-contracts/device.openapi.yaml`，完成PokeClaw安卓端设备API联调准备。

---

## 二、验收清单（逐项核验）

### 2.1 DTO与API契约对齐 ✅

| DTO类 | 文件路径 | 对齐状态 | 备注 |
|:---|:---|:---|:---|
| DeviceRegisterRequest | `cloud/model/CloudModels.kt` | ✅ | deviceId/deviceName/deviceModel/androidVersion/appVersion/publicKey |
| DeviceRegisterResponse | `cloud/model/CloudModels.kt` | ✅ | deviceToken/refreshToken/expiresIn |
| DeviceHeartbeatRequest | `cloud/model/CloudModels.kt` | ✅ | batteryLevel/isCharging/networkType |
| DeviceHeartbeatResponse | `cloud/model/CloudModels.kt` | ✅ | pendingTaskCount/skillVersion/serverTime |
| PendingTaskItem | `cloud/model/CloudModels.kt` | ✅ | taskUuid/command/mode/createdAt/priority |
| TaskResultRequest | `cloud/model/CloudModels.kt` | ✅ | status/result/errorMessage + 扩展错误字段 |
| TokenRefreshRequest | `cloud/model/CloudModels.kt` | ✅ | refreshToken |
| TokenRefreshResponse | `cloud/model/CloudModels.kt` | ✅ | deviceToken/expiresIn |
| ApiResponse<T> | `cloud/model/CloudModels.kt` | ✅ | code/msg/data/isSuccess() |

**核验结论**：所有DTO字段与 `device.openapi.yaml` 组件 schemas 完全一致。

### 2.2 Retrofit接口实现 ✅

| 接口 | 文件 | 实现状态 | 验证方式 |
|:---|:---|:---|:---|
| POST /api/claw-device/register | `cloud/api/CloudDeviceApi.kt` | ✅ | 无需认证，设备注册 |
| POST /api/claw-device/heartbeat | `cloud/api/CloudDeviceApi.kt` | ✅ | Bearer Token认证 |
| GET /api/claw-device/devices/{deviceId}/pending-tasks | `cloud/api/CloudDeviceApi.kt` | ✅ | Bearer Token认证 |
| POST /api/claw-device/tasks/{taskUuid}/result | `cloud/api/CloudDeviceApi.kt` | ✅ | Bearer Token认证 |
| POST /api/claw-device/token/refresh | `cloud/api/CloudDeviceApi.kt` | ✅ | 使用refreshToken刷新 |

**核验结论**：所有设备端API接口已在 `CloudDeviceApi.kt` 中声明。

### 2.3 Token管理（Android Keystore） ✅

| 功能 | 实现 | 验证点 |
|:---|:---|:---|
| 密钥生成 | `AndroidKeystoreCloudDeviceTokenStore` | AES-GCM，密钥在Keystore |
| Token加密存储 | `saveTokens()` | 密文+IV格式，不存明文 |
| Token解密读取 | `snapshot()` | 异常时清除并返回null |
| Token刷新检测 | `shouldRefresh()` | 提前10分钟检测 |
| Token有效期检查 | `hasDeviceToken()` | 对比当前时间 |

**核验结论**：Token安全存储实现符合规范。

### 2.4 离线降级逻辑 ✅

| 功能 | 实现文件 | 验证点 |
|:---|:---|:---|
| 离线队列持久化 | `CloudEventQueue.kt` | SharedPreferences + JSON |
| 最大队列容量 | 100条 | 超限丢弃最旧 |
| 批量补报 | `flushQueue()` | 单次最多10条 |
| 指数退避重试 | `enqueue()` | 失败时延长重试间隔 |
| 任务结果缓存 | `submitTaskResult()` | Token缺失或网络失败入队 |

**核验结论**：离线降级逻辑完整。

### 2.5 编排器整合 ✅

| 功能 | 实现文件 | 说明 |
|:---|:---|:---|
| 设备注册 | `CloudNodeOrchestrator.register()` | 首次启动自动注册 |
| 心跳循环 | `startHeartbeatLoop()` | 30秒间隔协程 |
| 任务拉取 | `pollPendingTasks()` | pendingTaskCount>0触发 |
| 任务执行 | `CloudTaskExecutor.execute()` | 调用现有TaskOrchestrator |
| 结果上报 | `reportTaskResult()` | 成功上报/失败入队 |
| 错误回传 | `buildErrorDetail()` | 完整错误信息封装 |

**核验结论**：执行节点最小闭环已实现。

---

## 三、联调前置检查

### 3.1 后端服务状态

| 检查项 | 命令 | 预期结果 |
|:---|:---|:---|
| 后端健康检查 | `curl http://192.168.250.3:8080/api/health` | 返回200 |
| API文档可达 | `curl http://192.168.250.3:8080/v3/api-docs` | 返回JSON |
| 设备注册接口 | `curl -X POST http://192.168.250.3:8080/api/claw-device/register` | 返回401(未带body)或400 |

**当前状态**：后端服务需启动后方可联调。

### 3.2 端侧配置

| 配置项 | 位置 | 当前值 |
|:---|:---|:---|
| 云端Base URL | `CloudDeviceApiFactory` | 需配置为 `http://192.168.250.3:8080` |
| 设备ID生成 | `CloudNodeOrchestrator` | UUID.randomUUID()，首次持久化 |

---

## 四、待联调验证项

| 序号 | 验证项 | 联调步骤 | 预期结果 |
|:---|:---|:---|:---|
| 1 | 设备注册流程 | 清数据后首次启动 → 调用register | 返回deviceToken和refreshToken |
| 2 | 心跳发送 | 注册成功后等待30秒 | 心跳请求包含电量/网络信息 |
| 3 | 心跳响应处理 | 返回pendingTaskCount=1 | 触发任务拉取 |
| 4 | 任务拉取 | 调用getPendingTasks | 返回任务列表 |
| 5 | 任务执行 | 执行云端下发的任务 | 调用本地TaskOrchestrator |
| 6 | 结果上报 | 任务完成后调用submitTaskResult | 上报成功 |
| 7 | Token刷新 | 等待接近过期或手动触发 | 使用refreshToken获取新deviceToken |
| 8 | 离线降级 | 断网后执行任务 → 联网 | 离线结果自动补报 |
| 9 | 错误回传 | 制造任务失败场景 | 上报含错误码和截图的完整信息 |
| 10 | 并发安全 | 快速多次心跳/任务 | 无异常，状态一致 |

---

## 五、产出文件清单

| 文件路径 | 说明 | 状态 |
|:---|:---|:---|
| `app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | DTO定义 | ✅ 已完成 |
| `app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt` | Retrofit接口 | ✅ 已完成 |
| `app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt` | Token存储 | ✅ 已完成 |
| `app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt` | 云端客户端 | ✅ 已完成 |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudEventQueue.kt` | 离线队列 | ✅ 已完成 |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudNodeOrchestrator.kt` | 编排器 | ✅ 已完成 |
| `app/src/main/java/io/agents/pokeclaw/cloud/CloudTaskExecutor.kt` | 任务执行 | ✅ 已完成 |
| `docs/pokeclaw-device-node-heartbeat.md` | 方案文档 | ✅ 已完成 |
| `.planning/pm/current/CMP-137-android-device-api-integration-checklist.md` | 本联调清单 | ✅ 本次产出 |

---

## 六、阻塞项与下一步

| 阻塞项 | 状态 | 解除条件 |
|:---|:---|:---|
| 后端服务启动 | ⏳ **阻塞** | 后端服务 `192.168.250.3:8080` 可访问 |
| 后端API验证 | ⏳ **阻塞** | 后端Controller实现与契约一致 |

**当前状态更新（2026-05-17）**：
- 代码端：✅ DTO/Retrofit/Token存储/离线队列/单元测试 全部就绪
- 后端服务：⏳ `192.168.250.3:8080` 不可达（curl返回HTTP 000）
- 阻塞原因：后端服务未启动，无法进行真实联调验证

**下一步行动**：
1. 联系后端阿诚确认 `192.168.250.3:8080` 部署状态
2. 后端就绪后执行【待联调验证项】逐项验证
3. 全部验证通过后，更新CMP-137状态为done

---

## 七、关联问题

| 问题编号 | 标题 | 当前状态 | 与本任务关系 |
|:---|:---|:---|:---|
| CMP-1964 | PokeClaw端侧执行节点心跳与错误上报方案 | done | 前置方案文档 |
| CMP-2097 | PokeClaw安卓端侧执行节点最小闭环 | blocked | 依赖本任务联调验证 |
| CMP-2001 | PokeClaw设备节点注册与云端报错回传 | blocked | 依赖本任务联调验证 |
| CMP-2233 | PokeClaw端侧任务回执重试队列 | blocked | 依赖本任务离线队列 |
| CMP-2236 | PokeClaw权限白名单与失败上报测试桩 | blocked | 依赖本任务错误码 |

---

## 八、执行信息

- **实际检查文件**：
  - `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` (后端契约)
  - `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt`
  - `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt`
  - `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt`
  - `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt`
  - `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudEventQueue.kt`
  - `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudNodeOrchestrator.kt`

- **实际产出**：
  - `/mnt/e/code/PokeClaw/.planning/pm/current/CMP-137-android-device-api-integration-checklist.md`

- **改动摘要**：
  - 核验所有DTO字段与后端device.openapi.yaml schemas一致
  - 核验Retrofit接口完整覆盖设备端API路径
  - 核验Token安全存储使用Android Keystore
  - 核验离线队列实现满足降级需求
  - 产出联调准备清单，明确待联调验证项

- **待验证清单**：
  - [ ] 设备注册流程（等后端服务启动）
  - [ ] 心跳发送与响应
  - [ ] 任务拉取与执行
  - [ ] 结果上报与错误回传
  - [ ] Token刷新机制
  - [ ] 离线降级与补报

---

## 九、文档变更日志

| 日期 | 操作 | 内容 |
|:---|:---|:---|
| 2026-05-17 | 创建 | 初始版本，完成设备API联调准备清单 |
| 2026-05-17 | 更新 | 安卓小龙执行核验：代码端就绪，后端服务不可达，联调阻塞 |
|