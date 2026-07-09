#!/usr/bin/env bash
# P 层 P2.4 派生：端侧任务状态机可见性
# 输入：artifacts/dyq28-local-loop/<ts>/  （来自 dyq28-local-loop-evidence.sh）
# 输出：在 operator-status.json 增 taskStateMachine 字段；
#       在 operator-dashboard.html 追加"端侧任务状态机可见性"区块
# 原则：不假装真机；counts=0 + lastTaskId="no_task" + source=明确标注

set -euo pipefail

BASE_DIR="${1:?用法: $0 <dyq28-local-loop-evidence 产物目录>}"
STATUS_JSON="$BASE_DIR/operator-status.json"
DASH_HTML="$BASE_DIR/operator-dashboard.html"

if [ ! -f "$STATUS_JSON" ]; then
  echo "[ERROR] 找不到 $STATUS_JSON" >&2
  exit 2
fi

if [ ! -f "$DASH_HTML" ]; then
  echo "[ERROR] 找不到 $DASH_HTML" >&2
  exit 2
fi

# 1) 给 operator-status.json 增 taskStateMachine 字段
python3 - <<PY
import json
from pathlib import Path

p = Path("$STATUS_JSON")
data = json.loads(p.read_text(encoding="utf-8"))

# 端侧无任务时统一 0 起步；不写假 uuid
data["taskStateMachine"] = {
    "states": ["SUCCESS", "FAILED", "RUNNING", "CANCELLED"],
    "counts": {"SUCCESS": 0, "FAILED": 0, "RUNNING": 0, "CANCELLED": 0},
    "lastTaskId": "no_task",
    "source": "device-empty-or-sample-not-fake",
    "note": "ADB 在线=0，状态机为契约化样例，不假装真机任务"
}

p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"[OK] {p} 已增 taskStateMachine 字段")
PY

# 2) 给 operator-dashboard.html 追加状态机可见性区块（在 </main> 前）
python3 - <<'PY'
from pathlib import Path
import re

p = Path("/dev/stdin")
PY

# 直接 shell here-doc 写追加内容
TMP_NEW_BLOCK="$(mktemp)"
cat > "$TMP_NEW_BLOCK" <<'EOF'

    <section class="card" style="margin-top:18px" id="task-state-machine">
      <h2>端侧任务状态机可见性</h2>
      <p>来源：operator-status.json.taskStateMachine；ADB 在线=0 时为契约化样例。</p>
      <table>
        <thead><tr><th>状态</th><th>计数</th><th>含义</th></tr></thead>
        <tbody>
          <tr><td>SUCCESS</td><td>0</td><td>端侧正常完成任务并已回传结果</td></tr>
          <tr><td>FAILED</td><td>0</td><td>端侧任务失败（区分 retryable / non-retryable）</td></tr>
          <tr><td>RUNNING</td><td>0</td><td>端侧任务正在执行中</td></tr>
          <tr><td>CANCELLED</td><td>0</td><td>端侧任务被用户/云端取消</td></tr>
        </tbody>
      </table>
      <ul>
        <li>上次任务 ID：<code>no_task</code></li>
        <li>数据来源：<code>device-empty-or-sample-not-fake</code>（无真机）</li>
        <li>状态机契约：与端云契约基线 4 态一致（SUCCESS/FAILED/RUNNING/CANCELLED）</li>
      </ul>
    </section>
EOF

# 把新块插到 </main> 之前
python3 - <<PY
from pathlib import Path

p = Path("$DASH_HTML")
html = p.read_text(encoding="utf-8")
new_block = Path("$TMP_NEW_BLOCK").read_text(encoding="utf-8")

if "task-state-machine" in html:
    print(f"[SKIP] {p} 已含 task-state-machine 区块")
else:
    needle = "</main>"
    if needle not in html:
        print(f"[ERROR] {p} 找不到 </main>" , flush=True)
        raise SystemExit(2)
    html2 = html.replace(needle, new_block + "\n  " + needle, 1)
    p.write_text(html2, encoding="utf-8")
    print(f"[OK] {p} 已追加状态机可见性区块")
PY

rm -f "$TMP_NEW_BLOCK"

echo "[DONE] P2.4 派生完成：$BASE_DIR"
