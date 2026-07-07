# R6 — dyq v1.1.0 契约补全 + 新能力客户端落地

> 设计稿 2026-06-13 — 来自 dyq 后端 2026-05-21 v1.1.0 升级 + C3-01 商业任务编排的客户端补全

## 背景

dyq 后端 2026-05-21 升级 device.openapi.yaml 到 v1.1.0（HMAC 双层认证 + C3-01 商业任务编排）。当前 PokeClaw 客户端 `submitTaskResult` 不带 `X-Claw-Signature` 头，生产环境后端会直接拒绝所有结果上报（401001 INVALID_SIGNATURE）。同时 dyq 新增 2 个 C 端可用 endpoint 和 PendingTaskItem.mode 扩展字段。

约束：客户端故事，不修改 dyq 后端任何文件（SQL/API/yaml）。

## 故事清单

| 编号 | 标题 | 优先级 | 范围 | 关键文件 |
|---|---|---|---|---|
| US-D-032 | HMAC-SHA256 签名 + 提交结果 v1.1.0 合规 | **P0** | 端侧 | HmacSigner + RetrofitDeviceCloudClient + DeviceApi |
| US-D-033 | task 单点查询（GET /api/claw-device/tasks/{taskUuid}） | P2 | 端侧 | DeviceApi + RetrofitDeviceCloudClient |
| US-D-034 | task 主动取消（POST /api/claw-device/tasks/{taskUuid}/cancel） | P2 | 端侧 | DeviceApi + RetrofitDeviceCloudClient |
| US-D-035 | PendingTaskItem.mode 扩展（dry_run / prepare_only） | P3 | 端侧 | CloudModels.kt (enum) |
| US-D-036 | 心跳 `networkType=offline` 显式上报 | P2 | 端侧 | CloudHeartbeatManager |

## 数据契约

### US-D-032 HMAC 签名算法

```
signing_string = X-Claw-Timestamp + "\n" + X-Claw-Nonce + "\n" + path + "\n" + sha256_hex(body)
X-Claw-Signature = hex(HMAC-SHA256(device_token, signing_string))
```

- 时间窗：5min（防重放）
- Nonce：UUID，Redis SETNX 5min TTL（服务端）
- 失败码：`401001 INVALID_SIGNATURE` / `401002 TIMESTAMP_EXPIRED` / `401003 NONCE_DUPLICATE` / `401004 DEVICE_MISMATCH`
- 强制范围：仅 `POST /api/claw-device/tasks/{taskUuid}/result`

### US-D-033 / US-D-034 新 endpoint

```
GET  /api/claw-device/tasks/{taskUuid}            → 200 + DeviceTaskVO
POST /api/claw-device/tasks/{taskUuid}/cancel     → 200 + { data: true|false }
```

### US-D-035 PendingTaskItem.mode

- 现有枚举：`TASK, INTERACTIVE`
- 新增：`dry_run, prepare_only`（仅 WeFlow 云端微信托管使用）
- 客户端策略：遇到 `dry_run/prepare_only` 时不执行本地任务，仅展示模式徽章

### US-D-036 networkType 离线

- 当前：心跳不发送 `offline`
- 改：网络断开时仍发心跳，`networkType="offline"`（与 OpenAPI 枚举对齐）

## 组件架构

```
cloud/auth/HmacSigner.kt           (新) — SHA-256 + HMAC-SHA256 工具，pure-Kotlin
cloud/auth/HmacHeaders.kt          (新) — X-Claw-Signature/Timestamp/Nonce 数据类
cloud/api/DeviceApi.kt             (改) — submitTaskResult 加 @Header + cancel/query 接口
cloud/RetrofitDeviceCloudClient.kt (改) — submitTaskResult 前置签名 + 401xxx 解析
cloud/CloudHeartbeatManager.kt     (改) — 网络断开时仍发心跳
cloud/model/CloudModels.kt         (改) — PendingTaskItem.mode enum 扩展
```

## 数据流（US-D-032 主流程）

```
submitTaskResult(taskUuid, payload)
  ↓
HmacSigner.sign(deviceToken, ts, nonce, path, body)
  ↓ build signature
DeviceApi.submitTaskResult(taskUuid, payload, X-Claw-Signature, X-Claw-Timestamp, X-Claw-Nonce)
  ↓
若 401001-401004: 解析 code → XLog.e("hmac-fail: code=401xxx reason=xxx")
若 200: Result.success
若网络异常: 入队 [CloudEventQueue]，补报时重新计算签名
```

## 错误处理

| 场景 | 处理 |
|---|---|
| 401001 INVALID_SIGNATURE | XLog.e，可能 token 漂移 → token refresh 后重试 1 次 |
| 401002 TIMESTAMP_EXPIRED | XLog.w，时钟漂移告警，重试 1 次（取新 ts） |
| 401003 NONCE_DUPLICATE | XLog.e，直接重试 |
| 401004 DEVICE_MISMATCH | XLog.e，token/deviceId 不匹配 → 全量重新注册 |
| 网络异常 | 入离线队列，恢复后补报 |

## 测试

| 文件 | 用例数 |
|---|---|
| HmacSignerTest | 14：签名一致性、时间窗、空 body、UTF-8、特殊字符、回归向量 |
| CancelTaskClientTest | 8：成功、false（终态）、异常、token refresh |
| QueryTaskClientTest | 6：成功、404、未认证 |
| PendingTaskItemModeTest | 5：枚举序列化、UNKNOWN 兜底 |
| HeartbeatOfflineTest | 4：断网仍发心跳、networkType 对齐 |

**总计 37 单元测试**，全部 pure-Kotlin（避开 Robolectric）。

## YAGNI 边界

明确不做：
- RabbitMQ task-assigned 推送订阅（需 RabbitMQ Android 客户端，复杂度不匹配）
- claw.openapi.yaml admin 端点（lobster/skill/training）—— 服务端内部能力
- WeChat 上报（CLAW3T-004/005）—— WeFlow 专属
- 小龙虾/养号/灰度白名单（LOBSTER-*）—— 后端独占
- 号源市场/账号市场（AM-PKG-*）—— 独立业务线
