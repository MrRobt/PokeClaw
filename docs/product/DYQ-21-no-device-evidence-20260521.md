# DYQ-21｜PokeClaw无真机替代证据包与回传契约样本

## 0. 背景与目标对齐
- 背景：`DYQ-9` / `DYQ-14` 因无在线真机与 ADB 串号阻塞，需提供可复现的无真机替代证据包，支持商业化闭环继续推进。
- Claw云端中枢与运行时沙箱：用 mock 后端验证注册、心跳、任务下发、结果回传和经验上报契约。
- PokeClaw端侧执行体：用脚本模拟端侧接收任务、执行任务、回传结果与沉淀经验。
- weflow微信控制底座：本次不修改 WeFlow 旧路径，不引入微信控制链路风险。

## 1. 本次改动文件
- `scripts/mock-dyq-backend.py`：新增 `POST /api/claw-device/experiences/report` 经验上报样本接口，并在 `/api/status` 输出经验样本计数。
- `scripts/dyq21-no-device-evidence.sh`：新增无真机替代证据脚本。
- `docs/product/DYQ-21-no-device-evidence-20260521.md`：本验收文档。
- `artifacts/dyq21-no-device-evidence/20260521-234500/*`：本次执行生成的响应、执行轨迹和汇总。

## 2. 验证命令
```bash
bash -n scripts/dyq21-no-device-evidence.sh
python3 -m py_compile scripts/mock-dyq-backend.py
scripts/dyq21-no-device-evidence.sh artifacts/dyq21-no-device-evidence/20260521-234500
cat artifacts/dyq21-no-device-evidence/20260521-234500/summary.md
cat artifacts/dyq21-no-device-evidence/20260521-234500/responses/experience.json
cat artifacts/dyq21-no-device-evidence/20260521-234500/traces/task_execution_trace.json
```

## 3. 结果
### 3.1 Mock注册、心跳、任务执行、结果回传
- register：HTTP 200，返回 `deviceToken`。
- heartbeat：HTTP 200，返回 `pendingTaskCount=1`。
- pending：HTTP 200，返回任务 `taskUuid=f968ac11-8cc6-475b-98e3-3e3dd82cc98e`。
- result：HTTP 200，返回 `received=true`。
- 证据目录：`artifacts/dyq21-no-device-evidence/20260521-234500/`

### 3.2 任务执行替代轨迹
- 文件：`artifacts/dyq21-no-device-evidence/20260521-234500/traces/task_execution_trace.json`
- 状态序列：`RECEIVED -> RUNNING -> SUCCEEDED`
- 模式：`NO_DEVICE_MOCK`

### 3.3 经验上报契约样本
- endpoint：`POST /api/claw-device/experiences/report`
- experience：HTTP 200，返回 `received=true`。
- experienceId：`exp-6eb86e48`
- `/api/status` 中 `experiences=1`，证明 mock 后端已接收并计数。

## 4. 回传契约样本
```json
{
  "taskUuid": "f968ac11-8cc6-475b-98e3-3e3dd82cc98e",
  "lessonType": "TASK_EXECUTION_SUCCESS",
  "outcome": "SUCCESS",
  "summary": "无真机阻塞期间，mock链路可稳定覆盖注册、心跳、任务执行、结果回传和经验上报契约。",
  "metrics": {
    "executionTimeMs": 245,
    "retryCount": 0,
    "pendingTaskCount": 1
  },
  "evidenceRefs": [
    "responses/register.json",
    "responses/heartbeat.json",
    "responses/pending.json",
    "responses/result.json",
    "traces/task_execution_trace.json"
  ]
}
```

## 5. 风险
- 当前为无真机替代证据，只能解除 mock 级契约样本缺口，不能替代 `adb_real_device=1` 的真实端侧证据。
- 经验上报接口为 mock 样本契约，真实 dyq 后端若采用不同路径或字段，需要由后端联调任务确认。
- 本次未修改 WeFlow 路径，微信控制底座未覆盖新增验证。

## 6. 待验证清单
- [ ] `DYQ-10` 提供真实后端路径后，用相同契约复跑并核对字段差异。
- [ ] `DYQ-14` 提供在线真机后，继续执行 `DYQ-9` 强校验，补齐 logcat 证据。
- [ ] 后端确认经验上报正式接口路径、鉴权方式和字段约束。

## 7. 审计日志
- 2026-05-21 23:45:00 +0800：新增无真机替代证据脚本。
- 2026-05-21 23:45:35 +0800：生成证据目录 `artifacts/dyq21-no-device-evidence/20260521-234500/`。
- 2026-05-21 23:46:23 +0800：整理本验收文档。
