# 三端最小闭环联调验收包 — 小蓝实测证据
> 时间: 2026-06-05 05:40 CST | 轮次: 实测验证

## 环境快照

| 项目 | 状态 |
|------|------|
| dyq-server:48080 | ✅ 已启动 (PID 407624, 启动耗时~12分钟) |
| WeFlow:8000 | ✅ 已启动 (controller_ready=true) |
| PokeClaw ADB | ❌ 无在线设备 |
| MySQL (192.168.250.3:3306) | ⚠️ 连接存在但认证受限 |
| Redis | ✅ PONG |
| Paperclip:3101 | ✅ status=ok |
| Maven 编译 | 🔄 进行中 (tenant + claw-biz 模块) |

## 云端中枢 (DYQ-2) 验证矩阵

| 编号 | 验证项 | 结果 | 证据 | 失败原因 |
|------|--------|------|------|----------|
| C1.1 | 健康探针 /actuator/health | ⚠️ code=500 | `{"code":500,"data":null,"msg":"系统异常"}` | 需认证/内部错误 |
| C1.2 | Swagger UI 可访问 | ✅ HTTP 200 | curl 返回 200 | - |
| C1.3 | 设备注册 /api/claw-device/register | ✅ code=0 | 返回 deviceToken + refreshToken, expiresIn=604800 | 需 tenant-id 头 |
| C1.4 | 设备心跳 /api/claw-device/heartbeat | ❌ code=401 | "账号未登录" | JAR未包含ClawDeviceAuthorizeRequestsCustomizer |
| C1.5 | 任务查询 /api/claw-task/pending | ❌ code=401 | "账号未登录" | 同上 |
| C2.1 | 管理接口(沙箱/技能/设备) | ❌ code=401 | "账号未登录" | 需管理后台认证凭据 |
| C2.2 | 注册无tenant-id | ❌ code=500 | NullPointerException: TenantContextHolder 不存在租户编号 | 需传 tenant-id: 1 |

**关键发现 C-P0**: 注册接口成功(code=0)，但心跳/任务/管理接口全部401。
根因：当前运行的 JAR (20:34构建) 未包含 `ClawDeviceAuthorizeRequestsCustomizer` 和 `ClawDeviceAuthInterceptor` 的编译产物。
claw-biz/target/classes/framework/web/config/ 为空，说明最新认证代码未编译打包进主 JAR。
Maven 正在编译 claw-biz 模块，完成后需重新打包并重启 dyq-server。

## WeFlow 微信控制底座 (DYQ-4) 验证矩阵

| 编号 | 验证项 | 结果 | 证据 |
|------|--------|------|------|
| W1.1 | 服务可达 | ✅ 返回 JSON | `{"detail":"Not Found"}` (FastAPI 默认) |
| W1.2 | 正确仓库 | ✅ /mnt/d/work/code/WeFlow 存在 | .git 目录存在 |
| W1.3 | Health 检查 | ✅ controller_ready=true | upstream 微信模块缺失 (pyweixin) |
| W1.4 | OpenAPI 文档 | ✅ 17个端点 | /health, /wechat/*, /listener/*, /dyq/device-node/* |
| W2.1 | 监听器状态 | ✅ API 可用 | running=false, 未启动 |
| W2.2 | 命令预览 | ⚠️ 需 command_id + capability 字段 | 校验正确返回 422 |
| W2.3 | 微信进程 | ✅ wechat_process=true, wechat_window=true | 但 active_session=null |
| W2.4 | upstream 就绪 | ❌ pyweixin 模块缺失 | "No module named 'pyweixin'" |

**关键发现 W-P0**: WeFlow 控制层(controller)已就绪，API 结构完整(17端点)，但微信 upstream 未就绪(pyweixin 模块缺失)，无法执行真实消息收发。

## PokeClaw 端侧 (DYQ-3) 验证矩阵

| 编号 | 验证项 | 结果 | 证据 |
|------|--------|------|------|
| P1.1 | ADB 设备 | ❌ 无在线设备 | "List of devices attached" 空 |
| P1.2 | 仓库存在 | ✅ /mnt/e/code/PokeClaw | .git 目录存在 |
| P1.3 | 验证脚本 | ✅ 6个脚本 | dyq3-e2e-smoke.py/sh/enhanced.py 等 |
| P1.4 | Mock 闭环 | ✅ (历史) | register/heartbeat/pending/result 全200 |

**关键发现 P-P0**: 无 ADB 真机设备，端侧只能通过 Mock 验证。Mock 全绿(历史证据)，但真实端云 E2E 无法执行。

## P0 风险清单

| 编号 | 风险 | 严重度 | 影响端 | 阻塞项 |
|------|------|--------|--------|--------|
| C-P0 | claw-biz 认证代码未编译打包，心跳/任务401 | P0 | 云端 | 需 Maven 编译完成 + 重新打包 + 重启 |
| W-P0 | pyweixin 模块缺失，微信消息收发不可用 | P0 | WeFlow | 需安装 pyweixin 依赖 |
| P-P0 | 无 ADB 真机设备 | P0 | PokeClaw | 需连接 Android 设备 |
| C-P1 | 管理后台认证凭据未知 | P1 | 云端 | 需确认 admin 密码 |

## 最小闭环结论

**三端最小闭环尚未打通。**

- ✅ 云端 48080 已启动，设备注册成功
- ✅ WeFlow 8000 已启动，17个API端点可访问
- ❌ 云端心跳/任务接口401 (认证代码未打包)
- ❌ WeFlow 微信消息收发不可用 (pyweixin缺失)
- ❌ 端侧无真机设备

**下一步最小可行方案**:
1. 等待 Maven 编译完成，重新打包 claw-biz，重启 dyq-server → 解除 C-P0
2. 安装 pyweixin: `pip install pyweixin` → 解除 W-P0
3. 连接 ADB 设备 → 解除 P-P0
4. 三项都解除后，执行完整端到端闭环验证

## 验证命令(可复现)

```bash
# 云端
curl -sS http://127.0.0.1:48080/swagger-ui.html -o /dev/null -w "%{http_code}"
curl -sS -X POST http://127.0.0.1:48080/api/claw-device/register -H "Content-Type: application/json" -H "tenant-id: 1" -d '{"deviceId":"test","deviceName":"test","deviceType":"android"}'

# WeFlow
curl -sS http://127.0.0.1:8000/health
curl -sS http://127.0.0.1:8000/listener/status

# PokeClaw
adb devices -l
```
