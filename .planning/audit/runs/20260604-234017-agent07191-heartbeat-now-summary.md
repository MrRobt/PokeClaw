# DYQ-3 心跳复核 20260604-234017-agent07191-heartbeat-now

## 前置
- 时间: 2026-06-04 23:40:17 CST
- 工作目录: /mnt/e/code/PokeClaw

## 脚本语法检查
- `bash -n scripts/dyq3-endcloud-smoke.sh`: PASS

## Mock 验收
- 命令: `MOCK_PORT=18331 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/mock`
- 结果: 7/7 PASS
- 关键证据:
  - 成功拉取 `taskUuid=6b0f483c-8bc9-4968-a389-874d0818ed63`
  - 注册/心跳/待处理任务/结果回传全部命中 `body.code in {0,200}`
  - 无令牌/坏令牌均返回业务码 401
  - 断网异常成功触发 `curl exit=7`

## 真实后端验收
- 命令: `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/real`
- 退出码: 1
- 结果: 健康检查阶段失败，未生成 `summary.md`

## 实时探测
- `lsof -i :48080`: Java 进程监听 `*:48080`
- `curl GET /actuator/health`: HTTP 200，业务体 `{"code":500,"data":null,"msg":"系统异常"}`
- `curl GET /health`: HTTP 200，业务体 `{"code":401,"data":null,"msg":"账号未登录"}`
- `curl POST /api/claw-device/register`: HTTP 200，业务体 `{"code":500,"data":null,"msg":"系统异常"}`
- `adb devices -l`: 无在线设备

## 结论
- Mock 端侧链路继续稳定通过。
- 真实后端已从上一轮“端口不可达”恢复为“端口监听但健康/注册接口业务异常”。
- 真实端云闭环仍被“后端 48080 业务异常 + 真机未上线”阻塞，DYQ-3 不能转完成。

## 证据目录
- `artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/mock/`
- `artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/real.stdout.log`
- `artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/probe.log`
