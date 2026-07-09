# DYQ-3 2026-06-05 00:00 心跳复核摘要

## 结论
- Mock 闭环继续稳定通过，7/7 全通过。
- 真实后端 `127.0.0.1:48080` 当前已监听，但仍无法进入设备注册成功态。
- 真实阻塞已收敛为两点：
  1. 健康探针不满足当前脚本判定：`GET /api/health` 返回 `code=401,msg=账号未登录`，`GET /actuator/health` 返回 `code=500,msg=系统异常`。
  2. 设备注册直接失败：`POST /api/claw-device/register` 返回 `code=500,msg=系统异常`。
- ADB 仍无在线设备，真机端到端仍不可执行。

## 本轮执行命令
```bash
MOCK_PORT=18341 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-235937-agent07191-heartbeat-now/mock
adb devices -l
lsof -i :48080
curl -sS --max-time 5 http://127.0.0.1:48080/actuator/health
curl -sS --max-time 5 http://127.0.0.1:48080/health
curl -sS --max-time 5 http://127.0.0.1:48080/api/health
curl -sS --max-time 10 -X POST http://127.0.0.1:48080/api/claw-device/register -H 'Content-Type: application/json' -d '{...}'
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/api/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260605-000036-agent07191-realprobe/real
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_CHECK=0 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260605-000036-agent07191-realprobe/real-no-health
```

## 关键结果
### Mock
- 输出目录：`artifacts/dyq3-smoke/20260604-235937-agent07191-heartbeat-now/mock`
- 成功拉取 `taskUuid=e6f6fd9d-045a-4ee0-95c9-3c511dd51016`
- 注册/心跳/任务拉取/结果回传/无 token/坏 token/断网 全部通过

### 真实后端
- `lsof -i :48080`：Java 进程监听中
- `GET /actuator/health`：`{"code":500,"data":null,"msg":"系统异常"}`
- `GET /health`：`{"code":401,"data":null,"msg":"账号未登录"}`
- `GET /api/health`：`{"code":401,"data":null,"msg":"账号未登录"}`
- `POST /api/claw-device/register`：`{"code":500,"data":null,"msg":"系统异常"}`
- `HEALTH_PATH=/api/health` 的真实冒烟：健康检查阶段失败
- `HEALTH_CHECK=0` 的真实冒烟：跳过健康检查后直接卡在注册 `body.code=500`

### ADB
- `adb devices -l` 空列表

## 本轮阻塞判断
1. 后端服务进程存在，但设备注册链路仍是业务异常，不是网络异常。
2. 健康端点当前带鉴权/异常返回，现有脚本无法把它当作“服务活着但业务未就绪”的中间态。
3. 真机未上线，无法补齐 Android 端日志与可见 UI 证据。

## 证据路径
- `artifacts/dyq3-smoke/20260604-235937-agent07191-heartbeat-now/mock`
- `artifacts/dyq3-smoke/20260605-000036-agent07191-realprobe/real`
- `artifacts/dyq3-smoke/20260605-000036-agent07191-realprobe/real-no-health.stdout.log`
- `.planning/audit/runs/20260605-000036-agent07191-realprobe-summary.md`
