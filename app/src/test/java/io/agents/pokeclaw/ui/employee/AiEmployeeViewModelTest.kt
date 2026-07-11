// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-039-EXT AI Employee Market ViewModel 单测

package io.agents.pokeclaw.ui.employee

import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiEmployeeViewModelTest {

    @Before
    fun setUp() {
        XLog.setTestMode(true)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default uiState contains seed employees`() {
        val vm = AiEmployeeViewModel(SeedEmployeeRepository())
        val employees = vm.uiState.value.employees
        assertEquals(3, employees.size)
        assertTrue(employees.any { it.id == "emp_cs_assistant" })
        assertTrue(employees.any { it.id == "emp_social" })
        assertTrue(employees.any { it.id == "emp_ecom" })
    }

    @Test
    fun `seed employees have positive credit cost`() {
        AiEmployeeSeed.EMPLOYEES.forEach { emp ->
            assertTrue("${emp.name} 应有正 cost", emp.creditCost > 0)
            assertTrue("${emp.name} 应有非空 id", emp.id.isNotBlank())
            assertTrue("${emp.name} 应有非空 name", emp.name.isNotBlank())
            assertTrue("${emp.name} 应有非空 category", emp.category.isNotBlank())
        }
    }

    @Test
    fun `seed employees are sorted by id stable`() {
        val ids = AiEmployeeSeed.EMPLOYEES.map { it.id }
        assertEquals(listOf("emp_cs_assistant", "emp_social", "emp_ecom"), ids)
    }

    @Test
    fun `factory creates AiEmployeeViewModel`() {
        val factory = AiEmployeeViewModel.Factory(SeedEmployeeRepository())
        val vm = factory.create(AiEmployeeViewModel::class.java)
        assertTrue(vm is AiEmployeeViewModel)
    }
}
