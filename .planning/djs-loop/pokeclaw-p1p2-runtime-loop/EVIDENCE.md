# EVIDENCE — P 层 P1.4 + P2.4 + P1.3 派生 + P2.3 派生

## 提交
- **commit**: `46adf1f9940375a24380965742992c8037d3c41b`
- **短码**: `46adf1f`
- **标题**: feat(端云闭环): 端云任务领取与结果状态机可见性证据
- **分支**: dev (本地)
- **未推送**: 是（按主控硬红线）
- **作者**: root
- **时间**: 2026-06-07T05:31:31+08:00

## 改动文件（7）
```
A  .planning/djs-loop/pokeclaw-p1p2-runtime-loop/DESIGN.md                                 140
A  .planning/djs-loop/pokeclaw-p1p2-runtime-loop/GOAL.md                                    76
A  .planning/djs-loop/pokeclaw-p1p2-runtime-loop/IMPLEMENTATION_PLAN.md                     47
A  .planning/djs-loop/pokeclaw-p1p2-runtime-loop/REQUIREMENTS.md                            89
A  .planning/djs-loop/pokeclaw-p1p2-runtime-loop/RUN_LOG.md                                 13
A  .planning/djs-loop/pokeclaw-p1p2-runtime-loop/derivative/kotlin-coverage-derivative.md  58
A  scripts/pokeclaw-p2p4-state-machine-derivative.sh                                       99
```
共 522 行新增，0 删除。

## 验证命令
- `bash scripts/dyq28-local-loop-evidence.sh`
- `bash scripts/pokeclaw-p2p4-state-machine-derivative.sh artifacts/dyq28-local-loop/20260607-052459`
- `python3 -c "import json; d=json.load(open('artifacts/.../operator-status.json')); print(d['taskStateMachine'])"`
- `grep -c "task-state-machine" artifacts/.../operator-dashboard.html`
- 跨项目：`ls -la /root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/`

## 业务可感知产出
1. `operator-status.json` 含 `taskStateMachine` 字段（4 态 + 计数 0 + lastTaskId="no_task" + source 标注）
2. `operator-dashboard.html` 含"端侧任务状态机可见性"区块（4 态表 + 数据来源 + 上次任务 ID）
3. `kotlin-coverage-derivative.md` 派生对照表（P1.3 heartbeat 鉴权 9 行 + P2.3 任务重试 × 人工接管 8 行 + 3 态标记）
4. 跨项目证据落 5 文件：`/root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/`

## 端侧证据路径
- 基线产物：`/mnt/e/code/PokeClaw/artifacts/dyq28-local-loop/20260607-052459/`
  - operator-status.json（1590B）
  - operator-dashboard.html（4668B）
  - operator-dashboard.md（1304B）
  - summary.md（1817B）
  - adb.log / gradle-test.log / run.log
- 跨项目副本：`/root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/`
  - 5 文件同步

## 阻塞原因（owner-blocked）
| 阻塞项 | 阻塞来源 | 何时解锁 |
|--------|----------|----------|
| P1.1 ReDroid 真机接入 | 真机/物理设备未到位 | 等 ReDroid 容器或 Pixel 真机 |
| P1.2 register 真实联通 48080 | 后端联调未启动 | 等 dyq 后端 t_b05b9bc0 扩展任务或 48080 联调 worker |
| P1.5 弱网/离线真实跑通 | 真机网络模拟 | 同 P1.1 |
| P2.1/P2.2 手机页面观察与截图 | 真机 ADB | 同 P1.1 |
| 真实任务领取-执行-回传 | 真机 + 48080 双就绪 | 同 P1.1 + P1.2 |

本层在 owner-blocked 前提下，最大化端侧自给自足：
- 用已存在 dyq28 脚本做 P1.4 端云冒烟
- 用派生模式做 P2.4 状态机可见（不假装真机）
- 用派生模式做 P1.3/P2.3 端侧契约对照（基于真实端侧类名 CloudTaskStatus/CloudHeartbeatManager）
- 用跨项目 evidence 目录落副本

## 红线守住
- 0 push：dev 本地 commit，未推送
- 无 token 明文：所有日志/JSON 不含 deviceToken/accessToken
- 架构文件 0 修改：未碰 ARCHITECTURE_RECONSTRUCTION.md / 主源码
- 真实外呼 0 调用：未发任何微信/短信/评论
- ADB 在线=0 时 `deviceStatus=no_online_device` 已标注，不假装

## 下游可消费
- W 层前端可读 operator-status.json 做面板
- QC 层可读 evidence/round42-20260607-pokeclaw/ 做验收
- S2 dyq 联调可基于 kotlin-coverage-derivative.md 对照端侧实现
- master-cron 可在本轮 commit 后继续 round44+

## 下一步最小动作
- 等真机就绪后，把 ADB 真机接入 + register 真实联调 48080 交给 P 层扩展 worker
- 本层不重复主控已锁的 C 层 (t_b05b9bc0) 与基线 (t_d2f8d4b7) 范围
