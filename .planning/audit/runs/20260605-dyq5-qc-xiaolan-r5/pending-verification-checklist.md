# 待验证问题清单 — DYQ-5 三端最小闭环联调（第5轮更新）

**更新日期**: 2026-06-04 21:00 CST
**维护人**: 测试员小蓝

---

## P0 阻塞项（必须解除才能闭环演示）

| 编号 | 问题 | 影响范围 | 验证命令 | 负责人 | 状态 | 本轮变化 |
|------|------|----------|----------|--------|------|----------|
| P0-1 | 设备注册接口返回500 | 三端闭环无法走通，PokeClaw/WeFlow无法对接真实后端 | `curl -X POST http://127.0.0.1:48080/api/claw-device/register -H "Content-Type: application/json" -d '{"deviceId":"qc-test","deviceName":"测试","deviceModel":"Pixel7","androidVersion":"14","appVersion":"1.0.0"}'` | 后端小龙 | ⚠️ Controller可达但Service层报错 | 与上轮相同 |
| P0-2 | 心跳@PermitAll未生效 | 端侧无法维持设备连接 | `curl -X POST http://127.0.0.1:48080/api/claw-device/heartbeat -H "Content-Type: application/json" -d '{"batteryLevel":85}'` | 后端小龙 | ⚠️ 401拦截 | 需修复ClawDeviceAuthInterceptor |
| P0-3 | /actuator/health返回500 | 健康检查不可用，K8s/监控无法判断服务状态 | `curl http://127.0.0.1:48080/actuator/health` | 后端小龙 | ⚠️ 业务层异常 | 新增识别 |
| P0-4 | 无ADB真机 | PokeClaw端侧UI无法真实验证 | `adb devices` | 主人/运维 | ❌ 无在线设备 | 无变化 |

## P1 重要项（影响完整度但不阻塞基本闭环）

| 编号 | 问题 | 影响范围 | 验证命令 | 负责人 | 状态 | 本轮变化 |
|------|------|----------|----------|--------|------|----------|
| P1-1 | OpenAPI spec路径前缀不一致 | 前端/端侧按spec生成代码会404 | 对照sandbox.openapi.yaml与swagger | 老周 | ⚠️ 需修复 | 无变化 |
| P1-2 | TLS证书字段varchar(64)不足 | 宿主机Docker TLS连接失败 | ALTER tls_client_key→TEXT | 阿盾 | ⚠️ 需修复 | 无变化 |
| P1-3 | PokeClaw git推送状态待补齐 | 审计链不完整 | `git log origin/dev --oneline -5` | 阿甲 | ⚠️ 待补 | 无变化 |
| P1-4 | WeFlow真实云端消费留痕 | 产品验收通过但无端到端证据 | WeFlow连接dyq-server后发送消息 | 小蓝/阿桥 | 🔄 依赖后端恢复 | 无变化 |
| P1-5 | 管理后台admin密码未知 | 无法登录验证管理功能 | 登录接口返回"账号密码不正确" | 主人 | ⚠️ 需确认 | 新增识别 |
| P1-6 | claw_device.device_token存截断值 | 旧设备token不可用于认证 | `SELECT device_token FROM dyqclaw.claw_device LIMIT 1` | 后端 | ⚠️ 需修复 | 新增识别 |

## 变更日志

| 日期 | 变更 |
|------|------|
| 2026-06-04 | 小蓝初版：汇总三端证据，产出8项待验证+5项风险 |
| 2026-06-04 | 小蓝心跳更新：新增P1-6(OpenAPI前缀)、P1-7(TLS字段) |
| 2026-06-05 | 小蓝第4轮心跳：P0-1从"无进程"→"进程存在但端口未监听" |
| 2026-06-04 | 小蓝第5轮心跳：48080端口已稳定监听（P0-1部分解除）；新增P0-3(health 500)、P1-5(密码未知)、P1-6(token截断)；OpenAPI 3499端点已暴露；claw_device 16条记录存在 |
