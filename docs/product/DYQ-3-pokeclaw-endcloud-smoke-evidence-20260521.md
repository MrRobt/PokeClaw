# DYQ-3｜PokeClaw端侧执行链路最小冒烟证据（2026-05-21）

## 0. 背景与目标对齐
- 指派来源：总负责人评论（2026-05-21）要求在 2026-05-22 18:00 前提交最小冒烟证据。
- 三大目标对齐：
  - Claw云端中枢与运行时沙箱：验证设备接口可用与任务闭环回传。
  - PokeClaw端侧执行体：验证注册、心跳、任务拉取、结果上报链路。
  - weflow微信控制底座：本次不改微信控制逻辑，保持现有接入稳定。

## 1. 本次改动文件
- `scripts/dyq3-endcloud-smoke.sh`：新增最小冒烟脚本，自动产出证据目录。
- `docs/product/DYQ-3-pokeclaw-endcloud-smoke-evidence-20260521.md`：本验收证据文档。
- `artifacts/dyq3-smoke/20260521-225354/*`：本次执行生成的原始日志与响应样本。

### 1.1 2026-05-21 第二轮补充
- `scripts/dyq3-endcloud-smoke.sh`：增强为支持外部后端复跑（`USE_MOCK_BACKEND=0`）和可配置健康检查（`HEALTH_CHECK`、`HEALTH_PATH`）。
- `scripts/dyq3-endcloud-smoke.sh`：新增弱网/断网连接失败证据采集（`heartbeat_network_down.err`）。
- `artifacts/dyq3-smoke/20260521-225727/*`：第二轮执行生成证据（含新增弱网错误原始输出）。

## 2. 验证命令
```bash
# 1) 执行最小冒烟（自动启动mock后端）
scripts/dyq3-endcloud-smoke.sh

# 2) 查看汇总
cat artifacts/dyq3-smoke/20260521-225354/summary.md

# 3) 查看关键原始证据
cat artifacts/dyq3-smoke/20260521-225354/responses/register.json
cat artifacts/dyq3-smoke/20260521-225354/responses/heartbeat.json
cat artifacts/dyq3-smoke/20260521-225354/responses/pending.json
cat artifacts/dyq3-smoke/20260521-225354/responses/result.json
cat artifacts/dyq3-smoke/20260521-225354/responses/heartbeat_no_token.json
cat artifacts/dyq3-smoke/20260521-225354/responses/heartbeat_bad_token.json
cat artifacts/dyq3-smoke/20260521-225354/adb_minimal.log

# 4) 第二轮新增证据（弱网/断网）
cat artifacts/dyq3-smoke/20260521-225727/summary.md
cat artifacts/dyq3-smoke/20260521-225727/responses/heartbeat_network_down.err

# 5) 外部后端复跑样例（不启动本地mock）
USE_MOCK_BACKEND=0 HEALTH_CHECK=0 DYQ_BASE_URL=http://<dyq-backend-host>:<port> scripts/dyq3-endcloud-smoke.sh
```

## 3. 结果

### 3.1 设备注册/心跳记录
- register: HTTP 200
- heartbeat: HTTP 200
- pendingTaskCount: 1
- 证据文件：
  - `artifacts/dyq3-smoke/20260521-225354/responses/register.json`
  - `artifacts/dyq3-smoke/20260521-225354/responses/heartbeat.json`

关键片段：
```json
{"code":200,"data":{"deviceId":"pokeclaw-dyq3-20260521-225354","deviceToken":"mock-device-token-94fc6dba","expiresIn":3600},"msg":"success"}
```
```json
{"code":200,"data":{"pendingTaskCount":1,"serverTime":"2026-05-21T22:53:54.750478"},"msg":"success"}
```

### 3.2 云端下发到端侧回传链路日志
- pending 拉取：HTTP 200，返回任务 `taskUuid=92e8625a-4439-45c1-80ea-df0a78837d33`
- result 回传：HTTP 200，`received=true`
- 证据文件：
  - `artifacts/dyq3-smoke/20260521-225354/responses/pending.json`
  - `artifacts/dyq3-smoke/20260521-225354/responses/result.json`
  - `artifacts/dyq3-smoke/20260521-225354/mock_server.log`

关键片段：
```json
{"code":200,"data":[{"uuid":"92e8625a-4439-45c1-80ea-df0a78837d33","type":"SIMPLE_ACTION","payload":{"action":"open_app","packageName":"com.android.settings"}}],"msg":"success"}
```
```json
{"code":200,"data":{"received":true,"taskUuid":"92e8625a-4439-45c1-80ea-df0a78837d33"},"msg":"success"}
```

### 3.3 ADB 最小验证记录
- 已执行：`adb start-server`、`adb devices -l`、`adb shell getprop ro.product.model`
- 结果：当前环境无已连接设备，`adb shell` 返回 `no devices/emulators found`（错误证据已保留）。
- 证据文件：`artifacts/dyq3-smoke/20260521-225354/adb_minimal.log`

关键片段：
```text
List of devices attached

adb: no devices/emulators found
adb shell exitCode=1
```

### 3.4 异常场景用户可见报错证据
- 场景1：心跳请求缺失 Authorization
  - 响应业务码：`code=401`
  - 响应消息：`缺少有效令牌`
- 场景2：心跳请求使用无效 token
  - 响应业务码：`code=401`
  - 响应消息：`令牌无效`
- 证据文件：
  - `artifacts/dyq3-smoke/20260521-225354/responses/heartbeat_no_token.json`
  - `artifacts/dyq3-smoke/20260521-225354/responses/heartbeat_bad_token.json`

### 3.5 弱网/断网错误证据（第二轮补充）
- 场景：心跳请求发送到不可达地址（`127.0.0.1:9`）触发连接失败。
- 结果：`curl_exit=7`，错误信息 `Failed to connect ... Could not connect to server`。
- 证据文件：
  - `artifacts/dyq3-smoke/20260521-225727/summary.md`
  - `artifacts/dyq3-smoke/20260521-225727/responses/heartbeat_network_down.err`

## 4. 风险
- 当前闭环基于 `scripts/mock-dyq-backend.py`，尚未覆盖真实 dyq 后端全部鉴权和业务校验策略。
- 当前执行环境无真机在线，ADB 仅完成最小命令验证，未覆盖端侧 logcat 实际运行日志。

## 5. 待验证清单
- [ ] 接入真实 dyq 后端地址后复跑脚本，对比真实响应字段与错误语义。
  - 2026-06-01 21:34 已复跑 `http://192.168.250.3:48081`：健康检查通过，但注册接口仍返回 `code=401,msg=账号未登录`，未返回 `deviceToken`，真实闭环继续阻塞于后端设备接口免登录/鉴权链路。
- [ ] 连接真机后补齐 ADB 证据：注册日志、30秒心跳日志、任务回传日志。
  - 2026-06-01 21:34 当前环境 `adb devices -l` 无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`。
- [ ] 增补“端侧执行失败”真机用户可见提示证据（权限缺失、网络断开）。
  - 2026-06-01 21:34 Mock 冒烟已覆盖弱网/断网连接失败，真机 UI 可见提示仍待在线设备补齐。

## 5.1 2026-06-01 心跳复核证据
|-|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-213428-agent07191-heartbeat/summary.md`|
|真实 dev 后端|阻塞：健康检查通过，注册 HTTP 200 但业务响应 `{"code":401,"data":null,"msg":"账号未登录"}`，未返回 `deviceToken`|`artifacts/dyq3-smoke/20260601-213428-agent07191-real/smoke_run.log`、`artifacts/dyq3-smoke/20260601-213428-agent07191-real/responses/register.json`|
|ADB 最小记录|阻塞：无在线设备|`artifacts/dyq3-smoke/20260601-213428-agent07191-heartbeat/adb_minimal.log`|

## 5.2 2026-06-01 二次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-223403-agent07191-heartbeat2/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 连接失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-223415-agent07191-real2/smoke_run.log`|
|ADB 最小记录|阻塞：无在线设备|`artifacts/dyq3-smoke/20260601-223403-agent07191-heartbeat2/adb_minimal.log`|

## 5.3 2026-06-01 三次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-224108-agent07191-heartbeat3/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-224122-agent07191-real3/smoke_run.log`、`artifacts/dyq3-smoke/20260601-224122-agent07191-real3-terminal.log`|
|ADB 最小记录|阻塞：无在线设备|`artifacts/dyq3-smoke/20260601-224108-agent07191-heartbeat3/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍被 `DYQ-10` 阻塞；`DYQ-10` 仍被 `DYQ-25` 阻塞；`DYQ-25` 当前 `in_progress`，等待 dev 白名单部署完成|Paperclip issue 快照：2026-06-01 22:42 +0800|

## 5.4 2026-06-01 四次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-224758-agent07191-heartbeat4/summary.md`、`artifacts/dyq3-smoke/20260601-224758-agent07191-heartbeat4-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-224758-agent07191-real4/smoke_run.log`、`artifacts/dyq3-smoke/20260601-224758-agent07191-real4-terminal.log`|
|ADB 最小记录|阻塞：无在线设备|`artifacts/dyq3-smoke/20260601-224758-agent07191-heartbeat4/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接阻塞 `DYQ-10` 仍为 `blocked`，终端阻塞 `DYQ-139` 当前 `in_progress`，等待真实后端白名单/鉴权链路恢复|Paperclip issue 快照：2026-06-01 22:48 +0800|

## 5.5 2026-06-01 五次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-225419-agent07191-heartbeat5/summary.md`、`artifacts/dyq3-smoke/20260601-225419-agent07191-heartbeat5-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-225431-agent07191-real5/smoke_run.log`、`artifacts/dyq3-smoke/20260601-225431-agent07191-real5-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260601-225419-agent07191-heartbeat5/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；阻塞关系仍包含 `DYQ-3 -> DYQ-5`、`DYQ-10 -> DYQ-3`、`DYQ-25 -> DYQ-10`、`DYQ-139 -> DYQ-25`，真实后端/三端验收依赖未解锁|Paperclip issue relation 快照：2026-06-01 22:54 +0800|

## 5.6 2026-06-01 六次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-230220-agent07191-heartbeat6/summary.md`、`artifacts/dyq3-smoke/20260601-230220-agent07191-heartbeat6-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-230220-agent07191-real6/smoke_run.log`、`artifacts/dyq3-smoke/20260601-230220-agent07191-real6-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260601-230220-agent07191-heartbeat6/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接阻塞 `DYQ-10` 仍为 `blocked`，其终端阻塞 `DYQ-139` 当前 `in_progress`；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-01 23:02 +0800|

## 5.7 2026-06-01 七次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-232050-agent07191-heartbeat7/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-232050-agent07191-real7/smoke_run.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260601-232050-agent07191-heartbeat7/adb_devices.log`、`artifacts/dyq3-smoke/20260601-232050-agent07191-heartbeat7/adb_model.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接阻塞 `DYQ-10` 仍为 `blocked`，其终端阻塞 `DYQ-139` 当前 `blocked`；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-01 23:21 +0800|

## 5.8 2026-06-01 八次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-232700-agent07191-heartbeat8/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-232700-agent07191-real8/smoke_run.log`、`artifacts/dyq3-smoke/20260601-232700-agent07191-real8-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260601-232700-agent07191-heartbeat8/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接阻塞 `DYQ-10` 仍为 `blocked`，终端阻塞样本 `DYQ-25` 当前仍需关注；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-01 23:27 +0800|

## 5.9 2026-06-01 九次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-233439-agent07191-heartbeat9/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-233439-agent07191-real9/smoke_run.log`、`artifacts/dyq3-smoke/20260601-233439-agent07191-real9-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260601-233439-agent07191-heartbeat9/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接阻塞 `DYQ-10` 仍为 `blocked`，终端阻塞样本 `DYQ-25` 仍为 `blocked`；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-01 23:34 +0800|


## 5.10 2026-06-01 十次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-234146-agent07191-heartbeat10/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-234146-agent07191-real10/smoke_run.log`、`artifacts/dyq3-smoke/20260601-234146-agent07191-real10/run_console.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260601-234146-agent07191-heartbeat10/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；`DYQ-9` 已 done 但仍存在历史 blocker 关系；当前实质强阻塞仍是 `DYQ-10`，其上游 `DYQ-25` 仍为 blocked；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue relation 快照：2026-06-01 23:41 +0800|

## 5.11 2026-06-01 十一次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260601-234827-agent07191-heartbeat11/summary.md`、`artifacts/dyq3-smoke/20260601-234827-agent07191-heartbeat11-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260601-234846-agent07191-real11/smoke_run.log`、`artifacts/dyq3-smoke/20260601-234846-agent07191-real11-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260601-234827-agent07191-heartbeat11/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；自身子任务 `DYQ-9` 已 done；直接强阻塞仍是 `DYQ-10`，其上游 `DYQ-25` 仍为 blocked 且当前只显示被已 done 的 `DYQ-139` 历史恢复任务阻塞，需后端/运维负责人释放后继续真实后端联调；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-01 23:48 +0800|


## 5.12 2026-06-02 十二次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-001132-agent07191-heartbeat12/summary.md`、`artifacts/dyq3-smoke/20260602-001132-agent07191-heartbeat12-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-001132-agent07191-real12/smoke_run.log`、`artifacts/dyq3-smoke/20260602-001132-agent07191-real12-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-001132-agent07191-heartbeat12/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；`DYQ-9` 已 done 但历史 blocker 关系仍在；当前强阻塞仍是 `DYQ-10`，其上游 `DYQ-25` 为 todo，需先完成 dev 白名单/鉴权链路后才能真实后端联调；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue relation 快照：2026-06-02 00:11 +0800|

## 5.13 2026-06-02 十三次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-002457-agent07191-heartbeat13/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-002457-agent07191-real13/smoke_run.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-002457-agent07191-heartbeat13/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；`DYQ-9` 已 done 但历史 blocker 关系仍在；当前强阻塞仍是 `DYQ-10`，其终端阻塞为 `DYQ-25`；`DYQ-25` 当前为 todo，需先完成 dev 白名单/鉴权链路后才能真实后端联调；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-02 00:24 +0800|

## 5.14 2026-06-02 十四次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-003113-agent07191-heartbeat14/summary.md`、`artifacts/dyq3-smoke/20260602-003113-agent07191-heartbeat14-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-003113-agent07191-real14/smoke_run.log`、`artifacts/dyq3-smoke/20260602-003113-agent07191-real14-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-003113-agent07191-heartbeat14/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`，其上游 `DYQ-25` 当前为 `todo`；`DYQ-139` 与 `DYQ-9` 已 done 但历史 blocker 关系仍在；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue/relation 快照：2026-06-02 00:31 +0800|

## 5.15 2026-06-02 十五次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-003913-agent07191-heartbeat15/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-003924-agent07191-real15/smoke_run.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-003913-agent07191-heartbeat15/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`，其终端阻塞 `DYQ-25` 当前为 `todo`；`DYQ-9` 已 done 但历史 blocker 关系仍在；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue/relation 快照：2026-06-02 00:39 +0800|

## 5.16 2026-06-02 十六次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-004641-agent07191-heartbeat16/summary.md`、`artifacts/dyq3-smoke/20260602-004641-agent07191-heartbeat16-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-004654-agent07191-real16/smoke_run.log`、`artifacts/dyq3-smoke/20260602-004654-agent07191-real16-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-004641-agent07191-heartbeat16/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；上游 `DYQ-25` 当前为 `todo`；`DYQ-9` 与 `DYQ-139` 已 done 但历史 blocker 关系仍在；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue/relation 快照：2026-06-02 00:46 +0800|

## 5.17 2026-06-02 十七次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-005519-agent07191-heartbeat17/summary.md`、`artifacts/dyq3-smoke/20260602-005519-agent07191-heartbeat17-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-005543-agent07191-real17/smoke_run.log`、`artifacts/dyq3-smoke/20260602-005543-agent07191-real17-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-005519-agent07191-heartbeat17/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞仍是 `DYQ-10`；`DYQ-10` 的终端阻塞样本为 `DYQ-25`，当前 `in_progress`；自身可做的本地 Mock 与 ADB 环境复核已完成，最终真实后端闭环等待后端/运维白名单部署解锁|Paperclip issue 快照：2026-06-02 00:55 +0800|

## 5.18 2026-06-02 十八次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-010227-agent07191-heartbeat18/summary.md`、`artifacts/dyq3-smoke/20260602-010227-agent07191-heartbeat18-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-010227-agent07191-real18/smoke_run.log`、`artifacts/dyq3-smoke/20260602-010227-agent07191-real18-terminal.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-010227-agent07191-heartbeat18/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；上游 `DYQ-25` 仍为 `blocked`，但其 blocker 样本 `DYQ-145` 已 `done`，需后端/运维负责人释放阻塞关系或继续白名单部署；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-02 01:02 +0800|

## 5.19 2026-06-02 十九次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-010831-agent07191-heartbeat19/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-010831-agent07191-real19/smoke_run.log`、`artifacts/dyq3-smoke/20260602-010831-agent07191-real19/real_exit.txt`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-010831-agent07191-heartbeat19/adb_devices.log`、`artifacts/dyq3-smoke/20260602-010831-agent07191-heartbeat19/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；上游 `DYQ-25` 当前为 `todo`，但最新评论显示白名单代码已进运行包、48081 未监听且 Flowable 元数据外键重复仍阻塞启动；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-02 01:08 +0800|

## 5.20 2026-06-02 二十次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-011531-agent07191-heartbeat20/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-011531-agent07191-real20/smoke_run.log`、`artifacts/dyq3-smoke/20260602-011531-agent07191-real20/real_exit.txt`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-011531-agent07191-heartbeat20/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；终端阻塞 `DYQ-25` 当前为 `todo`，最新证据仍指向 48081 未监听与 Flowable 元数据外键重复阻塞启动；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-02 01:15 +0800|

## 5.21 2026-06-02 二十一次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-012255-agent07191-heartbeat21/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-012303-agent07191-real21/smoke_run.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-012255-agent07191-heartbeat21/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；终端阻塞 `DYQ-25` 当前为 `todo`，最新证据仍指向 48081 未监听与 Flowable 元数据外键重复阻塞启动；`DYQ-5` 仍被 `DYQ-3` 阻塞|Paperclip issue 快照：2026-06-02 01:23 +0800|

## 5.26 2026-06-02 二十六次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-015820-agent07191-heartbeat26/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health` 健康检查连接失败，直连注册接口同样 curl exit=7，未进入真实注册链路|`artifacts/dyq3-smoke/20260602-015831-agent07191-real26/smoke_run.log`、`artifacts/dyq3-smoke/20260602-015831-agent07191-real26/real_exit.txt`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-015820-agent07191-heartbeat26/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；上游 `DYQ-25` 当前为 `todo`；真实端云闭环需等待 dev 白名单/鉴权部署恢复后复跑|Paperclip issue 快照：2026-06-02 01:59 +0800|

## 5.28 2026-06-02 二十八次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-021356-agent07191-heartbeat28/summary.md`、`artifacts/dyq3-smoke/20260602-021356-agent07191-heartbeat28-terminal.log`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health`、`http://192.168.250.3:8080/actuator/health`、`http://127.0.0.1:48081/actuator/health`、`http://127.0.0.1:8080/actuator/health` 均连接失败；真实冒烟健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-021408-agent07191-real28/smoke_run.log`、`artifacts/dyq3-smoke/20260602-021408-agent07191-real28/real_exit.txt`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-021356-agent07191-heartbeat28/adb_minimal.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；上游 `DYQ-25` 当前为 `todo`；真实端云闭环需等待 dev 白名单/鉴权部署恢复后复跑|Paperclip issue 快照：2026-06-02 02:14 +0800|

## 5.29 2026-06-02 二十九次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-022243-agent07191-heartbeat29/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health`、`http://192.168.250.3:8080/actuator/health`、`http://127.0.0.1:48081/actuator/health`、`http://127.0.0.1:8080/actuator/health` 均连接失败；真实冒烟健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-022243-agent07191-real29/smoke_run.log`、`artifacts/dyq3-smoke/20260602-022243-agent07191-real29/real_exit.txt`、`artifacts/dyq3-smoke/20260602-022243-agent07191-probe29/probe.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-022243-agent07191-heartbeat29/adb_minimal.log`、`artifacts/dyq3-smoke/20260602-022243-agent07191-probe29/probe.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；上游 `DYQ-25` 当前为 `in_progress`；`DYQ-5` 仍被 `DYQ-3` 阻塞；真实端云闭环需等待 dev 白名单/鉴权部署恢复后复跑|Paperclip issue 快照：2026-06-02 02:23 +0800|

## 5.30 2026-06-02 三十次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-022753-agent07191-heartbeat30/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health`、`http://192.168.250.3:8080/actuator/health`、`http://127.0.0.1:48081/actuator/health`、`http://127.0.0.1:8080/actuator/health` 均连接失败；真实冒烟健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-022808-agent07191-real30/smoke_run.log`、`artifacts/dyq3-smoke/20260602-022808-agent07191-probe30/probe.log`|
|ADB 最小记录|阻塞：无在线设备，`adb shell getprop ro.product.model` 返回 `no devices/emulators found`|`artifacts/dyq3-smoke/20260602-022753-agent07191-heartbeat30/adb_minimal.log`、`artifacts/dyq3-smoke/20260602-022808-agent07191-probe30/probe.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；上游 `DYQ-25` 当前为 `in_progress`；`DYQ-5` 仍被 `DYQ-3` 阻塞；真实端云闭环需等待 dev 白名单/鉴权部署恢复后复跑|Paperclip issue 快照：2026-06-02 02:28 +0800|

## 5.31 2026-06-02 三十一次心跳复核证据
|项|结果|证据|
|---|---|---|
|本地 Mock 端侧闭环|通过：注册、心跳、待处理任务拉取、任务结果回传均 HTTP 200；无令牌/坏令牌返回业务码 401；断网场景 curl exit=7|`artifacts/dyq3-smoke/20260602-023309-agent07191-heartbeat31/summary.md`|
|真实 dev 后端|阻塞：`http://192.168.250.3:48081/actuator/health`、`http://192.168.250.3:8080/actuator/health`、`http://127.0.0.1:48081/actuator/health`、`http://127.0.0.1:8080/actuator/health` 均连接失败；真实冒烟健康检查失败，未进入注册链路|`artifacts/dyq3-smoke/20260602-023318-agent07191-real31/smoke_run.log`、`artifacts/dyq3-smoke/20260602-023318-agent07191-probe31/probe.log`|
|ADB 最小记录|阻塞：无在线设备，`adb devices -l` 空|`artifacts/dyq3-smoke/20260602-023309-agent07191-heartbeat31/adb_minimal.log`、`artifacts/dyq3-smoke/20260602-023318-agent07191-probe31/probe.log`|
|依赖链复核|`DYQ-3` 仍为 `blocked`；直接强阻塞 `DYQ-10` 仍为 `blocked`；终端阻塞 `DYQ-25` 当前为 `in_progress`；`DYQ-5` 仍被 `DYQ-3` 阻塞；真实端云闭环需等待 dev 白名单/鉴权部署恢复后复跑|Paperclip issue 快照：2026-06-02 02:33 +0800|

## 6. 审计日志
- 2026-06-02 02:33:09 +0800：三十一次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-023309-agent07191-heartbeat31/`。
- 2026-06-02 02:33:18 +0800：三十一次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查连接失败，证据目录 `artifacts/dyq3-smoke/20260602-023318-agent07191-real31/`；补充四地址健康探测与 ADB 环境探测，证据目录 `artifacts/dyq3-smoke/20260602-023318-agent07191-probe31/`。
- 2026-06-02 02:27:53 +0800：三十次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-022753-agent07191-heartbeat30/`。
- 2026-06-02 02:28:08 +0800：三十次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查连接失败，证据目录 `artifacts/dyq3-smoke/20260602-022808-agent07191-real30/`；补充四地址健康探测与 ADB 环境探测，证据目录 `artifacts/dyq3-smoke/20260602-022808-agent07191-probe30/`。
- 2026-06-02 02:22:43 +0800：二十九次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-022243-agent07191-heartbeat29/`。
- 2026-06-02 02:22:43 +0800：二十九次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查连接失败，证据目录 `artifacts/dyq3-smoke/20260602-022243-agent07191-real29/`；补充四地址健康探测与 ADB 环境探测，证据目录 `artifacts/dyq3-smoke/20260602-022243-agent07191-probe29/`。
- 2026-06-02 02:13:56 +0800：二十八次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-021356-agent07191-heartbeat28/`。
- 2026-06-02 02:14:08 +0800：二十八次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查连接失败，证据目录 `artifacts/dyq3-smoke/20260602-021408-agent07191-real28/`。
- 2026-06-02 01:58:20 +0800：二十六次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-015820-agent07191-heartbeat26/`。
- 2026-06-02 01:58:31 +0800：二十六次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查连接失败，证据目录 `artifacts/dyq3-smoke/20260602-015831-agent07191-real26/`。
- 2026-06-02 01:22:55 +0800：二十一次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-012255-agent07191-heartbeat21/`。
- 2026-06-02 01:23:03 +0800：二十一次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-012303-agent07191-real21/`。
- 2026-06-02 01:15:31 +0800：二十次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-011531-agent07191-heartbeat20/`。
- 2026-06-02 01:15:31 +0800：二十次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-011531-agent07191-real20/`。
- 2026-06-02 01:08:31 +0800：十九次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-010831-agent07191-heartbeat19/`。
- 2026-06-02 01:08:31 +0800：十九次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-010831-agent07191-real19/`。
- 2026-06-02 01:02:27 +0800：十八次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-010227-agent07191-heartbeat18/`。
- 2026-06-02 01:02:27 +0800：十八次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-010227-agent07191-real18/`。
- 2026-06-02 00:55:19 +0800：十七次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-005519-agent07191-heartbeat17/`。
- 2026-06-02 00:55:43 +0800：十七次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-005543-agent07191-real17/`。
- 2026-06-02 00:46:41 +0800：十六次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-004641-agent07191-heartbeat16/`。
- 2026-06-02 00:46:54 +0800：十六次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-004654-agent07191-real16/`。
- 2026-06-02 00:39:13 +0800：十五次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-003913-agent07191-heartbeat15/`。
- 2026-06-02 00:39:24 +0800：十五次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-003924-agent07191-real15/`。
- 2026-06-02 00:31:13 +0800：十四次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-003113-agent07191-heartbeat14/`。
- 2026-06-02 00:31:13 +0800：十四次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-003113-agent07191-real14/`。
- 2026-06-02 00:24:57 +0800：十三次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-002457-agent07191-heartbeat13/`。
- 2026-06-02 00:24:57 +0800：十三次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-002457-agent07191-real13/`。
- 2026-06-02 00:11:32 +0800：十二次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260602-001132-agent07191-heartbeat12/`。
- 2026-06-02 00:11:32 +0800：十二次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260602-001132-agent07191-real12/`。
- 2026-06-01 23:48:27 +0800：十一次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-234827-agent07191-heartbeat11/`。
- 2026-06-01 23:48:46 +0800：十一次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-234846-agent07191-real11/`。
- 2026-06-01 23:41:46 +0800：十次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-234146-agent07191-heartbeat10/`。
- 2026-06-01 23:41:46 +0800：十次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-234146-agent07191-real10/`。
- 2026-06-01 23:34:39 +0800：九次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-233439-agent07191-heartbeat9/`。
- 2026-06-01 23:34:39 +0800：九次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-233439-agent07191-real9/`。
- 2026-06-01 23:27:00 +0800：八次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-232700-agent07191-heartbeat8/`。
- 2026-06-01 23:27:00 +0800：八次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-232700-agent07191-real8/`。
- 2026-06-01 23:20:50 +0800：七次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-232050-agent07191-heartbeat7/`。
- 2026-06-01 23:20:50 +0800：七次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-232050-agent07191-real7/`。
- 2026-06-01 23:02:20 +0800：六次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-230220-agent07191-heartbeat6/`。
- 2026-06-01 23:02:20 +0800：六次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-230220-agent07191-real6/`。
- 2026-06-01 22:54:19 +0800：五次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-225419-agent07191-heartbeat5/`。
- 2026-06-01 22:54:31 +0800：五次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-225431-agent07191-real5/`。
- 2026-06-01 22:47:58 +0800：四次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-224758-agent07191-heartbeat4/`。
- 2026-06-01 22:47:58 +0800：四次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-224758-agent07191-real4/`。
- 2026-05-21 22:53:54 +0800：执行 `scripts/dyq3-endcloud-smoke.sh`。
- 2026-05-21 22:53:59 +0800：生成证据目录 `artifacts/dyq3-smoke/20260521-225354/`。
- 2026-05-21 22:55:24 +0800：整理验收文档并归档。
- 2026-05-21 22:57:27 +0800：执行第二轮冒烟，新增弱网/断网错误采集。
- 2026-05-21 22:57:29 +0800：生成证据目录 `artifacts/dyq3-smoke/20260521-225727/`。
- 2026-05-21 22:58:16 +0800：更新本文档并补充外部后端复跑指引。
- 2026-05-21 23:22:03 +0800：按风险修正评论补充 `DYQ-3` 阻塞态处置和解锁后闭环清单。
- 2026-06-01 21:34:30 +0800：复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-213428-agent07191-heartbeat/`。
- 2026-06-01 21:34:31 +0800：复跑真实 dev 后端 `http://192.168.250.3:48081`，注册仍返回业务码 401，证据目录 `artifacts/dyq3-smoke/20260601-213428-agent07191-real/`。
- 2026-06-01 22:34:03 +0800：二次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-223403-agent07191-heartbeat2/`。
- 2026-06-01 22:34:15 +0800：二次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查连接失败，证据目录 `artifacts/dyq3-smoke/20260601-223415-agent07191-real2/`。
- 2026-06-01 22:41:08 +0800：三次复跑本地 Mock 端侧闭环，证据目录 `artifacts/dyq3-smoke/20260601-224108-agent07191-heartbeat3/`。
- 2026-06-01 22:41:22 +0800：三次复跑真实 dev 后端 `http://192.168.250.3:48081`，健康检查失败，证据目录 `artifacts/dyq3-smoke/20260601-224122-agent07191-real3/`。

## 7. 阻塞态处置（2026-05-21）
- 最新指令（2026-05-21）：`DYQ-3` 保持 `blocked`，等待依赖子任务完成后解锁。
- 阻塞依赖与责任人：
  - `DYQ-9`（真机ADB证据补齐）：负责人阿甲（端侧）。
  - `DYQ-10`（真实后端联通与鉴权差异）：负责人小龙（后端）。
- 当前执行约束：`DYQ-3` 不将依赖项视为已解锁，不推进依赖外的最终交付动作。

## 8. 解锁后一次性闭环清单（用于申请 in_review）
- 合并 `DYQ-9` 真机证据：注册、30秒心跳、任务回传、异常可见报错（含 ADB/logcat 原始片段）。
- 合并 `DYQ-10` 真实后端证据：联通性、鉴权差异、字段差异与修正结论。
- 在 `DYQ-3` 输出最终闭环汇总：
  - 改动文件清单
  - 验证命令清单
  - 结果与风险
  - 待验证清单收敛状态
  - 转 `in_review` 申请说明
