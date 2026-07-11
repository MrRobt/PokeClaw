// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// AI Employee Market Activity (US-D-039-EXT)
// 降级版本：dyq 端 app-side endpoint 尚未提供时使用 hardcoded SEED 列表
// (BACKLOG P1 — 等待 dyq ClawAppAiEmployeeController 上线后切换到真实 API)

package io.agents.pokeclaw.ui.employee

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.agents.pokeclaw.R
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AI Employee ViewModel
 *
 * Fix code-review M3：通过 [EmployeeRepository] 抽象解耦数据源。
 * 当前注入 [SeedEmployeeRepository]；dyq endpoint 上线后切到 [RemoteEmployeeRepository] 即可。
 */
class AiEmployeeViewModel(
    private val repository: EmployeeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState(emptyList()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadEmployees()
    }

    private fun loadEmployees() {
        viewModelScope.launch {
            val list = try {
                repository.list()
            } catch (e: Exception) {
                XLog.e("AiEmployeeViewModel", "loadEmployees: failed", e)
                emptyList()
            }
            _uiState.value = UiState(list)
        }
    }

    data class UiState(val employees: List<AiEmployee>)

    class Factory(
        private val repository: EmployeeRepository = SeedEmployeeRepository(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AiEmployeeViewModel::class.java))
            return AiEmployeeViewModel(repository) as T
        }
    }
}

/**
 * AI Employee Market Activity (US-D-039-EXT)
 *
 * 入口：Settings → AI Employee
 *
 * 当前降级实现：内部 hardcoded 3 个示范 employee（与 dyq V20260710__claw_ai_employee_seed.sql seed 数据一致）。
 * 当 dyq ClawAppAiEmployeeController 上线后：
 *  1. 在 Factory 里改 `SeedEmployeeRepository()` 为 `RemoteEmployeeRepository(client)`
 *  2. 雇佣按钮调用 POST /app-api/claw/app/ai-employee/employment
 */
class AiEmployeeActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "AiEmployeeActivity"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: EmployeeAdapter
    private lateinit var viewModel: AiEmployeeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_employee)
        title = getString(R.string.settings_ai_employee_title)

        viewModel = ViewModelProvider(this, AiEmployeeViewModel.Factory())[AiEmployeeViewModel::class.java]

        listView = findViewById(R.id.lv_ai_employee)
        adapter = EmployeeAdapter(this, emptyList()) { employee ->
            // TODO: 等待 dyq 端 ClawAppAiEmployeeController 上线后改为真实雇佣请求
            Toast.makeText(
                this,
                "「${employee.name}」雇佣功能即将上线，敬请期待",
                Toast.LENGTH_SHORT,
            ).show()
            XLog.i(TAG, "employee hire tap: id=${employee.id} name=${employee.name} (降级 UI)")
        }
        listView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.replaceAll(state.employees)
                }
            }
        }
    }

    private class EmployeeAdapter(
        activity: Activity,
        initial: List<AiEmployee>,
        private val onHireClick: (AiEmployee) -> Unit,
    ) : ArrayAdapter<AiEmployee>(
        activity,
        R.layout.item_ai_employee_row,
        R.id.tv_employee_title,
        initial,
    ) {
        fun replaceAll(items: List<AiEmployee>) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val item = getItem(position) ?: return view
            view.findViewById<TextView>(R.id.tv_employee_title).text =
                "${item.name}  ·  ${item.category}  ·  ${item.creditCost} 积分"
            view.findViewById<TextView>(R.id.tv_employee_subtitle).text = item.shortDescription
            view.findViewById<Button>(R.id.btn_employee_hire).setOnClickListener {
                onHireClick(item)
            }
            return view
        }
    }
}
