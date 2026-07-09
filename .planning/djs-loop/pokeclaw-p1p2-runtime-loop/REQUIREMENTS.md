# 需求

## 业务需求
PokeClaw 端云闭环中，P 层承接 C 层（C1/C2 mainline-overview、设备状态机），
在 ADB 真机未就绪、48080 真实后端联调未启动的 owner-blocked 前提下，最大化
端侧自给自足的"端云任务领取/执行结果/截图证据"可验收闭环。

本层交付必须被下游 worker（P 内部扩展 / W 前端 / S2 dyq 联调）直接消费，
不需要再读 P 层代码。

## 功能需求

### R1. P1.4 端云冒烟证据包
- 执行 `bash scripts/dyq28-local-loop-evidence.sh 0` 在 PokeClaw 仓库根
- 强制要求 `deviceStatus=no_online_device`（ADB 在线=0 时）
- 强制要求 `operator-status.json` 含 `safetyBoundary: ["不自动发送微信、短信..."]`
- 强制要求 `operator-dashboard.html` 含六类端侧结果表（成功/可重试/不可重试/超时/权限缺失/离线缓存）
- 产物落 `artifacts/dyq28-local-loop/<ts>/`

### R2. P1.3 heartbeat 鉴权与保活契约化（基于基线）
- 不新增 Kotlin 测试
- 在 `cloud-contract-baseline` 已有 `signing.headers` 基础上，**派生**产物
  `kotlin-coverage-derivative.md` 增补一段："heartbeat 鉴权 × 端侧 HMAC 实现"对照
- 端侧 `ClawSignatureGenerator.kt` 已实现 HmacSHA256（基线已覆盖）；本层只把
  heartbeat 调用方对照"用 deviceToken 签名"是否一致

### R3. P2.4 端侧任务状态机可见性
- 在 `operator-dashboard.html` 追加区块："端侧任务状态机可见性"
  - 4 态计数：SUCCESS / FAILED / RUNNING / CANCELLED
  - 上次任务 ID（无任务时显示 "no_task"）
  - 数据来源：device-test 字段或样例 0/0/0/0 + no_task（不伪造）
- 在 `operator-status.json` 追加字段 `taskStateMachine`：
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

### R4. P2.3 失败重试与人工接管队列契约化
- 在派生 `kotlin-coverage-derivative.md` 增补"任务重试 × 人工接管"对照段
- 端侧 `ClawTaskStateMachine.kt` 字段（已存在）对照契约：
  - 失败计数 3 → 入人工队列
  - 上次失败原因（retryable vs non-retryable）
  - 人工接管渠道（Tasker/MacroDroid Intent API）
- 3 态标记：✓对齐 / △扩展 / ✗缺失

### R5. 跨项目证据同步
- 把 `artifacts/dyq28-local-loop/<ts>/operator-status.json` +
  `operator-dashboard.html` 复制到
  `/root/paperclip-work/paperclip/.planning/djs-loop/dyq-goal-pool-1000/evidence/round42-20260607-pokeclaw/`
- 复制 `cloud-contract-baseline` 派生 `kotlin-coverage-derivative.md` 到
  同一目录
- 写一个 `EVIDENCE.md` 汇总本轮产物 + 提交号 + 阻塞原因

### R6. 提交与回填
- 1 个 git commit：`feat(端云闭环): 端云任务领取与结果状态机可见性证据`
- 落 dev 分支本地，**不强推**
- 提交号回填 EVIDENCE.md
- kanban 评论回填 5 字段：改动文件 / 验证命令 / 证据路径 / 提交号 / 阻塞原因

## 非功能需求
- 性能：所有派生产物 < 5s 出
- 兼容性：WSL/Linux/MacOS 都可跑
- 安全：deviceToken / accessToken 走脱敏（sha256 前 8 位 + 末 4 位）
- 可观测：EVIDENCE.md 含每步耗时 + 产物相对路径

## 验收标准
| 编号 | 标准 | 通过条件 |
|------|------|----------|
| AC1 | operator-status.json 含 P2.4 taskStateMachine 字段 | JSON 校验通过 |
| AC2 | operator-dashboard.html 含 P2.4 状态机可见性区块 | HTML grep 命中 |
| AC3 | kotlin-coverage-derivative.md 含 heartbeat 鉴权 + 任务重试对照 | md 段落命中 |
| AC4 | 跨项目 evidence/round42-20260607-pokeclaw/ 8 文件齐全 | ls 验证 |
| AC5 | 提交回填 | EVIDENCE.md 含 commit hash + 7 字符短码 |
| AC6 | 评论回填 | kanban 注释含 5 字段 |
| AC7 | 红线守住 | 0 push；无 token 明文；deviceStatus=no_online_device 不假装 |
| AC8 | 业务可感知 | 主控+运营打开 operator-dashboard.html 即可看出"端云任务领取-结果-状态机"链路可见 |

## 暂不做
- 不实现 P1.1 ReDroid 接入（owner-blocked）
- 不联调 48080 真实后端（owner-blocked）
- 不跑真机弱网/离线测试（owner-blocked）
- 不在 evidence 中显示真实 IMEI/deviceToken
- 不在派生文档里写"已对齐"但实际是假的（所有 ✗缺失 项必须如实）
