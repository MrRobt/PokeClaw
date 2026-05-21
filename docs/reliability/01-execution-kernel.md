# 细方向一：可靠执行内核与任务运行器设计

> 文档编号：PC-RELIABILITY-001  
> 目标：把端侧任务执行从“模型自由循环”升级为“运行器受控循环”  

---

## 一、问题定义

当前端侧小龙虾容易出现以下问题：

- 模型执行到一半忘记原始目标。
- 工具执行失败后继续按错误状态推进。
- 没有完成验证就直接说完成。
- 多次失败后仍然重复同一个动作。
- 云端下发任务、本地聊天任务、快捷任务之间状态口径不统一。

这些问题的根源是：**控制流没有被稳定地从模型上下文中抽出来。**

---

## 二、设计原则

1. 任务状态由运行器维护，不由模型记忆。
2. 模型每轮只产出一个或一组候选动作。
3. 所有动作必须经过校验才允许执行。
4. 每次执行必须产生标准结果。
5. 成功必须经过验收器确认。
6. 失败必须进入错误预算，不允许无限循环。

---

## 三、核心对象

建议新增包：

```text
app/src/main/java/io/agents/pokeclaw/reliability/
```

建议对象：

```text
ReliableTaskRunner          可靠任务运行器
ReliableTaskState           任务状态
ReliableTaskPlan            任务计划
ReliableTaskStep            任务步骤
ReliableAction              结构化动作
ReliableActionResult        动作执行结果
ReliableExecutionTrace      执行轨迹
ReliableTaskPolicy          任务策略
```

---

## 四、任务状态机

```text
CREATED       已创建
PLANNING      正在生成计划
OBSERVING     正在观察页面
THINKING      正在请求模型产出动作
VALIDATING    正在校验动作
EXECUTING     正在执行动作
VERIFYING     正在验收结果
RETRYING      正在纠偏重试
COMPLETED     已成功
FAILED        已失败
ESCALATED     已上报云端/请求人工或强模型协助
CANCELLED     已取消
```

状态转换必须由运行器集中管理，禁止散落在界面层、工具层、云端桥接层中。

---

## 五、运行循环

```text
启动任务
  ↓
生成任务计划
  ↓
进入循环：
  1. 观察当前页面
  2. 构造模型输入
  3. 模型输出结构化动作
  4. 动作校验
  5. 执行动作
  6. 记录轨迹
  7. 验收是否完成
  8. 未完成则进入下一轮
  9. 失败则纠偏/降级/上报
```

伪代码：

```kotlin
class ReliableTaskRunner(
    private val observer: PageObserver,
    private val planner: TaskPlanner,
    private val model: ActionModelClient,
    private val validator: ActionValidator,
    private val executor: ActionExecutor,
    private val verifier: TaskVerifier,
    private val errorTracker: ErrorTracker,
    private val reporter: ExecutionReporter,
) {
    suspend fun run(request: ReliableTaskRequest): ReliableTaskResult {
        val state = ReliableTaskState.create(request)
        val plan = planner.plan(request)
        while (!state.isTerminal()) {
            val observation = observer.observe()
            val action = model.nextAction(state, plan, observation)
            val validation = validator.validate(action, state, plan)
            if (!validation.accepted) {
                errorTracker.recordModelError(validation.reason)
                continue
            }
            val result = executor.execute(action)
            state.record(action, result)
            val verdict = verifier.verify(state, plan, observation, result)
            if (verdict.completed) return state.toSuccess(verdict)
            if (errorTracker.shouldEscalate()) return reporter.escalate(state)
        }
        return state.toFailure()
    }
}
```

---

## 六、与现有代码关系

| 现有模块 | 新关系 |
|---|---|
| `TaskFlowController.kt` | 只负责从聊天界面发起任务，不再承载复杂执行循环 |
| `CloudTaskExecutorBridge.kt` | 云端任务入口，调用 `ReliableTaskRunner` |
| `ToolRegistry.kt` | 作为动作执行器底层能力，不直接接受模型自由文本 |
| `ActiveTaskShellController.kt` | 展示运行器状态、进度、失败原因 |
| `CloudNodeOrchestrator.kt` | 负责把运行结果和证据上报云端 |

---

## 七、第一期最小任务类型

第一期先支持三类任务：

1. 打开应用类：打开微信、打开浏览器、打开设置。
2. 消息发送类：给联系人发送一条消息。
3. 信息读取类：读取当前通知、读取当前页面可见文本。

不要第一期就支持支付、删除、批量群发、复杂跨应用链路。

---

## 八、验收标准

- 运行器能为每个任务生成唯一任务编号。
- 每轮动作都有状态、输入摘要、动作、结果、错误分类。
- 任务终止状态只能是成功、失败、上报、取消之一。
- 任何工具失败都不会被当作成功继续推进。
- 连续失败达到上限后必须停止当前策略。
