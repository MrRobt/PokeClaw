# 细方向六：实施路线、文件落点、验收标准

> 文档编号：PC-RELIABILITY-006  
> 目标：把可靠执行体系拆成可实施阶段，后续可直接派任务开发  

---

## 一、实施总原则

1. 不直接大改现有架构。
2. 不把可靠性逻辑塞进界面层。
3. 第一阶段只包裹关键路径，第二阶段再抽象完整内核。
4. 每个阶段都必须有真实手机端到端验收。
5. 每次代码改动都更新 `QA_CHECKLIST.md`。

---

## 二、阶段一：动作协议与执行轨迹

### 目标

先让每次模型动作变得可校验、可记录、可复盘。

### 建议新增文件

```text
app/src/main/java/io/agents/pokeclaw/reliability/action/ReliableAction.kt
app/src/main/java/io/agents/pokeclaw/reliability/action/ReliableActionResult.kt
app/src/main/java/io/agents/pokeclaw/reliability/action/ActionValidator.kt
app/src/main/java/io/agents/pokeclaw/reliability/trace/ExecutionTrace.kt
```

### 关键改造

- 扩展 `ToolResult.kt` 的错误分类能力。
- 在 `ToolRegistry.kt` 外层增加动作校验入口。
- 日志记录每个动作的校验和执行结果。

### 验收

- 非法动作不会触发真实工具执行。
- 工具失败能归类为标准错误类型。
- 一次任务结束后能看到完整动作轨迹。

---

## 三、阶段二：任务运行器和步骤约束器

### 目标

把任务控制流从模型上下文中抽出来。

### 建议新增文件

```text
app/src/main/java/io/agents/pokeclaw/reliability/runner/ReliableTaskRunner.kt
app/src/main/java/io/agents/pokeclaw/reliability/runner/ReliableTaskState.kt
app/src/main/java/io/agents/pokeclaw/reliability/step/TaskStepPlan.kt
app/src/main/java/io/agents/pokeclaw/reliability/step/StepEnforcer.kt
app/src/main/java/io/agents/pokeclaw/reliability/error/ErrorTracker.kt
```

### 关键改造

- `TaskFlowController.kt` 改为调用 `ReliableTaskRunner`。
- `CloudTaskExecutorBridge.kt` 云端任务也进入同一个运行器。
- `ActiveTaskShellController.kt` 展示运行器状态。

### 验收

- 模型提前输出完成时会被拦截。
- 必要步骤未完成时不能进入成功状态。
- 同一步骤失败超过阈值后进入降级或上报。

---

## 四、阶段三：页面观察与验收器

### 目标

让执行结果依赖真实页面证据，而不是模型自称成功。

### 建议新增文件

```text
app/src/main/java/io/agents/pokeclaw/reliability/observe/PageObserver.kt
app/src/main/java/io/agents/pokeclaw/reliability/observe/PageObservation.kt
app/src/main/java/io/agents/pokeclaw/reliability/verify/TaskVerifier.kt
app/src/main/java/io/agents/pokeclaw/reliability/verify/VerificationRule.kt
```

### 关键改造

- 观察当前前台应用、可见文本、可点击目标、输入状态。
- 动作前后生成页面摘要。
- 任务完成必须通过验收器。

### 验收

- 点击工具返回成功但页面无变化时，任务不会误判成功。
- 消息发送类任务必须看到消息出现在聊天页才算成功。
- 页面观察失败时有明确错误类型。

---

## 五、阶段四：证据包和云端回执

### 目标

让端侧执行结果可以被云端复盘和优化。

### 建议新增文件

```text
app/src/main/java/io/agents/pokeclaw/reliability/evidence/EvidenceBundle.kt
app/src/main/java/io/agents/pokeclaw/reliability/evidence/EvidenceCollector.kt
app/src/main/java/io/agents/pokeclaw/reliability/report/ExecutionReporter.kt
app/src/main/java/io/agents/pokeclaw/reliability/report/ReliabilityMetrics.kt
```

### 关键改造

- `CloudNodeOrchestrator.kt` 上报可靠执行摘要。
- `CloudEventQueue.kt` 支持失败证据离线补报。
- `DebugReportManager.kt` 复用为证据聚合基础。

### 验收

- 失败任务可查看截图、控件树、动作轨迹。
- 云端回执包含失败步骤和错误类型。
- 离线失败证据可在网络恢复后补报。

---

## 六、阶段五：经验记忆和策略优化

### 目标

让端侧小龙虾越用越稳。

### 建议新增文件

```text
app/src/main/java/io/agents/pokeclaw/reliability/memory/ExecutionMemoryStore.kt
app/src/main/java/io/agents/pokeclaw/reliability/memory/SuccessPattern.kt
app/src/main/java/io/agents/pokeclaw/reliability/memory/FailurePattern.kt
app/src/main/java/io/agents/pokeclaw/reliability/policy/RetryPolicy.kt
app/src/main/java/io/agents/pokeclaw/reliability/policy/DegradePolicy.kt
```

### 关键改造

- 成功任务沉淀成功路径。
- 失败任务沉淀失败规避策略。
- 同类任务启动时加载最近经验。

### 验收

- 同类任务第二次执行能复用成功路径。
- 同类失败不会重复走完全相同错误动作。
- 经验命中率和成功率能被统计。

---

## 七、端到端验收场景

第一批建议加入 `QA_CHECKLIST.md`：

```text
R1. 结构化动作非法格式拦截：下发非法动作 → 不执行真实工具 → 返回 INVALID_ACTION。
R2. 微信消息发送成功闭环：打开微信 → 搜索联系人 → 输入消息 → 发送 → 截图验证。
R3. 联系人不存在失败闭环：搜索不存在联系人 → 三次以内停止 → 上报 TARGET_NOT_FOUND。
R4. 提前完成拦截：模型在未发送前输出 FINISH_TASK → 步骤约束器拒绝。
R5. 工具假成功拦截：点击返回成功但页面无变化 → 验收器拒绝完成。
R6. 离线回执补报：执行后断网 → 证据入队 → 恢复网络后补报。
```

---

## 八、两周实施建议

| 天数 | 目标 | 产出 |
|---:|---|---|
| 第 1-2 天 | 动作协议与错误类型 | `ReliableAction`、`ReliableActionResult`、基础校验 |
| 第 3-4 天 | 执行轨迹 | `ExecutionTrace`、日志、失败分类 |
| 第 5-7 天 | 任务运行器最小闭环 | `ReliableTaskRunner`、状态机、最大轮次 |
| 第 8-9 天 | 步骤约束和早停拦截 | `StepEnforcer`、必要步骤计划 |
| 第 10-11 天 | 页面观察和验收器 | `PageObserver`、`TaskVerifier` |
| 第 12-13 天 | 证据包和云端回执 | `EvidenceBundle`、`ExecutionReporter` |
| 第 14 天 | 真实手机回归 | 跑 R1-R6，输出成功率报告 |

---

## 九、待决策项

1. 第一批真实手机任务是否只选微信消息发送，还是同时覆盖打开应用和读取通知。
2. 证据截图是否默认上传云端，还是失败时才上传。
3. 本地模型连续输出非法动作后，是切云端模型还是直接失败。
4. 可靠执行内核是否作为所有任务唯一入口，还是先只接云端任务。
5. 经验记忆先存在本地，还是第一期就和云端经验库同步。

---

## 十、实施前检查清单

- 确认目标分支。
- 确认真实测试机和目标应用版本。
- 确认无障碍权限、通知权限、前台服务权限可用。
- 确认云端回执接口字段兼容方案。
- 确认 `QA_CHECKLIST.md` 新增可靠执行专项。
- 确认每个阶段完成后都有真实手机证据。
