# R7 — dyq v1.1.0 C 端 lobster 端点 + device 残余闭环 + R8 统一执行节点池

> 设计稿 2026-06-14 — 来自 dyq 后端 C 端 lobster 端点 21 个 + device 心跳响应残余 + 未来统一执行节点 9 端点的客户端补全

## 背景

R6 完成了 dyq v1.1.0 设备端 7 端点 + C3-01 商业任务编排的客户端落地（HMAC / 任务查询 / 任务取消 / mode 扩展 / 离线心跳）。但扫描 `claw.openapi.yaml` `/app-api/claw/app/lobster/*` 21 个 C 端端点（主人指令通道、技能市场、记忆、人格、画像）客户端 0 覆盖；device 心跳响应 `serverTime`/`skillVersion` 字段已建模未消费；HMAC 错误 `403001 TASK_DEVICE_MISMATCH` 未处理。同时 `executor-node.openapi.yaml` 9 个统一执行节点端点已就绪待未来迁移。

约束：客户端故事，不修改 dyq 后端任何文件（SQL/API/yaml）。所有未就绪字段用 hardcode/fallback 让客户端可独立推进。

## 故事清单

### R7 — 4 主题 + 1 残余

| 编号 | 标题 | 优先级 | 端点数 | 关键文件 |
|---|---|---|---|---|
| US-D-037 | device 残余闭环（serverTime 时钟漂移 + skillVersion 缓存刷新 + 403001 处理） | **P0** | 0 新增，3 改动 | CloudHeartbeatManager + RetrofitDeviceCloudClient + CloudModels |
| US-D-038 | 主人指令通道（lobster/command 提交+轮询 + hermes/feedback） | P0 | 3 | 新 LobsterCommandApi + LobsterCommandClient + Poller |
| US-D-039 | 技能市场 + 自定义技能生命周期（list / install / save / remove / batch-status） | P1 | 5 | 新 SkillMarketplaceApi + SkillMarketplaceClient + SkillLifecycleManager |
| US-D-040 | 小龙虾记忆 + 人格（CRUD + personality get/put/types） | P1 | 7 | 新 LobsterMemoryApi + LobsterPersonalityApi + 对应 client |
| US-D-041 | 小龙虾画像 + 执行历史 + 建议（my / my/stats / my/executions / my/skills / my/suggestions） | P2 | 5 | 新 LobsterProfileApi + LobsterProfileClient + ProfileFragment |

### R8 — 独立故事池

| 编号 | 标题 | 优先级 | 端点数 | 状态 |
|---|---|---|---|---|
| US-D-042 | 统一执行节点 API 迁移（/api/executor-nodes/* 9 端点） | P3 | 9 | R7 不实施，作为 R8 独立池 backlog |

## 数据契约

### US-D-037 device 残余闭环

```
DeviceHeartbeatResponse.serverTime   → CloudClockSkewDetector.compare(localNow, serverTime, threshold=4min)
                                        → 超过阈值 XLog.w("clock-skew: localDeltaMs=...") + refresh ts before HMAC
DeviceHeartbeatResponse.skillVersion  → SkillVersionCache.update(serverSkillVersion) 
                                        → 不一致时 XLog.i("skill-version: local=$local remote=$remote")
HMAC 403001 TASK_DEVICE_MISMATCH      → CloudClient.onDeviceMismatch()
                                        → invalidate token/deviceId, 触发 re-register
```

### US-D-038 lobster/command 端点

```
POST /app-api/claw/app/lobster/command
  body: { command: String, skillId?: String, context?: Map<String, Any> }
  → 200 { code, data: { executionId, status: PENDING } }

GET /app-api/claw/app/lobster/command/{executionId}/result
  → 200 { code, data: { status, result?, errorMessage?, progressPercent? } }

POST /app-api/claw/hermes/feedback
  body: { feedbackType, payload, taskUuid? }
  → 200/202 CommonResult
```

轮询策略：首次 500ms 间隔 5 次，第二次 1s 间隔 5 次，第三次起 2s 间隔至 30s 上限。状态 = SUCCESS/FAILED/CANCELLED/TIMEOUT 时停止。

### US-D-039 技能市场端点

```
GET  /app-api/claw/app/lobster/skill/list           → 200 CommonResult<ClawAppSkillMarketRespVO[]>
POST /app-api/claw/app/lobster/skill/install        body: { skillId } → 200 CommonResult<Boolean>
POST /app-api/claw/app/lobster/skill/save           body: ClawAppSkillSaveReqVO → 200 String (id) or Boolean
DELETE /app-api/claw/app/lobster/skill/remove       ?id=xxx → 200 CommonResult<Boolean>
PUT  /app-api/claw/app/lobster/skills/batch-status  body: BatchSkillStatusReqVO → 200 CommonResult<Boolean>
```

### US-D-040 记忆 + 人格

```
GET    /app-api/claw/app/lobster/my/memories?memoryType=&pageNo=&pageSize=  → PageResult<ClawMemoryRespVO>
POST   /app-api/claw/app/lobster/memory                                     body: ClawMemoryCreateReqVO → 200
DELETE /app-api/claw/app/lobster/memory/{id}                                 → 200 CommonResult<Boolean>
DELETE /app-api/claw/app/lobster/memory/all                                  → 200 CommonResult<Boolean>
GET    /app-api/claw/app/lobster/my/personality                              → CommonResult<ClawMoodRespVO>
PUT    /app-api/claw/app/lobster/personality                                 body: ClawMoodUpdateReqVO → 200
GET    /app-api/claw/app/lobster/personality/types                           → CommonResult<PersonalityTypes>
```

### US-D-041 画像 + 执行 + 建议

```
GET /app-api/claw/app/lobster/my              → CommonResult<ClawLobsterRespVO>
GET /app-api/claw/app/lobster/my/stats        → CommonResult<ClawLobsterStatsRespVO>
GET /app-api/claw/app/lobster/my/executions?skillId=&pageNo=&pageSize= → PageResult<ClawAppExecutionRespVO>
GET /app-api/claw/app/lobster/my/skills       → CommonResult<ClawAppSkillRespVO[]>
GET /app-api/claw/app/lobster/my/suggestions  → CommonResult<SuggestionResult>
```

### R8-POOL executor-node.openapi.yaml 9 端点（不入 R7）

```
POST /api/executor-nodes/register
POST /api/executor-nodes/token/refresh
POST /api/executor-nodes/{nodeId}/heartbeat
POST /api/executor-nodes/{nodeId}/capabilities
GET  /api/executor-nodes/{nodeId}/config
GET  /api/executor-nodes/{nodeId}/commands/pending
POST /api/executor-nodes/{nodeId}/commands/{commandId}/ack
GET  /api/executor-nodes/{nodeId}/commands/{commandId}
POST /api/executor-nodes/{nodeId}/commands/{commandId}/progress
POST /api/executor-nodes/{nodeId}/commands/{commandId}/result   (HMAC)
POST /api/executor-nodes/{nodeId}/commands/{commandId}/cancel
```

## 组件架构

```
新增 client 客户端（按主题拆分）：
  cloud/lobster/
    api/
      LobsterCommandApi.kt          (R7-1 提交+轮询+hermes/feedback)
      LobsterSkillMarketplaceApi.kt (R7-2 list/install/save/remove/batch-status)
      LobsterMemoryApi.kt           (R7-3 4 端点)
      LobsterPersonalityApi.kt      (R7-3 personality get/put/types)
      LobsterProfileApi.kt          (R7-4 my/stats/executions/skills/suggestions)
    client/
      LobsterCommandClient.kt
      SkillMarketplaceClient.kt
      LobsterMemoryClient.kt
      LobsterPersonalityClient.kt
      LobsterProfileClient.kt

  cloud/util/
    ClockSkewDetector.kt            (R7-0 serverTime vs localNow)
    SkillVersionCache.kt            (R7-0 skillVersion 本地缓存)
    PollingPolicy.kt                (R7-1 500ms→1s→2s 退避)

修改文件：
  cloud/CloudHeartbeatManager.kt    (R7-0 消费 serverTime/skillVersion)
  cloud/RetrofitDeviceCloudClient.kt(R7-0 处理 403001 触发 re-register)
  cloud/CloudClientFactory.kt       (R7-1~4 注册 5 套新 Api + 复用 BearerAuth)
  cloud/auth/CloudDeviceTokenStore.kt(新增 deviceUserToken 字段，与 deviceToken 共存)
```

## 数据流（US-D-038 lobster/command 主流程）

```
LobsterCommandClient.submit(command)
  ↓ POST /app-api/claw/app/lobster/command
  ↓ data.executionId
LobsterPoller.startPolling(executionId)
  ↓ schedule(500ms × 5, 1s × 5, 2s × N up to 30s)
  ↓ GET /app-api/claw/app/lobster/command/{executionId}/result
  ↓ status = SUCCESS/FAILED/CANCELLED/TIMEOUT → 停止
  ↓ onResult callback to LobsterCommandOrchestrator
  ↓ 上抛到 UI / Submit Hermes feedback (POST /app-api/claw/hermes/feedback)
```

## 错误处理

| 场景 | 处理 |
|---|---|
| R7-0: serverTime 偏差 >4min | XLog.w + 后续 HMAC 签名强制以 serverTime 基准计算 |
| R7-0: skillVersion 不一致 | XLog.i，触发 SkillRegistry 增量刷新（不阻塞当前任务） |
| R7-0: 403001 TASK_DEVICE_MISMATCH | invalidate token + deviceId，触发全量 re-register（不自动重试） |
| R7-1: command executionId 不存在 | XLog.w，返回 RESULT_NOT_FOUND 状态 |
| R7-1: 轮询超时 5min | 强制停止，XLog.e，返回 POLL_TIMEOUT |
| R7-1: hermes/feedback 202 | 视为成功（accepted） |
| R7-2: skill install 失败（已装/不存在） | XLog.e 返回 false，UI 弹 toast |
| R7-3: memory/all 删除 | 二次确认弹窗（防误删） |
| 网络异常 | 入离线队列，恢复后补报（仅 R7-1 写操作需要） |

## 测试

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| ClockSkewDetectorTest | 8 | 偏差 0/<4min/>4min/负偏差/边界/连续多次累积/单次大幅跳变 |
| SkillVersionCacheTest | 5 | init / update 同值 / update 升 / update 降 / 多 source 一致性 |
| HmacAuthErrorMappingTest | 6 | 401001/401002/401003/401004/403001/200 各自 XLog 输出断言 |
| LobsterCommandClientTest | 12 | 提交成功 / 失败 / 轮询 SUCCESS 收敛 / 轮询 FAILED 收敛 / 超时 / 异常重试 / hermes 200/202 |
| SkillMarketplaceClientTest | 10 | list 5 条 / install ok/fail / save new/update / remove ok/fail / batch-status |
| LobsterMemoryClientTest | 9 | list 0/N 条 / create ok / delete ok / clear all ok / 404 / 401 |
| LobsterPersonalityClientTest | 7 | get ok / get types 5+ / put ok / put 校验失败 |
| LobsterProfileClientTest | 9 | my ok / stats ok / executions 翻页 / skills 0/N / suggestions |

**总计 66 单元测试**，pure-Kotlin 优先（避开 Robolectric）。

## YAGNI 边界

明确不做：
- /app-api/claw/app/lobster/personality (compat alias) — 已被 /my/personality 覆盖
- /app-api/claw/app/lobster/my/stats 在 Settings 的图表展示 — 文本展示即可
- weflow.openapi.yaml — WeFlow 专属
- executor-node.openapi.yaml 9 端点 — R8 独立池
- 主动推送（WebSocket / SSE）— HTTP 轮询足够 MVP
- 命令撤销（/command/{id}/cancel 对应端点缺失）
- skill 评分/排行榜 — 云端未提供排序字段

## 关联已有故事

| 已有 | 关联 R7 故事 |
|---|---|
| US-D-018 USER-MEMORY | R7-3 记忆（云端镜像） |
| US-D-016 TASK-TEMPLATES | R7-2 技能市场 |
| US-D-024 MUXI-PIXVERSE | R7-2 技能市场（云端安装） |
| US-D-025 native_audio | R7-2 技能市场（云端安装） |
| US-D-026 TASK-COST-CHIP | R7-4 my/executions 含 creditConsumed |
| US-D-027 CS-AI-CREDIT-AWARE | R7-1 command 路径含 credit toast |
| US-D-031 SETTINGS-BILLING | R7-4 my/stats 用于 Settings 集成 |
| US-D-032 HMAC | R7-0 强化 HMAC 失败处理（403001） |
| US-D-033/034 任务查询/取消 | R7-1 command 端点 + R7-0 设备层 |

## 风险评估

| 故事 | 风险 | 缓解 |
|---|---|---|
| R7-0 | 误判时钟漂移导致大量 re-register | 阈值 4min（HMAC 5min 窗口内） |
| R7-1 | 轮询风暴 | PollingPolicy 退避 + 30s 上限 + 5min 超时 |
| R7-2 | 云端 skill 列表变更 → 本地缓存不一致 | TTL 5min + 手动刷新入口 |
| R7-3 | memory/all 误删 | UI 二次确认 + XLog.e 留痕 |
| R7-4 | 分页接口被前端误用 → 一次拉全 | PageResult<T> 强类型，service 层强制分页 |
