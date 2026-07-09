# DYQ-5 三端最小闭环联调验收包 — 心跳复核 19:45 CST

更新时间：2026-06-04 19:45 CST
关联任务：DYQ-5、DYQ-2、DYQ-3、DYQ-4、DYQ-1
执行人：测试员小蓝

## 1. 结论

**DYQ-5 保持阻塞，不可关闭。**

分端mock/本地验证全部通过，但真实三端贯通仍不可行：
- 云端后端实例不可达（48080/48081均连接拒绝）
- 无ADB在线设备，真机证据缺失
- 三端无法用统一requestId/taskUuid/eventId串联现场演示

## 2. 本轮实际验证结果

### 2.1 云端中枢（DYQ-2）— ❌ 不可达

```
验证时间: 2026-06-04 19:45:15 CST
http://127.0.0.1:48080/actuator/health → HTTP 000 (连接拒绝)
http://192.168.250.3:48080/actuator/health → HTTP 000 (连接拒绝)
http://192.168.250.3:48081/actuator/health → HTTP 000 (连接拒绝)
```

历史证据（阿盾 11:50 CST 验证，后端当时在线）：
- 成功：/admin-api/claw/ops/dashboard → 200, /admin-api/claw/device/list → 200, /admin-api/sandbox/host/page → 200
- 失败：/api/claw-device/register → 500, /api/claw-device/heartbeat → 401, 5个Claw Controller未注册
- 审计报告：/mnt/e/code/dyq/.planning/audit/runs/ops-adun-dyq2-20260604/audit-report.md

### 2.2 PokeClaw 端侧（DYQ-3）— ✅ Mock全通过

```
验证时间: 2026-06-04 19:45 CST
RUN_TAG=20260604-heartbeat-1945 scripts/dyq3-endcloud-smoke.sh
```

| 验证项 | 结果 | 详情 |
|:-------|:-----|:-----|
| register | ✅ PASS | HTTP 200, code=200, 返回deviceToken |
| heartbeat | ✅ PASS | HTTP 200, code=200 |
| pendingTask | ✅ PASS | HTTP 200, pendingTaskCount=1, taskUuid=2510a993-82c5-47ec-bc05-c7b589bac56d |
| result | ✅ PASS | HTTP 200, 结果回传成功 |
| 无token | ✅ PASS | code=401, msg=缺少有效令牌 |
| 坏token | ✅ PASS | code=401, msg=令牌无效 |
| 断网 | ✅ PASS | curl exit=7 |

证据目录：/mnt/e/code/PokeClaw/artifacts/dyq3-smoke/20260604-194522/

### 2.3 WeFlow 微信控制底座（DYQ-4）— ✅ 本地验证通过

```
验证时间: 2026-06-04 19:45 CST
cd /mnt/d/work/code/WeFlow && npm run wechat:control:verify
```

| 验证项 | 结果 |
|:-------|:-----|
| wechatControlService validation | ✅ passed |
| wechatReplyService verification | ✅ passed |
| httpService /api/v1/wechat route contract | ✅ passed |
| wechat control verification | ✅ passed |

历史失败证据：selector-failure-test → WECHAT_WINDOW_NOT_FOUND, manual_takeover_required=true

### 2.4 ADB设备 — ❌ 无在线设备

```
adb devices -l → List of devices attached (空)
```

## 3. 统一风险清单

### P0（阻塞商业化交付）
| # | 风险 | 影响 | 负责人 |
|:--|:-----|:-----|:-------|
| 1 | 云端后端实例不可达 | 无法执行任何真实三端贯通测试 | @阿盾/@老周 |
| 2 | 设备端API 500/401（register/heartbeat） | PokeClaw真实注册/心跳不可用 | @小龙 |
| 3 | 5个Claw Controller未注册 | experience/training/statistics/lobster-get接口全部500 | @小龙 |
| 4 | 无ADB在线设备 | PokeClaw缺真机执行证据 | @阿甲 |
| 5 | 三端缺统一串联记录 | 无法证明端到端数据贯通 | @小蓝 |

### P1（影响演示质量）
| # | 风险 | 影响 |
|:--|:-----|:-----|
| 1 | WeFlow仅本地验证，未真实微信环境现场复验 | 无法确认真实微信控制稳定 |
| 2 | OpenAPI契约路径缺少admin-api前缀 | 接口文档与实际不一致 |
| 3 | proxy_session_log表缺失 | 审计日志不完整 |
| 4 | 对外演示缺截图/录像证据 | 商业化展示不直观 |

## 4. 统一演示脚本 v1

**前置条件**：云端实例恢复 + ADB设备在线 + Windows微信登录态

```bash
# Step 1: 云端启动验证
curl -sS http://192.168.250.3:48080/actuator/health | jq .

# Step 2: PokeClaw 真机注册
adb shell am start -n io.agents.pokeclaw/.ui.main.MainActivity
# 等待注册完成，logcat抓取deviceId和deviceToken

# Step 3: 云端下发任务
curl -sS -X POST http://192.168.250.3:48080/api/claw-device/devices/{deviceId}/pending-tasks

# Step 4: PokeClaw执行并回传
# logcat追踪taskUuid和result回传

# Step 5: WeFlow触发微信控制
curl -sS -X POST http://localhost:3000/api/v1/wechat/send -d '{"target":"测试群","message":"DYQ-5联调验证消息"}'

# Step 6: 串联证据
# 云端: requestId/audit_log → PokeClaw: taskUuid/deviceId → WeFlow: eventId/send_attempt_id
```

## 5. 下一步最小可行解锁路径

1. **@阿盾**：恢复48080或48081实例可达
2. **@小龙**：修复claw-device注册/心跳500/401 + 注册5个缺失Controller
3. **@阿甲**：接入一台Android真机
4. **@阿桥**：Windows微信真实环境补一条WeFlow现场证据
5. 四项具备后，小蓝执行统一三端现场演示并更新本报告决定是否关闭DYQ-5
