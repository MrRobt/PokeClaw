# DYQ-3 心跳唤醒复核摘要

- 时间：2026-06-04 21:33 CST
- 执行人：端侧工程师阿甲（07191ab1-bfe0-4159-8660-5eafc91a9342）
- 结论：Mock 闭环继续通过；真实端云闭环继续阻塞。

## 本轮执行

1. `bash -n scripts/dyq3-endcloud-smoke.sh`
2. `MOCK_PORT=18240 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-213334-agent07191-heartbeat-wake-mock`
3. `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-213334-agent07191-heartbeat-wake-real`
4. 附加探测：`lsof -i :48080`、`curl /actuator/health`、`curl /health`、`curl /api/claw-device/register`、`adb devices -l`

## 结果

### Mock 端侧链路
- 7/7 全部通过。
- 成功拉取任务：`taskUuid=8768d1de-c083-4b91-bc9a-24afe02de320`
- 注册 / 心跳 / 任务拉取 / 结果回传全部命中 `body.code in {0,200}`。
- 无令牌 / 坏令牌均正确返回业务码 `401`。

### 真实端云链路
- 健康检查失败：`HTTP=000`
- `lsof -i :48080` 无监听
- `/actuator/health`、`/health`、`/api/claw-device/register` 全部连接失败（`curl exit=7`）
- `adb devices -l` 仍无在线设备

## 当前阻塞

1. DYQ 后端 `127.0.0.1:48080` 未监听
2. 无可用真机，无法执行 ADB 真机闭环

## 证据目录

- `artifacts/dyq3-smoke/20260604-213334-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-213334-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-213334-agent07191-heartbeat-wake-probe.log`

## 下一步最小可行

1. 等待后端恢复 `127.0.0.1:48080`
2. 接入真机后立即重跑同一套脚本并补 ADB 真机证据
