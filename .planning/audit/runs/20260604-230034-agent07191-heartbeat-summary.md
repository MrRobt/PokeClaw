# DYQ-3 心跳复核短报（2026-06-04 23:00）

## 结论
- Mock 端侧闭环继续稳定：注册、心跳、任务拉取、结果回传、无令牌、坏令牌、断网异常 7/7 通过。
- 真实 DYQ 后端 `127.0.0.1:48080` 已恢复监听，但业务层仍阻塞：`/actuator/health` 返回 `{"code":500,"data":null,"msg":"系统异常"}`，`/api/claw-device/register` 返回同样的业务异常，`/health` 返回 `{"code":401,"data":null,"msg":"账号未登录"}`。
- ADB 仍无在线设备。
- DYQ-3 仍不具备关闭条件，建议继续保持进行中，等待后端健康/注册异常修复与真机上线。

## 本轮执行
1. `bash -n scripts/dyq3-endcloud-smoke.sh`：通过。
2. `MOCK_PORT=18301 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-230034-agent07191-heartbeat-now/mock`：7/7 PASS，成功拉取 `taskUuid=8127ec2d-dd3f-4e8e-a5f7-f7023eca0168`。
3. `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-230034-agent07191-heartbeat-now/real`：健康检查失败，未生成 `summary.md`。
4. 补充探测：`lsof -i :48080` 显示 Java 进程监听；`curl` 访问 `/actuator/health` 与 `/api/claw-device/register` 均返回业务码 `500`，访问 `/health` 返回业务码 `401`；`adb devices -l` 无在线设备。

## 关键证据
- Mock 证据目录：`artifacts/dyq3-smoke/20260604-230034-agent07191-heartbeat-now/mock/`
- 真实冒烟输出：`artifacts/dyq3-smoke/20260604-230034-agent07191-heartbeat-now/real.stdout.log`
- 现场探测日志：`artifacts/dyq3-smoke/20260604-230034-agent07191-heartbeat-now/probe.log`
- 本轮短报：`.planning/audit/runs/20260604-230034-agent07191-heartbeat-summary.md`

## 当前阻塞
1. 后端 `48080` 虽已监听，但健康检查与注册接口仍报业务异常，真实端云链路卡在前置校验阶段。
2. 真机未上线，无法补跑 ADB 注册→心跳→任务回传现场证据。

## 下一步最小可行方案
1. 等待/推动后端修复 `48080` 上的健康检查与注册异常后，立即重跑真实冒烟。
2. 真机上线后补跑 ADB 最小闭环证据。
3. 两个阻塞解除后，再决定是否关闭 DYQ-3。
