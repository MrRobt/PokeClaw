# 三端最小闭环联调验收包 — 小蓝QC第4轮心跳（紧急更新）

**日期**: 2026-06-05 21:10
**执行人**: 测试员小蓝（ec2afe67）
**关联Issue**: DYQ-5
**父Issue**: DYQ-1

---

## 〇、环境即时探测（第4轮 - 二次更新）

| 探测项 | 结果 | 较上轮变化 |
|--------|------|-----------|
| dyq-server:48080 | ✅ **端口已监听！进程PID 393151** | 🎉 **P0-1首次通过！** |
| Mock后端:18080 | ✅ UP | 无变化 |
| Docker容器 | 无运行中容器 | 无变化 |
| ADB设备 | 无在线设备 | 无变化 |

---

## 一、真实后端API验证（本轮核心突破）

### 设备端API验证（/api/claw-device/）

| # | 接口 | 方法 | 返回码 | 返回消息 | 评价 |
|---|------|------|--------|----------|------|
| 1 | /register | POST | 500 | 系统异常 | ⚠️ Controller可达但Service层报错（DYQ-187关联） |
| 2 | /token/refresh | POST | 401 | 刷新令牌无效或已过期 | ✅ 正确拒绝假token |
| 3 | /heartbeat | POST | 401 | 账号未登录 | ⚠️ @PermitAll但设备认证Filter未生效 |
| 4 | /devices/{id}/pending-tasks | GET | 401 | 账号未登录 | ✅ 需设备Token，预期行为 |
| 5 | /tasks/{uuid}/result | POST | 401 | 账号未登录 | ✅ 需设备Token，预期行为 |

### 管理后台API验证（/claw/device/）

| # | 接口 | 方法 | 返回码 | 评价 |
|---|------|------|--------|------|
| 6 | /list | GET | 401 | ✅ 需管理员权限，预期行为 |

### 关键分析

1. **P0-1（dyq-server启动）已解除** ✅ 端口48080监听，API可调用
2. **P0-2（设备注册Controller）部分突破**：
   - 注册接口路径 `/api/claw-device/register` 可达（不是404）
   - `@PermitAll` 白名单生效（不走管理员认证）
   - 但Service层返回500——可能是：
     - 数据库表 `claw_device` 不存在
     - Service Bean注入失败（DYQ-187修复5个Controller注册问题）
     - 请求参数序列化问题
3. **Token刷新正确工作**：假token被正确拒绝，说明Controller+Service链路在此接口正常
4. **心跳接口401问题**：Controller标注`@PermitAll`但仍返回401——可能是`ClawDeviceSignatureFilter`或`ClawDeviceAuthInterceptor`拦截了请求

### 对比：PokeClaw端侧期望 vs 真实后端响应

| 端侧操作 | 期望响应 | 真实响应 | 差异 |
|----------|----------|----------|------|
| 设备注册 | 200 + deviceToken/refreshToken | 500系统异常 | ❌ Service层报错 |
| 心跳 | 200 + pendingTaskCount | 401未登录 | ❌ 认证Filter问题 |
| 任务拉取 | 200 + 任务列表 | 401未登录 | ❌ 需先注册获取Token |
| 任务结果提交 | 200 + ok | 401未登录 | ❌ 需先注册获取Token |
| Token刷新 | 401(假token) | 401刷新令牌无效 | ✅ 一致 |

---

## 二、P0待验证清单（第4轮更新）

| 编号 | 验证项 | 优先级 | 状态 | 本轮变化 |
|------|--------|--------|------|----------|
| P0-1 | dyq-server:48080启动 | P0 | ✅ **已通过！** | 🎉 4轮以来首次通过 |
| P0-2 | 云端设备注册Controller | P0 | ⚠️ **可达但500** | 路径可达，Service层报错 |
| P0-3 | 云端任务下发API闭环 | P0 | ⚠️ **部分可达** | 需Token后验证 |
| P0-4 | PokeClaw Mock→真实后端切换 | P0 | 🔄 可部分验证 | 注册500仍阻塞 |

---

## 三、下一步建议

1. **P0-2修复**：排查设备注册500错误——最可能是数据库表缺失或Service Bean注入失败
2. **心跳认证**：排查`ClawDeviceAuthInterceptor`为何拦截`@PermitAll`心跳请求
3. **DYQ-187**：确认Controller注册修复后重新编译部署
4. **PokeClaw冒烟**：注册接口修复后立即运行 `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 bash scripts/dyq3-endcloud-smoke.sh`

---

## 四、验证命令（可复现）

```bash
# 1. 健康检查
curl -sf http://127.0.0.1:48080/actuator/health

# 2. 设备注册（500错误）
curl -s -X POST http://127.0.0.1:48080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"qc-test-001","deviceName":"测试设备","deviceModel":"Pixel 7","androidVersion":"14","appVersion":"1.0.0"}'

# 3. Token刷新（正确拒绝）
curl -s -X POST http://127.0.0.1:48080/api/claw-device/token/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"fake-token"}'

# 4. 心跳（401问题）
curl -s -X POST http://127.0.0.1:48080/api/claw-device/heartbeat \
  -H "Content-Type: application/json" \
  -d '{"batteryLevel":85,"isCharging":true,"networkType":"wifi"}'

# 5. 待处理任务
curl -s http://127.0.0.1:48080/api/claw-device/devices/qc-test-001/pending-tasks
```
