# DYQ-3 心跳复核短报（2026-06-04 22:50）

## 结论
- Mock 端侧闭环稳定：注册、心跳、任务拉取、结果回传、无令牌、坏令牌、断网异常 7/7 通过。
- 真实 DYQ 后端 `127.0.0.1:48080` 当前仍未就绪：连续探测均连接拒绝，`lsof -i :48080` 无监听。
- 真机 ADB 仍无在线设备。
- DYQ-3 暂不具备关闭条件，建议保持阻塞跟进。

## 本轮执行
1. 语法检查：`bash -n scripts/dyq3-endcloud-smoke.sh` 通过。
2. Mock 冒烟：`MOCK_PORT=18281 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-225034-agent07191-heartbeat-mock` → 7/7 PASS。
3. 真实冒烟：`USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-225034-agent07191-heartbeat-real` → 健康检查失败，`HTTP=000`。
4. 后端探测：`curl` 访问 `/actuator/health`、`/health`、`/api/claw-device/register` 全部 `exit=7`。
5. ADB：`adb devices -l` → 无设备。
6. 进程侧补查：发现 `/mnt/e/code/dyq/dyq-server/target/dyq-server.jar` 正在启动（PID 475707），但截至 22:53 日志仅推进到 `DynamicTp registrar, no executors are configured.`，端口仍未绑定。

## 关键证据
- Mock 成功任务：`taskUuid=6d00d206-8a60-4753-9616-9b345769ae6f`
- Mock 证据目录：`artifacts/dyq3-smoke/20260604-225034-agent07191-heartbeat-mock/`
- 真实冒烟目录：`artifacts/dyq3-smoke/20260604-225034-agent07191-heartbeat-real/`
- 后端启动日志：`/mnt/e/code/dyq/logs/dyq-server-startup.log`

## 当前阻塞
1. 后端 `48080` 尚未监听，真实端云链路仍卡在健康检查前置阶段。
2. 真机未上线，无法补跑 ADB 最小闭环证据。

## 下一步最小可行方案
1. 等待/推动后端启动完成并绑定 `48080`，随后立即重跑真实冒烟。
2. 真机上线后补跑 ADB 注册→心跳→任务回传证据。
3. 两个阻塞解除后再决定是否关闭 DYQ-3。
