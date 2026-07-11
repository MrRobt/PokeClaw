// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-039 Skill Market ViewModel 单测

package io.agents.pokeclaw.ui.market

import io.agents.pokeclaw.cloud.lobster.api.LobsterSkillMarketplaceApi
import io.agents.pokeclaw.cloud.lobster.client.SkillMarketplaceClient
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.ArrayDeque

/**
 * SkillMarketViewModel 单测。
 *
 * <p>不引入 mockk / Mockito — 用 FakeApi 替换真实 Retrofit client。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillMarketViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        XLog.setTestMode(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init triggers loadSkills, transitions Loading then Loaded`() = runTest(testDispatcher) {
        val skill = ClawAppSkillMarketRespVO(
            skillId = "s1",
            skillName = "Search",
            description = "desc",
            vendor = "agents-io",
            installStatus = "NOT_INSTALLED",
            version = "1.0.0",
        )
        val api = FakeApi().apply {
            enqueueList { Response.success(CommonResult(code = 0, data = listOf(skill))) }
        }
        val vm = SkillMarketViewModel(SkillMarketplaceClient(api))

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Loaded, got $state", state is SkillMarketViewModel.UiState.Loaded)
        val loaded = state as SkillMarketViewModel.UiState.Loaded
        assertEquals(1, loaded.skills.size)
        assertEquals("s1", loaded.skills[0].skillId)
    }

    @Test
    fun `listSkills network failure transitions to Error`() = runTest(testDispatcher) {
        val api = FakeApi().apply {
            enqueueList { throw RuntimeException("connection refused") }
        }
        val vm = SkillMarketViewModel(SkillMarketplaceClient(api))

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error, got $state", state is SkillMarketViewModel.UiState.Error)
        assertTrue(
            "error message should mention network",
            (state as SkillMarketViewModel.UiState.Error).message.contains("网络")
        )
    }

    @Test
    fun `listSkills rejected by backend transitions to Error`() = runTest(testDispatcher) {
        val api = FakeApi().apply {
            enqueueList { Response.success(CommonResult(code = 999, msg = "biz failed", data = null)) }
        }
        val vm = SkillMarketViewModel(SkillMarketplaceClient(api))

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SkillMarketViewModel.UiState.Error)
        assertTrue((state as SkillMarketViewModel.UiState.Error).message.contains("biz failed"))
    }

    @Test
    fun `installSkill success emits Success event`() = runTest(testDispatcher) {
        val api = FakeApi().apply {
            enqueueList { Response.success(CommonResult(code = 0, data = emptyList<Any>())) }
            enqueueInstall { Response.success(CommonResult(code = 0, data = true)) }
        }
        val vm = SkillMarketViewModel(SkillMarketplaceClient(api))

        advanceUntilIdle()
        vm.installSkill("skill_xyz")
        advanceUntilIdle()

        val event = vm.installEvents.value
        assertNotNull(event)
        assertTrue(event is SkillMarketViewModel.InstallEvent.Success)
        assertEquals("skill_xyz", (event as SkillMarketViewModel.InstallEvent.Success).skillId)
    }

    @Test
    fun `installSkill false data emits Failed event`() = runTest(testDispatcher) {
        val api = FakeApi().apply {
            enqueueList { Response.success(CommonResult(code = 0, data = emptyList<Any>())) }
            enqueueInstall { Response.success(CommonResult(code = 0, data = false)) }
        }
        val vm = SkillMarketViewModel(SkillMarketplaceClient(api))

        advanceUntilIdle()
        vm.installSkill("skill_xyz")
        advanceUntilIdle()

        val event = vm.installEvents.value
        assertNotNull(event)
        assertTrue(event is SkillMarketViewModel.InstallEvent.Failed)
        assertEquals("后端拒绝", (event as SkillMarketViewModel.InstallEvent.Failed).reason)
    }

    @Test
    fun `consumeInstallEvent clears the event`() = runTest(testDispatcher) {
        val api = FakeApi().apply {
            enqueueList { Response.success(CommonResult(code = 0, data = emptyList<Any>())) }
            enqueueInstall { Response.success(CommonResult(code = 0, data = true)) }
        }
        val vm = SkillMarketViewModel(SkillMarketplaceClient(api))

        advanceUntilIdle()
        vm.installSkill("s1")
        advanceUntilIdle()
        assertNotNull(vm.installEvents.value)
        vm.consumeInstallEvent()
        assertNull(vm.installEvents.value)
    }

    @Test
    fun `loadSkills retry after error transitions Loading then Loaded`() = runTest(testDispatcher) {
        val skill = ClawAppSkillMarketRespVO(skillId = "s2", skillName = "n", description = "d", vendor = "v")
        val api = FakeApi().apply {
            enqueueList { throw RuntimeException("first attempt fails") }
        }
        val vm = SkillMarketViewModel(SkillMarketplaceClient(api))

        advanceUntilIdle()
        assertTrue(vm.uiState.value is SkillMarketViewModel.UiState.Error)

        // 重新 enqueue 成功响应
        api.enqueueList { Response.success(CommonResult(code = 0, data = listOf(skill))) }
        vm.loadSkills()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SkillMarketViewModel.UiState.Loaded)
        assertEquals(1, (state as SkillMarketViewModel.UiState.Loaded).skills.size)
    }

    // ── Fake API ──────────────────────────────────────────────────────────────

    private class FakeApi : LobsterSkillMarketplaceApi {
        private val listQueue = ArrayDeque<() -> Response<CommonResult>>()
        private val installQueue = ArrayDeque<() -> Response<CommonResult>>()
        private val saveQueue = ArrayDeque<() -> Response<CommonResult>>()
        private val removeQueue = ArrayDeque<() -> Response<CommonResult>>()
        private val batchQueue = ArrayDeque<() -> Response<CommonResult>>()

        fun enqueueList(block: () -> Response<CommonResult>) { listQueue.add(block) }
        fun enqueueInstall(block: () -> Response<CommonResult>) { installQueue.add(block) }
        fun enqueueSave(block: () -> Response<CommonResult>) { saveQueue.add(block) }
        fun enqueueRemove(block: () -> Response<CommonResult>) { removeQueue.add(block) }
        fun enqueueBatch(block: () -> Response<CommonResult>) { batchQueue.add(block) }

        override suspend fun listSkills(): Response<CommonResult> =
            listQueue.poll()?.invoke() ?: Response.success(CommonResult(code = 0, data = emptyList<Any>()))

        override suspend fun installSkill(skillId: Map<String, String>): Response<CommonResult> =
            // Fix code-review L4：Retrofit 端点要求 body 是 `{"skillId": "..."}` 单字段 map，
            // 不直接接 String。SkillMarketplaceClient.installSkill() 内部做 mapOf("skillId" to ...) 包装。
            // 这里签名匹配 api 接口；fake 不需要再包装。
            installQueue.poll()?.invoke() ?: Response.success(CommonResult(code = 0, data = false))

        override suspend fun saveSkill(req: io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillSaveReqVO): Response<CommonResult> =
            saveQueue.poll()?.invoke() ?: Response.success(CommonResult(code = 0, data = "ok"))

        override suspend fun removeSkill(id: String): Response<CommonResult> =
            removeQueue.poll()?.invoke() ?: Response.success(CommonResult(code = 0, data = false))

        override suspend fun batchUpdateStatus(req: io.agents.pokeclaw.cloud.lobster.model.BatchSkillStatusReqVO): Response<CommonResult> =
            batchQueue.poll()?.invoke() ?: Response.success(CommonResult(code = 0, data = false))
    }
}
