# PokeClaw 设备注册、任务轮询与结果回传摸底清单

日期：2026-05-15
负责人：安卓小龙
关联问题：CMP-1861
仓库：`/mnt/e/code/PokeClaw`
分支：`main`

## 一、前提假设核查

- 纸夹任务来源：已按无直接任务上下文要求，通过纸夹接口查询当前成员 `ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0` 名下未完成问题，并选择最高优先级问题 `CMP-1861`。
- 当前目录：`/mnt/e/code/PokeClaw`。
- 当前分支：`main`。
- 真实仓库：已确认本目录是 PokeClaw 安卓端仓库，源码包名为 `io.agents.pokeclaw`，不是 `zeroclaw`、`metaclaw` 或其他相近仓库。
- 产品边界：已复核 `README.md` 的产品方向、路线图和平台约束。PokeClaw 必须保持手机常驻端侧执行底座，不能改成电脑控制框架、云手机框架或后端编排中枢。
- 现有变更状态：本轮开始前已有 `QA_CHECKLIST.md` 修改和 `docs/product/` 新文档，未覆盖其内容；本轮新增独立摸底清单文件。

## 二、已实际检查文件

- `README.md`：确认端侧优先、手机常驻、通用移动智能体底座方向。
- `CLAUDE.md`：确认架构优先、日志可追踪、端到端验收优先规则。
- `app/build.gradle.kts`：确认已有 `okhttp`、`retrofit`、`gson`、`mmkv` 等依赖，端云客户端第一阶段无需新增网络或本地存储大型依赖。
- `app/src/main/java/io/agents/pokeclaw/TaskOrchestrator.kt`：确认任务锁、忙碌拒绝、确定性工具、技能、智能体循环、完成、失败、取消、阻塞都集中在现有任务编排器。
- `app/src/main/java/io/agents/pokeclaw/TaskEvent.kt`：确认已有 `LoopStart`、`Progress`、`ToolAction`、`ToolResult`、`TokenUpdate`、`Completed`、`Failed`、`Cancelled`、`Blocked`，可作为云端状态回传映射源。
- `app/src/main/java/io/agents/pokeclaw/TaskSessionStore.kt`：确认已有单一任务会话状态和忙碌判断，云端任务拉取必须尊重该状态，不能并发绕过。
- `app/src/main/java/io/agents/pokeclaw/AppCapabilityCoordinator.kt`：确认已有可访问性、通知监听、通知权限、前台服务、悬浮窗、电池优化、存储权限的能力快照来源。
- `app/src/main/java/io/agents/pokeclaw/ClawApplication.kt`：确认应用启动已有全局初始化入口和网络状态监听入口，后续云节点管理可在默认关闭前提下接入初始化。
- `app/src/main/java/io/agents/pokeclaw/server/ConfigServer.kt`、`ConfigServerManager.kt`：确认已有本机配置服务，但端云对接应采用手机主动外连云端，不要求 dyq 云端反连手机本机服务。
- `app/src/main/java/io/agents/pokeclaw/support/DebugReportManager.kt`：确认可参考调试报告采集思路，但云端上报必须严格脱敏。
- `QA_CHECKLIST.md`：确认已存在“Z 端云设备节点验收”草案，可继续作为后续实现验收入口。

## 三、两种方案取舍

### 方案一：直接把云端注册、轮询和回传塞进现有任务编排器

优点：文件少，短期接入快。

缺点：会让 `TaskOrchestrator` 同时承担网络、鉴权、设备身份、离线队列和任务状态映射职责，破坏现有任务核心边界；后续调试会把本地执行错误和云端通信错误混在一起。

结论：不选。

### 方案二：新增独立 `cloudnode` 端云适配层，任务仍进入现有编排器

优点：端云通信、设备身份、轮询、结果回传、离线队列独立封装；本地任务执行仍走 `TaskOrchestrator`，继承现有权限、前台服务、日志、忙碌拒绝和完成失败语义；更符合“通用移动智能体底座”方向。

缺点：首轮需要多建几个边界清晰的小文件，接入点比方案一多。

结论：选择方案二。理由是风险更低、可测性更好，不破坏端侧执行核心。

## 四、最小可跑闭环

第一阶段目标不是一口气做完整云端控制，而是跑通最小闭环：

1. 用户在设置页手动开启“云端设备节点”，默认关闭。
2. 端侧读取云端地址和配对令牌，生成稳定设备标识。
3. 端侧主动调用 dyq 云端注册接口，换取云端节点编号和短期令牌。
4. 端侧按云端返回间隔发送心跳，包含能力快照和当前任务状态。
5. 端侧主动轮询任务，收到任务后先检查 `TaskSessionStore.isTaskRunning()`。
6. 空闲时把云端任务投递到 `TaskOrchestrator.startNewTask()`；忙碌时回传 `TASK_BUSY`。
7. 监听 `TaskEvent`，把执行状态映射为云端状态回传。
8. 网络失败时把状态事件写入有限本地队列，恢复网络后按幂等键补报。
9. 鉴权失败时停止重试，提示用户重新配对。
10. 全链路日志使用 `CloudNode` 标签，但不得输出令牌、密钥、完整聊天正文、通知正文、联系人列表、完整屏幕文本或完整提示词。

## 五、建议新增包与职责

```text
app/src/main/java/io/agents/pokeclaw/cloudnode/
  CloudNodeConfig.kt              云端地址、令牌、开关、轮询间隔
  CloudNodeIdentity.kt            稳定设备标识、应用版本、构建指纹、机型信息
  CloudNodeCapabilityMapper.kt    从 AppCapabilityCoordinator 映射云端能力字段
  CloudNodeModels.kt              注册、心跳、任务、状态、错误、结果数据模型
  CloudNodeApi.kt                 Retrofit 接口定义
  CloudNodeClient.kt              请求执行、鉴权头、错误归一化、日志脱敏
  CloudTaskPoller.kt              任务轮询、忙碌检查、投递现有任务编排器
  CloudTaskEventReporter.kt       TaskEvent 到云端状态的映射与上报
  CloudEventQueue.kt              本地有限队列、幂等键、退避重试
```

## 六、端侧到云端状态映射

| 端侧来源 | 云端状态 | 说明 |
|---|---|---|
| 轮询收到任务且本机空闲 | `accepted` | 已接收，准备投递本地编排器 |
| `TaskEvent.LoopStart` | `running` | 上报执行轮次 |
| `TaskEvent.Progress` | `running` | 上报脱敏步骤摘要 |
| `TaskEvent.ToolAction` | `running` | 只上报工具展示名，不上传工具参数原文 |
| `TaskEvent.ToolResult` | `running` | 只上报成功/失败和截断后的脱敏摘要 |
| `TaskEvent.TokenUpdate` | `running` | 可上报令牌统计和模型成本摘要 |
| `TaskEvent.Completed` | `completed` | 上报任务完成摘要 |
| `TaskEvent.Failed` | `failed` | 上报错误码和脱敏错误摘要 |
| `TaskEvent.Cancelled` | `cancelled` | 用户或系统取消 |
| `TaskEvent.Blocked` | `blocked` | 系统弹窗、权限或环境阻塞 |
| 本机已有任务运行 | `busy` | 云端可稍后重试 |

## 七、离线缓存和安全边界

- 本地只缓存状态事件、错误码、截断摘要、幂等键、重试计数和时间戳。
- 不缓存完整聊天、完整屏幕文本、通知正文、联系人列表、密钥或配对令牌。
- 建议限制最多 100 条或 24 小时，超过后丢弃旧事件并保留一条本地用户可见提示。
- 云端返回鉴权失败时立即清空短期令牌并停止重试，避免无效循环。
- 端云任务不能绕过用户可见任务状态、前台服务、可访问性检查和本地停止按钮。

## 八、待后端确认契约

后续真正写代码前，需要 dyq 后端确认以下接口契约：

1. 设备注册路径、方法、字段名、返回节点编号和短期令牌格式。
2. 心跳路径、周期策略、能力字段枚举。
3. 任务拉取路径，采用短轮询、长轮询还是服务端事件。
4. 状态回传路径，任务状态枚举、错误码枚举、幂等键规则。
5. 令牌刷新和失效处理规则。
6. 云端任务是否只允许简单手机控制任务，复杂任务是否必须先在 dyq 后端拆解。

## 九、端到端验收入口

沿用 `QA_CHECKLIST.md` 的“Z 端云设备节点验收”，实现后至少跑：

- `Z1` 首次配对注册。
- `Z2` 鉴权失败用户可见。
- `Z3` 心跳能力快照。
- `Z4` 云端任务下发到本地执行。
- `Z5` 离线结果缓存与恢复补报。
- `Z6` 忙碌态拒绝新任务。
- `Z7` 敏感信息脱敏。

## 十、本轮实际执行命令和结果

```bash
pwd
# /mnt/e/code/PokeClaw

git branch --show-current
# main

git status --short
#  M QA_CHECKLIST.md
# ?? docs/product/

curl -sS "http://127.0.0.1:3101/api/companies/bfc57cd0-e725-42e2-b221-400eaca22123/issues?assigneeAgentId=ce4a5bf8-5b7a-4196-b5b1-91a7bf1a1ee0" \
  -H "Authorization: Bearer $PAPERCLIP_API_KEY"
# 成功返回 15 条当前成员问题，其中未完成 10 条；本轮选择最高优先级 CMP-1861。

./gradlew testDebugUnitTest assembleDebug
# 首次失败：构建产物目录无法删除，Kotlin 守护进程和增量构建目录占用导致。

./gradlew --stop && rm -rf app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin app/build/intermediates/project_dex_archive/debug/dexBuilderDebug && ./gradlew --no-daemon testDebugUnitTest assembleDebug
# 通过：BUILD SUCCESSFUL，生成调试包并完成单元测试。
```

## 十一、结论

PokeClaw 当前已有足够的端侧执行基础：任务编排器、结构化任务事件、任务会话锁、能力快照、网络依赖和本地配置能力都已存在。CMP-1861 下一步不应直接侵入核心编排器，而应新增独立 `cloudnode` 适配层，在默认关闭的前提下完成注册、心跳、任务轮询、状态回传和离线补报。
