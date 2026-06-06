# DYQ-5 三端最小闭环联调验收证据（第三轮）

**验收员**: 测试员小蓝 (ec2afe67)
**验收时间**: 2026-06-05 03:59 CST（第三轮检查）
**上游任务**: DYQ-2(in_progress) / DYQ-3(done) / DYQ-4(in_progress)

---

## 1. 各端运行状态

### 🖥️ DYQ云端中枢 (48080)
| 检查项 | 结果 | 证据 |
|--------|------|------|
| 进程存活 | ⚠️ WARNING | PID 739405, 运行5分24秒，但端口48080未监听 |
| 业务API框架可用 | ❓ UNKNOWN | 端口未监听，无法验证 |
| /actuator/health | ❓ UNKNOWN | 端口未监听，无法验证 |
| 登录认证 | ❌ BLOCKED | 需要凭据，且端口未监听 |

**结论**: DYQ后端进程正在启动中，但5分钟后端口仍未监听，可能存在启动问题。需要检查启动日志或重启服务。

### 📱 PokeClaw端侧执行体
| 检查项 | 结果 | 证据 |
|--------|------|------|
| 项目代码存在 | ✅ PASS | /mnt/e/code/PokeClaw/ 目录完整 |
| ADB设备连接 | ❌ FAIL | adb devices返回空列表，无设备连接 |
| API契约文档 | ✅ PASS | api-contracts/device.openapi.yaml 存在(9702字节) |
| 本地模式说明 | ✅ PASS | README确认支持Local模式(无需API Key) |
| 端侧模型 | ⚠️ INFO | 使用LiteRT-LM加载Gemma 4，需GPU/NNPI设备 |

**结论**: 代码就绪但无Android设备连接，无法执行端到端验证。

### 🌐 WeFlow微信控制底座 (8000)
| 检查项 | 结果 | 证据 |
|--------|------|------|
| Controller进程 | ✅ PASS | controller_ready=true |
| 健康检查 | ✅ PASS | controller_ready=true, upstream.ready=true, pywechat_imported=true |
| 微信进程 | ✅ PASS | wechatProcess=true, wechatWindow=true |
| 监听器状态 | ✅ PASS | running=true, failure_count=0, last_error=null |
| 设备节点注册信息 | ✅ PASS | nodeType=WEFLOW_WECHAT, status=online, listenerFailureCount=0 |
| 心跳接口 | ✅ PASS | status=online, runtimeInfo中所有字段为true |
| 安全策略 | ✅ PASS | preferGuiSend=true, forbidPrivateProtocol=true等6项已启用 |
| DYQ对接端点 | ✅ PASS | /dyq/device-node/ 下4个端点(registration/heartbeat/events/commands) |
| 微信发送接口 | ✅ PASS | prepare-text返回success=true, status=prepared |
| 事件接口 | ✅ PASS | /listener/events返回500个待处理事件 |

**结论**: WeFlow底座完全就绪，设备节点协议已实现，微信进程/窗口正常，监听器运行正常，发送接口可用。

---

## 2. 三端闭环链路验证

### 闭环1: WeFlow → DYQ (设备节点注册+心跳)
```
验证命令:
  curl http://127.0.0.1:8000/dyq/device-node/registration
  curl http://127.0.0.1:8000/dyq/device-node/heartbeat

结果: ✅ PASS (WeFlow侧)
证据: WeFlow侧声明了WEFLOW_WECHAT节点类型，心跳返回online，
     runtimeInfo中controllerReady=true, windowsAgentReady=true, wechatProcessReady=true, wechatWindowReady=true, listenerRunning=true, listenerFailureCount=0。
     DYQ侧因端口未监听无法确认注册回写。
```

### 闭环2: DYQ → PokeClaw (任务下发+执行)
```
验证命令:
  adb devices  # 需设备在线

结果: ❌ FAIL (设备未连接)
阻塞: 无ADB设备，无法验证任务下发→端侧执行→回执链路
```

### 闭环3: DYQ → WeFlow (命令下发+微信执行)
```
验证命令:
  POST /wechat/prepare-text (request_id, session_name, message_text)
  POST /wechat/confirm-send (request_id, session_name)

结果: ✅ PASS (WeFlow侧)
证据: prepare-text返回success=true, status=prepared,
     显示GUI操作步骤：activate_window, open_session。
     有证据截图路径。
     manual_takeover_required=true（需要手动确认发送）。
     DYQ侧因端口未监听无法验证命令下发。
```

---

## 3. 统一风险清单

| 级别 | 风险项 | 影响范围 | 建议处置 | 本次变化 |
|------|--------|----------|----------|----------|
| P0 | DYQ后端端口未监听 | 无法验证任何业务闭环 | 检查启动日志或重启服务 | 🆕新发现(进程运行但端口未监听) |
| P0 | 无ADB设备连接 | PokeClaw端侧完全无法验证 | 连接Android设备或启动模拟器 | 无变化 |
| P1 | DYQ认证凭据未知 | 无法验证需要认证的API | 获取admin token或测试账号 | 无变化 |
| P1 | DYQ↔WeFlow注册回写未验证 | 双向握手不完整 | DYQ启动后验证设备节点注册到DYQ | 无变化 |
| P1 | PokeClaw→DYQ回执链路未验证 | 端侧任务结果无法回传 | 需ADB设备+PokeClaw运行态 | 无变化 |
| ~~P0~~ | ~~WeFlow pyweixin模块缺失~~ | ~~已解除~~ | ~~已安装pywechat或使用fake模式~~ | ✅已解除 |
| ~~P0~~ | ~~WeFlow Windows Agent bridge不通~~ | ~~已解除~~ | ~~bridge连接正常~~ | ✅已解除 |

---

## 4. 统一演示脚本v1

### 演示脚本：三端最小闭环验证

**步骤1：验证WeFlow底座就绪**
```bash
# 输入：curl命令
curl -s http://127.0.0.1:8000/health
# 预期输出：
{
  "controller_ready": true,
  "upstream": {
    "ready": true,
    "backend": "fake",
    "pywechat_imported": true,
    "wechat_process": true,
    "wechat_window": true
  }
}
# 失败注入点：如果controller_ready=false，检查WeFlow进程；如果upstream.ready=false，检查pywechat模块
```

**步骤2：验证设备节点心跳**
```bash
# 输入：curl命令
curl -s http://127.0.0.1:8000/dyq/device-node/heartbeat
# 预期输出：
{
  "nodeKey": "weflow-local-winwechat-001",
  "status": "online",
  "runtimeInfo": {
    "controllerReady": true,
    "windowsAgentReady": true,
    "wechatProcessReady": true,
    "wechatWindowReady": true,
    "listenerRunning": true,
    "listenerFailureCount": 0
  }
}
# 失败注入点：如果status=offline，检查WeFlow进程；如果listenerFailureCount>0，检查pywechat模块
```

**步骤3：验证微信发送接口**
```bash
# 输入：curl命令
curl -s -X POST "http://127.0.0.1:8000/wechat/prepare-text" \
  -H "Content-Type: application/json" \
  -d '{"request_id":"demo-001","session_name":"文件传输助手","message_text":"演示测试消息"}'
# 预期输出：
{
  "success": true,
  "status": "prepared",
  "session_name": "文件传输助手",
  "steps": ["activate_window", "open_session"]
}
# 失败注入点：如果success=false，检查Windows Agent bridge连接；如果status=failed，检查微信窗口状态
```

**步骤4：验证DYQ后端就绪**
```bash
# 输入：curl命令
curl -s http://127.0.0.1:48080/actuator/health
# 预期输出：HTTP 200 OK (需认证) 或 HTTP 401 Unauthorized (正确行为)
# 失败注入点：如果端口未监听，检查DYQ进程；如果返回500，检查actuator配置
```

**步骤5：验证PokeClaw端侧**
```bash
# 输入：adb命令
adb devices
# 预期输出：设备列表（至少一个设备）
# 失败注入点：如果设备列表为空，检查USB连接或模拟器状态
```

---

## 5. 待验证清单

### ✅ 已验证
1. WeFlow底座框架就绪
2. WeFlow设备节点协议已实现
3. WeFlow微信进程/窗口正常
4. WeFlow监听器运行正常（无错误）
5. WeFlow发送接口可用（prepare-text成功）
6. PokeClaw项目代码存在
7. PokeClaw API契约文档存在

### ❌ 待验证
1. DYQ后端启动问题（端口未监听）
2. DYQ业务API功能
3. DYQ↔WeFlow注册回写
4. DYQ→PokeClaw任务下发
5. PokeClaw端侧执行
6. 端到端完整闭环

### 🚧 阻塞项
1. **P0**: DYQ后端端口未监听 - 需要检查启动日志或重启服务
2. **P0**: 无ADB设备连接 - 需要连接Android设备或启动模拟器
3. **P1**: DYQ认证凭据未知 - 需要获取admin token或测试账号

---

## 6. 最小验证命令集

```bash
# === WeFlow底座 ===
curl -s http://127.0.0.1:8000/health
curl -s http://127.0.0.1:8000/dyq/device-node/heartbeat
curl -s http://127.0.0.1:8000/dyq/device-node/registration
curl -s http://127.0.0.1:8000/listener/status
curl -s http://127.0.0.1:8000/listener/events
# 发送接口：
curl -s -X POST http://127.0.0.1:8000/wechat/prepare-text \
  -H "Content-Type: application/json" \
  -d '{"request_id":"demo-001","session_name":"文件传输助手","message_text":"演示测试消息"}'

# === DYQ云端中枢 ===
curl -s http://127.0.0.1:48080/actuator/health
curl -s http://127.0.0.1:48080/admin-api/actuator/health -H "Authorization: Bearer TOKEN"
# 登录后: curl -s -H "Authorization: Bearer TOKEN" http://127.0.0.1:48080/admin-api/sandbox/host/page

# === PokeClaw端侧 ===
adb devices
adb shell pm list packages | grep pokeclaw
```

---

## 7. 验收结论

**当前状态**: ⚠️ 三端最小闭环**部分打通**

### 单端可用性
- ✅ WeFlow底座框架: 通过(Controller运行, 设备节点协议完备, 微信进程/窗口正常)
- ✅ WeFlow微信核心功能: 通过(pywechat已导入, 监听器无错误, 发送接口可用)
- ❌ DYQ后端: 未通过(进程运行但端口未监听)
- ❌ PokeClaw端侧: 未验证(无ADB设备)

### 端间闭环
- ⚠️ WeFlow→DYQ注册: 部分(WeFlow侧就绪, DYQ侧无法确认)
- ❌ DYQ→PokeClaw: 未验证(设备+认证双重阻塞)
- ⚠️ DYQ→WeFlow: 部分(WeFlow发送链路就绪, DYQ侧无法验证)

### 本轮变化(vs第二轮)
1. ✅ 已解除: WeFlow pyweixin模块缺失问题(现在使用pywechat或fake模式)
2. ✅ 已解除: WeFlow Windows Agent bridge连接问题(现在连接正常)
3. ✅ 改善: WeFlow监听器运行正常(failure_count=0)
4. ✅ 改善: WeFlow发送接口可用(prepare-text成功)
5. 🆕 新发现: DYQ后端进程运行但端口未监听
6. 🆕 新发现: WeFlow upstream.ready=true, pywechat_imported=true

### 解除阻塞最小条件(按优先级)
1. **解决DYQ后端启动问题**: 检查启动日志或重启服务 → 解锁所有DYQ相关验证
2. **连接ADB设备**: 真机或模拟器 → 解锁PokeClaw端侧验证
3. **获取DYQ测试凭据**: admin token → 解锁需要认证的API验证

**建议**: DYQ-5保持in_progress状态，优先解决DYQ后端启动问题，然后验证三端闭环。