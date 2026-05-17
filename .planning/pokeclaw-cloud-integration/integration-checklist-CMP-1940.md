# PokeClaw 端云任务下发与结果回传联调清单

## 任务编号
CMP-1940

## 目标
推进 PokeClaw 安卓端对接 dyq，聚焦设备注册、任务下发、执行结果回传的端云协同清单，产出可执行的联调步骤。

## 仓库路径
- PokeClaw 安卓端: `/mnt/e/code/PokeClaw` (main 分支)
- DYQ 后端: `/mnt/e/code/dyq` (hermes 分支)

---

## 一、接口字段映射

### 1. 设备注册 (POST /api/claw-device/register)

| 请求字段 | 类型 | 说明 | PokeClaw 端侧来源 |
|---------|------|------|------------------|
| deviceId | String | 设备唯一标识 | 首次启动生成 UUID，持久化到 MMKV |
| deviceName | String? | 设备名称 | Build.MODEL + 用户自定义 |
| deviceModel | String? | 设备型号 | Build.MODEL |
| androidVersion | String? | Android 版本 | Build.VERSION.RELEASE |
| appVersion | String? | App 版本 | PackageManager 获取 |
| publicKey | String? | 公钥 | 预留字段，可 null |

| 响应字段 | 类型 | 说明 | PokeClaw 端侧存储 |
|---------|------|------|-----------------|
| deviceToken | String | JWT 设备令牌 | Android Keystore 加密存储 |
| refreshToken | String | JWT 刷新令牌 | Android Keystore 加密存储 |
| expiresIn | Int | 过期时间(秒) | 计算 expiresAtMillis |

**Kotlin DTO**: `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt`
- `DeviceRegisterRequest`
- `DeviceRegisterResponse`

### 2. 设备心跳 (POST /api/claw-device/heartbeat)

| 请求字段 | 类型 | 说明 | PokeClaw 端侧来源 |
|---------|------|------|------------------|
| batteryLevel | Int? | 电量百分比 | BatteryManager |
| isCharging | Boolean? | 是否充电中 | BatteryManager |
| networkType | String? | 网络类型 | ConnectivityManager (wifi/cellular/offline) |

| 响应字段 | 类型 | 说明 | PokeClaw 端侧使用 |
|---------|------|------|-----------------|
| pendingTaskCount | Int | 待处理任务数 | >0 时触发任务拉取 |
| skillVersion | Int | 技能版本号 | 用于本地技能更新判断 |
| serverTime | Long | 服务器时间戳 | 时钟同步参考 |

**Kotlin DTO**: `DeviceHeartbeatRequest`, `DeviceHeartbeatResponse`

### 3. 任务拉取 (GET /api/claw-device/devices/{deviceId}/pending-tasks)

| 响应字段 | 类型 | 说明 |
|---------|------|------|
| taskUuid | String | 任务 UUID |
| command | String | 执行命令 |
| mode | String? | 执行模式 (TASK/INTERACTIVE) |
| createdAt | Long | 创建时间戳(毫秒) |
| priority | String? | 优先级 (normal/high) |

**Kotlin DTO**: `PendingTaskItem`

### 4. 任务结果上报 (POST /api/claw-device/tasks/{taskUuid}/result)

| 请求字段 | 类型 | 说明 | PokeClaw 端侧来源 |
|---------|------|------|------------------|
| status | String | 任务状态 SUCCESS/FAILED/RUNNING/CANCELLED | 执行结果枚举 |
| result | String? | 执行结果文本 | 任务输出 |
| errorMessage | String? | 错误信息(用户可读) | 异常处理 |
| executionTimeMs | Long? | 执行耗时(毫秒) | 计时器 |
| toolCalls | String? | 工具调用记录(JSON) | 执行轨迹 |
| evidenceUrls | String? | 证据URL列表(JSON) | 截图/日志上传后URL |
| modelUsed | String? | 使用的模型 | local/cloud 模型名 |
| errorCategory | String? | 错误大类 | 错误分类 |
| errorCode | String? | 错误码 | 错误码定义 |
| errorDetail | String? | 详细错误信息 | 技术详情 |
| recoverable | Boolean? | 是否可重试 | 重试策略判断 |
| suggestedAction | String? | 建议用户操作 | 用户引导 |
| screenshotBase64 | String? | 失败截图(Base64) | 截图(可选) |
| logSnippet | String? | 日志片段 | 关键日志 |

**Kotlin DTO**: `TaskResultRequest`

### 5. Token 刷新 (POST /api/claw-device/token/refresh)

| 请求字段 | 类型 | 说明 |
|---------|------|------|
| refreshToken | String | 刷新令牌 |

| 响应字段 | 类型 | 说明 |
|---------|------|------|
| deviceToken | String | 新设备令牌 |
| expiresIn | Int | 过期时间(秒) |

**Kotlin DTO**: `TokenRefreshRequest`, `TokenRefreshResponse`

---

## 二、端侧 Kotlin 文件清单

### 核心文件（已存在）

| 文件路径 | 职责 |
|---------|------|
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt` | 设备云端客户端接口与 Retrofit 实现 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApi.kt` | Retrofit API 接口定义 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/CloudDeviceApiFactory.kt` | API 工厂，构建 Retrofit 实例 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/auth/CloudDeviceTokenStore.kt` | JWT Token 安全存储(Keystore) |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudEventQueue.kt` | 离线任务结果队列 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | 数据模型(DTO)定义 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloudnode/CloudExecutorNode.kt` | 云端执行节点引擎 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloudnode/CloudTaskExecutorBridge.kt` | 任务执行桥接 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudTaskExecutor.kt` | 云端任务执行器 |

### 测试文件

| 文件路径 | 职责 |
|---------|------|
| `/mnt/e/code/PokeClaw/app/src/test/java/io/agents/pokeclaw/cloud/api/CloudDeviceApiContractTest.kt` | API 契约测试 |
| `/mnt/e/code/PokeClaw/app/src/test/java/io/agents/pokeclaw/cloudnode/CloudExecutorNodeContractTest.kt` | 执行节点契约测试 |

---

## 三、后端 Java 文件清单

| 文件路径 | 职责 |
|---------|------|
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/controller/app/device/AppClawDeviceController.java` | 设备端 API Controller |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/service/device/ClawDeviceService.java` | Service 接口 |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/service/device/ClawDeviceServiceImpl.java` | Service 实现 |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/dal/dataobject/device/ClawDeviceDO.java` | 设备 DO |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-biz/src/main/java/com/douyouqu/dyq/module/claw/dal/dataobject/device/ClawDeviceTaskDO.java` | 任务 DO |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-api/src/main/java/com/douyouqu/dyq/module/claw/api/ClawDeviceApi.java` | API 接口定义 |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-api/src/main/java/com/douyouqu/dyq/module/claw/api/dto/ClawDeviceDTO.java` | 设备 DTO |
| `/mnt/e/code/dyq/dyq-module-claw/dyq-module-claw-api/src/main/java/com/douyouqu/dyq/module/claw/api/dto/ClawDeviceTaskDTO.java` | 任务 DTO |

### 契约文件

| 文件路径 | 职责 |
|---------|------|
| `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` | OpenAPI 3.0 设备 API 契约 |

---

## 四、联调步骤

### 步骤 1: 后端服务启动验证

```bash
# 1.1 检查后端编译状态
cd /mnt/e/code/dyq
mvn clean compile -pl dyq-module-claw/dyq-module-claw-biz -am

# 1.2 启动后端服务（确认端口 8080）
cd dyq-server
mvn spring-boot:run -Dspring.profiles.active=dev

# 1.3 验证设备注册接口
curl -X POST http://192.168.250.3:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "test-device-001",
    "deviceName": "测试设备",
    "deviceModel": "Pixel 8",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'
```

### 步骤 2: PokeClaw 端侧编译验证

```bash
# 2.1 检查 Kotlin 编译
cd /mnt/e/code/PokeClaw
./gradlew :app:compileDebugKotlin

# 2.2 检查单元测试
cd /mnt/e/code/PokeClaw
./gradlew :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloud.*"
```

### 步骤 3: 端到端联调验证

```bash
# 3.1 设备注册联调
curl -X POST http://192.168.250.3:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "pokeclaw-test-001",
    "deviceName": "PokeClaw测试机",
    "deviceModel": "Pixel 8 Pro",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'
# 预期响应: {"code":0,"data":{"deviceToken":"...","refreshToken":"...","expiresIn":604800}}

# 3.2 心跳联调（使用注册返回的 deviceToken）
curl -X POST http://192.168.250.3:8080/api/claw-device/heartbeat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {deviceToken}" \
  -d '{
    "batteryLevel": 85,
    "isCharging": false,
    "networkType": "wifi"
  }'
# 预期响应: {"code":0,"data":{"pendingTaskCount":0,"skillVersion":0,"serverTime":...}}

# 3.3 任务创建（管理端接口）
curl -X POST http://192.168.250.3:8080/admin-api/claw/device/task \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {adminToken}" \
  -d '{
    "deviceId": "pokeclaw-test-001",
    "command": "打开微信并检查新消息",
    "mode": "TASK",
    "priority": "normal"
  }'

# 3.4 任务拉取（设备端接口）
curl http://192.168.250.3:8080/api/claw-device/devices/pokeclaw-test-001/pending-tasks \
  -H "Authorization: Bearer {deviceToken}"
# 预期响应: [{"taskUuid":"...","command":"打开微信并检查新消息","mode":"TASK",...}]

# 3.5 任务结果上报
curl -X POST http://192.168.250.3:8080/api/claw-device/tasks/{taskUuid}/result \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {deviceToken}" \
  -d '{
    "status": "SUCCESS",
    "result": "已打开微信，发现3条新消息",
    "executionTimeMs": 5234,
    "modelUsed": "local"
  }'
```

---

## 五、端云协同流程图

```
┌─────────────┐          ┌──────────────┐          ┌─────────────┐
│ PokeClaw    │          │ DYQ Backend  │          │ Admin/Web   │
│ 安卓端侧    │          │ 小龙虾后端   │          │ 管理后台    │
└──────┬──────┘          └──────┬───────┘          └──────┬──────┘
       │                        │                         │
       │ ① POST /register      │                         │
       │───────────────────────>│                         │
       │                        │                         │
       │ ② deviceToken          │                         │
       │<───────────────────────│                         │
       │                        │                         │
       │ ③ POST /heartbeat     │                         │
       │ (每30秒)               │                         │
       │───────────────────────>│                         │
       │                        │                         │
       │ ④ pendingTaskCount > 0 │                         │
       │<───────────────────────│                         │
       │                        │                         │
       │ ⑤ GET /pending-tasks   │                         │
       │───────────────────────>│                         │
       │                        │                         │
       │ ⑥ TaskItem[]           │                         │
       │<───────────────────────│                         │
       │                        │                         │
       │ ═══════════════════════════════════════════════  │
       │ ⑦ 执行任务 (本地执行)   │                         │
       │ ═══════════════════════════════════════════════  │
       │                        │                         │
       │ ⑧ POST /result        │                         │
       │───────────────────────>│                         │
       │                        │                         │
       │ ⑨ 离线时缓存到         │                         │
       │ CloudEventQueue        │                         │
       │ 网络恢复后补报         │                         │
       │───────────────────────>│                         │
```

---

## 六、关键设计决策

### 6.1 Token 安全存储
- 使用 `AndroidKeystoreCloudDeviceTokenStore` 实现
- Token 加密后存储，密钥存 Android Keystore
- 不直接明文写入 SharedPreferences

### 6.2 离线队列
- 使用 `CloudEventQueue` 实现
- 最大缓存 100 条结果
- 指数退避重试策略
- 网络恢复后自动补报

### 6.3 错误回传
- 完整错误分类体系: errorCategory, errorCode
- 支持截图和日志片段上报
- 可恢复性标记 (recoverable)

### 6.4 任务状态机
```
PENDING -> RUNNING -> SUCCESS
                    -> FAILED
                    -> CANCELLED
```

---

## 七、待验证清单

- [ ] 后端 `/api/claw-device/*` 接口可正常访问
- [ ] PokeClaw 端侧编译通过
- [ ] 设备注册流程端到端验证
- [ ] 心跳保活机制验证 (30秒间隔)
- [ ] 任务下发-执行-回传完整闭环验证
- [ ] 离线队列缓存与补报机制验证
- [ ] Token 刷新机制验证
- [ ] 错误回传字段完整性验证

---

## 八、产出路径

| 产物 | 路径 |
|-----|------|
| 本联调清单 | `/mnt/e/code/PokeClaw/.planning/pokeclaw-cloud-integration/integration-checklist-CMP-1940.md` |
| PokeClaw 端侧代码 | `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/` |
| PokeClaw 端侧测试 | `/mnt/e/code/PokeClaw/app/src/test/java/io/agents/pokeclaw/cloud/` |
| 后端 API 契约 | `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` |
| 后端 Controller | `/mnt/e/code/dyq/dyq-module-claw/.../AppClawDeviceController.java` |
| 后端 Service | `/mnt/e/code/dyq/dyq-module-claw/.../ClawDeviceServiceImpl.java` |

---

## 九、改动摘要

本次产出为**文档和联调清单**，未修改代码。PokeClaw 端侧 cloud/ 包已实现：

1. **DeviceCloudClient** - 设备云端客户端接口与 Retrofit 实现
2. **CloudDeviceApi** - Retrofit API 契约，对齐 device.openapi.yaml
3. **CloudModels** - 完整 Kotlin DTO 定义
4. **CloudDeviceTokenStore** - Android Keystore 安全存储
5. **CloudEventQueue** - 离线结果队列
6. **CloudExecutorNode** - 执行节点引擎

后端已实现：
1. **AppClawDeviceController** - 设备端 5 个 REST API
2. **ClawDeviceServiceImpl** - 业务逻辑实现
3. **设备/任务数据模型** - DO/DTO 定义

---

## 十、阻塞与风险

| 风险项 | 状态 | 说明 |
|-------|------|------|
| 后端编译 | 需验证 | 需要执行 mvn clean compile 验证 |
| 网络环境 | 需配置 | 确保安卓端可访问 192.168.250.3:8080 |
| JWT 认证 | 已对齐 | 端侧带 Authorization: Bearer {token} |
| 数据库表 | 需确认 | claw_device_task 表结构已定义 |

---

文档生成时间: 2026-05-17
负责人: 安卓小龙
