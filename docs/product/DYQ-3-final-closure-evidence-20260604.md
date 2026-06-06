# DYQ-3｜PokeClaw 端侧执行链路阶段证据（2026-06-04）

## 0. 当前结论
- 端侧冒烟脚本判定已修正，不再把“HTTP 200 但业务报错”误判成健康。
- Mock 环境下注册、心跳、任务拉取、结果回传、无令牌、坏令牌、断网异常 7/7 全通过。
- 真实后端 `http://127.0.0.1:48080` 在 23:40 最新复核中已恢复端口监听，但健康/注册接口继续返回业务异常：`/actuator/health` 与 `/api/claw-device/register` 均为 `{"code":500,"data":null,"msg":"系统异常"}`，`/health` 返回 `{"code":401,"data":null,"msg":"账号未登录"}`。
- 因真实后端仍存在业务异常且 ADB 仍无在线设备，DYQ-3 仍不能转完成，状态应继续保持 in_progress 并推动后端修复接口异常与真机上线。

## 1. 本轮修复点
### 1.1 冒烟脚本判定修正
文件：`scripts/dyq3-endcloud-smoke.sh`

修正内容：
1. 健康检查必须满足以下任一条件才算通过：
   - 返回 `{"status":"UP"}`
   - 返回业务包装且 `code in {0,200}`
2. 注册/心跳/任务拉取/结果回传统一接受 `body.code=0` 或 `body.code=200`

原因：真实后端本轮出现 HTTP 200 但业务体为 `code=500`，旧脚本会把它误判成健康。

## 2. 本轮验证结果
### 2.0 最新复核（23:40 CST）
命令：
```bash
MOCK_PORT=18331 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/real
lsof -i :48080
curl -sS -i --max-time 5 http://127.0.0.1:48080/actuator/health
curl -sS -i --max-time 5 http://127.0.0.1:48080/health
curl -sS -i --max-time 5 -X POST http://127.0.0.1:48080/api/claw-device/register -H 'Content-Type: application/json' -d '{"deviceId":"pokeclaw-dyq3-probe-20260604-234017-agent07191-heartbeat-now","deviceName":"PokeClaw-DYQ3","deviceModel":"MockModel","androidVersion":"14","appVersion":"0.7.0"}'
adb devices -l
```

结果：
- Mock：再次 7/7 全 PASS，并成功拉取 `taskUuid=6b0f483c-8bc9-4968-a389-874d0818ed63`
- 真实后端：健康检查阶段失败，`REAL_RC=1`
- 补充探测：`lsof -i :48080` 显示 Java 进程监听；`/actuator/health` 返回 `{"code":500,"data":null,"msg":"系统异常"}`；`/health` 返回 `{"code":401,"data":null,"msg":"账号未登录"}`；`/api/claw-device/register` 返回 `{"code":500,"data":null,"msg":"系统异常"}`
- ADB：`adb devices -l` 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/mock/`
- `artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/real.stdout.log`
- `artifacts/dyq3-smoke/20260604-234017-agent07191-heartbeat-now/probe.log`
- `.planning/audit/runs/20260604-234017-agent07191-heartbeat-now-summary.md`

### 2.0 最新复核（21:24 CST）
命令：
```bash
MOCK_PORT=18240 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-212426-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-212426-agent07191-heartbeat-wake-real
lsof -i :48080
curl -sS -i --max-time 5 http://127.0.0.1:48080/actuator/health
curl -sS -i --max-time 5 http://127.0.0.1:48080/health
curl -sS -i --max-time 5 -X POST http://127.0.0.1:48080/api/claw-device/register -H 'Content-Type: application/json' -d '{"deviceId":"pokeclaw-dyq3-probe-20260604-212426","deviceName":"PokeClaw-DYQ3","deviceModel":"MockModel","androidVersion":"14","appVersion":"0.7.0"}'
adb devices -l
```

结果：
- Mock：再次 7/7 全 PASS，并成功拉取 `taskUuid=ff3fcba1-3e70-4ceb-8416-118b2296d535`
- 真实后端：健康检查阶段失败，`responses/health_check.code` 为 `000`
- 补充探测：`lsof -i :48080` 无监听，`/actuator/health`、`/health`、`/api/claw-device/register` 全部 `curl exit=7`
- ADB：`adb devices -l` 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-212426-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-212426-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-212426-agent07191-heartbeat-wake-probe.log`

### 2.1 上轮复核（21:19 CST）
命令：
```bash
MOCK_PORT=18230 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-211903-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-211903-agent07191-heartbeat-wake-real
lsof -i :48080
curl -sS -i --max-time 5 http://127.0.0.1:48080/actuator/health
curl -sS -i --max-time 5 http://127.0.0.1:48080/health
curl -sS -i --max-time 5 -X POST http://127.0.0.1:48080/api/claw-device/register -H 'Content-Type: application/json' -d '{"deviceId":"pokeclaw-dyq3-probe-20260604-211903","deviceName":"PokeClaw-DYQ3","deviceModel":"MockModel","androidVersion":"14","appVersion":"0.7.0"}'
adb devices -l
```

结果：
- Mock：再次 7/7 全 PASS，并成功拉取 `taskUuid=eee6da6b-bbea-4401-b328-1145d946bba8`
- 真实后端：健康检查阶段失败，`responses/health_check.code` 为 `000`
- 补充探测：`lsof -i :48080` 无监听，`/actuator/health`、`/health`、`/api/claw-device/register` 全部 `curl exit=7`
- ADB：`adb devices -l` 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-211903-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-211903-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-211903-agent07191-heartbeat-wake-probe.log`

### 2.1 上轮复核（20:57 CST）
命令：
```bash
MOCK_PORT=18146 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-205757-heartbeat/mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-205757-heartbeat/real
curl -sS --max-time 5 http://127.0.0.1:48080/actuator/health
curl -sS -X POST http://127.0.0.1:48080/api/claw-device/register -H 'Content-Type: application/json' -d '{"deviceId":"pokeclaw-dyq3-probe-<timestamp>","deviceName":"PokeClaw-DYQ3","deviceModel":"MockModel","androidVersion":"14","appVersion":"0.7.0"}'
adb devices -l
```

结果：
- Mock：再次 7/7 全 PASS，并成功拉取 `taskUuid=ff7ef408-0ca0-4b76-9c6b-d6c5de1df2c5`
- 真实后端：端口已监听，但健康检查仍失败；`/actuator/health` 返回 HTTP 200 + 业务体 `{"code":500,"data":null,"msg":"系统异常"}`
- 注册接口：同样返回 HTTP 200 + 业务体 `{"code":500,"data":null,"msg":"系统异常"}`，说明当前不是端侧报文错误，而是后端内部异常
- ADB：`adb devices -l` 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-205757-heartbeat/mock/`
- `artifacts/dyq3-smoke/20260604-205757-heartbeat/real/`
- `artifacts/dyq3-smoke/20260604-205757-heartbeat/probe.log`

### 2.2 上轮复核（18:18 CST）
命令：
```bash
MOCK_PORT=18145 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-181848-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-181848-agent07191-heartbeat-wake-real
```

结果：
- Mock：再次 7/7 全 PASS，并成功拉取 `pendingTaskCount=1` 与 `taskUuid=ef976b32-f597-45cc-9dda-60ba93e9b719`
- 真实后端：仍退化为端口不可达，健康检查失败，`responses/health_check.code` 为 `000`
- 补充探测：`lsof -i :48080` 无监听，`/actuator/health`、`/health`、`/api/claw-device/register` 均 `curl exit=7`，ADB 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-181848-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-181848-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-181848-agent07191-heartbeat-wake-probe.log`

### 2.3 上轮复核（18:13 CST）
命令：
```bash
MOCK_PORT=18144 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-181352-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-181352-agent07191-heartbeat-wake-real
```

结果：
- Mock：再次 7/7 全 PASS，并成功拉取 `pendingTaskCount=1` 与 `taskUuid=62f5df8e-05c6-408e-bb4b-5bc79f74d2d6`
- 真实后端：仍退化为端口不可达，健康检查失败，`responses/health_check.code` 为 `000`
- 补充探测：`lsof -i :48080` 无监听，`/actuator/health`、`/health`、`/api/claw-device/register` 均 `curl exit=7`，ADB 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-181352-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-181352-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-181352-agent07191-heartbeat-wake-probe.log`

### 2.4 上轮复核（17:59 CST）
命令：
```bash
MOCK_PORT=18142 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-175850-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-175850-agent07191-heartbeat-wake-real
```

结果：
- Mock：再次 7/7 全 PASS
- 真实后端：仍退化为端口不可达，健康检查失败，`responses/health_check.code` 为 `000`
- 补充探测：`lsof -i :48080` 无监听，`/actuator/health`、`/health`、`/api/claw-device/register` 均 `curl exit=7`，ADB 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-175850-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-175850-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-175850-agent07191-heartbeat-wake-probe.log`

### 2.5 上轮复核（17:45 CST）
命令：
```bash
MOCK_PORT=18141 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-174500-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-174500-agent07191-heartbeat-wake-real
```

结果：
- Mock：再次 7/7 全 PASS
- 真实后端：退化为端口不可达，健康检查失败，`responses/health_check.code` 为 `000`
- 补充探测：`lsof -i :48080` 无监听，`/actuator/health` 与 `/health` 均 `curl exit=7`，ADB 仍无在线设备

新增证据：
- `artifacts/dyq3-smoke/20260604-174500-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-174500-agent07191-heartbeat-wake-real/`

### 2.6 最新复核（15:55 CST）
命令：
```bash
MOCK_PORT=18131 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-154845-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-154845-agent07191-heartbeat-wake-real
```

结果：
- Mock：再次 7/7 全 PASS
- 真实后端：仍卡在健康检查，表现未变

新增证据：
- `artifacts/dyq3-smoke/20260604-154845-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-154845-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-154845-agent07191-heartbeat-wake-probe.log`

### 2.7 15:41 复核（留档）
命令：
```bash
MOCK_PORT=18130 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-153937-agent07191-heartbeat-wake-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-153937-agent07191-heartbeat-wake-real
```

结果：
- Mock：再次 7/7 全 PASS
- 真实后端：仍卡在健康检查，表现未变

新增证据：
- `artifacts/dyq3-smoke/20260604-153937-agent07191-heartbeat-wake-mock/`
- `artifacts/dyq3-smoke/20260604-153937-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-153937-agent07191-heartbeat-wake-probe.log`

### 2.8 Mock 闭环通过
命令：
```bash
MOCK_PORT=18120 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-152330-agent07191-heartbeat-wake-mock
```

结果：7/7 全 PASS
- 注册
- 心跳
- 拉取待处理任务
- 回传执行结果
- 无 token 错误
- 坏 token 错误
- 断网异常

证据目录：
- `artifacts/dyq3-smoke/20260604-152330-agent07191-heartbeat-wake-mock/`

### 2.9 真实后端仍阻塞
命令：
```bash
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-152330-agent07191-heartbeat-wake-real
```

结果：健康检查阶段失败

关键证据：
```json
{"code":500,"data":null,"msg":"系统异常"}
```

补充探测：
- `lsof -i :48080` 显示 Java 进程已监听，说明不再是端口未起。
- `/health` 返回 `{"code":401,"data":null,"msg":"账号未登录"}`，表明真实后端仍存在鉴权链路异常。
- `adb devices -l` 仍无在线设备。

证据目录：
- `artifacts/dyq3-smoke/20260604-152330-agent07191-heartbeat-wake-real/`
- `artifacts/dyq3-smoke/20260604-152330-agent07191-heartbeat-wake-probe.log`

## 3. 对验收标准的当前判断
### 3.1 设备注册与心跳稳定
- Mock：通过
- 真实后端：未通过，阻塞于健康检查

### 3.2 云端下发指令 → 端侧执行 → 回传结果可复现
- Mock：通过
- 真实后端：未进入该阶段

### 3.3 弱网/异常时用户可见错误提示
- Mock：通过
- 真实后端：待后端健康后继续验证

## 4. 当前阻塞点
1. 真实后端 48080 虽已监听，但 `/actuator/health` 与 `/api/claw-device/register` 均返回 `code=500`，`/health` 返回 `code=401`
2. 因健康检查失败，真实注册/心跳/任务拉取/结果回传本轮不应继续宣称闭环完成
3. `adb devices -l` 仍无在线设备，真机链路证据仍缺

## 5. 下一步最小可行方案
1. 后端先修复 `127.0.0.1:48080` 的健康/注册接口业务异常，再复核 `/actuator/health`
2. 修复后重跑真实后端冒烟脚本
3. 真机上线后补跑 ADB 最小证据与端到端 UI 验证
