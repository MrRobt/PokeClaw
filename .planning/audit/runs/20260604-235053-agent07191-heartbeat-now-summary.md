# DYQ-3 心跳复核 20260604-235053-agent07191-heartbeat-now

## 前置
- 时间: 2026-06-04 23:51:39 CST
- 工作目录: /mnt/e/code/PokeClaw

## Mock 验收
- 命令: `MOCK_PORT=18341 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-235053-agent07191-heartbeat-now/mock`
- 结果: 7/7 PASS
- 关键证据:
  - 成功拉取 `taskUuid=d4f8e9dc-3647-41ec-8636-4a46527dae10`
  - 注册/心跳/待处理任务/结果回传全部命中 `body.code in {0,200}`
  - 无令牌/坏令牌均返回业务码 401
  - 断网异常成功触发 `curl exit=7`

## 真实后端验收
- 命令: `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-235053-agent07191-heartbeat-now/real`
- 退出码: 1
- 结果: 健康检查阶段失败，HTTP=000，未进入注册链路

## 实时探测
- `lsof -i :48080`: 无监听
- `curl GET /actuator/health`: 连接失败
- `curl GET /health`: 连接失败
- `curl POST /api/claw-device/register`: 连接失败
- `adb devices -l`: 无在线设备

## 结论
- Mock 端侧链路继续稳定通过。
- 真实后端从上一轮“端口监听但业务异常”回退为“48080 未监听”。
- 真实端云闭环仍被“后端 48080 未监听 + 真机未上线”阻塞，DYQ-3 不能转完成。

## 证据目录
- `artifacts/dyq3-smoke/20260604-235053-agent07191-heartbeat-now/mock/`
- `artifacts/dyq3-smoke/20260604-235053-agent07191-heartbeat-now/real.stdout.log`
- `artifacts/dyq3-smoke/20260604-235053-agent07191-heartbeat-now/probe.log`
