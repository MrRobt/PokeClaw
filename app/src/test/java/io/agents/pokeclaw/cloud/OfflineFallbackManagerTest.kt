// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// P0 #2: OfflineFallbackManager 单测 — 状态机、模型可用性、离线任务执行语义。
// 注意：当前模型执行是占位实现（TODO 接入本地 Gemma 4），测试断言占位行为。

package io.agents.pokeclaw.cloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OfflineFallbackManager 状态机 + 离线任务执行单测。
 *
 * 覆盖：
 * - 初始状态（isOfflineMode=false, isLocalModelAvailable=false）
 * - enterOfflineMode / exitOfflineMode 状态切换
 * - canUseLocalModel 仅在两个状态都为 true 时为 true
 * - setLocalModelAvailable 单独不影响 isOfflineMode
 * - executeOfflineTask 在本地模型不可用时返回 failure
 * - executeOfflineTask 在本地模型可用时返回 success 占位
 * - 占位文案使用正确型号 "Gemma 4"（回归保护，避免再写错 "Gemmma"）
 */
class OfflineFallbackManagerTest {

    /**
     * 注入私有构造的 OfflineFallbackManager，绕过单例与 Context（无需 Android）。
     * 利用 Kotlin 的反射 setAccessible 突破 private constructor 限制。
     */
    private fun newManager(): OfflineFallbackManager {
        val ctor = OfflineFallbackManager::class.java.getDeclaredConstructor(android.content.Context::class.java)
        ctor.isAccessible = true
        // 传入 null — Manager 内部未使用 context（state flow only），JVM 测试安全。
        return ctor.newInstance(null)
    }

    @Test
    fun `initial state - isOfflineMode false and isLocalModelAvailable false`() {
        val m = newManager()
        assertFalse("默认应不处于离线模式", m.isOfflineMode.value)
        assertFalse("默认本地模型不可用", m.isLocalModelAvailable.value)
        assertFalse("默认 canUseLocalModel=false", m.canUseLocalModel())
    }

    @Test
    fun `enterOfflineMode - 切换 isOfflineMode 为 true`() {
        val m = newManager()
        m.enterOfflineMode()
        assertTrue(m.isOfflineMode.value)
        assertFalse("仅 enterOfflineMode 不足以让 canUseLocalModel=true（模型未标记可用）", m.canUseLocalModel())
    }

    @Test
    fun `exitOfflineMode - 切回 false`() {
        val m = newManager().apply { enterOfflineMode() }
        m.exitOfflineMode()
        assertFalse(m.isOfflineMode.value)
    }

    @Test
    fun `setLocalModelAvailable - 不影响 isOfflineMode`() {
        val m = newManager()
        m.setLocalModelAvailable(true)
        assertTrue(m.isLocalModelAvailable.value)
        assertFalse("setLocalModelAvailable 不应自动进入离线模式", m.isOfflineMode.value)
        assertFalse("需要 enterOfflineMode + setLocalModelAvailable(true) 两个条件都满足", m.canUseLocalModel())
    }

    @Test
    fun `canUseLocalModel - 仅在 offline AND model available 时为 true`() {
        val m = newManager()
        // 仅 offline
        m.enterOfflineMode()
        assertFalse(m.canUseLocalModel())
        // offline + model available
        m.setLocalModelAvailable(true)
        assertTrue(m.canUseLocalModel())
        // 仅 model available
        m.exitOfflineMode()
        assertFalse(m.canUseLocalModel())
    }

    @Test
    fun `executeOfflineTask 本地模型不可用 - 返回 failure 含明确 error`() = runTest {
        val m = newManager()  // 默认 model available=false
        val r = m.executeOfflineTask("open settings")
        assertFalse("本地模型不可用时 executeOfflineTask 应失败", r.success)
        assertNull(r.result)
        assertNotNull("失败时应带 error 文案", r.error)
        assertTrue(
            "error 文案应提示本地模型不可用，实际：${r.error}",
            r.error!!.contains("本地模型不可用"),
        )
    }

    @Test
    fun `executeOfflineTask 模型可用 - 返回 success 占位且文案不含拼写错误`() = runTest {
        val m = newManager().apply {
            enterOfflineMode()
            setLocalModelAvailable(true)
        }
        val r = m.executeOfflineTask("send hello")
        assertTrue("本地模型可用时 executeOfflineTask 应成功（占位实现）", r.success)
        assertNull(r.error)
        assertNotNull(r.result)
        // 回归保护：占位文案应使用正确型号名 "Gemma 4"，避免再写错 "Gemmma"
        assertFalse(
            "占位文案不应包含拼写错误 Gemmma，实际：${r.result}",
            r.result!!.contains("Gemmma"),
        )
        assertTrue(
            "占位文案应包含型号 Gemma 4，实际：${r.result}",
            r.result!!.contains("Gemma 4"),
        )
    }

    @Test
    fun `executeOfflineTask offline mode false 即便 model available 也失败`() = runTest {
        val m = newManager().apply { setLocalModelAvailable(true) }
        // 没调 enterOfflineMode，所以 canUseLocalModel=false
        val r = m.executeOfflineTask("any command")
        assertFalse(r.success)
    }

    @Test
    fun `isOfflineMode is StateFlow - 可观察 多次状态变更`() {
        val m = newManager()
        val values = mutableListOf<Boolean>()
        // 简单轮询记录（避免引入 Turbine 等额外依赖）
        values.add(m.isOfflineMode.value)
        m.enterOfflineMode()
        values.add(m.isOfflineMode.value)
        m.exitOfflineMode()
        values.add(m.isOfflineMode.value)
        m.enterOfflineMode()
        values.add(m.isOfflineMode.value)
        assertEquals(listOf(false, true, false, true), values)
    }

    // --- 扩展覆盖 ---

    @Test
    fun `isLocalModelAvailable is StateFlow - 可观察 多次状态变更`() {
        val m = newManager()
        val values = mutableListOf<Boolean>()
        values.add(m.isLocalModelAvailable.value)
        m.setLocalModelAvailable(true)
        values.add(m.isLocalModelAvailable.value)
        m.setLocalModelAvailable(false)
        values.add(m.isLocalModelAvailable.value)
        assertEquals(listOf(false, true, false), values)
    }

    @Test
    fun `setLocalModelAvailable true 之后 再 false canUseLocalModel 变回 false`() {
        val m = newManager().apply {
            enterOfflineMode()
            setLocalModelAvailable(true)
        }
        assertTrue(m.canUseLocalModel())
        m.setLocalModelAvailable(false)
        assertFalse(m.canUseLocalModel())
        assertFalse(m.isLocalModelAvailable.value)
        // isOfflineMode 不受影响
        assertTrue(m.isOfflineMode.value)
    }

    @Test
    fun `enterOfflineMode 重复调用是幂等的`() {
        val m = newManager()
        m.enterOfflineMode()
        assertTrue(m.isOfflineMode.value)
        m.enterOfflineMode()
        assertTrue(m.isOfflineMode.value)
    }

    @Test
    fun `exitOfflineMode 重复调用是幂等的`() {
        val m = newManager()
        m.exitOfflineMode()
        assertFalse(m.isOfflineMode.value)
        m.exitOfflineMode()
        assertFalse(m.isOfflineMode.value)
    }

    @Test
    fun `setLocalModelAvailable 重复相同值不重复改变 StateFlow 当前值`() {
        val m = newManager()
        m.setLocalModelAvailable(true)
        assertTrue(m.isLocalModelAvailable.value)
        // 第二次赋相同值，StateFlow 仍保持 true
        m.setLocalModelAvailable(true)
        assertTrue(m.isLocalModelAvailable.value)
    }

    @Test
    fun `OfflineTaskResult data class equality 与 copy`() {
        val a = OfflineFallbackManager.OfflineTaskResult(success = true, result = "x", error = null)
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val c = a.copy(success = false, error = "err", result = null)
        assertFalse("copy 改 success 后不相等", a == c)
        assertEquals("err", c.error)
        assertNull(c.result)
    }

    @Test
    fun `OfflineTaskResult 默认值 success=false result=null error=null`() {
        // 数据类无法无参构造（除全部参数都有默认）— 验证直接构造即可
        val r = OfflineFallbackManager.OfflineTaskResult(success = false, result = null, error = null)
        assertFalse(r.success)
        assertNull(r.result)
        assertNull(r.error)
    }

    @Test
    fun `executeOfflineTask 连续多次调用 状态一致`() = runTest {
        // 连续 5 次成功调用都返回 success（占位实现无副作用）
        val m = newManager().apply {
            enterOfflineMode()
            setLocalModelAvailable(true)
        }
        repeat(5) {
            val r = m.executeOfflineTask("task-$it")
            assertTrue("第 $it 次应成功", r.success)
            assertNotNull(r.result)
        }
    }

    @Test
    fun `exitOfflineMode 之后 executeOfflineTask 失败`() = runTest {
        val m = newManager().apply {
            enterOfflineMode()
            setLocalModelAvailable(true)
        }
        // 先成功一次
        val first = m.executeOfflineTask("ok")
        assertTrue(first.success)
        // 退出离线模式后再调 — canUseLocalModel=false → failure
        m.exitOfflineMode()
        val second = m.executeOfflineTask("after-exit")
        assertFalse(second.success)
        assertNotNull(second.error)
    }

    @Test
    fun `enterOfflineMode 后 setLocalModelAvailable false executeOfflineTask 返回正确 error 文案`() = runTest {
        val m = newManager().apply { enterOfflineMode() }
        val r = m.executeOfflineTask("x")
        assertFalse(r.success)
        assertEquals("本地模型不可用，无法执行离线任务", r.error)
    }

    @Test
    fun `canUseLocalModel 状态组合真值表`() {
        val m = newManager()
        // 初始：false / false → false
        assertFalse(m.canUseLocalModel())
        m.enterOfflineMode()
        // true / false → false
        assertFalse(m.canUseLocalModel())
        m.setLocalModelAvailable(true)
        // true / true → true
        assertTrue(m.canUseLocalModel())
        m.exitOfflineMode()
        // false / true → false
        assertFalse(m.canUseLocalModel())
    }
}
