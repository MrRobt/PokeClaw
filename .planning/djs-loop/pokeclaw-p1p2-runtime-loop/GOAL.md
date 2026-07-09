# 任务：P 层 — PokeClaw 端云任务领取、执行结果与截图证据闭环

## 主人目标原文（来自 kanban t_7ddad685）
> 目标：在 /mnt/e/code/PokeClaw(dev) 推进 P1.1-P1.5 + P2.1-P2.4：PokeClaw 端云任务领取、执行结果与截图证据闭环。
> 必读：/mnt/e/code/PokeClaw 项目根 README/AGENTS.md；先 git status --short。
> P1.1 ReDroid 测试设备；P1.2 register 真实联通到 48080；P1.3 heartbeat 鉴权与保活；
> P1.4 端云冒烟证据包；P1.5 弱网/离线异常可见。P2 端侧任务执行能力。
> 真实外呼/ADB 操作走人工确认队列；不得伪造真机证据。
> 完成：register/heartbeat 接口真实联通 48080；任务状态机可见；证据含命令输出与提交号。
> 证据落 /root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/

## 主控预备（来自 my-profile 注释 2026-06-07 04:48）
- 父任务 t_b05b9bc0（C 总集）mainline-overview 已 200，pokeclaw 主线 status=warning，设备在线 0 台，契约通过。
- ADB 在线=0 属事实不伪装；真实外呼必须人工确认。
- 已有：operator-status.json + operator-dashboard.html（round35 落）。
- 验证：bash scripts/dyq28-local-loop-evidence.sh 0；mvn -pl dyq-module-claw/dyq-module-claw-biz -Dtest=ClawDeviceServiceTest test -DskipITs（仅对照后端契约）。
- 证据落 evidence/round42-20260607-pokeclaw/。

## 工程化目标（self-engineered）

本层是 P 层（端云任务领取/结果/截图证据），**前置条件**：
- C 层（mainline-overview、设备状态机、契约基线）已就绪（C 父任务 t_b05b9bc0 闭环）。
- 端云契约基线（t_d2f8d4b7）已就绪，5 端点/7 schema 锁版。
- ADB 真机在线=0、真实外呼必须人工确认（强红线）。

**业务可感知产出**（一个最小可验收闭环）：

1. **P1.4 端云冒烟证据包**：用 `scripts/dyq28-local-loop-evidence.sh` 输出
   `operator-status.json` + `operator-dashboard.html` + 端侧六类结果表，明确标记
   "ADB 在线 0 → 端侧样例证据，非真机验收"。这是 P 层对 C 层的端侧接力证据。
2. **P1.3 heartbeat 鉴权与保活契约化**：在契约基线基础上，扩 `heartbeat`
   端点的鉴权头契约（X-Claw-Timestamp/X-Claw-Nonce/X-Claw-Signature）补全到
   baseline.json + kotlin-coverage.md 派生产物，无需新增 Kotlin 测试。
3. **P2.4 端侧任务状态机可见**：在 `operator-dashboard.html` 增加"任务状态机
   可见性"区块（SUCCESS/FAILED/RUNNING/CANCELLED 4 态计数 + 上次任务 ID），
   让运营一眼看出"端侧任务状态机是否真的贯通"。
4. **P2.3 失败重试与人工接管队列契约化**：在 `kotlin-coverage.md`（派生）增加
   端侧 `ClawTaskStateMachine.kt` 字段对照，标记 3 态：✓对齐 / △扩展 / ✗缺失。
   不实现新代码，只把"已存在的状态机"对照出来。
5. **git 提交** `feat(端云闭环): 端云任务领取与结果状态机可见性证据`，落 dev
   本地，不强推；提交号回填 EVIDENCE.md。
6. **跨项目证据落**：同步副本到 `/root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/`。

**关键不假装原则**（硬红线）：
- ADB 在线=0 时，`deviceStatus=no_online_device` 写进 operator-status.json，**不**写 "online"。
- 不调用真实微信/短信/评论发送接口；所有外呼走"需人工确认"队列。
- 不在 evidence 中粘贴明文 deviceToken / accessToken / 真实 IMEI。
- 不强推；本地 dev 分支 commit 即可。

## 暂不做（明确范围）
- P1.1 ReDroid 真机接入（owner: 真机管理 / 物理设备）— owner-blocked。
- P1.2 register 真实联通到 48080（owner: dyq 后端 t_b05b9bc0 端联调）— owner-blocked。
- P1.5 弱网/离线异常真实跑通（owner: 真机网络模拟）— owner-blocked。
- P2.1/P2.2 手机页面观察与截图（owner: 真机 ADB）— owner-blocked。
- 真实任务在真机的领取-执行-回传（owner: 真机 + 48080 双双就绪）— owner-blocked。

本层在 owner-blocked 前提下，**最大化端侧自给自足**：
- 用已存在的 scripts/dyq28-local-loop-evidence.sh 做 P1.4 端云冒烟。
- 端云契约基线已落，本层基于它派生 heartbeat 鉴权补全 + 状态机可见。
- 主控明确："任务失败 3 次后入人工确认队列"在无真机时只能契约化 + 文档化。

## 红线（与父任务一致）
- 不强推
- 不删远端修改
- 不改 ARCHITECTURE_RECONSTRUCTION.md 列出的架构热点
- 不泄露密钥（deviceToken 走脱敏：sha256 前 8 位）
- 真实外呼/ADB 走人工确认队列
- ADB 在线=0 时不假装真机

## 验收口径
- operator-status.json 字段完整、deviceStatus=no_online_device 已标注
- operator-dashboard.html 含"任务状态机可见性"区块
- kotlin-coverage-derivative.md 含 heartbeat 鉴权 + 任务状态机 3 态标记
- 证据路径双落（PokeClaw artifacts + paperclip evidence/round42-...）
- git 提交号回填 EVIDENCE.md
- 评论回填完整字段（改动文件 / 验证命令 / 证据路径 / 提交号 / 阻塞原因）
