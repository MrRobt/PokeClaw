# P1.5 弱网/离线异常可见性审计（仅审计，不改代码）

- 时间：2026-06-06 15:08 (UTC+8)
- 角色：PokeClaw 端侧 worker (cron `b4645074980a`)
- 工作目录：`/mnt/e/code/PokeClaw`
- git 状态：本地有 4 个他人未提交改动文件，本轮全部避开，不写入任何生产代码
- 范围：仅 `OfflineFallbackManager.kt` + `CloudNodeOrchestrator.kt` + 已有 `dyq3-endcloud-smoke.sh` 弱网分支

## 一、结论

| 维度 | 状态 | 证据 |
|---|---|---|
| 端云最小冒烟脚本（mock 模式） | ✅ PASS 7/7 | `smoke/summary.md`、`smoke/smoke_run.log` |
| 弱网场景 curl 异常可触发 | ✅ PASS | `responses/heartbeat_network_down.err` (curl exit=7) |
| 端云协议层 `NETWORK_UNAVAILABLE` 错误码 | ✅ 已存在 | `cloudnode/CloudExecutorNodeContract.kt:33` |
| `CloudNodeOrchestrator` 把弱网结构化错误上报云端 | ✅ 已接通（他人未提交改动） | `CloudNodeOrchestrator.kt:342-348`、`buildResultLogSnippet` |
| **离线降级回退到本地模型** | ❌ **死代码** | `OfflineFallbackManager.kt` 全部 API 在全仓 0 外部调用方 |
| `adb devices -l` 真机 | ❌ 无 | `smoke/adb_minimal.log` |

## 二、弱网"用户可见" — 真实端云链路差距

冒烟脚本 `dyq3-endcloud-smoke.sh` 步骤 6 (行 252-265) 的弱网/断网检查**只证明 curl 失败**：

- `curl -sS --connect-timeout 2 --max-time 4 -X POST "$NETWORK_DOWN_BASE_URL/..."` 必返回 exit != 0
- 脚本仅打印 `[PASS] 网络断连异常已触发: exit=$NETWORK_EXIT`
- **完全没有验证** Android 端 Kotlin 代码在弱网时的行为
- **没有触发** `XLog.e(TAG, "...")` 的 `adb logcat` 抓取

也就是说，脚本能证明"网络确实断了"，但**不能证明"端侧用户看到了网络断"**。要闭合 P1.5 真验收，必须有真机 + adb logcat 抓 `PokeClaw/OfflineFallback` / `PokeClaw/CloudHeartbeat` 标签的 `XLog` 输出。当前 `adb devices -l` 为空，端云真机链路没有跑通。

## 三、架构缺口：OfflineFallbackManager 是死代码

通过 `grep -rn "OfflineFallbackManager" --include="*.kt"` 与 `grep -rn "enterOfflineMode\|exitOfflineMode\|setLocalModelAvailable" --include="*.kt"` 全仓扫描：

```
（除 OfflineFallbackManager.kt 自身外，没有任何 .kt 文件引用这些 API）
```

具体：

| API | 唯一引用位置 | 调用方 | 影响 |
|---|---|---|---|
| `OfflineFallbackManager.getInstance` | 文件内部 | 无 | 类无法被加载到内存中（lazy） |
| `enterOfflineMode()` | 文件内部 | 无 | 弱网时不会进入离线模式 |
| `exitOfflineMode()` | 文件内部 | 无 | 网络恢复时不会退出离线 |
| `setLocalModelAvailable()` | 文件内部 | 无 | 本地模型可用性永远默认 false |
| `executeOfflineTask()` | 文件内部 | 无 | 永远不会被调用 |

对应 P1.5 验收 "**弱网时使用本地模型执行**" 的最小闭环，**没有连通**：

- `CloudHeartbeatManager` 失败时未调用 `enterOfflineMode()`
- `CloudNodeOrchestrator` 在 `CloudTaskErrorCode.NETWORK_UNAVAILABLE` 时未路由到 `executeOfflineTask()`
- 没有 `ConnectivityManager.NetworkCallback` 监听网络状态变化

虽然 `XLog.i("进入离线模式，切换到本地模型")` 字符串已写在文件里，但在生产运行时**永远不会出现**在 logcat。

## 四、本轮 P1.5 真实推进了什么

1. **固化一次可重放的端云冒烟证据**（不修改脚本）:
   - 跑 `MOCK_PORT=18500 USE_MOCK_BACKEND=1 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh .planning/audit/runs/20260606-1508-pokeclaw-p15-audit/smoke`
   - 7/7 步骤全 PASS，注册/心跳/拉任务/回传/无 token/坏 token/弱网断连 均产生证据
   - 落 `summary.md`、`smoke_run.log`、`mock_server.log`、`responses/*.json|.code|.err`

2. **静态审计 OfflineFallbackManager 死代码问题**（不修、不动文件）:
   - 全仓搜索结果：API 全部 self-contained
   - 与 `CloudNodeOrchestrator` 已有的 `buildErrorDetail(NETWORK_UNAVAILABLE)` 形成"协议已通、闭环未接"的结构

3. **不冲突本轮其他 worker**:
   - 完全没有触碰 `CloudNodeOrchestrator.kt`、`QA_CHECKLIST.md`、两个 `commercial-e2e-evidence.md`、`scripts/dyq3-endcloud-smoke.sh`
   - 仅写入本审计目录下的 `report.md` 和 `smoke/`

## 五、给下一轮 50 分钟 worker 的 TDD 任务建议（不本轮执行）

按主人红线 "**Architecture Before Features**"，下一轮请先决定如何处理 OfflineFallbackManager：

### 任务 T1：决定是接活还是删除（**先问主人**）

- **方案 A：接活** — 在 `CloudHeartbeatManager` 失败 ≥ N 次时调用 `enterOfflineMode()`，在 `CloudNodeOrchestrator` 检测到 `NETWORK_UNAVAILABLE` 时路由到 `executeOfflineTask()`
- **方案 B：删除** — 当前是死代码、YAGNI，应在 P1.5 之前先删掉
- **方案 C：保留并标注** — 写 `@Deprecated("未接通 — P1.5 后处理")` 但要先有产品决定

### 任务 T2：若主人选 A，先写 TDD 失败测试

最小 RED 测试应覆盖：

```kotlin
// 期望测试一：连续心跳失败 N 次后，OfflineFallbackManager.isOfflineMode = true
@Test fun offline_mode_engaged_after_heartbeat_failures() { ... }

// 期望测试二：网络恢复且 heartbeat 成功后，isOfflineMode = false
@Test fun offline_mode_exits_after_recovery() { ... }

// 期望测试三：NETWORK_UNAVAILABLE 任务结果回传包含 errorCategory=NETWORK、recoverable=true
@Test fun network_unavailable_result_has_structured_error() { ... }
```

但因本仓目前**无单元测试目录、无 `app/src/test/`**，T2 还需要先建测试模块（与生产代码改动分离，单独 PR）。**本轮不执行**。

### 任务 T3：真机接入（**环境阻塞**）

P1.5 真验收需要：

- `adb devices -l` 出现 127.0.0.1:5555 (redroid 容器) 或真机
- 跑 `adb logcat --pid=$(pidof io.agents.pokeclaw)` 抓 `PokeClaw/OfflineFallback` 标签
- 模拟断网（`adb shell svc wifi disable && adb shell svc data disable`）→ 验证 `XLog.i("进入离线模式...")` 出现

此为环境依赖，主控需协调 P1.1 "ReDroid 容器"先跑通。

## 六、残留风险

1. **冒烟脚本的"弱网"步骤只能证明 curl 失败** — 它从设计上就不验证 Android 端行为，P1.5 真验收需要真机 + logcat 配套
2. **OfflineFallbackManager 死代码** — 下一轮 worker 必然要面对此架构债；不修会持续累积假象（看上去有降级，实际不会触发）
3. **adb 真机** — 端云端到端无真机，所有 E2E 冒烟都跑 mock 后端；这与 P1.1 ReDroid 容器未跑通直接相关
4. **他人未提交改动** — 当前 `CloudNodeOrchestrator.kt` 内的 `evidenceUrls`/`logSnippet` 字段填充未提交，**不要在本仓基于它再写新代码**，避免在他人的 TDD 周期上叠加；下一轮 50 分钟 worker 若要续做，先 `git status` + `git diff --check` 再决定

## 七、证据路径

- 本审计报告: `.planning/audit/runs/20260606-1508-pokeclaw-p15-audit/report.md`
- 端云冒烟证据: `.planning/audit/runs/20260606-1508-pokeclaw-p15-audit/smoke/`
  - `summary.md`（含 taskUuid、HTTP 码、错误码）
  - `smoke_run.log`（步骤日志）
  - `mock_server.log`（mock 后端日志）
  - `responses/*.json|.code`（注册/心跳/拉任务/回传/异常业务码）
  - `responses/heartbeat_network_down.err`（curl exit=7 弱网原始输出）
  - `adb_minimal.log`（adb devices -l 空）
- 静态审计依据（命令，主人可重跑）:
  - `grep -rn "OfflineFallbackManager" --include="*.kt" app/src/main/java`
  - `grep -rn "enterOfflineMode\|exitOfflineMode\|setLocalModelAvailable" --include="*.kt" app/src/main/java`

## 八、本轮改动文件清单

| 改动 | 文件 | 大小 | 内容 |
|---|---|---|---|
| 新增 | `.planning/audit/runs/20260606-1508-pokeclaw-p15-audit/report.md` | ~5KB | 本审计 |
| 新增 | `.planning/audit/runs/20260606-1508-pokeclaw-p15-audit/smoke/*` | ~7KB | 端云冒烟产物 |

**本轮没有改动任何生产代码、测试代码、构建配置、OpenAPI、文档根目录、他人未提交文件。**
