# DYQ-14｜在线真机与 ADB 串号保障记录

## 0. 目标对齐
- Claw 云端中枢与运行时沙箱：为 DYQ-9 的注册、心跳、任务回传验收解除 ADB 设备阻塞。
- PokeClaw 端侧执行体：确认当前执行环境是否存在可用 Android 真机/模拟器串号。
- weflow 微信控制底座：本次不改 weflow 链路，仅提供端侧验收前置条件。

## 1. 本次改动文件
- `docs/product/DYQ-14-adb-device-availability-20260521.md`：新增 DYQ-14 设备可用性审计记录。
- `artifacts/dyq14-adb-availability/20260521-231852/*`：新增本次 ADB 可用性检查与 DYQ-9 强校验证据。

## 2. 验证命令
```bash
adb version
adb kill-server
adb start-server
adb devices -l
ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh artifacts/dyq14-adb-availability/20260521-231852/dyq9-required
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -Command "adb version; adb devices -l"
```

## 3. 结果
- WSL 侧 ADB 可用：`Android Debug Bridge version 1.0.41`，路径 `/opt/android-sdk/platform-tools/adb`。
- 重启 ADB server 后，`adb devices -l` 仍只返回表头，未发现 `device`、`offline` 或 `unauthorized` 设备。
- DYQ-9 强校验命令已执行，接口侧注册、心跳、任务拉取、结果回传均通过 mock 后端闭环；失败点仅在 ADB 真机检查。
- `artifacts/dyq14-adb-availability/20260521-231852/dyq9-required/adb_minimal.log` 记录：
  - `adb_real_device=0`
  - `[FAIL] ADB_REQUIRED=1 但无可用设备`
- Windows PowerShell 可执行文件存在，但 Windows 侧 PATH 未提供 `adb` 命令，无法从当前 WSL 心跳直接查询 Windows ADB 设备列表。

## 4. 当前阻塞
- 当前无法提供 DYQ-9 所需 `ADB_SERIAL=<serial>`，因为执行环境没有在线 Android 真机/模拟器。
- DYQ-14 应保持阻塞，直到设备保障方提供 USB 或 TCP/IP ADB 串号，并保证至少 20 分钟在线窗口。

## 4.1 2026-05-21 23:54 巡检纠偏响应
- 最新复核：`adb version && adb devices -l` 通过执行，`adb devices -l` 仍只返回表头，无在线设备。
- 无真机原因：当前 WSL/Codex CLI 环境没有 Android 真机/模拟器可见串号，也没有可达的 TCP/IP ADB 地址。
- 替代证据：已回链 `DYQ-21` 无真机替代证据包，路径 `artifacts/dyq21-no-device-evidence/20260521-234500/summary.md`。
- 替代范围：`DYQ-21` 已覆盖 mock 注册、心跳、任务执行、结果回传、经验上报样本，可用于商业化演示的无真机契约证据；不能替代真实 `adb_real_device=1`。

## 5. 解除阻塞动作
设备保障方需要执行其一：

1. USB 真机接入当前运行环境，并确认 `adb devices -l` 出现 `<serial> device ...`。
2. TCP/IP 设备接入，提供形如 `<host>:5555` 的串号，并确认 `adb connect <host>:5555` 后 `adb devices -l` 为 `device`。

解除后复跑：

```bash
ADB_SERIAL=<serial> ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh
```

## 6. 风险
- 若设备只接入 Windows 但未桥接给 WSL，当前 Codex CLI 执行环境仍无法通过 `/opt/android-sdk/platform-tools/adb` 发现设备。
- 若设备状态为 `unauthorized`，需要人工在手机端确认 USB 调试授权。
- 若使用 TCP/IP ADB，需要确认设备与 WSL 网络互通且 5555 端口可达。

## 7. 待验证清单
- [ ] 提供在线真机/模拟器串号。
- [ ] 复跑 `ADB_SERIAL=<serial> ADB_REQUIRED=1 scripts/dyq9-adb-min-evidence.sh`。
- [ ] 回传新产物目录，并确认 `adb_real_device=1`。
- [ ] 补齐端侧 logcat 中 `register`、`heartbeat`、`pending`、`result` 关键字证据。

## 8. 审计日志
- 2026-05-21 23:18:52 +0800：执行 ADB 可用性检查并启动 DYQ-9 强校验。
- 2026-05-21 23:19:00 +0800：确认 `adb_real_device=0`，强校验因无设备退出 1。
- 2026-05-21 23:19:34 +0800：补充 DYQ-14 专属设备保障审计文档。
- 2026-05-21 23:54:26 +0800：按巡检纠偏复核 ADB 状态，确认仍无设备，并回链 DYQ-21 替代证据包。
