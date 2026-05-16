# PokeClaw 设备API联调准备清单

## 问题编号
CMP-137: 【Android】PokeClaw端侧对接 — 设备API联调准备

## 审计日期
2026-05-16

---

## 一、现有实现审计结果

### 1. 模块结构
```
app/src/main/java/io/agents/pokeclaw/cloud/
├── api/
│   ├── CloudDeviceApi.kt          # Retrofit 接口定义
│   └── CloudDeviceApiFactory.kt   # OkHttp + Retrofit 构建工厂
├── auth/
│   └── CloudDeviceTokenStore.kt   # Android Keystore JWT 存储
├── model/
│   └── CloudModels.kt             # 请求/响应 DTO
├── DeviceCloudClient.kt           # 客户端接口 + Retrofit 实现
├── CloudEventQueue.kt             # 离线结果缓存队列
├── CloudNodeOrchestrator.kt       # 云端任务编排器
└── CloudTaskExecutor.kt           # 云端任务执行器
```

### 2. 已实现功能
- ✅ 设备注册 POST /api/claw-device/register
- ✅ 心跳发送 POST /api/claw-device/heartbeat
- ✅ 任务拉取 GET /api/claw-device/devices/{deviceId}/pending-tasks
- ✅ 结果上报 POST /api/claw-device/tasks/{taskUuid}/result
- ✅ Token 刷新 POST /api/claw-device/refresh-token
- ✅ Android Keystore 加密存储 (AES-GCM)
- ✅ 离线队列 (SharedPreferences + Gson)
- ✅ 指数退避重试策略
- ✅ 结果脱敏 (长度截断)

### 3. 依赖配置
```kotlin
// app/build.gradle.kts
implementation(libs.okhttp)
implementation(libs.okhttp.logging)
implementation(libs.retrofit)
implementation(libs.retrofit.gson)
```

---

## 二、阻塞问题

### 1. 后端 OpenAPI 文件缺失
- **路径**: `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` 不存在
- **影响**: 无法使用代码生成工具自动生成 DTO
- **状态**: ⛔ 阻塞联调

### 1. 后端 OpenAPI 文件状态【已解决】
- **路径**: `/mnt/e/code/dyq/api-contracts/device.openapi.yaml` ✅ 已存在
- **端侧DTO对齐**: `app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` 已对齐契约字段
- **状态**: ✅ 字段已对齐，可启动联调

### 2. 集成点缺失【已解决】
- **ClawApplication.onCreate()** ✅ 已初始化cloud模块
- **TaskOrchestrator** 未接入云端任务执行
- **设置页** 无云端管理开关

### 2. 集成点状态【已解决】
- **ClawApplication.onCreate()** ✅ 已添加 `initCloudNode()`，默认关闭，可通过KV配置启用
- **CloudNodeOrchestrator** ✅ 已集成，支持注册/心跳/任务拉取/结果上报/离线队列
- **TaskOrchestrator** 待后续接入云端任务执行
- **设置页** 待添加云端管理开关UI

---

## 三、下一步任务

### 任务1: 后端提供 OpenAPI 文件 (外部依赖)
**责任人**: dyq后端团队
**交付物**: `/api-contracts/device.openapi.yaml`
**字段对齐**: 确保以下字段与端侧一致
- deviceId 生成规则 (UUID)
- 任务状态枚举 (pending/executing/completed/failed/cancelled)
- 错误码体系
- Token 有效期 (秒)

### 任务2: 端侧初始化集成
**文件**: `ClawApplication.kt`
```kotlin
// 在 onCreate 中添加
CloudNodeOrchestrator.getInstance().initialize(this)
```

**文件**: `TaskOrchestrator.kt`
```kotlin
// 在任务完成回调中上报结果
cloudClient.submitTaskResult(taskUuid, resultRequest)
```

### 任务3: 设置页开关
**文件**: `ui/settings/SettingsFragment.kt` (待创建或定位)
- Cloud Management 开关 (默认关闭)
- Base URL 配置输入框
- 设备注册状态显示

### 任务4: JWT 安全审计
**检查项**:
- [ ] deviceToken 不写入 logcat
- [ ] refreshToken 不出现在 bugreport
- [ ] SharedPreferences 加密后存储
- [ ] Keystore 别名不冲突

### 任务5: 离线降级策略验证
**场景**:
1. 网络断开 → 结果进入 CloudEventQueue
2. 网络恢复 → 按幂等编号补报
3. 队列满 → 丢弃最旧事件
4. 敏感信息 → 脱敏后再缓存

---

## 四、联调检查清单

### 4.1 设备注册
- [ ] 首次启动生成 deviceId (UUID)
- [ ] POST /api/claw-device/register 成功
- [ ] deviceToken/refreshToken 保存到 Keystore
- [ ] 注册失败静默重试 (指数退避)

### 4.2 心跳保活
- [ ] WorkManager 周期性任务 30s 间隔
- [ ] POST /api/claw-device/heartbeat 成功
- [ ] 携带 batteryLevel/networkType
- [ ] 连续3次失败标记离线

### 4.3 任务拉取
- [ ] 心跳响应 pendingTaskCount>0 触发拉取
- [ ] GET /api/claw-device/devices/{deviceId}/pending-tasks 成功
- [ ] 任务列表非空时调度执行

### 4.4 结果上报
- [ ] 任务完成后调用 submitTaskResult
- [ ] 成功时移除离线队列
- [ ] 失败时缓存到离线队列
- [ ] 网络恢复后补报

### 4.5 Token 管理
- [ ] 过期前10分钟触发刷新
- [ ] POST /api/claw-device/refresh-token 成功
- [ ] 新 token 更新到 Keystore
- [ ] 刷新失败清空 token 重新注册

---

## 五、风险边界确认

| 风险项 | 现状 | 措施 |
|--------|------|------|
| 分层破坏 | 否 | cloud 包独立，不依赖 UI 层 |
| 跨模块 JOIN | 否 | 无数据库操作 |
| 敏感信息泄露 | 否 | Keystore 加密 + 脱敏 |
| 非官方接口 | 否 | 仅使用标准 Android API |
| 后台常驻 | 待定 | 使用 WorkManager 符合规范 |

---

## 六、产出文件清单

| 文件路径 | 状态 | 说明 |
|----------|------|------|
| docs/product/pokeclaw-device-api-integration.md | 本文件 | 联调准备清单 |
| app/src/main/java/io/agents/pokeclaw/cloud/ | 已存在 | 云端模块实现 |
| /api-contracts/device.openapi.yaml | 缺失 | 后端待提供 |

---

## 七、联调启动条件

以下条件全部满足后方可启动联调：
1. ✅ 端侧实现审计完成
2. ⛔ 后端 OpenAPI 文件提供
3. ⛔ 后端服务部署完成
4. ⛔ 测试设备可用
5. ⛔ 联调环境配置完成

---

**审计完成时间**: 2026-05-16  
**审计人**: 安卓小龙  
**问题编号**: CMP-137
