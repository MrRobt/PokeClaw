# IMPLEMENTATION_PLAN

STATUS: IN_PROGRESS

## 任务切片（按主人要求每轮 1 个可验收小目标）

### 切片 1：P1.4 端云冒烟证据包（基线）
- [ ] 执行 `bash scripts/dyq28-local-loop-evidence.sh 0` 生成 artifacts/dyq28-local-loop/<ts>/
- [ ] 验证 operator-status.json 含 deviceStatus/safetyBoundary/runtimeChecks
- [ ] 验证 operator-dashboard.html 含六类端侧结果表

### 切片 2：P2.4 端侧任务状态机可见性派生
- [ ] 写 scripts/pokeclaw-p2p4-state-machine-derivative.sh
- [ ] 该脚本：读 artifacts/dyq28-local-loop/<ts>/operator-status.json，
        增 taskStateMachine 字段；追加 HTML 区块
- [ ] 执行派生，确认 JSON 校验通过 + HTML 含状态机区块

### 切片 3：P1.3 + P2.3 派生文档
- [ ] 写 .planning/djs-loop/pokeclaw-p1p2-runtime-loop/derivative/kotlin-coverage-derivative.md
- [ ] 增补 heartbeat 鉴权 + 任务重试 × 人工接管 对照表
- [ ] 3 状态标记：✓对齐 / △扩展 / ✗缺失

### 切片 4：跨项目证据落
- [ ] 复制 operator-status.json + operator-dashboard.html + kotlin-coverage-derivative.md + summary.md 到
      /root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/
- [ ] 写 EVIDENCE.md 汇总本轮产物 + 提交号 + 阻塞原因

### 切片 5：git 提交
- [ ] git add scripts/pokeclaw-p2p4-state-machine-derivative.sh
        .planning/djs-loop/pokeclaw-p1p2-runtime-loop/derivative/
        evidence/round42-20260607-pokeclaw/ (PokeClaw 端副本)
- [ ] git commit -m "feat(端云闭环): 端云任务领取与结果状态机可见性证据"
- [ ] 落 dev 本地，不强推
- [ ] 提交号回填 EVIDENCE.md

### 切片 6：评论回填 + kanban_complete
- [ ] 写 kanban_comment 含 5 字段：改动文件 / 验证命令 / 证据路径 / 提交号 / 阻塞原因
- [ ] kanban_complete 任务

## 状态机
DISCOVERY → DESIGN → PLANNING → BUILDING → VERIFYING → COMPLETE

## 真实约束
- ADB 在线=0（硬事实）
- 48080 后端已运行（C 层验证过），但本层 P1.2 真实联调 owner-blocked
- 端云契约基线 artifacts 已在 .planning/djs-loop/cloud-contract-baseline/artifacts/.../
- 必须不假装、不强推、不写假 token
