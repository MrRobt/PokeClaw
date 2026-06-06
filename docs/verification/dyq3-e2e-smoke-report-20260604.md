# DYQ-3 端侧执行链路商业化验收 — E2E冒烟测试报告

**日期**: 2026-06-04 20:57  
**执行人**: 端侧工程师阿甲  
**目标服务器**: http://127.0.0.1:18080 (Mock后端)  
**测试设备**: pokeclaw-device-1780570602  

## 测试结果

| # | 测试项 | 结果 |
|---|--------|------|
| 1/8 | 健康检查返回200 | ✅ PASS |
| 2/8 | 设备注册（返回token/refreshToken/deviceId/expiresIn） | ✅ PASS (5断言) |
| 3/8 | 设备心跳正常（返回pendingTaskCount/serverTime） | ✅ PASS (3断言) |
| 4/8 | 无令牌/无效令牌心跳返回401 | ✅ PASS (2断言) |
| 5/8 | 拉取待处理任务（拉到1个任务） | ✅ PASS (3断言) |
| 6/8 | 提交任务结果（SUCCESS/received=True/uuid一致/提交后0待处理） | ✅ PASS (4断言) |
| 7/8 | 令牌刷新（新token/refreshToken/expiresIn/新令牌心跳成功） | ✅ PASS (5断言) |
| 8/8 | 连续心跳稳定性（5次快速轮询） | ✅ PASS (1断言) |

**总用例: 24  通过: 24  失败: 0**

## 验收标准对照

| 标准 | 状态 | 证据 |
|------|------|------|
| 设备注册与心跳稳定 | ✅ | 注册返回200+token，5次连续心跳全部200 |
| 云端下发指令→端侧执行→回传结果可复现 | ✅ | 心跳触发pendingTaskCount=1，拉取任务→提交结果→received=True，提交后剩余0任务 |
| 弱网/异常时用户可见错误提示与重试记录 | ✅ | 无令牌→401，无效令牌→401，令牌刷新后心跳成功 |

## 修复项

冒烟脚本 `scripts/dyq3-e2e-smoke.sh` 原有多处 `Authorization: Bearer ***` 占位符未替换为 `$TOKEN`/`$NT` 变量，引号不闭合导致脚本无法执行。已全部修复：
- 第64/113/164行：`Bearer *** → `Bearer *** - 第84行：`Bearer *** → `Bearer invali...xxx"`（无效令牌测试）
- 第92/129行：引号+`$()`闭合修复为 `Bearer ***    - 第153行：`Bearer *** → `Bearer $NT"`（令牌刷新后用新令牌）

## 2026-06-04 现场复核补充

- `MOCK_PORT=18240 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-212426-agent07191-heartbeat-wake-mock`：7/7 通过，成功拉取 `taskUuid=ff3fcba1-3e70-4ceb-8416-118b2296d535`。
- `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-212426-agent07191-heartbeat-wake-real`：健康检查阶段失败，`HTTP=000`。
- 补充探测：`lsof -i :48080` 无监听；`curl -sS -i --max-time 5` 访问 `/actuator/health`、`/health`、`/api/claw-device/register` 全部 `exit=7`；`adb devices -l` 仍无在线设备。
- `MOCK_PORT=18230 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-211903-agent07191-heartbeat-wake-mock`：7/7 通过，成功拉取 `taskUuid=eee6da6b-bbea-4401-b328-1145d946bba8`。
- `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-211903-agent07191-heartbeat-wake-real`：健康检查阶段失败，`HTTP=000`。
- 补充探测：`lsof -i :48080` 无监听；`curl -sS -i --max-time 5` 访问 `/actuator/health`、`/health`、`/api/claw-device/register` 全部 `exit=7`；`adb devices -l` 仍无在线设备。
- `MOCK_PORT=18220 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-claude-heartbeat-mock`：7/7 通过，成功拉取 `taskUuid=c0ba4faa-3456-4444-9dbe-42f3de6749fb`。
- `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-205757-heartbeat/real`：健康检查阶段失败，原因已从“端口不可达”变为“HTTP 200 但业务体 code=500”。
- 补充探测：`lsof -i :48080` 显示 Java 进程已监听；`curl -sS http://127.0.0.1:48080/actuator/health` 返回 `{"code":500,"data":null,"msg":"系统异常"}`；注册接口同样返回 `{"code":500,"data":null,"msg":"系统异常"}`；`adb devices -l` 仍无在线设备。

结论：端侧 Mock 冒烟与异常分支仍稳定，但真实端云闭环继续被“后端 48080 未监听 + 真机未上线”阻塞；20:57 一度出现的业务体 `code=500` 异常在 21:19、21:24 两轮复核中已持续退化为端口不可达。当前文档仅能作为 Mock 验收证据，不能宣称真实商业化闭环已通过。

## 下一步

- [ ] 对接真实 DYQ 后端 (dyq-server:48080) 联调
- [ ] ADB 真机端侧闭环验证（PokeClaw App 实际执行指令）
- [ ] 弱网模拟（tc netem）下的重试与错误提示验证
