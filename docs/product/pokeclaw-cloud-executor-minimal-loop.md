# PokeClaw 安卓端侧执行节点最小闭环落地记录

日期：2026-05-16
负责人：安卓小龙
关联问题：CMP-2097
仓库：`/mnt/e/code/PokeClaw`
分支：`main`

## 一、前提假设核查

- 已通过纸夹接口查询当前成员 `ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0` 名下未完成问题，选择最高优先级任务 `CMP-2097`。
- 当前实际目录为 `/mnt/e/code/PokeClaw`，不是任务描述中的旧路径 `/data/code/PokeClaw`；本机记忆与当前仓库规则均指向 `/mnt/e/code/PokeClaw`。
- 当前分支为 `main`，远端为 `https://github.com/MrRobt/PokeClaw.git`。
- 已复核 `README.md` 的产品方向、路线图和平台约束：本次只补端侧执行节点契约和本地模拟闭环，不把 PokeClaw 改成云手机、电脑控制框架或后端中枢。
- 本轮开始前仓库已有未提交改动：`QA_CHECKLIST.md`、`SkillRegistry.kt`、`SkillStatsStore.kt`、`cloud/model/CloudModels.kt`、`cloudnode` 测试、部分产品文档。新增内容采用独立文件，避免覆盖既有工作。

## 二、两种方案取舍

### 方案一：直接在 `TaskOrchestrator` 中加入云端任务字段和上报逻辑

优点：接入点少，短期能快速把云端任务送进现有任务流。

缺点：会把云端协议、网络错误、设备身份、重试策略塞进任务编排核心，增加架构耦合；也会破坏现有本地优先、通用移动智能体底座的边界。

结论：不选。

### 方案二：新增独立 `cloudnode` 契约层，任务执行仍复用现有编排器

优点：云端任务模型、状态上报、错误分类和本地模拟都在独立包内；后续真实网络客户端、离线队列、设置页开关可以继续沿这个边界扩展；不会侵入 `TaskOrchestrator`。

缺点：第一轮只完成契约和模拟，不直接连接真实 dyq 后端。

结论：选择方案二。理由是风险更低、边界清晰、可测试，符合“不破坏现有端侧架构”的验收要求。

## 三、本轮实际落地内容

### 新增契约代码

文件：`app/src/main/java/io/agents/pokeclaw/cloudnode/CloudExecutorNodeContract.kt`

包含：

- `CloudExecutorTask`：云端下发任务模型，包含任务编号、设备编号、指令、追踪编号、下发时间和元数据。
- `CloudTaskStatus`：端侧任务状态，覆盖已接收、运行中、成功、失败、取消。
- `CloudTaskErrorCode`：错误分类，覆盖权限缺失、网络不可用、任务拒绝、工具失败、执行超时和未知错误。
- `CloudTaskExecutionResult`：执行完成后的成功/失败结果。
- `CloudTaskStatusReport`：上报载荷，保留任务编号、设备编号、追踪编号、状态、时间、摘要、错误码、可重试标记和产物路径。
- `CloudExecutorNodeSimulator`：本地模拟器，不触网、不操作手机，只验证接收 → 运行 → 终态上报顺序。

### 已有单元测试复用

文件：`app/src/test/java/io/agents/pokeclaw/cloudnode/CloudExecutorNodeContractTest.kt`

覆盖：

- 成功路径：接收任务 → 运行中 → 成功上报，保留任务编号、追踪编号、摘要和产物路径。
- 失败路径：权限缺失时生成失败上报，错误码为 `PERMISSION_MISSING`，并标记可重试。

### QA 清单补充

文件：`QA_CHECKLIST.md`

新增 `Z8`：端侧执行节点本地模拟闭环，用于在无真机、无 dyq 模拟服务时先验证契约层顺序和错误回传。

## 四、本地模拟闭环说明

当前最小闭环为纯本地可验证流程：

1. 构造 `CloudExecutorTask`，代表云端下发“打开设置查看电量”等简单手机控制任务。
2. `CloudExecutorNodeSimulator` 先生成 `RECEIVED` 上报。
3. 模拟器再生成 `RUNNING` 上报。
4. 本地执行回调返回成功或失败结果。
5. 模拟器生成 `SUCCEEDED` 或 `FAILED` 终态上报。
6. 测试断言任务编号、追踪编号、错误码、可重试标记和产物路径都被保留。

这满足 CMP-2097 的“任务模型、状态上报、错误反馈接口封装或文档落地；可本地模拟一条任务”的第一阶段验收。

## 五、边界与后续待办

- 本轮没有连接真实 dyq 后端接口，没有引入新依赖，没有改动任务编排器。
- 后续应在 dyq 后端契约稳定后，新增 `CloudNodeApi`、`CloudNodeClient`、`CloudTaskPoller`、`CloudTaskEventReporter` 和离线队列。
- 真实端到端验收仍需真机、云端模拟服务或 dyq 后端测试环境，按 `QA_CHECKLIST.md` 的 `Z1` 到 `Z8` 执行。
- 上报内容必须继续脱敏，禁止上传完整通知正文、联系人列表、提示词、密钥或完整屏幕文本。
