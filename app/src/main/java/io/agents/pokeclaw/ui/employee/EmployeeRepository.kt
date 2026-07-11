// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// AI Employee 数据模型 + Repository 抽象（Fix code-review M3）
// 便于从 Seed 切换到 Remote 端点时只换实现，不动 ViewModel/UI

package io.agents.pokeclaw.ui.employee

/**
 * AI Employee 数据模型
 *
 * <p>字段对齐 dyq `ai_employee` 表（见 V20260710__claw_ai_employee_ddl.sql）。
 * `creditCost` 字段为客户端业务字段，dyq SQL 表无此列（由 SkillMarket / Pricing
 * 子系统计算），在 Remote 实现中需要从其他接口补充，本轮先 hardcoded 0。
 */
data class AiEmployee(
    val id: String,
    val name: String,
    val category: String,
    val shortDescription: String,
    val creditCost: Int,
    val rating: Float,
)

/**
 * AI Employee 数据源抽象（Fix code-review M3）。
 *
 * <p>当前有两个实现：
 * <ul>
 *   <li>[SeedEmployeeRepository] — 降级 stub，返回 [AiEmployeeSeed.EMPLOYEES]</li>
 *   <li>[RemoteEmployeeRepository] — TODO 待 dyq ClawAppAiEmployeeController 上线后实现</li>
 * </ul>
 *
 * <p>ViewModel 通过接口拿数据；切换实现只需改一行（默认构造或 DI 注入）。
 */
interface EmployeeRepository {
    suspend fun list(): List<AiEmployee>
}

/**
 * 降级实现：返回 hardcoded seed 列表。
 *
 * <p>保留到 dyq app-side AI employee endpoint 上线后被 [RemoteEmployeeRepository] 替换。
 */
class SeedEmployeeRepository : EmployeeRepository {
    override suspend fun list(): List<AiEmployee> = AiEmployeeSeed.EMPLOYEES
}

/**
 * Seed 列表（与 dyq V20260710__claw_ai_employee_seed.sql 一致）
 *
 * ⚠️ 当 dyq ClawAppAiEmployeeController 上线后删除此常量，改走真实 API。
 */
object AiEmployeeSeed {
    val EMPLOYEES: List<AiEmployee> = listOf(
        AiEmployee(
            id = "emp_cs_assistant",
            name = "客服小助手",
            category = "客服",
            shortDescription = "7×24h 智能客服，自动回复客户咨询",
            creditCost = 100,
            rating = 4.7f,
        ),
        AiEmployee(
            id = "emp_social",
            name = "社交小助手",
            category = "社交",
            shortDescription = "多平台内容发布 + 评论智能回复",
            creditCost = 150,
            rating = 4.5f,
        ),
        AiEmployee(
            id = "emp_ecom",
            name = "电商小助手",
            category = "电商",
            shortDescription = "商品上架 + 定价 + 客服 + 履约一站式",
            creditCost = 200,
            rating = 4.4f,
        ),
    )
}
