# 端侧 Kotlin 实现与契约派生对照（P 层 P1.3 + P2.3 增量）

> 派生自：.planning/djs-loop/cloud-contract-baseline/（端云契约基线，t_d2f8d4b7）
> 派生目标：在不修改端侧 Kotlin 代码、不新增测试的前提下，把"heartbeat 鉴权"
> 和"任务重试 × 人工接管"两块契约与端侧实现对齐，标记 3 态。

- 生成时间：2026-06-07
- 基线 commit：t_d2f8d4b7 已闭环
- C 层 commit：t_b05b9bc0 mainline-overview 已闭环
- ADB 在线设备：0（事实不假装）
- 端云契约基线：5 端点 / 7 schema 锁版

## 一、P1.3 heartbeat 鉴权 × 端侧 HMAC 实现

| 契约项 | 端侧位置 | 状态 | 备注 |
|--------|----------|------|------|
| POST /api/claw-device/heartbeat 端点 | cloud/api/DeviceApi.kt | ✓对齐 | 与契约 path 完全一致 |
| heartbeat 鉴权 Bearer + 签名头 | cloud/auth/ClawSignatureGenerator.kt | ✓对齐 | 基线已覆盖 |
| X-Claw-Timestamp / X-Claw-Nonce / X-Claw-Signature | ClawSignatureGenerator.kt:sign() | ✓对齐 | 三头齐全 |
| 签名算法 HMAC-SHA256 | ClawSignatureGenerator.kt:HmacSHA256 | ✓对齐 | 与契约 security 段一致 |
| 签名字符串 = ts + \n + nonce + \n + path + \n + sha256_hex(body) | ClawSignatureGenerator.kt | ✓对齐 | 与契约一致 |
| 密钥 = deviceToken | ClawSignatureGenerator.kt:key() | ✓对齐 | 与契约一致 |
| heartbeat 失败计数 | cloud/CloudHeartbeatManager.kt:consecutiveFailures | ✓对齐 | 端侧有计数 |
| 连续 3 次失败标记离线 | CloudHeartbeatManager.kt:MAX_CONSECUTIVE_FAILURES = 3 | ✓对齐 | 与本层硬约束一致 |
| 离线时返回 Result.retry() | CloudHeartbeatManager.kt | ✓对齐 | WorkManager 调度 |

## 二、P2.3 任务重试 × 人工接管

| 契约项 | 端侧位置 | 状态 | 备注 |
|--------|----------|------|------|
| 任务状态机 4 态 SUCCESS/FAILED/RUNNING/CANCELLED | cloudnode/CloudExecutorNodeContract.kt:CloudTaskStatus | ✓对齐 | enum 完整 |
| 错误码分级 CloudTaskErrorCode | cloudnode/CloudExecutorNodeContract.kt:CloudTaskErrorCode | ✓对齐 | enum 完整 |
| 失败标记 retryable: Boolean | cloudnode/CloudExecutorNodeContract.kt:CloudTaskExecutionResult | ✓对齐 | 与契约 errorCategory/recoverable 字段一致 |
| 失败原因传递 errorCode + retryable | cloudnode/CloudTaskReceiptManager.kt | ✓对齐 | 上报云端字段一致 |
| 失败 3 次入人工确认队列 | cloud/CloudHeartbeatManager.kt:MAX_CONSECUTIVE_FAILURES=3 | △扩展 | 端侧仅做离线标记，未实现"人工确认队列"——契约派生命名空间，owner-blocked（无真机） |
| 人工接管渠道 Tasker/MacroDroid Intent | automation/ExternalAutomationActivity.kt | ✓对齐 | README § External Automation 已记录 |
| 离线缓存（弱网/无网） | cloud/CloudEventQueue.kt | ✓对齐 | 端侧已实现事件队列 |
| 上次任务 ID 可读 | cloud/CloudTaskReceiptManager.kt:lastReceipt | ✓对齐 | 端侧持有 lastTaskId |

## 三、与 P2.4 状态机可见性的对齐

operator-status.json.taskStateMachine 与端侧 CloudTaskStatus enum 字段一致：
- states: [SUCCESS, FAILED, RUNNING, CANCELLED]
- counts 起步全 0（无真机）
- lastTaskId = "no_task"（不假造）
- source = "device-empty-or-sample-not-fake"（明示来源）

## 四、本派生不在本轮做的事

- 不实现 "3 次失败 → 人工队列" 的端侧新代码（owner-blocked：无真机无法联调）
- 不修改 ClawSignatureGenerator.kt、CloudHeartbeatManager.kt、CloudExecutorNodeContract.kt
- 不在端侧新增 Kotlin 测试（基线 t_d2f8d4b7 已规定：派生不引入新测试）
- 不在 P 层动端云契约基线 artifacts（基线已提交，覆写会污染基线 commit）

## 五、风险

- 派生 markdown 描述如果与端侧实际实现发生漂移，下游消费者需要回到此文档上游的"端侧位置"列重新校验文件路径
- 派生目录与基线目录并列：`/mnt/e/code/PokeClaw/.planning/djs-loop/pokeclaw-p1p2-runtime-loop/derivative/kotlin-coverage-derivative.md`
