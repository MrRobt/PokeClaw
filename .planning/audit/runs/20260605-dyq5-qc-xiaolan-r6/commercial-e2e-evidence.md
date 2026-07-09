# DYQ-5 三端最小闭环联调验收包 — 第6轮心跳复核

生成时间: 2026-06-05T07:00+08:00
验证人: 测试员小蓝 (ec2afe67)
状态: **blocked — 4个P0阻塞项持续未解，云端仍完全不可用**

---

## 一、三端实时运行状态（2026-06-05 07:00）

| 端 | 服务 | 地址 | 状态 | 变化 |
|---|---|---|---|---|
| 云端 | dyq-server | :48080 | **不可用** | 无变化，进程仍不存在 |
| 云端 | Nacos | :8848 | **不可达** | 无变化 |
| 云端 | MySQL | 192.168.250.3:3306 | 可达 | 无变化 |
| 云端 | Redis | :6379 | 可达 | 无变化 |
| 云端 | Swagger | :48080 | **不可达** | 无变化（上次可用） |
| WeFlow | controller | :8000 | 运行中 | 无变化 |
| WeFlow | agent | :18700 | 运行中 | 无变化 |
| WeFlow | 微信进程 | Windows | 运行中(5个WeChatAppEx) | 无变化 |
| WeFlow | pyweixin | - | **未安装** | 无变化，failure_count从890→1313 |
| PokeClaw | ADB | - | **无设备** | 无变化 |

---

## 二、各端成功证据

### 2.1 云端中枢 — 无成功证据（连续2轮）

上次成功证据（2026-06-04 22:05）:
- Swagger HTTP 200, 38组API文档可用
- 52个Claw API已注册
- 登录拒绝(401)符合安全预期

**本次**: dyq-server进程不存在，48080/48081端口均不可达，Nacos不可达。云端完全不可用。

### 2.2 WeFlow微信 — 有成功证据（确认）

**验证时间**: 2026-06-05 07:00
**验证命令与结果**:

```bash
# 1. Controller健康检查
curl -sS 'http://127.0.0.1:8000/health'
# 结果: controller_ready=true, upstream_ready=false, pywechat_imported=false
# 成功点: Controller层正常响应

# 2. 设备节点注册协议
curl -sS 'http://127.0.0.1:8000/dyq/device-node/registration'
# 结果: nodeType=WEFLOW_WECHAT, nodeKey=weflow-local-winwechat-001
#        5个能力声明(health/message.receive/message.send_text/prepare_text/command.receipt)
#        安全策略: preferGuiSend=true, forbidPrivateProtocol=true, requireRequestId=true
# 成功点: 注册协议完整，安全策略完备

# 3. 心跳协议
curl -sS 'http://127.0.0.1:8000/dyq/device-node/heartbeat'
# 结果: status=online, capabilitiesVersion=2026-05-15
# 成功点: 心跳正常上报

# 4. Agent端OpenAPI
curl -sS 'http://127.0.0.1:18700/openapi.json'
# 结果: 8个API可用(health/send-text/prepare-text/confirm-send/listener-start/stop/events/status)
# 成功点: Agent接口文档完整

# 5. 微信进程
# Windows侧5个WeChatAppEx.exe进程在运行
# 成功点: 微信客户端运行中
```

### 2.3 PokeClaw端侧 — 无成功证据

ADB无设备，端侧执行无法实机验证。上次成功证据为Mock闭环。

---

## 三、各端失败/异常证据

### 3.1 云端中枢 — 完全不可用（P0，持续）

**验证命令**:
```bash
# 进程检查
ps aux | grep dyq-server.jar | grep -v grep
# 结果: 空 — 进程不存在

# Nacos可达性
curl -sS --connect-timeout 3 'http://127.0.0.1:8848/nacos/'
# 结果: Connection refused
curl -sS --connect-timeout 3 'http://192.168.250.3:8848/nacos/'
# 结果: Connection timed out

# 端口检查
curl -sS --connect-timeout 3 'http://127.0.0.1:48080/'
# 结果: Connection refused
```

**结论**: 云端中枢完全不可用。Nacos不可达导致dyq-server无法启动。

### 3.2 WeFlow微信 — pyweixin缺失（P0，恶化）

**验证命令**:
```bash
curl -sS 'http://127.0.0.1:8000/listener/status'
```

**结果**:
```json
{
  "running": true,
  "last_error": "BackendError: pyweixin: No module named 'pyweixin'",
  "failure_count": 1313,
  "last_success_at": null,
  "pending_event_count": 500,
  "session_whitelist": []
}
```

**结论**: pyweixin模块仍未安装，微信监听连续失败1313次（上次890次，增加了423次）。Controller层正常但桥接层不可用。微信消息收发链路断。

### 3.3 PokeClaw端侧 — ADB无设备（P0，持续）

```bash
adb devices
# List of devices attached
# (空列表)
```

**结论**: 仍无安卓设备连接，端侧执行无法实机验证。

---

## 四、P0阻塞项更新（4个，持续未解）

| 编号 | P0阻塞 | 影响 | 状态 | 变化 |
|------|--------|------|------|------|
| V01 | Nacos不可达 → dyq-server无法启动 | 云端中枢完全不可用 | 未解决 | 无变化 |
| V02 | 后端48081未监听 | PokeClaw真实注册链路断 | 未解决 | 无变化 |
| V03 | ADB无真机设备 | 端侧执行无法实机验证 | 未解决 | 无变化 |
| V04 | pyweixin模块缺失 | 微信GUI操控不可用 | 未解决 | failure_count 890→1313(恶化) |

---

## 五、可复现最小验证命令

### 云端（当前失败）
```bash
# Nacos不可达检测
python3 -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('127.0.0.1',8848))" 2>&1
# 预期: Connection refused

# dyq-server进程检查
ps aux | grep dyq-server.jar | grep -v grep
# 预期: 空（进程不存在）
```

### WeFlow（部分成功）
```bash
curl -sS 'http://127.0.0.1:8000/health'
# 预期: controller_ready=true, upstream_ready=false

curl -sS 'http://127.0.0.1:8000/dyq/device-node/registration'
# 预期: nodeType=WEFLOW_WECHAT, 5个capabilities

curl -sS 'http://127.0.0.1:8000/listener/status'
# 预期: running=true, failure_count>1000, pyweixin缺失
```

### PokeClaw
```bash
adb devices
# 预期: 空列表
```

---

## 六、风险清单

| 级别 | 风险 | 影响 | 端 | 建议解法 |
|------|------|------|----|----------|
| P0 | Nacos不可达 | 云端完全不可用 | DYQ-2 | 启动Nacos或改本地配置绕过 |
| P0 | 后端48081未监听 | PokeClaw真实注册断 | DYQ-2/3 | 启动实例或修复路由 |
| P0 | ADB无真机 | 端侧无法实机验证 | DYQ-3 | 接入安卓设备 |
| P0 | pyweixin缺失 | 微信操控不可用 | DYQ-4 | pip install pyweixin |
| P1 | dyq-server进程无自动恢复 | 服务不稳定 | DYQ-2 | 配置systemd/supervisor |
| P1 | pyweixin failure_count持续增长(890→1313) | 资源浪费+日志膨胀 | DYQ-4 | 安装模块或停止监听循环 |
| P1 | OpenAPI契约0命中(91/0) | 文档脱节 | DYQ-2 | 排查swagger扫描路径 |

---

## 七、验收结论

| 验收标准 | 达成情况 |
|----------|----------|
| 每端至少1条成功证据 | 部分达成 — 仅WeFlow有新证据，云端/PokeClaw依赖历史 |
| 每端至少1条失败/异常证据 | 达成 — 云端完全不可用，WeFlow桥接失败，PokeClaw无设备 |
| 可复现最小验证命令 | 达成 |
| 统一风险清单(P0/P1) | 达成 — 4个P0 + 3个P1 |

**整体判定**: DYQ-5 保持 **blocked**。4个P0阻塞项连续2轮未解，其中pyweixin failure_count从890增至1313（恶化趋势）。与r5相比无正面进展。

**建议解除顺序**:
1. **最优先**: 启动Nacos → 重启dyq-server（解开V01+V02，恢复云端中枢）
2. pip install pyweixin（解开V04，恢复微信桥接层）
3. 接入安卓设备（解开V03，恢复PokeClaw实机验证）

---

## 八、历史证据索引

- 上次验收(r5): `/mnt/e/code/PokeClaw/.planning/audit/runs/20260605-dyq5-qc-xiaolan-r5/commercial-e2e-evidence.md`
- 三端闭环验收: `/mnt/e/code/PokeClaw/.planning/audit/runs/DYQ-5-20260604-三端闭环联调验收/commercial-e2e-evidence.md`
- DYQ-2冒烟: `/mnt/e/code/dyq/.planning/audit/runs/DYQ-2-20260521-云端沙箱最小冒烟/SMOKE-EVIDENCE.md`
- DYQ-3冒烟: `/mnt/e/code/PokeClaw/docs/product/DYQ-3-pokeclaw-endcloud-smoke-evidence-20260521.md`
- DYQ-4冒烟: `/mnt/d/work/code/weflow-wechat-autopilot/docs/qa/dyq-4-weflow-min-smoke-20260521.md`
