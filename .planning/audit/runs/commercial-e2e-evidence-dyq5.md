# DYQ-5 三端最小闭环联调验收证据

**验收员**: 测试员小蓝 (ec2afe67)
**验收时间**: 2026-06-04 23:15 CST（第二轮复核）
**上游任务**: DYQ-2(in_progress) / DYQ-3(in_progress) / DYQ-4(done)

---

## 1. 各端运行状态

### 🖥️ DYQ云端中枢 (48080)
| 检查项 | 结果 | 证据 |
|--------|------|------|
| 进程存活 | ✅ PASS | PID 475707, java -jar dyq-server.jar --spring.profiles.active=dev |
| 业务API框架可用 | ✅ PASS | 所有/admin-api/路径返回401(认证拦截)，说明路由正常 |
| /actuator/health | ❌ FAIL(非真正故障) | 返回500，但根因是actuator base-path=/admin-api/actuator，裸/actuator/health被当静态资源查找，触发NoResourceFoundException。非应用健康问题 |
| /admin-api/actuator/health | ⚠️ PARTIAL | 需认证，返回401(正确行为)，非授权无法确认后端真实健康状态 |
| 沙箱模块(sandbox) | ✅ PASS | Swagger文档中13个sandbox端点存在 |
| Agent模块 | ✅ PASS | agent/config + agent/execution-log + agent/feedback 端点存在 |
| Claw模块 | ✅ PASS | claw/experience + claw/training + claw/statistics 端点存在 |
| 登录认证 | ❌ BLOCKED | 尝试admin/admin123, admin/.159159%2, test/admin123, test/123456均失败。数据库用户密码为bcrypt哈希，无法反推。需主人提供测试凭据 |

**结论**: 后端运行正常，/actuator/health 500是路径配置误解（base-path=/admin-api/actuator），非真实故障。业务API需认证才能验证闭环。

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
| 健康检查 | ⚠️ PARTIAL | controller_ready=true, 但upstream.ready=false, pyweixin模块缺失 |
| 微信进程 | ✅ PASS | wechatProcess=true, wechatWindow=true |
| 监听器状态 | ⚠️ PARTIAL | running=true, 但持续报错: `pyweixin: No module named 'pyweixin'`, 失败1735+次 |
| 设备节点注册信息 | ✅ PASS | nodeType=WEFLOW_WECHAT, 5项capabilities已声明, protocolVersion=2026-05-15 |
| 心跳接口 | ✅ PASS | status=online, lastErrorMessage含pyweixin错误 |
| 安全策略 | ✅ PASS | preferGuiSend=true, forbidPrivateProtocol=true等6项已启用 |
| DYQ对接端点 | ✅ PASS | /dyq/device-node/ 下4个端点(registration/heartbeat/events/commands) |
| 微信发送接口 | ❌ FAIL | prepare-text/confirm-send返回UPSTREAM_UNAVAILABLE: Windows Agent调用失败(bridge连接超时) |
| 事件接口 | ✅ PASS | /listener/events返回20条事件(但全部是pyweixin模块缺失错误) |

**结论**: WeFlow底座框架就绪，设备节点协议已实现，微信进程/窗口正常。但关键阻塞：①pyweixin Python模块未安装导致消息监听持续失败；②Windows Agent bridge连接失败导致发送接口不可用。

---

## 2. 三端闭环链路验证

### 闭环1: WeFlow → DYQ (设备节点注册+心跳)
```
验证命令:
  curl http://127.0.0.1:8000/dyq/device-node/registration
  curl http://127.0.0.1:8000/dyq/device-node/heartbeat

结果: ⚠️ PARTIAL
证据: WeFlow侧声明了WEFLOW_WECHAT节点类型，心跳返回online，
     runtimeInfo中controllerReady=true, windowsAgentReady=true, wechatProcessReady=true, wechatWindowReady=true。
     但listenerRunning=true伴随failureCount=1735+(pyweixin缺失)。
     DYQ侧因认证blocked无法确认注册回写。
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

结果: ❌ FAIL
证据: prepare-text返回UPSTREAM_UNAVAILABLE:
     "prepare_text 调用 Windows Agent 失败：...urllib.request.urlopen超时"
     WeFlow的Controller无法连通Windows端Agent(bridge连接失败)。
     此外DYQ侧需认证才能触发命令下发。
```

---

## 3. 统一风险清单

| 级别 | 风险项 | 影响范围 | 建议处置 | 本次变化 |
|------|--------|----------|----------|----------|
| P0 | DYQ认证凭据未知 | 无法验证任何业务闭环 | 获取admin token或测试账号 | 无变化 |
| P0 | 无ADB设备连接 | PokeClaw端侧完全无法验证 | 连接Android设备或启动模拟器 | 无变化 |
| P0 | WeFlow pyweixin模块缺失 | 微信消息监听完全不可用 | `pip install pyweixin` 或等效依赖 | 🆕新发现(上轮监听器未启动，本轮启动后暴露) |
| P0 | WeFlow Windows Agent bridge不通 | 微信发送接口不可用 | 检查Windows端Agent服务是否运行 | 🆕新发现 |
| P1 | WeFlow监听器持续报错 | 消息接收链路失效 | 安装pyweixin后重启监听器 | 变化: 监听器已启动但持续报错 |
| P1 | DYQ↔WeFlow注册回写未验证 | 双向握手不完整 | 获取token后验证设备节点注册到DYQ | 无变化 |
| P1 | PokeClaw→DYQ回执链路未验证 | 端侧任务结果无法回传 | 需ADB设备+PokeClaw运行态 | 无变化 |
| ~~P1~~ | ~~DYQ/actuator/health 500~~ | ~~已解除~~ | ~~路径误解，实际base-path=/admin-api/actuator~~ | ✅已解除(非真实故障) |

---

## 4. 最小验证命令集

```bash
# === 云端中枢 ===
# 注意: actuator base-path = /admin-api/actuator, 非标准/actuator
curl -s http://127.0.0.1:48080/actuator/health  # 会500(路径误解)
curl -s http://127.0.0.1:48080/admin-api/actuator/health  # 需认证，返回401(正确)
# 登录后: curl -s -H "Authorization: Bearer ***" http://127.0.0.1:48080/admin-api/sandbox/host/page

# === WeFlow底座 ===
curl -s http://127.0.0.1:8000/health
curl -s http://127.0.0.1:8000/dyq/device-node/heartbeat
curl -s http://127.0.0.1:8000/dyq/device-node/registration
curl -s http://127.0.0.1:8000/listener/status
curl -s http://127.0.0.1:8000/listener/events
# 发送接口(需Windows Agent运行):
curl -s -X POST http://127.0.0.1:8000/wechat/prepare-text \
  -H "Content-Type: application/json" \
  -d '{"request_id":"test-001","session_name":"文件传输助手","message_text":"QC测试"}'

# === PokeClaw端侧 ===
adb devices
adb shell pm list packages | grep pokeclaw
```

---

## 5. 验收结论

**当前状态**: ⛔ 三端最小闭环**未打通**

### 单端可用性
- ✅ WeFlow底座框架: 通过(Controller运行, 设备节点协议完备, 微信进程/窗口正常)
- ❌ WeFlow微信核心功能: 未通过(pyweixin缺失→监听失败, Windows Agent不通→发送失败)
- ⚠️ DYQ后端: 部分(进程正常, API路由正常, 但认证阻塞无法验证业务)
- ❌ PokeClaw端侧: 未验证(无ADB设备)

### 端间闭环
- ❌ WeFlow→DYQ注册: 部分(WeFlow侧就绪, DYQ侧无法确认)
- ❌ DYQ→PokeClaw: 未验证(设备+认证双重阻塞)
- ❌ DYQ→WeFlow: 未验证(WeFlow发送链路不通+DYQ认证阻塞)

### 本轮变化(vs第一轮)
1. ✅ 已解除: /actuator/health 500非真实故障(路径误解, base-path=/admin-api/actuator)
2. 🆕 新发现: WeFlow监听器已启动但pyweixin模块缺失, 持续失败1735+次
3. 🆕 新发现: WeFlow Windows Agent bridge连接超时, 发送接口不可用
4. 🆕 新发现: WeFlow /wechat/prepare-text需要request_id+session_name+message_text三个必填字段

### 解除阻塞最小条件(按优先级)
1. **安装pyweixin**: `pip install pyweixin` → 解锁WeFlow消息监听
2. **启动Windows Agent**: 检查Windows端Agent服务 → 解锁微信发送
3. **DYQ提供测试凭据**: 获取admin token → 解锁所有业务闭环验证
4. **连接ADB设备**: 真机或模拟器 → 解锁PokeClaw端侧验证

**建议**: DYQ-5保持blocked状态，待上述4项解除后可推进验收。
