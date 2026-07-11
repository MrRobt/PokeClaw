# PokeClaw ↔ dyq Skill Market / Vendor Billing 契约

**状态**：v1.0 · 2026-07-10

**目的**：把 PokeClaw App 端 `LobsterSkillMarketplaceApi` 已经消费的几个端点，与 dyq 端 `ClawSkillController` 的实际路径对齐，并新增 vendor billing pricing 端点。

---

## 1. 已有端点（沿用现状，仅补文档）

### 1.1 GET /app-api/claw/app/lobster/skill/list

**用途**：获取技能市场列表

**请求**：GET，body 空

**鉴权**：`Authorization: Bearer <deviceToken>`（与三段鉴权组合，详见 `claw-device-three-segment-auth.md`）

**响应 schema**（与现有 `ClawAppSkillMarketRespVO` 对齐）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": [
    {
      "skillId": "skill_search_youtube_v1",
      "skillName": "Search YouTube",
      "description": "在 YouTube 中搜索指定关键词",
      "vendor": "agents-io",
      "installStatus": "NOT_INSTALLED",
      "version": "1.0.0",
      "iconUrl": "https://cdn.example.com/icons/youtube.png",
      "channelCode": "skill_market",
      "definition": "{\"steps\":[...]}"
    }
  ]
}
```

**PokeClaw 端实现**：`LobsterSkillMarketplaceApi.listSkills()` + `SkillMarketplaceClient.listSkills()`

**dyq 端实现**：`dyq-module-claw/.../controller/app/ClawAppSkillController.java`（待确认路径）— 若路径不一致，本契约**待调整**

**业务码**：
- `0` = 成功
- `200` = 成功（兼容）
- 其他 = 业务错误，UI 弹 toast

### 1.2 POST /app-api/claw/app/lobster/skill/install

**用途**：安装指定 skill

**请求**：

```json
{ "skillId": "skill_search_youtube_v1" }
```

**响应**：

```json
{ "code": 0, "msg": "ok", "data": true }
```

`data = true` 安装成功；`data = false` 业务失败（skill 不存在 / 重复安装）

**PokeClaw 端**：`SkillMarketplaceClient.installSkill(skillId)`

### 1.3 POST /app-api/claw/app/lobster/skill/save

**用途**：保存（新建或更新）skill

**请求**（与 `ClawAppSkillSaveReqVO` 对齐）：

```json
{
  "id": null,
  "skillName": "My Custom Skill",
  "vendor": "user",
  "paramsSchema": "{\"type\":\"object\",...}",
  "channelCode": "user_skill"
}
```

**响应**：
- 新建：`{ "code": 0, "msg": "ok", "data": "new_skill_id_string" }`
- 更新：`{ "code": 0, "msg": "ok", "data": true }`

### 1.4 DELETE /app-api/claw/app/lobster/skill/remove?id={id}

**用途**：删除 skill

**响应**：

```json
{ "code": 0, "msg": "ok", "data": true }
```

### 1.5 PUT /app-api/claw/app/lobster/skills/batch-status

**用途**：批量启/停用 skill

**请求**：

```json
{
  "ids": ["skill_a", "skill_b"],
  "enable": true
}
```

**响应**：

```json
{ "code": 0, "msg": "ok", "data": true }
```

---

## 2. 新增端点（本契约定义，待 dyq 实现）

### 2.1 GET /app-api/claw/app/billing/pricing/list

**用途**：设备端获取当前可见的 vendor 计价表（替换 `VendorBillingRegistry.SEED` 占位）

**请求**：GET，body 空

**鉴权**：`Authorization: Bearer <deviceToken>` + 可选 `X-Claw-Tenant-Id`

**响应**：

```json
{
  "code": 0,
  "msg": "ok",
  "data": [
    {
      "vendorCode": "cloudphone",
      "workflowType": "*",
      "billingDimension": "duration",
      "displayName": "云手机 · 按时长",
      "creditCost": 10,
      "currency": "credit",
      "status": "CONFIGURED"
    },
    {
      "vendorCode": "cs_ai",
      "workflowType": "*",
      "billingDimension": "token",
      "displayName": "CS-AI · 按 token",
      "creditCost": 1,
      "currency": "credit",
      "status": "CONFIGURED"
    }
  ]
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `vendorCode` | string | ✅ | 供应商编码（`cloudphone` / `digital_human` / `cs_ai`） |
| `workflowType` | string | ✅ | 工作流类型（`*` 通用 / 特定业务名） |
| `billingDimension` | string | ✅ | 计费维度（`duration` / `token` / `call`） |
| `displayName` | string | ✅ | UI 显示名 |
| `creditCost` | int | ⛔ | 每单位 cost（积分制；`null` = 未配置） |
| `currency` | string | ⛔ | 默认 `credit` |
| `status` | string | ✅ | `CONFIGURED` / `PLACEHOLDER` / `UNKNOWN` |

**业务码**：
- `0` = 成功
- `1020010001` = 鉴权失败
- `1020011002` = 重复 vendor 配置冲突
- 其他 = 业务错误

**PokeClaw 端实现**（待补）：

```kotlin
// LobsterBillingApi.kt（新增）
interface LobsterBillingApi {
    @GET("app-api/claw/app/billing/pricing/list")
    suspend fun listPricing(): Response<CommonResult>
}

// BillingPricingClient.kt（新增）
class BillingPricingClient(private val api: LobsterBillingApi) {
    sealed class Result {
        data class OkList(val entries: List<VendorBillingEntry>) : Result()
        data class Rejected(val message: String) : Result()
    }
    suspend fun list(): Result { ... }
}
```

**dyq 端实现**（待补）：

```
ClawAppBillingController.listPricing() → ClawAppBillingService → VendorPricingConfigService
```

`ClawAppBillingPricingRespVO` snake_case 字段映射到 PokeClaw `VendorBillingEntry`（已存在，字段一致）。

---

## 3. AI Employee Market 端点（**待 dyq 端实现后补全**）

**当前状态**：dyq 端 `dyq-module-ai-employee` 模块已有 `AiEmployeeApi` / `AiEmploymentApi` 与 admin controller（`OwnerRevenueController` / `OwnerWithdrawController`），但**没有 app-side 端点**给 PokeClaw 端拉取员工市场。

**约定路径**（待 dyq 端确认）：

```
GET    /app-api/claw/app/ai-employee/list           # 员工市场列表
GET    /app-api/claw/app/ai-employee/{id}           # 员工详情
POST   /app-api/claw/app/ai-employee/employment     # 雇佣员工
```

**PokeClaw 端降级策略**（当 dyq 端 endpoint 未就绪时）：

```kotlin
// AiEmployeeClient.kt（降级版）
class AiEmployeeClient {
    suspend fun list(): Result {
        // TODO: 等待 dyq 端 app-side endpoint 完成
        // 当前降级返回 hardcoded 列表（BACKLOG P1）
        return Result.OkList(SEED_EMPLOYEES)
    }
    
    companion object {
        val SEED_EMPLOYEES = listOf(
            AiEmployee("emp_cs_assistant", "客服小助手", "7×24h 自动回复客户消息", 100),
            AiEmployee("emp_social", "社交小助手", "监控多平台评论并智能回复", 150),
            AiEmployee("emp_ecom", "电商小助手", "商品上架 + 评论管理 + 客服", 200),
        )
    }
}
```

**何时移除降级**：当 dyq `ClawAppAiEmployeeController` 提供 `GET /app-api/claw/app/ai-employee/list` 真实数据后，删除 `SEED_EMPLOYEES` 改走真实 API。

---

## 4. 端点对齐矩阵

| 端点 | PokeClaw 端 | dyq 端 | 状态 |
|---|---|---|---|
| GET /app-api/claw/app/lobster/skill/list | `LobsterSkillMarketplaceApi.listSkills()` | `ClawAppSkillController.list()` 或 admin `ClawSkillController` | ✅ 已有（路径待 dyq 端确认） |
| POST /app-api/claw/app/lobster/skill/install | `SkillMarketplaceClient.installSkill()` | 同上 | ✅ 已有 |
| POST /app-api/claw/app/lobster/skill/save | `SkillMarketplaceClient.saveSkill()` | 同上 | ✅ 已有 |
| DELETE /app-api/claw/app/lobster/skill/remove | `SkillMarketplaceClient.removeSkill()` | 同上 | ✅ 已有 |
| PUT /app-api/claw/app/lobster/skills/batch-status | `SkillMarketplaceClient.batchUpdateStatus()` | 同上 | ✅ 已有 |
| **GET /app-api/claw/app/billing/pricing/list** | **待新增** `LobsterBillingApi` | **待新增** `ClawAppBillingController` | 🆕 P0 |
| GET /app-api/claw/app/ai-employee/list | 待新增 | 待新增 | 🆕 P1（BACKLOG） |

---

## 5. 错误码统一

- 业务码（`code` 字段）：`0` / `200` = 成功，其他 = 业务失败
- HTTP status：成功 200；业务失败 200（业务码携带在 body）；4xx 5xx 由网关返回
- 鉴权失败：401xxx（详见 `claw-device-three-segment-auth.md` §5）

---

## 6. 引用

- 现有 PokeClaw client：`app/.../cloud/lobster/api/LobsterSkillMarketplaceApi.kt`
- 现有 PokeClaw DTO：`app/.../cloud/lobster/model/SkillMarketplaceDtos.kt`
- 现有 PokeClaw client 包装：`app/.../cloud/lobster/client/SkillMarketplaceClient.kt`
- 现有 PokeClaw 入口：`app/.../ui/settings/VendorBillingActivity.kt` + `VendorBillingRegistry.kt`
- 现有 dyq 端 admin controller：`dyq-module-claw/.../controller/admin/ClawSkillController.java`（待确认前缀 `/claw/skill`）
- 现有 dyq 端 API：`dyq-module-claw/.../api/ClawSkillApi.java`（内部 Feign，不直接暴露给 app）
- 现有 dyq 端 `dyq-module-ai-employee`：内部 Feign，无 app-side controller
