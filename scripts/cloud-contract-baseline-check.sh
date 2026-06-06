#!/usr/bin/env bash
# cloud-contract-baseline-check.sh
# 端云契约基线闭环入口
# 用法：bash scripts/cloud-contract-baseline-check.sh [out_dir]
# 退出码：0=PASS，1=FAIL，2=BLOCKED

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${1:-$ROOT_DIR/artifacts/cloud-contract-baseline/$TS}"
LOG_FILE="$OUT_DIR/run.log"

mkdir -p "$OUT_DIR"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"; }

log "===== 端云契约基线闭环开始 ====="
log "ROOT_DIR=$ROOT_DIR"
log "OUT_DIR=$OUT_DIR"

cd "$ROOT_DIR"

# 1) baseline 解析 + 端侧覆盖
log "[T1-T7] 跑契约解析 + 端侧覆盖"
POKECLAW_ROOT="$ROOT_DIR" python3 "$ROOT_DIR/scripts/cloud-contract-baseline-check.py" "$OUT_DIR" 2>&1 | tee -a "$LOG_FILE"
RC=${PIPESTATUS[0]}

if [[ $RC -ne 0 ]]; then
  log "[FAIL] baseline 解析失败 (rc=$RC)"
  exit 1
fi

# 2) 端侧 Kotlin 编译
log "[T8] 跑 :app:compileDebugKotlin"
if ! ./gradlew :app:compileDebugKotlin --console=plain --no-daemon 2>&1 | tee -a "$LOG_FILE"; then
  log "[FAIL] 编译失败"
  exit 1
fi
log "[PASS] 编译通过"

# 3) git 状态快照
log "[T9-pre] git status / log 快照"
{
  echo "===== git status --short ====="
  git status --short
  echo
  echo "===== git log -1 ====="
  git log -1 --pretty='%H %s' 2>/dev/null || true
} > "$OUT_DIR/git_status.txt"
cat "$OUT_DIR/git_status.txt" | tee -a "$LOG_FILE"

# 4) EVIDENCE.md 生成（自动段）
EVID="$OUT_DIR/EVIDENCE.md"
cat > "$EVID" <<EOS
# 端云契约基线闭环证据

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- ROOT: $ROOT_DIR
- 任务: t_d2f8d4b7 (djs-loop / cloud-contract-baseline)

## 1. 产物清单
- baseline.json: $(realpath "$OUT_DIR/baseline.json" 2>/dev/null || echo "$OUT_DIR/baseline.json")
- kotlin-coverage.md: $(realpath "$OUT_DIR/kotlin-coverage.md" 2>/dev/null || echo "$OUT_DIR/kotlin-coverage.md")
- run.log: $LOG_FILE
- git_status.txt: $OUT_DIR/git_status.txt

## 2. 覆盖率（自动段）
$(grep -E '^\s*"(endpoints|schemas|request_schemas|response_schemas|coverage)"' "$OUT_DIR/baseline.json" 2>/dev/null || true)

覆盖统计片段（来自脚本 stdout）：
\`\`\`
$(grep -E '"coverage"|"endpoints"|"schemas"' "$LOG_FILE" | head -20)
\`\`\`

## 3. 编译验证
- 命令: \`./gradlew :app:compileDebugKotlin --console=plain --no-daemon\`
- 结论: BUILD SUCCESSFUL（见 run.log）

## 4. git 状态
\`\`\`
$(cat "$OUT_DIR/git_status.txt")
\`\`\`

## 5. 人工备注（由 owner 填写）
- 残余阻塞：
- 下一步建议：
EOS

log "[DONE] 闭环完成，证据见: $EVID"
exit 0
