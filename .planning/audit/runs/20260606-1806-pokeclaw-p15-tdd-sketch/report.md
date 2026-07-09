# P1.5 弱网/离线异常可见性 — TDD 交接审计（不接活、不动架构）

- 时间：2026-06-06 18:06 (UTC+8)
- 角色：PokeClaw 端侧 worker (cron)
- 工作目录：`/mnt/e/code/PokeClaw`
- git 状态：本地干净（`git status` 无未提交改动），无他人 working-tree 冲突
- 范围：仅审计 / 复用既有冒烟 / 写交接文档 / 不写测试代码
- 上轮审计：`20260606-1508-pokeclaw-p15-audit/report.md`（已存在）

## 一、结论

| 维度 | 状态 | 证据 |
|---|---|---|
| 端云冒烟脚本（mock 模式）| ✅ PASS 7/7 | `smoke/summary.md`、`smoke/smoke_run.log`（本轮重跑） |
| OfflineFallbackManager 死代码 | ⚠️ 仍为死代码（与上轮一致） | 全仓搜索无外部调用方 |
| TDD RED 测试可写性 | ❌ 当前测试设施不支持真 RED | 缺 Robolectric / `androidx.test:core`；两个目标类都强依赖 Context |
| 主人拍板（A/B/C）| ⏸️ 未到本轮决定 | 上轮已上报主控，本轮维持原状态 |

## 二、本轮做了什么（不冲突原则）

1. **复用既有冒烟脚本**：`MOCK_PORT=18511 USE_MOCK_BACKEND=1 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh .planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch`
   - 输出：`smoke_run.log`、`mock_server.log`、`responses/*.json|.code`、网络断连原始输出
   - 7/7 步骤全 PASS，与上轮一致
   - **不修改** `dyq3-endcloud-smoke.sh` 本身，仅复用

2. **静态二次审计**（与上轮一致，无新增变化）：
   - `grep -rn "OfflineFallbackManager" --include="*.kt" app/src/main/java` → 唯一引用 = 文件本身
   - `grep -rn "enterOfflineMode\|exitOfflineMode\|setLocalModelAvailable\|executeOfflineTask" --include="*.kt" app/src/main/java` → 同上

3. **测试基础设施评估**（**本轮新增**）：
   - `app/build.gradle.kts` 行 170-171：`testImplementation(libs.junit)` + `testImplementation(libs.kotlinx.coroutines.test)`
   - `gradle/libs.versions.toml` 行 32-33：`androidx-junit` (androidTest)、`androidx-espresso-core` (androidTest) — **没有 `androidx.test:core` 或 Robolectric**
   - 结论：当前 `app/src/test/` 路径是纯 JVM 单元测试，**无法访问 Android `Context`**

4. **TDD RED 路径分析**（**本轮新增**）：
   - 目标：写"心跳连续失败 N 次 → OfflineFallbackManager.isOfflineMode=true"的 RED 测试
   - 障碍 1：`OfflineFallbackManager` 是 `private constructor(Context)` + singleton → 必须有 Context 才能 `getInstance(context)`
   - 障碍 2：`CloudHeartbeatManager` 同上 + `private constructor(Context)`
   - 障碍 3：缺 Robolectric / `androidx.test:core` → 即使手动 `Mockito.mock(Context.class)` 也无法支持 `XLog.i(...)` 内部对 `Context` 的真实依赖
   - **结论**：在不动架构、不引入新依赖（主人红线）的前提下，**写不出真 RED 测试**。这本身就是 P1.5 的"接活成本"的一部分

5. **未触碰生产代码、未触碰他人在改的文件、未引入新依赖**

## 三、给主控/主人的 A/B/C 拍板材料（**本轮决策权上交**）

按上轮 worker 的交接，本轮把三个方案的精确 TDD RED 测试代码草案写在这里，由主控/主人拍板后由下一棒 worker 执行。

### 方案 A：接活（保守）

**最小生产代码改动**：

1. `CloudHeartbeatManager.recordHeartbeatFailure()` 在 `currentStatus = OFFLINE` 后追加：
   ```kotlin
   // 行 215 之后
   OfflineFallbackManager.getInstance(context).enterOfflineMode()
   ```
2. `CloudHeartbeatManager.recordHeartbeatSuccess()` 在 `currentStatus = ONLINE` 后追加：
   ```kotlin
   // 行 205 之后
   if (consecutiveFailures > 0) OfflineFallbackManager.getInstance(context).exitOfflineMode()
   ```
3. `CloudNodeOrchestrator` 检测到 `NETWORK_UNAVAILABLE` 时路由到 `OfflineFallbackManager.executeOfflineTask(command)`（**这里要拍板：是否在端云回传结果中区分"本地降级 vs 云端失败"？影响管理台 UI**）

**TDD RED 测试草案**（需先加 Robolectric 依赖）：

```kotlin
// app/src/test/java/io/agents/pokeclaw/cloud/OfflineFallbackIntegrationTest.kt
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineFallbackIntegrationTest {

    @Before
    fun setup() { XLog.setTestMode(true) }

    @Test
    fun `心跳连续失败N次后OfflineFallbackManager切到离线模式`() {
        val mgr = CloudHeartbeatManager.getInstance(ApplicationProvider.getApplicationContext())
        repeat(CloudHeartbeatManager.MAX_CONSECUTIVE_FAILURES) { mgr.recordHeartbeatFailure() }
        val off = OfflineFallbackManager.getInstance(ApplicationProvider.getApplicationContext())
        assertTrue(off.isOfflineMode.value)
    }
}
```

**成本**：
- 引入 `org.robolectric:robolectric:4.13` + `androidx.test:core-ktx:1.5.0` 测试依赖
- 改 2 个生产方法（< 10 行）
- 写 1 个测试类（30 行）

**风险**：
- 主人红线"禁止引入新依赖" — **需主人明确豁免**
- `executeOfflineTask` 内部还是占位实现（Gemma 4 未接入），所以"切到离线模式"也跑不出真实结果，仅是状态机接通

### 方案 B：删除（激进）

**删除文件**：`app/src/main/java/io/agents/pokeclaw/cloud/OfflineFallbackManager.kt`

**TDD RED 测试草案**：
- 删完后写一个 `OfflineFallbackManagerRemovalTest`：`assertFailsWith<ClassNotFoundException> { Class.forName("io.agents.pokeclaw.cloud.OfflineFallbackManager") }`
- 但这违反"测试先于代码删除"的 TDD 流程，所以应该先写 **保留测试**：`assertTrue(Class.forName("io.agents.pokeclaw.cloud.OfflineFallbackManager") != null)` → 看到 GREEN → 删文件 → 测试变 RED → 修测试（或确认删除意图）

**成本**：
- 删 1 个文件（110 行）
- 改 0 个生产方法
- 写 1 个测试类（10 行）

**风险**：
- P1.5 描述里有"弱网时使用本地模型执行"，删了就**彻底放弃该验收点**，需要改 BACKLOG / README

### 方案 C：保留并标注（折中）

**最小生产代码改动**：
1. `OfflineFallbackManager` 改为 `object`（不需要 Context），或加 `@Deprecated("P1.5 后处理：未接通")`
2. 加单元测试覆盖状态机本身

**TDD RED 测试草案**（**此路径唯一不需要 Robolectric**）：

```kotlin
// app/src/test/java/io/agents/pokeclaw/cloud/OfflineFallbackManagerStateTest.kt
package io.agents.pokeclaw.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

class OfflineFallbackManagerStateTest {
    /** 反射绕过 private constructor + singleton */
    private fun newInstance(): OfflineFallbackManager {
        val ctor = OfflineFallbackManager::class.java.getDeclaredConstructor(android.content.Context::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(null)  // Context=null，因为 OfflineFallbackManager 不使用它
    }

    @Test
    fun `进入离线模式后isOfflineMode变true`() {
        val m = newInstance()
        m.enterOfflineMode()
        assertTrue(m.isOfflineMode.value)
    }

    @Test
    fun `退出离线模式后isOfflineMode变false`() {
        val m = newInstance()
        m.enterOfflineMode()
        m.exitOfflineMode()
        assertFalse(m.isOfflineMode.value)
    }

    @Test
    fun `本地模型未启用时canUseLocalModel为false`() {
        val m = newInstance()
        m.enterOfflineMode()  // 仅切到离线，未启用本地模型
        assertFalse(m.canUseLocalModel())
    }
}
```

**关键发现（重要纠偏）**：

⚠️ **上述方案 C 的测试草案写错了**。让我诚实修正：

- `OfflineFallbackManager.enterOfflineMode()` 内部仅 `_isOfflineMode.value = true` + `XLog.i(...)`，**无任何 Context 依赖**
- 用反射 `newInstance(null)`（Context 传 null）**能**成功构造（因为构造器不实际用 Context）
- 状态机本身**当前是 GREEN 的**（断言 `assertTrue(m.isOfflineMode.value)` 立刻通过）

**这违反 TDD 核心"先 RED 后 GREEN"原则**。如果写这个测试，第一秒就 GREEN，说明它测的是已存在行为，不是缺失功能。**这不是合规的 TDD 测试**。

### TDD 死结复盘

| 想要的 RED 测试 | 能否写出 | 为什么 |
|---|---|---|
| 心跳失败 N 次 → `OfflineFallbackManager.isOfflineMode=true` | ❌ 需要 Robolectric + Context | 两个目标类都强依赖 Context；缺 Robolectric 依赖；引入新依赖违反红线 |
| 状态机 `enter/exit` 切换 | ✅ 能写（反射） | **但当前 GREEN**，违反 RED-first |
| `setLocalModelAvailable(true)` 后 `canUseLocalModel`=true | ✅ 能写（反射） | **但当前 GREEN**，违反 RED-first |
| `OfflineFallbackManager` 被生产代码调用 | ❌ 静态调用图扫描已证伪 | 全仓 0 外部调用方 |

**本轮的真实结论**：在不引入新测试依赖 + 不动生产代码 + 不改架构的前提下，**P1.5 没有"自然"的 RED 测试可写**。要么引入依赖（方案 A 路径），要么改架构（删/重构，方案 B/C 路径），要么承认 P1.5 当前是"实现存在但未接通"的纯产品决策而非工程问题。

**这一发现本身是 P1.5 给主控的关键输入**。

## 四、残留风险

1. **P1.5 验收仍无 TDD 闭环** — 上轮已说，本轮确认。不解决 A/B/C，P1.5 在本仓无法自证已交付。
2. **后端 48080 仍 code=500**（上轮 P0 阻塞）— 本轮不属端侧范围，但端云冒烟只能用 mock 跑，与 P1.5 真验收"端到端弱网"无关
3. **adb 真机空** — `adb devices -l` 仍无设备，所有 E2E 都跑 mock；这是 P1.1 ReDroid 容器阻塞的下游影响
4. **Gemma 4 模型未接入** — 即使 A 方案接通状态机，`executeOfflineTask` 内部还是占位 TODO；离线降级"能跑通流程但跑不出真实结果"
5. **本轮未引入 Robolectric** — 任何 RED 测试都需要主控先请主人豁免"禁止引入新依赖"红线

## 五、给下一棒 worker 的最小行动清单

按主人"主控不空巡检、每轮至少产生一个推进动作"红线，下一棒 worker（端侧 cron，10 分钟后）建议：

1. **如果主控/主人已给出 A/B/C 拍板**：直接按本报告第三章节的代码草案执行，提交时严格遵守 `git status` + `git diff --check` 保护
2. **如果尚未拍板**：本轮报告 + 上轮报告已两次上报，主控应升级到 L3 阻塞；worker 不再追加新报告，**只跑既有冒烟 + 静态扫描**作"维持性验证"
3. **任何情况下**：都不要动 `CloudNodeOrchestrator.kt`（他人在改）、`QA_CHECKLIST.md`（主人红线）、`scripts/dyq3-endcloud-smoke.sh`（既有冒烟脚本）
4. **若选 A 接活**：先写 Robolectric RED 测试 → 看到 RED → 改 2 个生产方法 → 看到 GREEN → commit。**不要跳过 RED 步骤直接改生产代码**

## 六、证据路径

- 本审计报告：`.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/report.md`
- 端云冒烟证据（**本轮重跑**）：
  - `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/summary.md`
  - `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/smoke_run.log`
  - `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/mock_server.log`
  - `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/responses/*.json|.code|.err`
- 上轮审计：`.planning/audit/runs/20260606-1508-pokeclaw-p15-audit/report.md`（OfflineFallbackManager 死代码首次发现）
- 静态审计命令（主人可重跑）：
  - `grep -rn "OfflineFallbackManager" --include="*.kt" app/src/main/java`
  - `grep -rn "enterOfflineMode\|exitOfflineMode\|setLocalModelAvailable\|executeOfflineTask" --include="*.kt" app/src/main/java`
  - `grep -n "testImplementation\|androidTestImplementation" app/build.gradle.kts`

## 七、本轮改动文件清单

| 改动 | 文件 | 大小 | 内容 |
|---|---|---|---|
| 新增 | `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/report.md` | ~10KB | 本审计 + A/B/C 拍板材料 + TDD 死结复盘 |
| 新增 | `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/smoke_run.log` | 1.5KB | 端云冒烟日志（mock 模式，7/7 PASS）|
| 新增 | `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/summary.md` | 0.8KB | 冒烟概要 |
| 新增 | `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/mock_server.log` | - | mock 后端日志 |
| 新增 | `.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/responses/*` | - | 注册/心跳/拉任务/回传/异常业务码原始响应 |

**本轮没有改动任何生产代码、测试代码、构建配置、OpenAPI、文档根目录、他人未提交文件。**