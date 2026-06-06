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

cat > "$SUMMARY" <<EOF
# DYQ-28 PokeClaw 端侧本地闭环样例证据

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- 输出目录: $OUT_DIR
- 状态: PASS

## 用户/业务可感知能力
- 提供可操作入口：\`scripts/dyq28-local-loop-evidence.sh\`。
- 覆盖端侧任务执行六类结果：成功执行、可重试失败、不可重试失败、执行超时、权限缺失、离线缓存。
- 在云端或真机阻塞时，仍可生成端侧执行状态机与结果回执证据，辅助 P1/P2 验收。

## 验证
- Gradle 目标测试: \`:app:testDebugUnitTest --tests io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest\`
- 测试日志: $TEST_LOG
- ADB 可用性记录: $ADB_LOG

## 安全边界
- 本脚本不触发真实微信发送。
- 本脚本不写入真实云端生产数据。
- 若无在线设备，ADB 记录仅作为环境证据，不作为失败条件。
EOF

echo "[DONE] 本地闭环证据已生成: $SUMMARY" | tee -a "$RUN_LOG"
