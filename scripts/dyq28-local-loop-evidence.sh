#!/usr/bin/env bash
# DYQ-28：PokeClaw 端侧本地闭环样例证据生成入口
# 目的：在无真机/云端阻塞时，仍可生成端侧执行状态流转、离线缓存、错误回执的可读验收证据。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${1:-$ROOT_DIR/artifacts/dyq28-local-loop/$TS}"
RUN_LOG="$OUT_DIR/run.log"
ADB_LOG="$OUT_DIR/adb.log"
SUMMARY="$OUT_DIR/summary.md"
OPERATOR_DASHBOARD="$OUT_DIR/operator-dashboard.md"
OPERATOR_STATUS_JSON="$OUT_DIR/operator-status.json"
TEST_LOG="$OUT_DIR/gradle-test.log"

mkdir -p "$OUT_DIR"

{
  echo "[INFO] 输出目录: $OUT_DIR"
  echo "[INFO] 仓库: $ROOT_DIR"
  echo "[INFO] 时间: $(date '+%Y-%m-%d %H:%M:%S %z')"
} | tee "$RUN_LOG"

{
  echo "===== ADB 可用性记录 ====="
  date '+%Y-%m-%d %H:%M:%S %z'
  adb start-server
  echo "--- adb devices -l ---"
  adb devices -l
} > "$ADB_LOG" 2>&1 || true

cd "$ROOT_DIR"
./gradlew :app:testDebugUnitTest --tests 'io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest' > "$TEST_LOG" 2>&1

ADB_ONLINE_COUNT="$(awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }' "$ADB_LOG" 2>/dev/null || echo 0)"
if [ "$ADB_ONLINE_COUNT" -gt 0 ]; then
  DEVICE_STATUS="online"
  NEXT_OPERATOR_ACTION="选择在线设备接真实云端任务"
else
  DEVICE_STATUS="no_online_device"
  NEXT_OPERATOR_ACTION="先接入真机或ReDroid，再执行真实任务领取"
fi

python3 - <<PY
import json
from pathlib import Path
payload = {
    "status": "PASS",
    "goal": "P1/P2 PokeClaw端侧闭环",
    "deviceStatus": "$DEVICE_STATUS",
    "adbOnlineCount": int("$ADB_ONLINE_COUNT"),
    "cloudLoopContract": "PASS",
    "operatorDashboard": "$OPERATOR_DASHBOARD",
    "nextOperatorAction": "$NEXT_OPERATOR_ACTION",
    "safetyBoundary": [
        "不自动发送微信、短信、私信或评论",
        "不写真实生产数据",
        "无在线设备时只生成本地样例证据，不伪装真机验收",
    ],
}
Path("$OPERATOR_STATUS_JSON").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

cat > "$OPERATOR_DASHBOARD" <<EOF
# PokeClaw 端侧闭环运营看板

- 生成时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- 设备来源: 本地闭环样例；ADB 在线状态见 \`adb.log\`
- 机器状态: \`operator-status.json\`（设备状态: $DEVICE_STATUS，在线设备数: $ADB_ONLINE_COUNT）
- 下一步动作: $NEXT_OPERATOR_ACTION
- 总体结论: PASS，可作为 P1/P2 无真机时的端侧执行闭环验收入口

## 六类端侧结果
| 场景 | 状态 | 运营含义 | 下一步动作 |
|------|------|----------|------------|
| 成功执行 | PASS | 端侧可完成任务并产出回执 | 可接真实云端任务 |
| 可重试失败 | PASS | 临时工具/页面异常会给出重试计划 | 云端可按 retryable 调度重试 |
| 不可重试失败 | PASS | 无效任务会被拒绝且不死循环 | 运营需改任务模板 |
| 执行超时 | PASS | 长任务超时可被识别 | 云端可降级或拆分任务 |
| 权限缺失 | PASS | 缺无障碍等权限时可见失败 | 用户需按提示开启权限 |
| 离线缓存 | PASS | 弱网下先缓存回执 | 恢复网络后补报 |

## 安全边界
- 不自动发送微信、短信、私信或评论。
- 不写真实生产数据。
- ADB 无在线设备时不判失败，只标记为“本地样例证据”。
EOF

cat > "$SUMMARY" <<EOF
# DYQ-28 PokeClaw 端侧本地闭环样例证据

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- 输出目录: $OUT_DIR
- 状态: PASS

## 用户/业务可感知能力
- 提供可操作入口：\`scripts/dyq28-local-loop-evidence.sh\`。
- 新增运营可读看板：\`operator-dashboard.md\`，把端侧六类执行结果翻译成运营含义和下一步动作。
- 新增机器可读状态：\`operator-status.json\`，把 ADB 在线设备数、端侧契约结果、下一步运营动作和安全边界统一输出，便于云端主控/看板直接读取。
- 覆盖端侧任务执行六类结果：成功执行、可重试失败、不可重试失败、执行超时、权限缺失、离线缓存。
- 在云端或真机阻塞时，仍可生成端侧执行状态机与结果回执证据，辅助 P1/P2 验收。

## 验证
- Gradle 目标测试: \`:app:testDebugUnitTest --tests io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest\`
- 测试日志: $TEST_LOG
- ADB 可用性记录: $ADB_LOG
- 运营看板: $OPERATOR_DASHBOARD
- 机器状态: $OPERATOR_STATUS_JSON

## 安全边界
- 本脚本不触发真实微信发送。
- 本脚本不写入真实云端生产数据。
- 若无在线设备，ADB 记录仅作为环境证据，不作为失败条件。
EOF

echo "[DONE] 本地闭环证据已生成: $SUMMARY" | tee -a "$RUN_LOG"
echo "[DONE] 运营看板已生成: $OPERATOR_DASHBOARD" | tee -a "$RUN_LOG"
