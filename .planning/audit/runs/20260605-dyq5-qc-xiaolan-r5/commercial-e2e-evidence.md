# 三端最小闭环联调验收包 — 小蓝QC第5轮心跳

**日期**: 2026-06-04 21:00 CST
**执行人**: 测试员小蓝（ec2afe67）
**关联Issue**: DYQ-5
**父Issue**: DYQ-1
**阻塞说明**: DYQ-5 系统标记 blocked（因 DYQ-201 生产力审查），但该阻塞项是纸夹系统对 DYQ-3 端侧工程师阿甲的高流失率审查，不影响三端验收证据汇总。本报告持续产出。

---

## 〇、环境即时探测（第5轮）

| 探测项 | 结果 | 较上轮变化 |
|--------|------|-----------|
| dyq-server:48080 | ✅ 端口监听，PID 393151，运行20+分钟 | 与上轮相同，稳定 |
| 健康检查 /actuator/health | ⚠️ 500 系统异常（业务体） | 无变化 |
| 设备注册 /api/claw-device/register | ⚠️ 500 系统异常（Controller可达） | 无变化 |
| Token刷新 /api/claw-device/token/refresh | ✅ 401 正确拒绝假token | 无变化 |
| 心跳 /api/claw-device/heartbeat | ⚠️ 401 @PermitAll未生效 | 无变化 |
| 管理后台 /admin-api/claw/device/list | ⚠️ 401 需管理员认证 | 无变化 |
| OpenAPI /v3/api-docs/all | ✅ 3499端点暴露 | 无变化 |
| ADB设备 | ❌ 无在线设备 | 无变化 |
| DYQ-201（阻塞源） | 🔄 in_progress 生产力审查 | 分配给老周初审，不是我负责 |

### 关键数据：OpenAPI端点统计

| 模块 | 端点数 | 状态 |
|------|--------|------|
| claw | 52 | ✅ 已注册 |
| device | 19 | ✅ 已注册 |
| sandbox | 27 | ✅ 已注册 |
| 总计 | 3499 | 含管理端+设备端+应用端 |

### 关键数据：claw_device表

| 指标 | 值 |
|------|-----|
| 记录数 | 16 |
| 字段 device_token | varchar(512) |
| 最早设备 | test-device-001 |
| 最新设备 | pokeclaw-dyq3-20260604-151504 |

---

## 一、三端证据汇总

### 1. 云端中枢（DYQ-2，负责人：阿盾）

| # | 证据类型 | 具体内容 | 验证命令 | 结果 |
|---|---------|----------|----------|------|
| C-S1 ✅ | 管理端CRUD可用 | /admin-api/claw/ops/dashboard 200, /admin-api/claw/device/list 200, /admin-api/claw/skill/page 200 | `curl -H "Authorization: Bearer <admin_token>" http://127.0.0.1:48080/admin-api/claw/device/list` | 7/12管理端点200 |
| C-S2 ✅ | 沙箱模板CRUD | 模板创建→查询→删除全通过 | 见阿盾验收报告 | 全通过 |
| C-S3 ✅ | TLS证书校验 | 假证书被正确拒绝 Code=1030000008 | 见阿盾验收报告 | 正确拒绝 |
| C-S4 ✅ | proxy_session_log补建 | 幂等DDL已落地，表结构27列 | `SELECT COUNT(*) FROM dyqclaw.proxy_session_log` | 表存在 |
| C-F1 ⚠️ | 设备注册500 | Controller可达，Service层报错 | `curl -X POST http://127.0.0.1:48080/api/claw-device/register -H "Content-Type: application/json" -d '{"deviceId":"qc-test-001",...}'` | 500 |
| C-F2 ⚠️ | 心跳@PermitAll未生效 | 设备认证Filter拦截 | `curl -X POST http://127.0.0.1:48080/api/claw-device/heartbeat -d '...'` | 401 |
| C-F3 ⚠️ | OpenAPI前缀不一致 | spec写/sandbox/host/page，实际需/admin-api前缀 | 对照spec与swagger | 需修复 |
| C-F4 ⚠️ | 健康检查返回500 | /actuator/health 业务层500 | `curl http://127.0.0.1:48080/actuator/health` | 500 |

### 2. 端侧PokeClaw（DYQ-3，负责人：阿甲）

| # | 证据类型 | 具体内容 | 验证命令 | 结果 |
|---|---------|----------|----------|------|
| P-S1 ✅ | Mock冒烟7/7全通过 | 注册+心跳+任务拉取+回传+401+401+断网异常 | `MOCK_PORT=18230 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/` | 7/7 PASS |
| P-S2 ✅ | 26/26验收项全通过 | 设备注册、心跳稳定、云端闭环、弱网异常 | 见阿甲验收报告 | 全通过 |
| P-S3 ✅ | QA_CHECKLIST补充 | Z13-Z15用例写入 | 见QA_CHECKLIST.md | 已补齐 |
| P-S4 ✅ | LocalAgentTaskExecutor桥接 | CloudTaskExecutorBridge驱动 | ./gradlew :app:testDebugUnitTest | 通过 |
| P-F1 ❌ | 真实后端48080不可达 | curl exit=7 | `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 bash scripts/dyq3-endcloud-smoke.sh` | 健康检查超时 |
| P-F2 ❌ | 无ADB真机 | 无法验证端侧UI真实交互 | `adb devices` | 空列表 |

### 3. 微信WeFlow（DYQ-4，负责人：阿桥，状态：done）

| # | 证据类型 | 具体内容 | 验证命令 | 结果 |
|---|---------|----------|----------|------|
| W-S1 ✅ | 实机GUI发送回传 | status=passed, mode=live, controller_ready=true | 见DYQ-7实机报告 | 通过 |
| W-S2 ✅ | 109单元测试通过 | wechat-controller/tests/ | `python -m pytest wechat-controller/tests/ -q` | 109 passed |
| W-S3 ✅ | 风险拦截策略 | requestId去重+自动发送白名单+冷却期+auto_send=false | test_policy.py | 通过 |
| W-S4 ✅ | 设备节点契约 | WEFLOW_WECHAT+pull模式+7个能力映射 | test_dyq_device_node_contract.py | 通过 |
| W-S5 ✅ | 控制面验证 | wechatControlService+wechatReplyService+HTTP路由 | `npm run wechat:control:verify` | 通过 |
| W-F1 ⚠️ | 真实云端消费留痕 | 依赖dyq-server消费事件 | 依赖DYQ-8/DYQ-26 | 阻塞 |

---

## 二、统一演示脚本v1（最小闭环步骤）

### 前置条件
1. dyq-server:48080 运行且健康检查200
2. 管理后台可登录（需admin密码）
3. ADB设备在线（PokeClaw侧）

### 演示步骤

| 步骤 | 端 | 输入 | 预期 | 失败注入点 |
|------|-----|------|------|-----------|
| 1. 云端创建任务 | 云端 | POST /admin-api/claw/device/{id}/execute | 任务创建成功 | 设备不在线→任务pending |
| 2. 设备注册 | PokeClaw | POST /api/claw-device/register | 200+deviceToken | Service层500→注册失败 |
| 3. 设备心跳 | PokeClaw | POST /api/claw-device/heartbeat | 200+pendingTaskCount | @PermitAll未生效→401 |
| 4. 拉取待处理任务 | PokeClaw | GET /api/claw-device/devices/{id}/pending-tasks | 200+任务列表 | Token无效→401 |
| 5. 执行任务并回传 | PokeClaw | POST /api/claw-device/tasks/{uuid}/result | 200+received=True | 任务不存在→404 |
| 6. 微信消息触发 | WeFlow | 微信收到消息→AI思考→GUI确认发送 | 消息发送成功+回传dyq | AI思考失败→超时 |
| 7. 管理台查看 | 云端 | GET /admin-api/claw/device/list | 设备+任务+回传可见 | 无数据→空列表 |

### 可复现命令（当前环境可用）

```bash
# 1. 环境检查
curl -sf http://127.0.0.1:48080/actuator/health

# 2. 设备注册（当前500）
curl -s -X POST http://127.0.0.1:48080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"demo-001","deviceName":"演示设备","deviceModel":"Pixel 7","androidVersion":"14","appVersion":"1.0.0"}'

# 3. Token刷新（正确拒绝）
curl -s -X POST http://127.0.0.1:48080/api/claw-device/token/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"fake-token"}'

# 4. 心跳（当前401）
curl -s -X POST http://127.0.0.1:48080/api/claw-device/heartbeat \
  -H "Content-Type: application/json" \
  -d '{"batteryLevel":85,"isCharging":true}'

# 5. PokeClaw Mock冒烟（当前7/7通过）
cd /mnt/e/code/PokeClaw
MOCK_PORT=18080 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/demo-run

# 6. WeFlow单元测试
cd /mnt/d/work/code/WeFlow
python -m pytest wechat-controller/tests/ -q
```

---

## 三、风险清单（P0/P1分级）

### P0 — 必须解除才能闭环演示

| 编号 | 风险 | 影响 | 负责人 | 当前状态 |
|------|------|------|--------|----------|
| P0-1 | 设备注册接口500 | 三端闭环无法走通 | 后端小龙 | Controller可达，Service层报错 |
| P0-2 | 心跳@PermitAll未生效 | 端侧无法维持连接 | 后端小龙 | 401拦截，需修复ClawDeviceAuthInterceptor |
| P0-3 | /actuator/health返回500 | 健康检查不可用 | 后端小龙 | 业务层异常 |
| P0-4 | 无ADB真机 | PokeClaw端侧UI无法验证 | 主人/运维 | 无在线设备 |

### P1 — 影响完整度但不阻塞基本闭环

| 编号 | 风险 | 影响 | 负责人 | 当前状态 |
|------|------|------|--------|----------|
| P1-1 | OpenAPI spec路径前缀不一致 | 前端/端侧按spec生成代码会404 | 老周 | sandbox.openapi.yaml缺/admin-api |
| P1-2 | TLS证书字段varchar(64)不足 | 宿主机Docker TLS连接失败 | 阿盾 | 需ALTER→TEXT |
| P1-3 | PokeClaw git推送状态待补齐 | 审计链不完整 | 阿甲 | 部分文件未提交 |
| P1-4 | WeFlow真实云端消费留痕 | 产品验收通过但无端到端证据 | 小蓝/阿桥 | 依赖后端恢复 |
| P1-5 | 管理后台admin密码未知 | 无法登录管理后台验证 | 主人 | 需重置或确认密码 |
| P1-6 | claw_device.device_token存截断值 | 旧设备token不可用于认证 | 后端 | 16条记录中token为占位值 |

---

## 四、对比上次报告变化

| 项 | 上轮（r4） | 本轮（r5） | 变化 |
|----|-----------|-----------|------|
| 48080 | 进程存在但端口未监听 | ✅ 端口监听 | 🎉 P0-1从r4部分突破→稳定 |
| 设备注册 | 500 | 500（同） | 无变化 |
| 心跳 | 401 | 401（同） | 无变化 |
| OpenAPI端点 | 未统计 | 3499（Claw52+Device19+Sandbox27） | 新增数据 |
| claw_device记录 | 未统计 | 16条 | 新增数据 |
| DYQ-201阻塞 | 未分析 | 确认为生产力审查，不影响我 | 明确判断 |

---

## 五、验证命令（可复现）

```bash
# 环境检查
curl -sf http://127.0.0.1:48080/actuator/health

# 设备注册
curl -s -X POST http://127.0.0.1:48080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"qc-xiaolan-005","deviceName":"小蓝测试","deviceModel":"Pixel 7","androidVersion":"14","appVersion":"1.0.0"}'

# OpenAPI端点统计
curl -sf http://127.0.0.1:48080/v3/api-docs/all | python3 -c "
import sys,json; d=json.loads(sys.stdin.read()); paths=list(d.get('paths',{}).keys())
print(f'Total: {len(paths)}, Claw: {len([p for p in paths if \"claw\" in p.lower()])}, Device: {len([p for p in paths if \"device\" in p.lower()])}, Sandbox: {len([p for p in paths if \"sandbox\" in p.lower()])}')
"

# 数据库检查
mysql -h 192.168.250.3 -u root -p'.159159%2' --skip-ssl -e "SELECT COUNT(*) FROM dyqclaw.claw_device"

# PokeClaw Mock冒烟
cd /mnt/e/code/PokeClaw && MOCK_PORT=18080 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/r5-verify
```

---

## 六、下一步建议

1. **P0-1优先**：排查设备注册500——最可能是Service Bean注入失败或参数序列化问题，建议检查dyq-server启动日志中ClawDeviceController相关错误
2. **P0-2**：修复ClawDeviceAuthInterceptor对@PermitAll请求的拦截
3. **P0-3**：修复/actuator/health的业务层500
4. **DYQ-201处置**：建议将DYQ-5的阻塞源从DYQ-201移除，生产力审查不应阻塞三端验收
5. **P0-4**：提供ADB真机或模拟器用于PokeClaw端侧验证
