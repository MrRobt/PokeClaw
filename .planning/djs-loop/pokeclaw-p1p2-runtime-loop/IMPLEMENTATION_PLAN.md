# IMPLEMENTATION_PLAN

STATUS: COMPLETE

## 任务切片（按主人要求每轮 1 个可验收小目标）

### 切片 1：P1.4 端云冒烟证据包（基线）
- [x] 执行 `bash scripts/dyq28-local-loop-evidence.sh` 生成 artifacts/dyq28-local-loop/20260607-052459/
- [x] 验证 operator-status.json 含 deviceStatus=no_online_device / safetyBoundary / runtimeChecks
- [x] 验证 operator-dashboard.html 含六类端侧结果表

### 切片 2：P2.4 端侧任务状态机可见性派生
- [x] 写 scripts/pokeclaw-p2p4-state-machine-derivative.sh
- [x] 该脚本：读 artifacts/.../operator-status.json 增 taskStateMachine 字段；追加 HTML 区块
- [x] 执行派生，JSON 校验通过 + HTML 含 task-state-machine 区块（58→78 行）

### 切片 3：P1.3 + P2.3 派生文档
- [x] 写 .planning/djs-loop/pokeclaw-p1p2-runtime-loop/derivative/kotlin-coverage-derivative.md
- [x] 增补 heartbeat 鉴权 9 行 + 任务重试 × 人工接管 8 行对照
- [x] 3 状态标记：✓对齐 / △扩展 / ✗缺失（基于真实端侧类名）

### 切片 4：跨项目证据落
- [x] 复制 5 份 + 写 EVIDENCE.md 1 份 = 6 份到 /root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/
- [x] EVIDENCE.md 汇总本轮产物 + 提交号 + 阻塞原因

### 切片 5：git 提交
- [x] 主 commit: 46adf1f (7 文件，522 行)
- [x] 补 commit: 9d25af5 (1 文件 EVIDENCE.md，77 行)
- [x] 落 dev 本地，不强推
- [x] 提交号回填 EVIDENCE.md

### 切片 6：评论回填 + kanban_complete
- [x] round42 完成：5 字段评论 + kanban_complete

### 切片 7（r44）：P1.1-P2.4 端云任务领取 / 执行结果 / 截图证据 / 六类结果结构化
- [x] 写 `scripts/pokeclaw-p1p1-p2p2-runtime-evidence.sh` 派生脚本
- [x] 给 `operator-status.json` 增 `p1p2Contract` 字段（cloudClaim + cloudResult + screenshotEvidence + sixResults 6 项）
- [x] 给 `operator-dashboard.html` 追加 3 个区块（P2.1 cloud claim / cloud result / P2.2 screenshot）
- [x] `bash -n` 三个脚本全过
- [x] `./scripts/dyq28-local-loop-evidence.sh artifacts/round44-pokeclaw/` 通过
- [x] `./scripts/pokeclaw-p2p4-state-machine-derivative.sh artifacts/round44-pokeclaw/` 通过
- [x] `./scripts/pokeclaw-p1p1-p2p2-runtime-evidence.sh artifacts/round44-pokeclaw/` 通过
- [x] `CloudExecutorNodeContractTest` 通过（BUILD SUCCESSFUL）
- [x] 写 `artifacts/round44-pokeclaw/r44-summary.md`（r44 增量说明）
- [x] 写 `.planning/djs-loop/pokeclaw-p1p2-runtime-loop/r44-incremental.md`（r44 增量说明）
- [x] git 提交（dev 本地，不强推）
- [x] 跨项目证据落 `/root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round44-20260607-pokeclaw/`
- [x] 评论回填 + kanban_complete

## 状态机
DISCOVERY → DESIGN → PLANNING → BUILDING → VERIFYING → COMPLETE

## 真实约束
- ADB 在线=0（硬事实）
- 48080 后端已运行（C 层验证过），但本层 P1.2 真实联调 owner-blocked
- 端云契约基线 artifacts 已在 .planning/djs-loop/cloud-contract-baseline/artifacts/.../
- 必须不假装、不强推、不写假 token

## r44 派生脚本清单
- `scripts/dyq28-local-loop-evidence.sh`（基线，0 改动）
- `scripts/pokeclaw-p2p4-state-machine-derivative.sh`（round42，0 改动）
- `scripts/pokeclaw-p1p1-p2p2-runtime-evidence.sh`（r44 新增）
