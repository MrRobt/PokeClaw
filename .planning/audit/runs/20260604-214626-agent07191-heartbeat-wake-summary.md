# DYQ-3 心跳唤醒复核（2026-06-04 21:46）

- 问题：DYQ-3｜PokeClaw 端侧执行链路商业化验收
- 执行人：端侧工程师阿甲
- 结论：Mock 端侧闭环继续通过；真实端云闭环仍阻塞，但阻塞口径从“48080 未监听”更新为“48080 已监听，/actuator/health 与 register 均返回业务码 500，/health 返回 401，ADB 仍无在线设备”。

## 本轮命令

```bash
bash -n scripts/dyq3-endcloud-smoke.sh
MOCK_PORT=18241 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-214626-agent07191-heartbeat-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-214626-agent07191-heartbeat-real
lsof -i :48080
curl -sS -i --max-time 5 http://127.0.0.1:48080/actuator/health
curl -sS -i --max-time 5 http://127.0.0.1:48080/health
curl -sS -i --max-time 5 -X POST http://127.0.0.1:48080/api/claw-device/register -H 'Content-Type: application/json' -d '{"deviceId":"pokeclaw-dyq3-probe-20260604-214626","deviceName":"PokeClaw-DYQ3","deviceModel":"MockModel","androidVersion":"14","appVersion":"0.7.0"}'
adb devices -l
```

## 结果

### 1. Mock 闭环
- 7/7 全 PASS
- 成功拉取任务：`706a30a7-a7ca-4424-a2b3-503b8c525bf0`
- 注册/心跳/任务拉取/结果回传全部命中 `body.code in {0,200}`
- 无 token、坏 token 均正确返回业务码 401
- 断网异常正确触发 `curl exit=7`

### 2. 真实后端
- 冒烟脚本在健康检查阶段失败，退出码 `1`
- `lsof -i :48080`：存在 Java 进程监听 48080
- `/actuator/health`：HTTP 200，但响应体为 `{"code":500,"data":null,"msg":"系统异常"}`
- `/health`：HTTP 200，但响应体为 `{"code":401,"data":null,"msg":"账号未登录"}`
- `/api/claw-device/register`：HTTP 200，但响应体为 `{"code":500,"data":null,"msg":"系统异常"}`
- `adb devices -l`：无在线设备

## 证据路径
- `artifacts/dyq3-smoke/20260604-214626-agent07191-heartbeat-mock/`
- `artifacts/dyq3-smoke/20260604-214626-agent07191-heartbeat-real/`
- `artifacts/dyq3-smoke/20260604-214626-agent07191-heartbeat-probe.log`

## 阻塞结论
1. 端侧 Mock 验收继续稳定，不是端侧脚本回归。
2. 真实后端当前不是“端口未起”，而是“服务已起但健康/注册业务异常”。
3. 真机仍未接入，真实端侧执行证据无法补齐。
4. DYQ-3 不能转完成，需继续推动后端修复 `500/401` 与真机上线。