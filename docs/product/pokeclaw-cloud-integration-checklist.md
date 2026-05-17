# PokeClaw 端云任务下发与结果回传联调清单

> 文档版本: 2025-05-17
> 关联 Issue: CMP-1940
> 后端契约: /mnt/e/code/dyq/api-contracts/device.openapi.yaml

---

## 一、接口字段映射表

### 1.1 设备注册 `/api/claw-device/register`

| 字段 | Android 端 (Kotlin) | 后端 (Java) | 状态 |
|------|---------------------|-------------|------|
| deviceId | `DeviceRegisterRequest.deviceId: String` | `ClawDeviceRegisterReqVO.deviceId` | ✅ 对齐 |
| deviceName | `deviceName: String?` | `deviceName: String` | ✅ 对齐 |
| deviceModel | `deviceModel: String?` | `deviceModel: String` | ✅ 对齐 |
| androidVersion | `androidVersion: String?` | `androidVersion: String` | ✅ 对齐 |
| appVersion | `appVersion: String?` | `appVersion: String` | ✅ 对齐 |
| publicKey | `publicKey: String?` | `publicKey: String` | ✅ 对齐 |

**响应字段:**
| 字段 | Android 端 | 后端 | 状态 |
|------|------------|------|------|
| deviceToken | `DeviceRegisterResponse.deviceToken` | `deviceToken: String` | ✅ 对齐 |
| refreshToken | `refreshToken` | `refreshToken: String` | ✅ 对齐 |
| expiresIn | `expiresIn: Int` | `expiresIn: int` | ✅ 对齐 |

### 1.2 设备心跳 `/api/claw-device/heartbeat`

| 字段 | Android 端 (Kotlin) | 后端 (Java) | 状态 |
|------|---------------------|-------------|------|
| batteryLevel | `DeviceHeartbeatRequest.batteryLevel: Int?` | `batteryLevel: Integer` | ✅ 对齐 |
| isCharging | `isCharging: Boolean?` | `isCharging: Boolean` | ✅ 对齐 |
| networkType | `networkType: String?` (wifi/cellular/offline) | `networkType: String` | ✅ 对齐 |

**响应字段:**
| 字段 | Android 端 | 后端 | 状态 |
|------|------------|------|------|
| pendingTaskCount | `DeviceHeartbeatResponse.pendingTaskCount: Int` | `pendingTaskCount: int` | ✅ 对齐 |
| skillVersion | `skillVersion: Int` | `skillVersion: int` | ⚠️ 后端返回 int，需确认字段名 |
| serverTime | `serverTime: Long` | `serverTime: long` | ✅ 对齐 |

### 1.3 任务轮询 `/api/claw-device/devices/{deviceId}/pending-tasks`

| 字段 | Android 端 (Kotlin) | 后端 (Java) | 状态 |
|------|---------------------|-------------|------|
| taskUuid | `PendingTaskItem.taskUuid: String` | `taskUuid: String` | ✅ 对齐 |
| command | `command: String` | `command: String` | ✅ 对齐 |
| mode | `mode: String?` | `mode: String` | ✅ 对齐 |
| createdAt | `createdAt: Long` | `createdAt: Long` | ✅ 对齐 |
| priority | `priority: String?` | `priority: String` | ✅ 对齐 |

### 1.4 任务结果上报 `/api/claw-device/tasks/{taskUuid}/result` ⚠️ **发现问题**

| 字段 | Android 端 (Kotlin) | 后端 (Java) | OpenAPI Spec | 状态 |
|------|---------------------|-------------|--------------|------|
| status | `TaskStatus.SUCCESS/FAILED/RUNNING/CANCELLED` | `completed/failed/cancelled` | `SUCCESS/FAILED/RUNNING/CANCELLED` | ❌ **不一致** |
| result | `result: String?` | `result: String` | `result: string` | ✅ 对齐 |
| errorMessage | `errorMessage: String?` | `errorMessage: String` | `errorMessage: string` | ✅ 对齐 |
| executionTimeMs | `executionTimeMs: Long?` | `executionTimeMs: Long` | `executionTimeMs: integer` | ✅ 对齐 |
| toolCalls | `toolCalls: String?` | `toolCalls: String` | `toolCalls: string` | ✅ 对齐 |
| evidenceUrls | `evidenceUrls: String?` | `evidenceUrls: String` | `evidenceUrls: string` | ✅ 对齐 |
| modelUsed | `modelUsed: String?` | `modelUsed: String` | `modelUsed: string` | ✅ 对齐 |

**⚠️ 关键问题:**
- 后端 AppClawDeviceTaskResultReqVO.java 注释写 `completed/failed/cancelled`
- OpenAPI 规范写 `SUCCESS, FAILED, RUNNING, CANCELLED`
- Android 端使用 `SUCCESS, FAILED, RUNNING, CANCELLED`
- **需要统一为 OpenAPI 规范的枚举值**

---

## 二、发现问题与修复

### 问题 1: 任务状态枚举值不一致

**位置:**
- 后端: `AppClawDeviceTaskResultReqVO.java` 第 14 行注释
- OpenAPI: `device.openapi.yaml` 第 163 行
- Android: `CloudModels.kt` 第 206-212 行

**当前状态:**
```java
// 后端 VO 注释（错误）
@Schema(description = "状态: completed/failed/cancelled", ...)

// OpenAPI 规范（正确）
enum: [SUCCESS, FAILED, RUNNING, CANCELLED]

// Android 端（正确，但需确认后端实际接收值）
enum class TaskStatus(val value: String) {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED")
}
```

**修复方案:**
1. 后端 VO 注释修正为与 OpenAPI 一致
2. Android 端保持现有枚举值
3. 验证后端实际接收和存储的值

### 问题 2: Token 刷新响应缺少 refreshToken

**位置:**
- 后端: `AppClawDeviceController.refreshToken()` 第 141-144 行
- OpenAPI: `TokenRefreshResponse` 定义
- Android: `TokenRefreshResponse` 类

**当前状态:**
后端仅返回 `deviceToken` 和 `expiresIn`，但 OpenAPI 和 Android 端期望还有 `refreshToken`。

---

## 三、联调步骤

### Step 1: 后端服务启动验证
```bash
cd /mnt/e/code/dyq
mvn clean install -DskipTests
# 启动服务后验证:
curl -X POST http://localhost:8080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "test-device-001",
    "deviceName": "测试设备",
    "deviceModel": "TestModel",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'
```

### Step 2: Android 端单元测试
```kotlin
// 测试设备注册
val request = DeviceRegisterRequest(
    deviceId = "test-device-001",
    deviceName = "测试设备",
    deviceModel = "Xiaomi 14",
    androidVersion = "14",
    appVersion = "0.7.0"
)
```

### Step 3: 端到端联调
1. Android 端调用注册接口
2. 保存返回的 deviceToken 和 refreshToken
3. 使用 deviceToken 发送心跳
4. 验证 pendingTaskCount 返回
5. 调用任务轮询接口
6. 执行任务后上报结果

---

## 四、待修复文件清单

| 文件路径 | 问题 | 优先级 |
|----------|------|--------|
| `/mnt/e/code/dyq/dyq-module-claw/.../AppClawDeviceTaskResultReqVO.java` | 注释枚举值与 OpenAPI 不一致 | P1 |
| `/mnt/e/code/dyq/dyq-module-claw/.../AppClawDeviceController.java` | Token 刷新响应缺少 refreshToken | P2 |
| `/mnt/e/code/PokeClaw/.../cloud/model/CloudModels.kt` | 已对齐，需验证 | - |
| `/mnt/e/code/PokeClaw/.../cloud/api/CloudDeviceApi.kt` | 已对齐，需验证 | - |

---

## 五、验证结果

| 检查项 | 状态 | 备注 |
|--------|------|------|
| 设备注册接口字段对齐 | ✅ | 完全一致 |
| 心跳接口字段对齐 | ✅ | 完全一致 |
| 任务轮询接口字段对齐 | ✅ | 完全一致 |
| 任务结果上报接口字段对齐 | ⚠️ | 状态枚举需确认 |
| Token 刷新响应字段完整 | ⚠️ | 后端缺少 refreshToken |

---

## 六、下一步行动

1. [ ] 修复后端 `AppClawDeviceTaskResultReqVO.java` 注释（统一为 OpenAPI 规范）
2. [ ] 修复后端 Token 刷新接口响应（补全 refreshToken）
3. [ ] 启动后端服务进行实际联调
4. [ ] 编写 Android 端接口单元测试
5. [ ] 验证完整端到端流程

---

*文档生成: 安卓小龙*
*关联: CMP-1940, CMP-137, CMP-1964*
