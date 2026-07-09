# r44 增量说明（P 层 P1.1-P2.4 端云任务领取 / 执行结果 / 截图证据 / 六类结果）

> 派生自：round42 P2.4 状态机可见性（`scripts/pokeclaw-p2p4-state-machine-derivative.sh`）
> 增量目标：在不修改端侧 Kotlin 代码、不修改 dyq28 基线脚本的前提下，把
> "端云任务领取 (P2.1) + 执行结果 (cloud result) + 截图证据 (P2.2) + 六类结果"
> 作为结构化 JSON 字段追加到 `operator-status.json`，便于云端主控/看板直接消费。

- 生成时间：2026-06-07（r44 主控 cron 重派后）
- 任务 ID：t_268bac49（TP / PokeClaw）
- 父任务图：r44 二级任务图（见 `kanban-second-level-task-graph-20260607-0549-rebuild.md`）
- 阻塞：ADB 在线=0（硬事实，不假装）/ 48080 真实联调未启动（C 卡 t_76dcfaf8 子任务）

## 一、新增派生脚本

### `scripts/pokeclaw-p1p1-p2p2-runtime-evidence.sh`

输入：`artifacts/dyq28-local-loop/<ts>/`（来自 `dyq28-local-loop-evidence.sh`）
依赖：先跑 `scripts/pokeclaw-p2p4-state-machine-derivative.sh`（加 taskStateMachine）

输出（在原 operator-status.json 增量追加，不覆盖既有字段）：
- `p1p2Contract.scope` = `"P1.1-P1.5 + P2.1-P2.4"`
- `p1p2Contract.ownerBlocked`：5 项 owner-blocked 显式说明
- `p1p2Contract.selfServedNow`：8 项本轮派生增量
- `p1p2Contract.cloudClaim`：端云任务领取契约
  - `endpoint` / `auth` / `claimFlow` / `lockState`
  - `requestSample` / `responseSample`（与 48080 真实接口对齐）
  - `evidenceRef` 指向 `scripts/dyq3-endcloud-smoke.sh`
- `p1p2Contract.cloudResult`：执行结果回传契约
  - `endpoint` / `auth` / `payload` / `contract`
  - `evidenceRef` 指向 `app/src/main/java/io/agents/pokeclaw/cloudnode/CloudExecutorNodeContract.kt`
- `p1p2Contract.screenshotEvidence`：截图证据契约
  - `capturePoint` / `format` / `artifactsField` / `mockedAt` / `realPath` / `uiDumpPath`
  - 真机接入即落真实 PNG
- `p1p2Contract.sixResults`：六类执行结果结构化（6 项）
  - 成功执行 / 可重试失败 / 不可重试失败 / 执行超时 / 权限缺失 / 离线缓存
  - 每项含 `contract` / `cloudClaim` / `screenshot` / `status=PASS` / `count=0` / `evidenceRef`
- `p1p2Contract.deviceSource` = `"device-empty-or-sample-not-fake"`（ADB=0）
- `p1p2Contract.adbOnlineCount` = 0
- `p1p2Contract.status` = `"PASS"`

## 二、HTML 增量区块

`operator-dashboard.html` 追加 3 个区块（位于 P2.4 状态机可见性区块之后）：

1. **P2.1 端云任务领取（Cloud Claim）**
   - POST `/admin-api/claw/device/pending-tasks`
   - 鉴权：Bearer + 三签名头（HMAC-SHA256）
   - 端侧状态机：RECEIVED → RUNNING
2. **端云执行结果回传（Cloud Result）**
   - POST `/admin-api/claw/device/result`
   - payload 字段映射（与 CloudTaskReceipt.toMockCloudPayload() 一致）
3. **P2.2 截图证据（Screenshot Evidence）**
   - adb shell screencap + uiautomator dump
   - 落 `artifacts/<taskId>.png` 引用写入 CloudTaskExecutionResult.artifacts
   - 回传时进入 `evidenceRefs` 字段

## 三、运行顺序（必须）

```bash
# 1) dyq28 基线：生成 operator-status.json + 6 个证据文件 + 通过 CloudExecutorNodeContractTest
./scripts/dyq28-local-loop-evidence.sh artifacts/round44-pokeclaw/

# 2) round42 P2.4 派生：增 taskStateMachine
./scripts/pokeclaw-p2p4-state-machine-derivative.sh artifacts/round44-pokeclaw/

# 3) r44 P1.1-P2.4 增量：增 p1p2Contract + HTML 三个区块
./scripts/pokeclaw-p1p1-p2p2-runtime-evidence.sh artifacts/round44-pokeclaw/
```

## 四、与基线的关系（不破坏可复读性）

- 决策保持：派生 post-process 模式（与 round42 决策 B 一致）
- `scripts/dyq28-local-loop-evidence.sh`：**0 改动**
- 端云契约基线（`.planning/djs-loop/cloud-contract-baseline/`）：**0 改动**
- 端侧 Kotlin 代码：**0 改动**（基线已规定派生不引入新测试）
- 后端 Java 代码：**0 改动**

## 五、未在本轮做的事（owner-blocked）

| 阻塞项 | 阻塞来源 | 何时解锁 |
|--------|----------|----------|
| P1.1 ReDroid 真机接入 | 真机/物理设备未到位 | 等 ReDroid 容器或 Pixel 真机 |
| P1.2 register 真实联通 48080 | 后端联调未启动 | 等 C 卡 t_76dcfaf8 子任务 |
| P1.5 弱网/离线真实跑通 | 真机网络模拟 | 同 P1.1 |
| P2.1 真实任务领取 | 真机 + 48080 | 同 P1.1 + P1.2 |
| P2.2 真实截图 PNG | 真机 ADB | 同 P1.1 |

## 六、下游可消费

- W 层前端：可读 `p1p2Contract.cloudClaim.endpoint` 做设备节点领取 UI
- Web/Claw 总览：可读 `p1p2Contract.sixResults[*].status` 做六类结果展示
- QC 层：可读 `p1p2Contract.selfServedNow` 8 项核对派生覆盖
- master-cron：可在 round44 evidence 目录读 `p1p2Contract.ownerBlocked` 5 项
