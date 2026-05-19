# PokeClaw 设备API联调清单

> 任务编号: CMP-137  
> 作者: 安卓小龙  
> 创建时间: 2026-05-18  
> 关联: dyq后端设备API

---

## 一、已生成代码文件清单

### 1.1 数据模型 (DTO)
路径: `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/`

| 文件名 | 用途 |
|--------|------|
| DeviceRegisterRequest.kt | 设备注册请求 |
| DeviceRegisterResponse.kt | 设备注册响应 |
| DeviceHeartbeatRequest.kt | 心跳请求 |
| DeviceHeartbeatResponse.kt | 心跳响应 |
| PendingTaskItem.kt | 待处理任务项 |
| TaskResultRequest.kt | 任务结果上报请求 |
| TokenRefreshRequest.kt | Token刷新请求 |
| TokenRefreshResponse.kt | Token刷新响应 |
| CommonResult.kt | 通用响应包装 |
| PageResult.kt | 分页结果 |

### 1.2 API接口
路径: `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/api/`

| 文件名 | 用途 |
|--------|------|
| DeviceApi.kt | Retrofit接口定义 |

### 1.3 核心服务
路径: `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/`

| 文件名 | 用途 |
|--------|------|
| TokenManager.kt | Token安全存储(Keystore) |
| CloudClient.kt | Retrofit客户端封装 |
| DeviceService.kt | 设备服务(注册/心跳/任务) |
| OfflineFallbackManager.kt | 离线降级管理 |

---

## 二、接口字段映射

### 2.1 设备注册
```
POST /api/claw-device/register
Request: DeviceRegisterRequest
  - deviceId: String (必填)
  - deviceName: String?
  - deviceModel: String?
  - androidVersion: String?
  - appVersion: String?
  - publicKey: String?

Response: CommonResult<DeviceRegisterResponse>
  - deviceToken: String (JWT, 7天有效期)
  - refreshToken: String (JWT, 30天有效期)
  - expiresIn: Int (秒)
```

### 2.2 设备心跳
```
POST /api/claw-device/heartbeat
Header: Authorization: Bearer {deviceToken}
Request: DeviceHeartbeatRequest
  - batteryLevel: Int (0-100)
  - isCharging: Boolean
  - networkType: String (wifi/cellular/offline)

Response: CommonResult<DeviceHeartbeatResponse>
  - pendingTaskCount: Int
  - skillVersion: Int
  - serverTime: Long (毫秒时间戳)
```

### 2.3 拉取待处理任务
```
GET /api/claw-device/devices/{deviceId}/pending-tasks
Header: Authorization: Bearer {deviceToken}

Response: CommonResult<List<PendingTaskItem>>
  - taskUuid: String
  - command: String
  - mode: String
  - createdAt: Long
  - priority: String
```

### 2.4 提交任务结果
```
POST /api/claw-device/tasks/{taskUuid}/result
Header: Authorization: Bearer {deviceToken}
Request: TaskResultRequest
  - status: String (SUCCESS/FAILED/RUNNING/CANCELLED)
  - result: String?
  - errorMessage: String?
  - executionTimeMs: Int?
  - errorCategory: String?
  - errorCode: String?
```

### 2.5 刷新Token
```
POST /api/claw-device/token/refresh
Request: TokenRefreshRequest
  - refreshToken: String (必填)

Response: CommonResult<TokenRefreshResponse>
  - deviceToken: String
  - expiresIn: Int
```

---

## 三、Token管理说明

### 3.1 存储方式
- **deviceToken**: 加密存储在 Android Keystore，有效期7天
- **refreshToken**: 加密存储在 Android Keystore，有效期30天
- **deviceId**: 明文存储在 SharedPreferences

### 3.2 自动刷新逻辑
1. 每次API调用前检查token是否过期(提前5分钟)
2. 过期时自动调用 `/api/claw-device/token/refresh`
3. 刷新失败(401)时清除token，触发重新注册

### 3.3 安全要求
- Token不得明文存储
- Keystore密钥每次启动时检查
- 支持证书绑定(可选)

---

## 四、离线降级策略

### 4.1 触发条件
- 网络连接失败(超时30秒)
- 收到HTTP 5xx错误
- 连续3次心跳失败

### 4.2 降级行为
1. 进入离线模式，暂停云端任务拉取
2. 任务执行切换到本地 Gemma 4 模型
3. 结果暂存本地队列，网络恢复后批量上报

### 4.3 恢复检测
- 每30秒尝试一次心跳
- 心跳成功后退出离线模式
- 上报离线期间缓存的结果

---

## 五、联调步骤

### Step 1: 后端准备
```bash
# 确认后端已启动
curl http://192.168.250.3:8080/actuator/health

# 确认设备API可用
curl -s http://192.168.250.3:8080/api/claw-device/register \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test-device-001","deviceName":"测试设备"}'
```

### Step 2: 安卓端验证
1. 编译检查: `./gradlew :app:compileDebugKotlin`
2. 安装APK: `./gradlew :app:installDebug`
3. 查看日志: `adb logcat -s PokeClaw/*`

### Step 3: 端到端测试
1. 启动App，观察注册请求
2. 等待30秒，观察心跳请求
3. 后台创建任务，观察任务拉取
4. 执行任务，观察结果上报

---

## 六、常见问题

### Q1: 注册失败401
**原因**: deviceId已被其他设备使用  
**解决**: 清除App数据，重新生成deviceId

### Q2: Token刷新失败
**原因**: refreshToken过期  
**解决**: 自动触发重新注册流程

### Q3: 心跳返回pendingTaskCount=0但实际有任务
**原因**: 任务状态未同步  
**解决**: 检查后端任务分配逻辑

---

## 七、待验证问题清单

- [ ] DeviceRegisterRequest 字段对齐后端
- [ ] DeviceHeartbeatRequest 网络类型枚举对齐
- [ ] TaskResultRequest 错误分类字段对齐
- [ ] Token自动刷新流程验证
- [ ] 离线降级触发和恢复验证
- [ ] 设备ID生成策略(IMEI限制场景)
- [ ] Keystore加密/解密性能测试
