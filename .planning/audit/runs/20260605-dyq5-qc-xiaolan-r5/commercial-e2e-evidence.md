# DYQ-5 三端最小闭环联调验收包 — 心跳复核

生成时间: 2026-06-05T06:30+08:00
验证人: 测试员小蓝 (ec2afe67)
状态: **blocked — 4个P0阻塞项，云端服务当前不可用**

---

## 一、三端实时运行状态（2026-06-05 06:25）

| 端 | 服务 | 地址 | 状态 | 变化 |
|---|---|---|---|---|
| 云端 | dyq-server | :48080 | **不可用** | 上次运行中，本次已停 |
| 云端 | Nacos | :8848 | **不可达** | 新发现 |
| 云端 | MySQL | 192.168.250.3:3306 | 可达 | 无变化 |
| 云端 | Redis | :6379 | 可达 | 无变化 |
| WeFlow | controller | :8000 | 运行中 | 无变化 |
| WeFlow | agent | :18700 | 运行中 | 无变化 |
| PokeClaw | ADB | - | **无设备** | 无变化 |

---

## 二、各端成功证据

### 2.1 云端中枢 — 无新成功证据

上次成功证据（2026-06-04 22:05）:
- Swagger HTTP 200, 38组API文档可用
- 52个Claw API已注册
- 登录拒绝(401)符合安全预期

**本次**: dyq-server进程已不存在（原PID 407624已停），尝试重启但因Nacos不可达而卡在Spring初始化阶段，无法完成启动。

### 2.2 WeFlow微信 — 有新成功证据

**验证时间**: 2026-06-05 06:25
**验证命令**:
```bash
curl -sS 'http://127.0.0.1:8000/health'
curl -sS 'http://127.0.0.1:8000/dyq/device-node/registration'
curl -sS 'http://127.0.0.1:8000/dyq/device-node/heartbeat'
curl -sS 'http://127.0.0.1:18700/openapi.json'
```

**结果**:
- Controller health: controller_ready=true, upstream_ready=false
- 设备节点注册协议: nodeType=WEFLOW_WECHAT, 5个能力声明
- 心跳协议: status=online, nodeKey=weflow-local-winwechat-001
- Agent端: 8个API可用(health/send-text/prepare-text/confirm-send/listener-start/stop/events/status)
- 自动回复: 可响应（20个系统事件被正确跳过，blocked=true，error_code=SYSTEM_EVENT_SKIPPED）

### 2.3 PokeClaw端侧 — 无新成功证据

Mock闭环仍为上次记录。ADB无设备，端云闭环断。

---

## 三、各端失败/异常证据

### 3.1 云端中枢 — 关键异常（新）

**验证命令**:
```bash
# dyq-server进程检查
ps aux | grep dyq-server.jar | grep -v grep
# 返回空 — 进程已不存在

# Nacos可达性
curl -sS 'http://127.0.0.1:8848/nacos/'
# 返回: Connection refused
curl -sS 'http://192.168.250.3:8848/nacos/'
# 返回: Connection timed out

# 启动尝试
cd /mnt/e/code/dyq && java -jar dyq-server/target/dyq-server.jar --spring.profiles.active=dev
# 卡在MiniDaoClassPathMapperScanner/Nacos注册阶段，无法完成启动
```

**结论**: dyq-server进程已崩溃/被杀，Nacos服务不可达导致无法重启。这是新增P0阻塞项。

### 3.2 WeFlow微信 — 持续异常

**验证命令**:
```bash
curl -sS 'http://127.0.0.1:8000/listener/status'
```

**结果**:
```json
{
  "running": true,
  "last_error": "BackendError: pyweixin: No module named 'pyweixin'",
  "failure_count": 890,
  "last_success_at": null
}
```

**结论**: pyweixin模块仍未安装，微信监听连续失败890次。Controller层正常但桥接层不可用。

### 3.3 PokeClaw端侧 — 持续异常

```bash
adb devices
# List of devices attached
# (空列表)
```

**结论**: 仍无安卓设备连接，端侧执行无法实机验证。

---

## 四、P0阻塞项更新（4个，新增1个）

| 编号 | P0阻塞 | 影响 | 状态 | 变化 |
|------|--------|------|------|------|
| V01 | Nacos服务不可达 → dyq-server无法启动 | 云端中枢完全不可用 | **新增** | 上次未发现 |
| V02 | 后端48081未监听 | PokeClaw真实注册链路断 | 未解决 | — |
| V03 | ADB无真机设备 | 端侧执行无法实机验证 | 未解决 | — |
| V04 | pyweixin模块缺失 | 微信GUI操控不可用 | 未解决 | failure_count从834增至890 |

---

## 五、可复现最小验证命令

### 云端（当前失败）
```bash
# Nacos不可达检测
python3 -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('127.0.0.1',8848))" 2>&1

# dyq-server进程检查
ps aux | grep dyq-server.jar | grep -v grep
# 预期: 空（进程已停止）
```

### WeFlow（部分成功）
```bash
curl -sS 'http://127.0.0.1:8000/health'
# 预期: controller_ready=true, upstream_ready=false, pywechat_imported=false

curl -sS 'http://127.0.0.1:8000/dyq/device-node/registration'
# 预期: nodeType=WEFLOW_WECHAT, 5个capabilities
```

### PokeClaw
```bash
adb devices
# 预期: 空列表（无设备）
```

---

## 六、风险清单

| 级别 | 风险 | 影响 | 端 | 建议解法 |
|------|------|------|----|----------|
| P0 | Nacos不可达 | 云端完全不可用 | DYQ-2 | 启动Nacos或改本地配置 |
| P0 | 后端48081未监听 | PokeClaw真实注册断 | DYQ-2/3 | 启动实例或修复路由 |
| P0 | ADB无真机 | 端侧无法实机验证 | DYQ-3 | 接入安卓设备 |
| P0 | pyweixin缺失 | 微信操控不可用 | DYQ-4 | pip install pyweixin |
| P1 | dyq-server进程无自动恢复 | 服务不稳定 | DYQ-2 | 配置systemd/supervisor |
| P1 | OpenAPI契约0命中(91/0) | 文档脱节 | DYQ-2 | 排查swagger扫描路径 |

---

## 七、验收结论

| 验收标准 | 达成情况 |
|----------|----------|
| 每端至少1条成功证据 | 部分达成 — WeFlow有新证据，云端/PokeClaw依赖上次 |
| 每端至少1条失败/异常证据 | 达成 — 新增Nacos不可达证据 |
| 可复现最小验证命令 | 达成 |
| 统一风险清单(P0/P1) | 达成 — 4个P0 + 2个P1 |

**整体判定**: DYQ-5 保持 blocked。与上次相比，**Nacos不可达**成为新的P0阻塞项，导致云端从"部分可用"退化为"完全不可用"。解决顺序建议: ①启动Nacos → ②启动dyq-server → ③修复pyweixin → ④接入安卓设备。

---

## 八、历史证据索引

- 上次验收: `/mnt/e/code/PokeClaw/.planning/audit/runs/DYQ-5-20260604-三端闭环联调验收/commercial-e2e-evidence.md`
- DYQ-2: `/mnt/e/code/dyq/.planning/audit/runs/DYQ-2-20260521-云端沙箱最小冒烟/SMOKE-EVIDENCE.md`
- DYQ-3: `/mnt/e/code/PokeClaw/docs/product/DYQ-3-pokeclaw-endcloud-smoke-evidence-20260521.md`
- DYQ-4: `/mnt/d/work/code/weflow-wechat-autopilot/docs/qa/dyq-4-weflow-min-smoke-20260521.md`
