# DYQ-9｜验证/真机ADB最小证据补齐（注册-心跳-回传）

## 0. 目标对齐（商业化三大目标）
- Claw云端中枢与运行时沙箱：验证设备注册、心跳、任务回传接口最小可用性与异常可见性。
- PokeClaw端侧执行体：补齐端侧链路最小证据脚本与可复跑产物。
- weflow微信控制底座：本次不改weflow链路，仅保持兼容，不引入新风险。

## 1. 本次改动文件
- `scripts/dyq9-adb-min-evidence.sh`：新增 DYQ-9 专用最小证据采集脚本。
- `docs/product/DYQ-9-adb-min-evidence-20260521.md`：本验收证据文档。
- `artifacts/dyq9-adb-min/20260521-230328/*`：本次执行产物（响应、日志、汇总）。

## 2. 验证命令
```bash
# 1) 语法检查
bash -n scripts/dyq9-adb-min-evidence.sh

# 2) 执行最小证据采集（默认启动本地mock后端）
scripts/dyq9-adb-min-evidence.sh

# 3) 查看汇总
cat artifacts/dyq9-adb-min/20260521-230328/summary.md

# 4) 查看关键证据
cat artifacts/dyq9-adb-min/20260521-230328/responses/register.json
cat artifacts/dyq9-adb-min/20260521-230328/responses/heartbeat.json
cat artifacts/dyq9-adb-min/20260521-230328/responses/pending.json
cat artifacts/dyq9-adb-min/20260521-230328/responses/result.json
cat artifacts/dyq9-adb-min/20260521-230328/adb_minimal.log

# 5) 真机强校验模式（无设备时应失败）
ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh

# 6) 对接真实后端复跑（不启mock）
USE_MOCK_BACKEND=0 HEALTH_CHECK=0 DYQ_BASE_URL=http://<dyq-backend-host>:<port> scripts/dyq9-adb-min-evidence.sh
```

## 3. 结果
### 3.1 注册-心跳-回传最小闭环
- register：HTTP 200，返回 `deviceToken`。
- heartbeat：HTTP 200，返回 `pendingTaskCount=1`。
- pending-tasks：HTTP 200，返回 `taskUuid=94465704-36c1-48fc-946b-5955647c9e6b`。
- result：HTTP 200，任务回传 `received=true`。

证据目录：`artifacts/dyq9-adb-min/20260521-230328/`

### 3.2 异常场景可见性
- 缺失 token：HTTP 200 + 业务码 `code=401`。
- 无效 token：HTTP 200 + 业务码 `code=401`。
- 断网：`curl exit=7`，已落盘原始错误到 `heartbeat_network_down.err`。

### 3.3 ADB 最小证据
- 已执行：`adb start-server`、`adb devices -l`。
- 当前结果：`adb_real_device=0`（环境无在线真机/模拟器）。
- 证据文件：`artifacts/dyq9-adb-min/20260521-230328/adb_minimal.log`。

### 3.4 真机强校验模式（阻塞可观测）
- 执行：`ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh`
- 结果：进程退出码 `1`，并在 `adb_minimal.log` 记录 `[FAIL] ADB_REQUIRED=1 但无可用设备`。
- 证据目录：`artifacts/dyq9-adb-min/20260521-230423/`。

## 4. 风险
- 当前闭环基于 `scripts/mock-dyq-backend.py`，尚未覆盖真实 dyq 后端鉴权策略差异。
- 当前环境无在线真机，未采集到端侧 logcat 的真实注册/心跳/回传日志。

## 5. 待验证清单
- [ ] 接入真机并开启 USB 调试，复跑 `ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh`。
- [ ] 对接真实 dyq 后端复跑，核对真实 `HTTP` 与 `code` 语义是否一致。
- [ ] 补齐真机 logcat 关键字证据：`register`、`heartbeat`、`pending`、`result`。

## 6. 阻塞与解除动作
- 阻塞项：真机 ADB 证据缺失（`adb_real_device=0`）。
- unblock owner：设备调试负责人（提供可用真机序列号并保持在线）。
- unblock action：执行 `ADB_SERIAL=<serial> ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh` 并回传新产物目录。

## 6.1 巡检纠偏后的下一条可执行命令
- 当前无真机时，先引用 `DYQ-21` 替代证据包作为商业化临时闭环证据：`artifacts/dyq21-no-device-evidence/20260521-234500/summary.md`。
- 一旦拿到在线真机或 TCP/IP ADB 串号，下一条命令固定为：

```bash
ADB_SERIAL=<serial> ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh
```

- 该命令的通过标准：`adb_real_device=1`，并产出注册、心跳、pending、result 的 logcat 关键字证据。

## 7. 审计日志
- 2026-05-21 23:02:xx +0800：新增脚本 `scripts/dyq9-adb-min-evidence.sh`。
- 2026-05-21 23:03:28 +0800：执行脚本并生成证据目录 `artifacts/dyq9-adb-min/20260521-230328/`。
- 2026-05-21 23:04:xx +0800：整理本验收文档并标注阻塞项与解除动作。
- 2026-05-21 23:54:26 +0800：按巡检纠偏补充 DYQ-21 替代证据包回链与下一条强校验命令。
