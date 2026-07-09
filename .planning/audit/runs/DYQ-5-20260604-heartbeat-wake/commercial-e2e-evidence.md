# DYQ-5 三端最小闭环联调验收包（心跳唤醒复核）

更新时间：2026-06-04 20:09 CST
关联任务：DYQ-5、DYQ-2、DYQ-3、DYQ-4、DYQ-1
执行人：测试员小蓝

## 1. 结论
- 当前不能关闭 DYQ-5，状态应继续保持阻塞。
- 原因不是三端都无证据，而是“分端成功证据已具备、真实三端现场贯通证据仍缺”。
- 本轮已完成一次最新复核（20:09 CST）：
  1. PokeClaw mock 端到端链路再次通过（最新证据目录：`/mnt/e/code/PokeClaw/artifacts/dyq3-smoke/20260604-2009-dyq5-heartbeat-mock/`）；
  2. WeFlow 本地控制校验再次通过（4/4 passed）；
  3. 云端 48080 健康探针当前仍不可达（连接拒绝，HTTP 000）；
  4. ADB 仍无在线设备，真实手机链路证据仍缺。
  5. 历史默认 mock 端口冲突修复仍有效，本轮使用默认端口即可稳定跑通。

## 2. 本轮实际验证命令
```bash
# 云端健康探针
curl -sS -m 8 http://127.0.0.1:48080/actuator/health

# 端侧真机可用性
adb devices -l

# PokeClaw 最小 mock 闭环
bash -n scripts/dyq3-endcloud-smoke.sh
USE_MOCK_BACKEND=1 RUN_TAG=20260604-2009-dyq5-heartbeat scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-2009-dyq5-heartbeat-mock

# WeFlow 本地控制验证
cd /mnt/d/work/code/WeFlow && npm run wechat:control:verify
```

## 3. 三端成功 / 异常证据汇总

### 3.1 云端中枢（DYQ-2）
成功证据：
- 审计报告：`/mnt/e/code/dyq/.planning/audit/runs/ops-adun-dyq2-20260604/audit-report.md`
- 已验证成功项：
  - `/admin-api/claw/ops/dashboard` → HTTP 200, 业务码 0
  - `/admin-api/claw/device/list` → HTTP 200, 业务码 0
  - `/admin-api/sandbox/host/page` → HTTP 200, 业务码 0

异常证据：
- `curl -sS -m 8 http://127.0.0.1:48080/actuator/health` → 连接失败
- `curl -sS -m 8 http://192.168.250.3:48081/actuator/health` → 连接失败
- `/api/claw-device/register` → 500
- `/api/claw-device/heartbeat` → 401
- `/admin-api/claw/experience/page` 等控制面接口 → `NoResourceFoundException`

### 3.2 PokeClaw 端侧执行体（DYQ-3）
成功证据：
- 最新复跑（20:09 CST）：`/mnt/e/code/PokeClaw/artifacts/dyq3-smoke/20260604-2009-dyq5-heartbeat-mock/`
- 已验证成功项：
  - register → HTTP 200
  - heartbeat → HTTP 200
  - pending → HTTP 200
  - result → HTTP 200
  - taskUuid：`2fe2f000-0e93-4bfd-8ded-1f963cbb43fa`
- 兼容性与历史复核：`/mnt/e/code/PokeClaw/artifacts/dyq3-smoke/20260604-qa-port-fallback-explicit/`（显式 `MOCK_PORT=18190` 通过），`/mnt/e/code/PokeClaw/docs/product/DYQ-3-final-closure-evidence-20260604.md`（多轮真实后端阻塞记录）

异常证据：
- 无 token → `code=401, msg=缺少有效令牌`
- 坏 token → `code=401, msg=令牌无效`
- 断网注入 → `curl_exit=7`
- 历史残留 mock 占用 18080 时，旧脚本会误连旧实例导致 `pendingTaskCount=0`；本轮已修复为自动切换空闲端口
- `adb devices -l` 当前无在线设备，真实手机链路仍缺现场证据

### 3.3 WeFlow 微信控制底座（DYQ-4）
成功证据：
- 本轮命令：`cd /mnt/d/work/code/WeFlow && npm run wechat:control:verify`
- 本轮结果：
  - `wechatControlService validation passed`
  - `wechatReplyService verification passed`
  - `httpService /api/v1/wechat route contract passed`
  - `wechat control verification passed`

异常证据：
- 失败注入证据：`/mnt/d/work/code/WeFlow/.pytest-audit/gui-send-evidence/selector-failure-test/20260523-040255-427605/failure-evidence.json`
- 关键异常：
  - `error_code=WECHAT_WINDOW_NOT_FOUND`
  - `send_action_executed=false`
  - `manual_takeover_required=true`
  - 自动重试被禁止，要求人工接管

## 4. 统一演示脚本 v1
1. 云端侧先确认 48080 或 48081 任一真实实例可达，并准备 1 条可用设备端接口样例。
2. PokeClaw 接入真机，执行注册 → 心跳 → 拉任务 → 回传，记录 `deviceId`、`taskUuid`、logcat 时间戳。
3. WeFlow 在 Windows 微信真实登录态执行一次受控发送，记录触发输入、控制动作、结果回传、失败接管分支。
4. 用统一关联键串联现场证据：
   - 云端：requestId / audit log
   - PokeClaw：taskUuid / deviceId
   - WeFlow：eventId / send_attempt_id
5. 若任一端失败，必须同步记录：失败时间、错误码、用户可见提示、人工接管动作。

## 5. 当前阻塞项
### P0
1. 云端真实实例当前不可达，导致真实三端贯通无法现场执行。
2. 云端设备端 API 仍存在 500/401 历史阻塞，真实注册/心跳/回传不能判定为稳定可用。
3. PokeClaw 无在线 ADB 设备，缺真实手机执行证据。
4. 三端现场缺一次统一 requestId / taskUuid / eventId 串联记录。

### P1
1. WeFlow 本轮仍是本地控制验证，未在真实 Windows 微信登录态现场复验。
2. 云端部分控制面接口仍返回资源缺失异常。
3. 对外演示仍缺更直观的截图/录像证据。

## 6. 下一步最小可行解锁路径
1. 先由云端负责人恢复 48080 或 48081 健康探针可达。
2. 修复 `claw-device` 注册/心跳 500/401 后复跑真实后端链路。
3. 接入一台 Android 真机，补齐 ADB 与 logcat 证据。
4. 在 Windows 微信真实环境补一条 WeFlow 现场发送与回传证据。
5. 四项具备后，再执行一次统一三端现场演示并决定是否关闭 DYQ-5。
