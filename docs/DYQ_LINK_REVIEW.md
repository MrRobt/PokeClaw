# DYQ ↔ PokeClaw 端云链路 — 接口与能力梳理

> 复核日期 2026-07-08。对象:dyq 后端(豆有趣,`com.douyouqu.dyq`,Spring Boot 3.4.1 单体多模块)与 PokeClaw(Android 端侧执行体)之间的设备-API 链路。
> 证据级别:**E3(活的 dyq 后端真实往返)** —— 在测试服务器运行中的 `dyq-server:test` 容器(端口 `:48081`)上完成 register→heartbeat→下发→拉取→HMAC 回传全环。
> ⚠️ 本文不含任何密钥/令牌/DB 口令;涉及处只写"见服务器配置"。

## 0. 一句话结论

**链路是通的。** PokeClaw 客户端的设备-API 路径、鉴权、HMAC 签名格式与 dyq `AppClawDeviceController` 完全对齐,并在真后端上端到端跑通。落地要修的是**两个部署缺口**(下方 §5),不是协议不通。

---

## 1. dyq 是什么(实际能力总览)

**豆有趣(DYQ)** 定位是"24 小时自动赚钱机器":内容自动生产 → 自动发布 → 自动互动 + 截流获客 + 无人商城 + 养号矩阵。单体多模块(**~44 个 `dyq-module-*`**),SaaS 多租户(自动注入 `tenant_id`),端口 `:48081`。三大技术支柱:

- **Claw(小龙虾)= 云端大脑/编排**(`dyq-module-claw`)
- **PokeClaw = 端侧执行体**(本仓库 app)
- **WeFlow = 微信控制基座**

### 与端侧/云手机/Agent 强相关的模块

| 模块 | 能力 |
|---|---|
| **claw** | PokeClaw 的云端大脑。**设备面**(`AppClawDeviceController`/`ClawDeviceService`:注册/心跳/任务下发/结果+证据)+ **小龙虾 Agent 面**(`ClawLobsterService` 每用户 AI 实体 FRY→GROW→MATURE→EVOLVED、自进化技能、经验/记忆/情绪、截流获客、微信控制) |
| **cloudphone** | 云手机机群运营:多厂商适配(pmock/prmcontrol)、账号与账号组、批量群控、养号计划、发布/评论脚本、看板配额 |
| **control** | 底层设备控制+视觉决策桥:`AdbActionExecutor` + 实时/视频投屏、脚本引擎、`ControlDecisionService`→Python 视觉(Detector/OCR),MQ 派发,训练/自动标注 |
| **behavior-simulation** | 拟人行为:persona 决策 + LLM 决策 + 注意力状态机,反检测养号 |
| **director / aigateway / aigc** | AI 编导脚本、统一多厂商模型网关(OpenAI/Anthropic/Gemini/Qwen/…)、工业级 AIGC 生产(GPU 调度/合规/分佣) |

### 大图(端到端业务流)

```
aigc/director 生成内容
  → claw(云端大脑)规划任务并下发
    → PokeClaw 在云手机上执行(cloudphone + pmock/无印 ADB)
      → PokeClaw + control 用 claw-yolo-hub 视觉模型看屏操作(发帖/评论/截流/养号)
    → 结果/轨迹回传
  → claw 进化技能(sandbox 安全执行 + behavior-simulation 拟人 + 训练服务)
底座:billing-settlement / credential / proxy(变现 / 密钥 / IP 隔离)
```

- **云手机**:dyq 不自有,通过 pmock / 无印魔盒(wuin.cc) / prmcontrol 的 HTTP OpenAPI 驱动第三方机群。
- **OpenSandbox(`:8080`)** = dev/test 控制面(跑 dyq-server 的 sandbox profile),**不是运行时组件** —— 这解释了为什么 PokeClaw 硬编码的 `:8080` 连过去是 OpenSandbox 而非 dyq。

---

## 2. 设备-API 契约(链路本体)

控制器:`dyq-module-claw-biz/.../controller/device/AppClawDeviceController.java`。前缀 `/api/claw-device`(device 包**不加** `/admin-api`|`/app-api`)。响应统一 `{code,data,msg}`,`code=0` 为成功。

| # | 方法 + 路径 | 鉴权 | 说明 |
|---|---|---|---|
| 1 | POST `/register` | **开放** | 首启注册;req 需 `deviceId`(+可选 deviceName/deviceModel/androidVersion/appVersion/deviceType/publicKey/capabilities);返回 `deviceToken`(JWT 7d)+`refreshToken`(30d) |
| 2 | POST `/heartbeat` | Bearer | 上报电量/网络/当前任务;返回 `pendingTaskCount/skillVersion/capabilityVersion/serverTime` |
| 3 | GET `/agent/tasks/poll` | Bearer | 长轮询拉任务(limit/timeout) |
| 4 | GET `/devices/{deviceId}/pending-tasks` | Bearer | 拉待处理任务(legacy,limit=10),拉取即翻 `ASSIGNED` |
| 5 | POST `/tasks/{taskUuid}/result` | **Bearer + HMAC** | 回传终态(status=SUCCESS/FAILED/CANCELLED + result/toolCalls/evidenceUrls/…) |
| 6 | POST `/agent/tasks/{taskUuid}/evidence` | Bearer | 上传证据(⚠ 存内存 `ConcurrentHashMap`,**未持久化**) |
| 7 | POST `/token/refresh` | **开放** | 用 refreshToken 换新 deviceToken |
| 8 | POST `/events` | **Bearer + HMAC** | WeFlow 微信事件推送 |
| 9 | GET `/wechat/risk-policy` | Bearer | 拉微信风控策略 |

PokeClaw 客户端(`cloud/api/DeviceApi.kt`)路径与上表 **1:1 对齐**;另有 `GET /tasks/{uuid}`、`POST /tasks/{uuid}/cancel`(带 HMAC)。

### 鉴权 / 签名

- **JWT**:HS256,claims `sub=deviceId, type=device|refresh`;secret 见服务器配置(⚠ 当前是**开发默认值**,生产必须覆盖)。deviceToken 7d、refreshToken 30d。
- **拦截器** `ClawDeviceAuthInterceptor` 校验 Bearer 并提取 deviceId;仅 `/register`、`/token/refresh` 放行。Security 放行名单**逐条枚举**(新增 device 路径若漏加会被 401)。
- **HMAC**(仅 `/result` 与 `/events`):头 `X-Claw-Timestamp`(±5min)、`X-Claw-Nonce`(Redis SETNX 防重放 5min)、`X-Claw-Signature`。
  ```
  signature   = hex(HMAC-SHA256(key = deviceToken,
                                msg = ts + "\n" + nonce + "\n" + requestURI + "\n" + sha256hex(rawBody)))
  ```
  失败码:`401001` 签名/令牌不符、`401002` 时间戳过期、`401003` nonce 重放;失败写 `claw_device_audit_log`。

### 下发流程 + 任务生命周期

- 表 `claw_device_task`;`ClawDeviceServiceImpl.createTask()`:配额/黑名单校验 → 生成 `taskUuid`+`commercialTaskId` → INSERT `status=PENDING`(mode=interactive/priority=normal/source=admin/retry 0-3)→ 置 `claw_device.current_task_id` → 发 `TASK_ASSIGNED` MQ(**仅通知,设备仍拉取**)。
- 拉取:`WHERE device_id=? AND status='PENDING' ORDER BY create_time LIMIT n`,拉即翻 `ASSIGNED`(`@TenantIgnore`)。
- 回传:置终态 + 脱敏 result + `completed_at`;FAILED 重试用尽 → `PENDING_MANUAL` 人工接管。
- **状态机**:`PENDING → ASSIGNED → [RUNNING] → SUCCESS|FAILED|CANCELLED`;另有 `TIMEOUT`(>600s)、`OFFLINE`(心跳超时回退)、`NEED_MANUAL`。
- **任务类型**:`content_publish` / `private_message_reply` / `account_nurture` / `cs_takeover`;**执行体** `executorType`:`pokeclaw` / `weflow` / `cloud_agent` / `sandbox`;设备指令是自由文本 `command`。

### 注入任务(E2E 三法)

1. **Admin REST** `POST /admin-api/claw/device/{deviceId}/execute`(需后台登录 + 权限 `claw:device:execute`)。
2. **策略下发** `POST /admin-api/claw/task/dispatch`(`claw:task:dispatch`,自动选设备)。
3. **直插 DB**(绕过鉴权,测试用):`claw_device_task` 至少 `task_uuid/device_id/command/status='PENDING'/executor_type='pokeclaw'`(设备须先 register)。

---

## 2b. 全接口对齐审计(端侧 Retrofit ↔ dyq 控制器)

复核了 PokeClaw `cloud/` 下**全部** Retrofit 端点与 dyq 控制器的对齐。路由规则:dyq 给 `controller.admin.*` 加 `/admin-api`、`controller.app.*` 加 `/app-api`、`controller.device.*` 不加。

**结论:28 ALIGNED / 0 DRIFT / 2 MISSING(dyq 范围内)** + 10 个 ModelHub 属独立服务(非 dyq)。**0 漂移** = 无路径/契约错位。

| API 区 | 端点 | 对齐 |
|---|---|---|
| 设备 API(`DeviceApi`) | 7 | 5 ✅ / 2 缺 |
| Lobster 命令(command/result/hermes-feedback) | 3 | 3 ✅ |
| Lobster 人格(personality×3) | 3 | 3 ✅ |
| Lobster 技能市场(list/install/save/remove/batch-status) | 5 | 5 ✅ |
| Lobster 记忆(list/create/delete/clear-all) | 4 | 4 ✅ |
| Lobster 档案(my/stats/executions/skills/suggestions) | 5 | 5 ✅ |
| 云手机(`CloudPhoneClient`:page/connection/control/start/stop) | 5 | 5 ✅ |
| ModelHub(`/api/v1/*`) | 10 | 属 `claw-yolo-hub` 独立服务,dyq 里本无 |

**2 个真实缺口**(端侧已调用、dyq 仅在 admin 面有、设备令牌够不着):
1. `GET /api/claw-device/tasks/{taskUuid}`(客户端 `DeviceApi.getTaskByUuid`)—— dyq 只有 `ClawDeviceController#getTaskByUuid` @ `/admin-api/claw/device/tasks/{taskUuid}`。**需在 `AppClawDeviceController` 补设备面端点。**
2. `POST /api/claw-device/tasks/{taskUuid}/cancel`(客户端 `DeviceApi.cancelTask`,带 HMAC)—— 同为 admin-only;且 `ClawDeviceSignatureFilter.PROTECTED_PATH`(`^/api/claw-device/(tasks/[^/]+/result|events)$`)**未含 cancel**,补端点时要把 HMAC 覆盖一并加上。

**ModelHub 10 端点**:打到独立 YOLO 模型中心(`claw-yolo-hub` FastAPI `/api/v1/*`),dyq 本就不提供 —— 只需确认该服务已部署,非 dyq 缺口。

**契约注记(对齐但请求体命名不同,字段一致不算漂移)**:`hermes/feedback`(`HermesFeedbackReq`↔`ClawHermesFeedbackDTO`)、`skill/install`(`Map<String,String>`↔`ClawAppSkillInstallReqVO{skillId}`)。

## 3. E2E 实测(活后端 `:48081`)

| 步 | 动作 | 结果 |
|---|---|---|
| 1 | `POST /register`(curl 模拟设备) | `code:0` → deviceToken(JWT HS384 7d)+ refreshToken |
| 2 | `POST /heartbeat`(Bearer) | `code:0` → `{pendingTaskCount, serverTime, skillVersion, capabilityVersion}` |
| 3 | 云端造任务(DB 直插 PENDING) | 成功 |
| 4 | `GET /devices/{id}/pending-tasks`(Bearer) | `code:0` 拉到该任务;DB `status` **PENDING→ASSIGNED** + `assigned_at` |
| 5 | `POST /tasks/{uuid}/result`(Bearer + **HMAC**) | `HTTP 200 code:0 "ok"`;DB **SUCCESS** + execution_time_ms + model_used + completed_at + result |

**HMAC 自算签名被服务器接受** → 证明签名串格式与服务器 `ClawDeviceSignatureFilter` 及 PokeClaw `HmacSigner` 三方一致。

---

## 4. 结论:链路通 ✅

契约(路径/鉴权/HMAC)完全对齐,注册→心跳→下发→拉取→回传全环在真后端跑通。**PokeClaw 侧无需改协议**即可对接 dyq;唯一差在部署配置(§5)。

## 5. 落地缺口与建议

1. **P1 — Base-URL 漂移**:PokeClaw 默认云端点是 `:8080`(现为 OpenSandbox)与 `10.0.2.2:8080` / `claw.agents.io` / `staging.claw.agents.io`,**均非 dyq 实际的 `:48081`**。
   → 要么把 dyq 挂到设备可达的稳定域名(nginx :80/:443 反代 → :48081),要么让 PokeClaw 的 `cloud_base_url` 可配并指向 dyq。已连通性验证:直连 `:48081` 全通。
2. **P1 — 私网可达性**:美区云手机到不了 `192.168.250.3`(私有 IP)。真机 PokeClaw 对接 dyq 必须用**公网/可路由端点**,或在**与 dyq 同网的本地 emulator**(服务器 `android` 容器)上验证端到端。
3. **P2 — 证据未持久化**:`/evidence` 存内存 `ConcurrentHashMap`,重启即丢 —— 端侧回传的截图证据不会落库。
4. **安全 — JWT 默认 secret**:运行配置用的是开发默认 secret(可离线伪造令牌/HMAC);生产务必覆盖 `dyq.claw.device.jwt-secret`。
5. **健壮性 — Security 放行名单逐条枚举**:新增 device 路径若漏登记会在 Security 层 401(接口演进时易踩)。

## 6. 关键文件

- dyq 设备控制器:`dyq-module-claw-biz/.../controller/device/AppClawDeviceController.java`
- dyq 鉴权/HMAC:`.../web/config/ClawDeviceAuthInterceptor.java`、`.../security/ClawDeviceSignatureFilter.java`、`.../util/ClawDeviceJwtUtil.java`
- dyq 服务/表:`.../service/device/ClawDeviceServiceImpl.java`、DO `.../dal/dataobject/device/ClawDeviceTaskDO.java`、状态枚举 `.../enums/ClawDeviceTaskStatusEnum.java`
- PokeClaw 客户端:`cloud/api/DeviceApi.kt`、`cloud/RetrofitDeviceCloudClient.kt`、`cloud/model/CloudModels.kt`、`cloud/auth/HmacSigner`
- 端云边界既有文档:`CLOUD_SUBSYSTEM_BOUNDARY.md`
