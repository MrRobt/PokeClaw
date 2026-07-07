# R8-POOL — 统一执行节点 API 迁移池

> 状态：R7 不实施 / backlog 占位 / R7 实施期间不修改 device.openapi.yaml 对应端点
> 来源：dyq `executor-node.openapi.yaml` — 9 端点（未来 dyq v1.2.0 强制迁移目标）

## 9 端点清单

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

注：原表 9 行扩展为 11 行（含 progress/result/cancel 三个 command 阶段子端点）。

## 启动条件

R8 启动必须同时满足：

1. **dyq v1.2.0 切换**：dyq 后端正式废弃 `device.openapi.yaml` 7 端点，全面改用 `executor-node.openapi.yaml`
2. **双轨灰度方案**：迁移期间客户端保留两套 API 客户端，通过 `MigrationAdapter` 按 deviceId/deviceClass 路由
3. **客户端 MigrationAdapter**：在 `CloudClientFactory` 内增加 `useExecutorNodeApi: Boolean` 配置开关，false 时走 device.openapi.yaml，true 时切到 executor-node

## YAGNI 边界

- **R7 期间不实现**：R7 客户端故事仅对齐 `device.openapi.yaml` 7 端点；本目录为占位
- **不修改 dyq 后端**：客户端故事不修改 dyq 任何文件（SQL/API/yaml）
- **不设计 MigrationAdapter**：等 dyq v1.2.0 落地信号再启动

## 关联 R7 故事

| R8 端点 | R7 替代（device.openapi.yaml） |
|---|---|
| /executor-nodes/register | /api/claw-device/register |
| /executor-nodes/token/refresh | /api/claw-device/token/refresh |
| /executor-nodes/{nodeId}/heartbeat | /api/claw-device/heartbeat |
| /executor-nodes/{nodeId}/capabilities | （R7 无对应，可从 heartbeat 派生） |
| /executor-nodes/{nodeId}/config | （R7 无对应，新功能） |
| /executor-nodes/{nodeId}/commands/pending | /api/claw-device/devices/{deviceId}/pending-tasks |
| /executor-nodes/{nodeId}/commands/{commandId}/ack | （R7 无对应，命令级别 ack） |
| /executor-nodes/{nodeId}/commands/{commandId} | /api/claw-device/tasks/{taskUuid}（R6 US-D-033） |
| /executor-nodes/{nodeId}/commands/{commandId}/progress | （R7 无对应，progress 上报） |
| /executor-nodes/{nodeId}/commands/{commandId}/result | /api/claw-device/tasks/{taskUuid}/result（HMAC） |
| /executor-nodes/{nodeId}/commands/{commandId}/cancel | /api/claw-device/tasks/{taskUuid}/cancel |

R7 期间 device.openapi.yaml 7 端点继续作为唯一客户端路径；本目录保持 inactive。
