# 设计

## 总体思路

不引入新工具链、不动端侧 Kotlin 代码、不动后端 Java 代码；用
**派生（derivative）模式** 复用 C 层（t_b05b9bc0 mainline-overview）+ 端云契约基线
（.planning/djs-loop/cloud-contract-baseline）已落地的产物，按 P 层需求
做"对外可验收证据增量"。

- 端云冒烟脚本：复用 `scripts/dyq28-local-loop-evidence.sh`（已存在）
- 端云契约基线：复用 `.planning/djs-loop/cloud-contract-baseline/artifacts/.../baseline.json`
- 派生产物：基于 baseline.json 生成 heartbeat 鉴权 × 状态机可见性 派生 md
- 端侧 Kotlin 代码：0 改动（本轮）
- 后端 Java 代码：0 改动（本轮）
- 真实外呼/ADB：0 调用（本轮，按主控硬红线）

## 数据流

```
scripts/dyq28-local-loop-evidence.sh
        │
        ▼
artifacts/dyq28-local-loop/<ts>/
   operator-status.json
   operator-dashboard.html
   operator-dashboard.md
   summary.md
        │
        ▼ P2.4 派生：post-process
add taskStateMachine to operator-status.json
add 状态机可见性区块 to operator-dashboard.html
        │
        ▼ P1.3 派生：基于 baseline.json
.cloud-contract-baseline-derivative/
   kotlin-coverage-derivative.md
        │
        ▼ 复制到 paperclip 证据目录
/root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/
   operator-status.json
   operator-dashboard.html
   kotlin-coverage-derivative.md
   summary.md
   EVIDENCE.md (汇总 + 提交号 + 阻塞)
        │
        ▼
git commit (dev 本地, 不强推)
```

## 关键决策

### 决策 1：派生模式而非直接编辑
- **选项 A**：直接编辑 `scripts/dyq28-local-loop-evidence.sh`，加 P2.4 字段
  - 优点：单一来源
  - 缺点：动 C 层脚本，污染 round35 已落证据的可复读性
- **选项 B**：派生 post-process 脚本，独立运行，叠加产物
  - 优点：C 层脚本不动，round35 证据可复读
  - 缺点：多一个脚本
- **决定 B**。本轮新增 `scripts/pokeclaw-p2p4-state-machine-derivative.sh`，
  输入是 `artifacts/dyq28-local-loop/<ts>/`，输出叠加新字段/区块。

### 决策 2：不在端云契约基线 artifacts 上覆写
- **选项 A**：覆盖 `kotlin-coverage.md`
- **选项 B**：新增 `kotlin-coverage-derivative.md`
- **决定 B**。基线已提交，覆写会污染"端云契约基线闭环"的提交号。

### 决策 3：P2.4 状态机计数用 0 起步，不取样
- 端侧无任务时 `counts: {SUCCESS:0, FAILED:0, RUNNING:0, CANCELLED:0}` + `lastTaskId: "no_task"`
- 标 `source: "device-empty-or-sample-not-fake"`，明确不是真机数据
- 不假装"端侧有任务"，绝不写 `lastTaskId: "fake-uuid-..."`

### 决策 4：跨项目证据落 paperclip 不强同步
- **选项 A**：每次 P 层落都实时同步
- **选项 B**：本轮落一次，跨项目 cron 每小时同步
- **决定 B**。本轮只复制一次本轮产物到 paperclip evidence/round42-...。
  未来若做持续同步，再起 cron。

### 决策 5：commit 信息严格按主人格式
- `feat(端云闭环): 端云任务领取与结果状态机可见性证据`
- 一行标题 + 1-3 行 body
- 不强推

## 接口契约（自产自用）

`operator-status.json` 增量字段（v1.1）：

```json
{
  "taskStateMachine": {
    "states": ["SUCCESS", "FAILED", "RUNNING", "CANCELLED"],
    "counts": {"SUCCESS": 0, "FAILED": 0, "RUNNING": 0, "CANCELLED": 0},
    "lastTaskId": "no_task",
    "source": "device-empty-or-sample-not-fake"
  }
}
```

`operator-dashboard.html` 增量区块：

```html
<section class="card" style="margin-top:18px">
  <h2>端侧任务状态机可见性</h2>
  <ul>
    <li>状态集: SUCCESS / FAILED / RUNNING / CANCELLED</li>
    <li>当前计数: SUCCESS=0 FAILED=0 RUNNING=0 CANCELLED=0</li>
    <li>上次任务 ID: no_task</li>
    <li>数据来源: device-empty-or-sample-not-fake（无真机）</li>
  </ul>
</section>
```

`kotlin-coverage-derivative.md` 增量段落（v1）：

```markdown
## 派生：heartbeat 鉴权 × 任务重试 × 人工接管（来自 P 层增量）

| 契约项 | 端侧位置 | 状态 | 备注 |
|--------|----------|------|------|
| heartbeat 鉴权签名 X-Claw-Signature | ClawSignatureGenerator.kt:HmacSHA256 | ✓对齐 | 基线已覆盖 |
| heartbeat 用 deviceToken 签名 | ClawSignatureGenerator.kt:sign() | ✓对齐 | 基线已覆盖 |
| 失败重试 3 次入人工队列 | ClawTaskStateMachine.kt:failureCount | △扩展 | 端侧计数有，契约未要求 |
| 上次失败原因 retryable/非 | ClawTaskStateMachine.kt:lastFailureType | ✓对齐 | 与契约 errorCategory 一致 |
| 人工接管渠道 Tasker Intent | ExternalAutomationActivity.kt | ✓对齐 | 与 README § External Automation 一致 |
```

## 风险与回退

| 风险 | 缓解 |
|------|------|
| 后处理脚本破坏原 HTML 结构 | 用 `sed`/`python -c` 在固定锚点后追加，不动原内容 |
| 跨项目复制失败（权限） | 先 ls 验证目标目录可写；失败写 RUN_LOG |
| git commit pre-commit hook 失败 | 不重试，把失败原因写 EVIDENCE.md |
| ADB 在线=0 时主控强要求"真机证据" | 不假装；写 `deviceStatus=no_online_device`；报告阻塞 |

## 与父任务的关系

- 父 1：t_b05b9bc0（C 总集 mainline-overview）— 已闭环，本层消费
- 父 2：t_bd4d90a4（二级任务图）— 已重建
- 兄弟：t_33551fd0、t_65c58e5c（QC/S 链接）— 本层不直接交互
- 消费：cloud-contract-baseline（t_d2f8d4b7）— 已闭环
- 跨项目：paperclip 证据目录 cron 同步
