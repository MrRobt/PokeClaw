# DYQ-5 三端最小闭环联调验收包

## 0. 当前结论
- 已完成三端最小闭环验收包初版，包含云端中枢、PokeClaw、WeFlow 各 1 条成功证据与 1 条失败/异常证据。
- 我在 2026-06-04 17:38:19 至 17:42:22 CST 重新执行了当前可复现的最小验证：PokeClaw Mock 闭环通过、真实 48080 后端失败、WeFlow 控制验证通过、WeFlow GUI 失败注入通过。
- DYQ-5 当前不能关闭，真实三端统一演示仍被 DYQ-3 阻塞：本机 WSL 下 `127.0.0.1:48080` 连接拒绝，ADB 也无在线设备。
- 建议状态继续保持阻塞，等待云端实例与真机恢复后再补最终统一演示录像/截图/日志。

## 1. 本轮执行时间线
| 时间 | 动作 | 结果 |
|---|---|---|
| 2026-06-04 17:38:19 CST | `bash -n scripts/dyq3-endcloud-smoke.sh` | 通过 |
| 2026-06-04 17:38:19 CST | `MOCK_PORT=18141 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-qa-heartbeat-mock` | 7/7 通过 |
| 2026-06-04 17:38:19 CST | `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-qa-heartbeat-real` | 健康检查失败，连接拒绝 |
| 2026-06-04 17:39:53 CST | `cd /mnt/d/work/code/WeFlow && npm run wechat:control:verify` | 通过 |
| 2026-06-04 17:39:53 CST | `cd /mnt/d/work/code/WeFlow && npm run typecheck` | 通过 |
| 2026-06-04 17:42:20 CST | `python3 /mnt/d/work/code/WeFlow/wechat-controller/scripts/weflow_gui_send_dry_run.py --mode mock-selector-failure --request-id dyq5-selector-fail-$(date +%s)` | 失败注入命中，安全阻断生效 |

## 2. 三端证据总表
| 端 | 成功证据 | 失败/异常证据 | 当前判定 |
|---|---|---|---|
| 云端中枢 DYQ-2 | `/mnt/e/code/dyq/.planning/audit/runs/ops-adun-dyq2-20260604/audit-report.md`：`/admin-api/claw/ops/dashboard`、`/admin-api/claw/device/list`、`/admin-api/sandbox/host/page` 返回业务码 0 | 同报告显示 `/api/claw-device/register` 500、`/api/claw-device/heartbeat` 401、多个 `/admin-api/claw/*` 资源缺失；本轮 WSL 下 `curl http://127.0.0.1:48080/actuator/health` 连接拒绝 | 部分通过，仍有 P0 阻塞 |
| PokeClaw DYQ-3 | `artifacts/dyq3-smoke/20260604-qa-heartbeat-mock/`：注册、心跳、拉任务、回传、无令牌、坏令牌、断网共 7/7 通过 | `artifacts/dyq3-smoke/20260604-qa-heartbeat-real/`：`/actuator/health` 连接拒绝；`adb devices -l` 空列表 | Mock 通过，真实链路阻塞 |
| WeFlow DYQ-4 | `cd /mnt/d/work/code/WeFlow && npm run wechat:control:verify`、`npm run typecheck` 本轮均通过；产品验收记录 `/mnt/e/code/dyq/.planning/audit/runs/20260602-003333-dyq-4-product-acceptance.md` 已放行产品侧 | `.pytest-audit/gui-send-evidence/dyq5-selector-fail-1780566138/20260604-174220-794729/failure-evidence.json`：`WECHAT_WINDOW_NOT_FOUND`，状态 `blocked`，要求人工接管 | 本地控制链路可演示，真实 Windows 微信环境待现场复验 |

## 3. 逐端明细

### 3.1 云端中枢 DYQ-2
来源：`/mnt/e/code/dyq/.planning/audit/runs/ops-adun-dyq2-20260604/audit-report.md`

成功证据：
1. `GET /admin-api/claw/ops/dashboard` → HTTP 200，业务码 0。
2. `GET /admin-api/claw/device/list` → HTTP 200，业务码 0，返回 2 台测试设备。
3. `GET /admin-api/sandbox/host/page`、`/admin-api/sandbox/instance/page` → HTTP 200，业务码 0。

失败/异常证据：
1. `POST /api/claw-device/register` → 500，系统异常。
2. `POST /api/claw-device/heartbeat` → 401，账号未登录。
3. `GET /admin-api/claw/experience/page`、`training/page`、`statistics/summary` → 业务码 500，`NoResourceFoundException`。
4. 本轮现场补探：`curl -sS -m 8 http://127.0.0.1:48080/actuator/health` 与 `/health` 在 WSL 下均连接拒绝，说明当前我所在环境无法直接完成真实统一演示。

最小复现命令：
```bash
# 历史审计报告（已留档）
cat /mnt/e/code/dyq/.planning/audit/runs/ops-adun-dyq2-20260604/audit-report.md

# 当前现场补探
curl -sS -m 8 http://127.0.0.1:48080/actuator/health
curl -sS -m 8 http://127.0.0.1:48080/health
```

### 3.2 PokeClaw DYQ-3
来源：
- 本轮新证据：`artifacts/dyq3-smoke/20260604-qa-heartbeat-mock/`
- 本轮新证据：`artifacts/dyq3-smoke/20260604-qa-heartbeat-real/`
- 既有说明：`docs/product/DYQ-3-final-closure-evidence-20260604.md`

成功证据：
1. Mock 环境注册成功，返回 `deviceToken`。
2. Mock 环境心跳成功，`pendingTaskCount=1`。
3. Mock 环境拉取任务成功，`taskUuid=be1e07f9-c5a7-485b-a632-c7f841bfb337`。
4. Mock 环境结果回传成功。
5. 无令牌、坏令牌、断网异常均能给出可见错误。

失败/异常证据：
1. 真实后端 48080 健康检查失败，HTTP=000，连接拒绝。
2. `adb devices -l` 无在线设备，真机链路未打通。

最小复现命令：
```bash
cd /mnt/e/code/PokeClaw
bash -n scripts/dyq3-endcloud-smoke.sh
MOCK_PORT=18141 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-qa-heartbeat-mock
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-qa-heartbeat-real
adb devices -l
```

### 3.3 WeFlow DYQ-4
来源：
- 本轮新验证：`/mnt/d/work/code/WeFlow`
- 产品验收：`/mnt/e/code/dyq/.planning/audit/runs/20260602-003333-dyq-4-product-acceptance.md`
- Dry-run 清单：`/mnt/d/work/code/WeFlow/wechat-controller/docs/qa/dyq-4-gui-safe-reply-dry-run-checklist-20260523.md`

成功证据：
1. `npm run wechat:control:verify` 输出四项通过：
   - `wechatControlService validation passed`
   - `wechatReplyService verification passed`
   - `httpService /api/v1/wechat route contract passed`
   - `wechat control verification passed`
2. `npm run typecheck` 本轮通过。
3. DYQ-4 产品验收已明确：可按“本地控制链路 + 云端消费归档证据”完成产品侧验收。

失败/异常证据：
1. `mock-selector-failure` 失败注入返回：
   - `status=blocked`
   - `error_code=WECHAT_WINDOW_NOT_FOUND`
   - `manual_takeover_required=true`
   - `send_action_executed=false`
2. 失败证据文件：`.pytest-audit/gui-send-evidence/dyq5-selector-fail-1780566138/20260604-174220-794729/failure-evidence.json`
3. 该失败证明 GUI 守卫生效：检测不到微信窗口时会阻断而不是误发。

最小复现命令：
```bash
cd /mnt/d/work/code/WeFlow
npm run wechat:control:verify
npm run typecheck

cd /mnt/d/work/code/WeFlow/wechat-controller
python3 scripts/weflow_gui_send_dry_run.py --mode mock-selector-failure --request-id dyq5-selector-fail-$(date +%s)
```

## 4. 统一演示脚本 v1
目标：最小成本向主人演示“三端都有产出，但真实统一闭环仍受阻塞”的当前事实。

### 步骤 1：云端现状确认
输入：
```bash
curl -sS -m 8 http://127.0.0.1:48080/actuator/health
```
预期：
- 若返回业务成功，再继续真实三端联调。
- 当前实际：连接拒绝，记录为阻塞证据。
失败注入点：
- 改探测 `/health`，若仍拒绝，则确认不是单一路径问题。

### 步骤 2：PokeClaw 本地闭环确认
输入：
```bash
cd /mnt/e/code/PokeClaw
MOCK_PORT=18141 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-qa-heartbeat-mock
```
预期：
- 7/7 全通过。
失败注入点：
- 断网检查自动触发 `curl exit=7`。
- 无 token / 坏 token 返回 401 业务错误。

### 步骤 3：PokeClaw 真实后端探测
输入：
```bash
cd /mnt/e/code/PokeClaw
USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-qa-heartbeat-real
```
预期：
- 若云端恢复，应进入真实注册/心跳。
- 当前实际：健康检查失败，链路中断。
失败注入点：
- 当前环境已经自然命中连接拒绝。

### 步骤 4：WeFlow 本地控制链路确认
输入：
```bash
cd /mnt/d/work/code/WeFlow
npm run wechat:control:verify
```
预期：
- 四项校验全部通过。
失败注入点：
- 进入下一步 dry-run 失败注入，确认守卫生效。

### 步骤 5：WeFlow GUI 失败注入
输入：
```bash
cd /mnt/d/work/code/WeFlow/wechat-controller
python3 scripts/weflow_gui_send_dry_run.py --mode mock-selector-failure --request-id dyq5-selector-fail-$(date +%s)
```
预期：
- 返回 `WECHAT_WINDOW_NOT_FOUND`。
- `manual_takeover_required=true`。
- 不执行真实发送。
失败注入点：
- 当前命令本身就是失败注入。

## 5. P0 / P1 风险清单
### P0
1. 云端 48080 在我当前 WSL 环境下连接拒绝，真实统一演示无法贯通。
2. PokeClaw 无在线 ADB 设备，真实手机端证据缺失。
3. 云端设备端 API 历史审计仍显示 `register=500`、`heartbeat=401`。
4. 三端统一 requestId / taskUuid / eventId 真实串联证据仍缺一次现场实跑。

### P1
1. 云端 `experience/training/statistics` 等接口仍有资源缺失风险。
2. WeFlow 真实 Windows 微信登录态、窗口状态、外部执行端在线状态尚未在本轮现场复验。
3. 统一演示仍缺截图/录像级证据，只具备日志与文档证据。

## 6. 对 DYQ-1 的回填建议
1. 不要把 DYQ-5 改完成；应标记为“验收包已更新，但真实统一演示仍阻塞”。
2. 优先解除云端 48080 与设备注册问题，再补一次真实三端串联演示。
3. 真机接入后，按本文件第 4 节脚本补录：云端健康、PokeClaw 真机、WeFlow Windows 微信 GUI 三类现场证据。

## 7. 本轮产出文件
- `/mnt/e/code/PokeClaw/.planning/audit/runs/20260604-dyq5-heartbeat/commercial-e2e-evidence.md`
- `/mnt/e/code/PokeClaw/docs/Wiki/06-Reference/05-待验证问题清单.md`
