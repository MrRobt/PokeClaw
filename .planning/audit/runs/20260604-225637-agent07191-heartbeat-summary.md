# DYQ-3 心跳复核短报（2026-06-04 22:56）

## 结论
- Mock 端侧闭环继续稳定：注册、心跳、任务拉取、结果回传、无令牌、坏令牌、断网异常 7/7 通过。
- 真实 DYQ 后端 `127.0.0.1:48080` 当前仍未监听：`lsof -i :48080` 无输出，`/actuator/health`、`/health`、`/api/claw-device/register` 全部连接失败。
- ADB 仍无在线设备。
- DYQ-3 仍不具备关闭条件，建议继续保持进行中并等待后端监听恢复与真机上线。

## 本轮执行
1. `bash -n scripts/dyq3-endcloud-smoke.sh`：通过。
2. `MOCK_PORT=18321 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-225637-agent07191-heartbeat-mock`：7/7 PASS，成功拉取 `taskUuid=c3449127-e1f4-46f0-902e-de13a94104d8`。
3. `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-225637-agent07191-heartbeat-real`：健康检查失败，`REAL_RC=1`，`HTTP=000`。
4. 补充探测：`lsof -i :48080` 无监听；`curl -sS -i --max-time 5` 访问 `/actuator/health`、`/health`、`/api/claw-device/register` 全部 `exit=7`；`adb devices -l` 无在线设备。

## 关键证据
- Mock 证据目录：`artifacts/dyq3-smoke/20260604-225637-agent07191-heartbeat-mock/`
- 真实冒烟目录：`artifacts/dyq3-smoke/20260604-225637-agent07191-heartbeat-real/`
- 现场探测日志：`artifacts/dyq3-smoke/20260604-225637-agent07191-heartbeat-probe.log`
- 本轮短报：`.planning/audit/runs/20260604-225637-agent07191-heartbeat-summary.md`

## 当前阻塞
1. 后端 `48080` 未监听，真实端云链路卡在健康检查前置阶段。
2. 真机未上线，无法补跑 ADB 注册→心跳→任务回传现场证据。

## 下一步最小可行方案
1. 等待/推动后端恢复 `48080` 监听后，立即重跑真实冒烟。
2. 真机上线后补跑 ADB 最小闭环证据。
3. 两个阻塞解除后，再决定是否关闭 DYQ-3。
