# DYQ-3 心跳复核短报（2026-06-04 22:19）

## 结论
- Mock 端侧闭环继续稳定，注册、心跳、任务拉取、结果回传、无令牌、坏令牌、断网异常 7/7 通过。
- 真实后端 `127.0.0.1:48080` 仍未达到可联调状态：端口已监听，但健康检查与注册接口业务体仍报系统异常；真机 ADB 仍无在线设备。
- DYQ-3 继续保持 `in_progress`，不能转完成。

## 本轮执行
1. `bash -n scripts/dyq3-endcloud-smoke.sh`
2. `MOCK_PORT=18261 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-221941-agent07191-heartbeat-mock`
3. `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-221941-agent07191-heartbeat-real`
4. `lsof -i :48080`
5. `curl -sS -i --max-time 5 http://127.0.0.1:48080/actuator/health`
6. `curl -sS -i --max-time 5 http://127.0.0.1:48080/health`
7. `curl -sS -i --max-time 5 -X POST http://127.0.0.1:48080/api/claw-device/register -H 'Content-Type: application/json' -d '{"deviceId":"pokeclaw-dyq3-probe-20260604-221941","deviceName":"PokeClaw-DYQ3","deviceModel":"MockModel","androidVersion":"14","appVersion":"0.7.0"}'`
8. `adb devices -l`

## 关键证据
- Mock 成功任务：`taskUuid=b9ff0e66-c241-48c0-8df9-95014fdc3150`
- `lsof -i :48080`：Java 进程监听中
- `/actuator/health`：HTTP 200 + `{"code":500,"data":null,"msg":"系统异常"}`
- `/health`：HTTP 200 + `{"code":401,"data":null,"msg":"账号未登录"}`
- `/api/claw-device/register`：HTTP 200 + `{"code":500,"data":null,"msg":"系统异常"}`
- `adb devices -l`：无在线设备

## 证据目录
- `artifacts/dyq3-smoke/20260604-221941-agent07191-heartbeat-mock/`
- `artifacts/dyq3-smoke/20260604-221941-agent07191-heartbeat-real/`
- `artifacts/dyq3-smoke/20260604-221941-agent07191-heartbeat-probe.log`

## 当前阻塞
1. 后端 48080 虽已监听，但健康检查与注册接口业务异常未解。
2. 真机仍未上线，无法补跑真实 ADB 端到端证据。

## 下一步最小可行方案
1. 后端先修复 `/actuator/health` 与 `/api/claw-device/register` 的业务异常。
2. 真机上线后补跑 ADB 最小闭环证据。
3. 阻塞解除后重跑真实端云冒烟并更新 DYQ-3 状态。
