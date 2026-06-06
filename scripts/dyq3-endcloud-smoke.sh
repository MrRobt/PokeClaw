#!/usr/bin/env bash
# DYQ-3: PokeClaw 端侧执行链路最小商业化冒烟
# 产出：设备注册/心跳、云端下发→端侧回传、ADB最小验证、异常报错证据

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${1:-$ROOT_DIR/artifacts/dyq3-smoke/$TS}"
MOCK_PORT="${MOCK_PORT:-18080}"
BASE_URL="${DYQ_BASE_URL:-http://127.0.0.1:$MOCK_PORT}"
USE_MOCK_BACKEND="${USE_MOCK_BACKEND:-1}"
HEALTH_CHECK="${HEALTH_CHECK:-1}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"
NETWORK_DOWN_BASE_URL="${NETWORK_DOWN_BASE_URL:-http://127.0.0.1:9}"
MOCK_LOG="$OUT_DIR/mock_server.log"
RUN_LOG="$OUT_DIR/smoke_run.log"
ADB_LOG="$OUT_DIR/adb_minimal.log"
RESP_DIR="$OUT_DIR/responses"
NETWORK_ERR_FILE="$RESP_DIR/heartbeat_network_down.err"

mkdir -p "$OUT_DIR" "$RESP_DIR"

pick_free_port() {
  python3 - <<'PY'
import socket
with socket.socket() as s:
    s.bind(('127.0.0.1', 0))
    print(s.getsockname()[1])
PY
}

if [[ "$USE_MOCK_BACKEND" == "1" && -z "${DYQ_BASE_URL:-}" ]] && lsof -i TCP:"$MOCK_PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
  ORIGINAL_MOCK_PORT="$MOCK_PORT"
  MOCK_PORT="$(pick_free_port)"
  BASE_URL="http://127.0.0.1:$MOCK_PORT"
fi

echo "[INFO] 输出目录: $OUT_DIR" | tee -a "$RUN_LOG"
if [[ -n "${ORIGINAL_MOCK_PORT:-}" ]]; then
  echo "[WARN] 默认 mock 端口 $ORIGINAL_MOCK_PORT 已被占用，自动切换到 $MOCK_PORT" | tee -a "$RUN_LOG"
fi
echo "[INFO] BASE_URL: $BASE_URL" | tee -a "$RUN_LOG"

MOCK_PID=""
cleanup() {
  if [[ -n "$MOCK_PID" ]] && kill -0 "$MOCK_PID" 2>/dev/null; then
    kill "$MOCK_PID" 2>/dev/null || true
    wait "$MOCK_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if [[ "$USE_MOCK_BACKEND" == "1" ]]; then
  MOCK_PORT="$MOCK_PORT" python3 -u "$ROOT_DIR/scripts/mock-dyq-backend.py" >"$MOCK_LOG" 2>&1 &
  MOCK_PID=$!
  echo "[INFO] mock 后端已启动, pid=$MOCK_PID" | tee -a "$RUN_LOG"
else
  echo "[INFO] 跳过启动本地mock，使用外部后端: $BASE_URL" | tee -a "$RUN_LOG"
fi

health_response_ok() {
  local body_file="$1"
  python3 - "$body_file" <<'PY'
import json, sys
from pathlib import Path

body = Path(sys.argv[1]).read_text(encoding='utf-8').strip()
if not body:
    raise SystemExit(1)

try:
    obj = json.loads(body)
except json.JSONDecodeError:
    raise SystemExit(1)

if isinstance(obj, dict):
    status = obj.get("status")
    if status == "UP":
        raise SystemExit(0)
    code = obj.get("code")
    if code in (0, 200, "0", "200"):
        raise SystemExit(0)

raise SystemExit(1)
PY
}

wait_health() {
  local retry=0
  local health_body="$RESP_DIR/health_check.json"
  local health_code="$RESP_DIR/health_check.code"
  while (( retry < 30 )); do
    if curl -sS -o "$health_body" -w "%{http_code}" "$BASE_URL$HEALTH_PATH" > "$health_code"; then
      local http_code
      http_code="$(cat "$health_code")"
      if [[ "$http_code" == "200" ]] && health_response_ok "$health_body"; then
        echo "[PASS] 健康检查通过" | tee -a "$RUN_LOG"
        return 0
      fi
    fi
    retry=$((retry + 1))
    sleep 1
  done
  local final_code="000"
  [[ -f "$health_code" ]] && final_code="$(cat "$health_code")"
  echo "[FAIL] 健康检查失败: $BASE_URL$HEALTH_PATH (HTTP=$final_code)" | tee -a "$RUN_LOG"
  return 1
}

build_signature_headers() {
  local token="$1"
  local path="$2"
  local body="$3"
  python3 - "$token" "$path" "$body" <<'PY'
import hashlib, hmac, sys, time, uuid

token, path, body = sys.argv[1], sys.argv[2], sys.argv[3]
timestamp = str(int(time.time() * 1000))
nonce = uuid.uuid4().hex
body_hash = hashlib.sha256(body.encode('utf-8')).hexdigest()
signing_string = "\n".join([timestamp, nonce, path, body_hash])
signature = hmac.new(token.encode('utf-8'), signing_string.encode('utf-8'), hashlib.sha256).hexdigest()
print(timestamp)
print(nonce)
print(signature)
PY
}

request() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local token="${5:-}"

  local resp_file="$RESP_DIR/${name}.json"
  local code_file="$RESP_DIR/${name}.code"
  local auth_header=""
  if [[ -n "$token" ]]; then
    # 真实请求必须携带完整令牌；日志与汇报只写脱敏结果，不输出该变量值。
    auth_header="Authorization: Bearer ${token}"
  fi

  if [[ -n "$body" && -n "$token" ]]; then
    if [[ "$name" == "result" ]]; then
      mapfile -t sig_parts < <(build_signature_headers "$token" "$path" "$body")
      curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
        -H "Content-Type: application/json" \
        -H "$auth_header" \
        -H "X-Claw-Timestamp: ${sig_parts[0]}" \
        -H "X-Claw-Nonce: ${sig_parts[1]}" \
        -H "X-Claw-Signature: ${sig_parts[2]}" \
        -d "$body" > "$code_file"
    else
      curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
        -H "Content-Type: application/json" \
        -H "$auth_header" \
        -d "$body" > "$code_file"
    fi
  elif [[ -n "$body" ]]; then
    curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
      -H "Content-Type: application/json" -d "$body" > "$code_file"
  elif [[ -n "$token" ]]; then
    curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
      -H "$auth_header" > "$code_file"
  else
    curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" > "$code_file"
  fi

  local code
  code="$(cat "$code_file")"
  echo "[HTTP] $name => $code" | tee -a "$RUN_LOG"
}

json_get() {
  local file="$1"
  local expr="$2"
  python3 - "$file" "$expr" <<'PY'
import json, sys
f, expr = sys.argv[1], sys.argv[2]
obj = json.load(open(f, 'r', encoding='utf-8'))
cur = obj
for p in expr.split('.'):
    if p.isdigit():
        cur = cur[int(p)]
    else:
        cur = cur.get(p) if isinstance(cur, dict) else None
    if cur is None:
        break
print("" if cur is None else cur)
PY
}

assert_http() {
  local name="$1"
  local expected_csv="$2"
  local code
  code="$(cat "$RESP_DIR/${name}.code")"
  IFS=',' read -r -a expected_list <<< "$expected_csv"
  for expected in "${expected_list[@]}"; do
    if [[ "$code" == "$expected" ]]; then
      echo "[PASS] $name HTTP 命中允许值=$expected_csv（实际=$code）" | tee -a "$RUN_LOG"
      return 0
    fi
  done
  echo "[FAIL] $name 期望HTTP属于{$expected_csv}, 实际=$code" | tee -a "$RUN_LOG"
  return 1
}

assert_body_code() {
  local name="$1"
  local expected_csv="$2"
  local actual
  actual="$(json_get "$RESP_DIR/${name}.json" "code")"
  IFS=',' read -r -a expected_list <<< "$expected_csv"
  for expected in "${expected_list[@]}"; do
    if [[ "$actual" == "$expected" ]]; then
      echo "[PASS] $name body.code 命中允许值=$expected_csv（实际=$actual）" | tee -a "$RUN_LOG"
      return 0
    fi
  done
  echo "[FAIL] $name 期望body.code属于{$expected_csv}, 实际=$actual" | tee -a "$RUN_LOG"
  return 1
}

if [[ "$HEALTH_CHECK" == "1" ]]; then
  wait_health
else
  echo "[INFO] 已跳过健康检查" | tee -a "$RUN_LOG"
fi

DEVICE_ID="${DEVICE_ID:-pokeclaw-dyq3-$TS}"
REGISTER_BODY="{\"deviceId\":\"$DEVICE_ID\",\"deviceName\":\"PokeClaw-DYQ3\",\"deviceModel\":\"MockModel\",\"androidVersion\":\"14\",\"appVersion\":\"0.7.0\"}"
HEARTBEAT_BODY='{"batteryLevel":78,"isCharging":false,"networkType":"wifi"}'

# 1) 设备注册
request register POST /api/claw-device/register "$REGISTER_BODY"
assert_http register 200
assert_body_code register 0,200
DEVICE_TOKEN="$(json_get "$RESP_DIR/register.json" "data.deviceToken")"
[[ -n "$DEVICE_TOKEN" ]] || { echo "[FAIL] 注册未返回 deviceToken" | tee -a "$RUN_LOG"; exit 1; }
echo "[PASS] 注册返回 deviceToken" | tee -a "$RUN_LOG"

# 2) 心跳
request heartbeat POST /api/claw-device/heartbeat "$HEARTBEAT_BODY" "$DEVICE_TOKEN"
assert_http heartbeat 200
assert_body_code heartbeat 0,200
PENDING_COUNT="$(json_get "$RESP_DIR/heartbeat.json" "data.pendingTaskCount")"
echo "[INFO] pendingTaskCount=$PENDING_COUNT" | tee -a "$RUN_LOG"

# 2.5) 可选：通过管理后台下发一条真实任务种子，用于真实后端闭环验证
if [[ "${ADMIN_SEED_TASK:-0}" == "1" ]]; then
  ADMIN_BASE_URL="${ADMIN_BASE_URL:-$BASE_URL}"
  ADMIN_TENANT_NAME="${ADMIN_TENANT_NAME:-yisheng}"
  ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
  ADMIN_TENANT_ID="${ADMIN_TENANT_ID:-1}"
  if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
    echo "[FAIL] ADMIN_SEED_TASK=1 需要设置 ADMIN_PASSWORD" | tee -a "$RUN_LOG"
    exit 1
  fi
  ADMIN_LOGIN_BODY="{\"tenantName\":\"$ADMIN_TENANT_NAME\",\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}"
  curl -sS -o "$RESP_DIR/admin_login.json" -w "%{http_code}" -X POST "$ADMIN_BASE_URL/admin-api/system/auth/login" \
    -H "Content-Type: application/json" \
    -H "tenant-id: $ADMIN_TENANT_ID" \
    -d "$ADMIN_LOGIN_BODY" > "$RESP_DIR/admin_login.code"
  assert_http admin_login 200
  assert_body_code admin_login 0,200
  ADMIN_TOKEN="$(json_get "$RESP_DIR/admin_login.json" "data.accessToken")"
  [[ -n "$ADMIN_TOKEN" ]] || { echo "[FAIL] 管理后台登录未返回 accessToken" | tee -a "$RUN_LOG"; exit 1; }
  ADMIN_EXECUTE_BODY="{\"command\":\"DYQ-3真实端云闭环种子 requestId=$DEVICE_ID eventId=round8\",\"mode\":\"auto\"}"
  # 真实请求必须携带完整管理后台令牌；日志与汇报只写凭证来源文件和脱敏结果。
  admin_auth_header="Authorization: Bearer ${ADMIN_TOKEN}"
  curl -sS -o "$RESP_DIR/admin_execute.json" -w "%{http_code}" -X POST "$ADMIN_BASE_URL/admin-api/claw/device/$DEVICE_ID/execute" \
    -H "Content-Type: application/json" \
    -H "tenant-id: $ADMIN_TENANT_ID" \
    -H "$admin_auth_header" \
    -d "$ADMIN_EXECUTE_BODY" > "$RESP_DIR/admin_execute.code"
  assert_http admin_execute 200
  assert_body_code admin_execute 0,200
  SEEDED_TASK_UUID="$(json_get "$RESP_DIR/admin_execute.json" "data")"
  [[ -n "$SEEDED_TASK_UUID" ]] || { echo "[FAIL] 管理后台下发任务未返回 taskUuid" | tee -a "$RUN_LOG"; exit 1; }
  echo "[PASS] 管理后台已下发真实任务 taskUuid=$SEEDED_TASK_UUID" | tee -a "$RUN_LOG"
fi

# 3) 拉取待处理任务（云端下发）
request pending GET "/api/claw-device/devices/$DEVICE_ID/pending-tasks" "" "$DEVICE_TOKEN"
assert_http pending 200
assert_body_code pending 0,200
TASK_UUID="$(python3 - "$RESP_DIR/pending.json" <<'PY'
import json, sys
from pathlib import Path
obj = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
data = obj.get('data') if isinstance(obj, dict) else None
if isinstance(data, list) and data:
    print(data[0].get('uuid') or data[0].get('taskUuid') or '')
elif isinstance(data, dict):
    tasks = data.get('list') or data.get('tasks') or []
    if isinstance(tasks, list) and tasks:
        print(tasks[0].get('uuid') or tasks[0].get('taskUuid') or '')
PY
)"
if [[ -z "$TASK_UUID" ]]; then
  echo "[BLOCKED] 云端暂无待下发任务：register/heartbeat/pending 已通过，但无法验证任务结果回传" | tee -a "$RUN_LOG"
  cat > "$OUT_DIR/summary.md" <<EOS
# DYQ-3 端侧执行链路最小冒烟结果

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- BASE_URL: $BASE_URL
- 设备ID: $DEVICE_ID
- 输出目录: $OUT_DIR
- 状态: BLOCKED

## 1. 已通过
- 健康检查: 通过
- register: HTTP $(cat "$RESP_DIR/register.code")
- heartbeat: HTTP $(cat "$RESP_DIR/heartbeat.code")
- pending: HTTP $(cat "$RESP_DIR/pending.code")
- pendingTaskCount: $PENDING_COUNT

## 2. 阻塞点
- 云端没有返回待执行任务 uuid，无法继续验证 result 回传。
- 下一步需要先由云端创建/分配一条面向设备 $DEVICE_ID 的待执行任务，或提供真实任务种子接口。
EOS
  exit 2
fi
echo "[PASS] 拉取到任务 taskUuid=$TASK_UUID" | tee -a "$RUN_LOG"

# 4) 任务结果回传（端侧回传）
RESULT_BODY="{\"status\":\"SUCCESS\",\"result\":\"DYQ-3最小链路执行成功\",\"executionTimeMs\":321,\"modelUsed\":\"local\"}"
request result POST "/api/claw-device/tasks/$TASK_UUID/result" "$RESULT_BODY" "$DEVICE_TOKEN"
assert_http result 200
assert_body_code result 0,200
echo "[PASS] 任务结果已回传 taskUuid=$TASK_UUID" | tee -a "$RUN_LOG"

# 5) 异常场景：缺失/无效token（用户可见报错）
request heartbeat_no_token POST /api/claw-device/heartbeat "$HEARTBEAT_BODY"
assert_http heartbeat_no_token 200,401
NO_TOKEN_CODE="$(json_get "$RESP_DIR/heartbeat_no_token.json" "code")"
NO_TOKEN_MSG="$(json_get "$RESP_DIR/heartbeat_no_token.json" "msg")"
if [[ "$NO_TOKEN_CODE" == "401" ]]; then
  echo "[PASS] 无token报错可见: code=$NO_TOKEN_CODE, msg=$NO_TOKEN_MSG" | tee -a "$RUN_LOG"
else
  echo "[FAIL] 无token未返回预期业务码401" | tee -a "$RUN_LOG"
  exit 1
fi

request heartbeat_bad_token POST /api/claw-device/heartbeat "$HEARTBEAT_BODY" "bad-token"
assert_http heartbeat_bad_token 200,401
BAD_TOKEN_CODE="$(json_get "$RESP_DIR/heartbeat_bad_token.json" "code")"
BAD_TOKEN_MSG="$(json_get "$RESP_DIR/heartbeat_bad_token.json" "msg")"
if [[ "$BAD_TOKEN_CODE" == "401" ]]; then
  echo "[PASS] 无效token报错可见: code=$BAD_TOKEN_CODE, msg=$BAD_TOKEN_MSG" | tee -a "$RUN_LOG"
else
  echo "[FAIL] 无效token未返回预期业务码401" | tee -a "$RUN_LOG"
  exit 1
fi

# 6) 弱网/断网异常：连接失败证据（用户可见报错基础证据）
set +e
curl -sS --connect-timeout 2 --max-time 4 -o /dev/null \
  -X POST "$NETWORK_DOWN_BASE_URL/api/claw-device/heartbeat" \
  -H "Content-Type: application/json" -H "Authorization: Bearer mock-network-down-token" \
  -d "$HEARTBEAT_BODY" 2>"$NETWORK_ERR_FILE"
NETWORK_EXIT=$?
set -e
if [[ "$NETWORK_EXIT" != "0" ]]; then
  echo "[PASS] 网络断连异常已触发: exit=$NETWORK_EXIT" | tee -a "$RUN_LOG"
else
  echo "[FAIL] 网络断连异常未触发，检查NETWORK_DOWN_BASE_URL=$NETWORK_DOWN_BASE_URL" | tee -a "$RUN_LOG"
  exit 1
fi

# 7) ADB 最小验证记录
{
  echo "===== ADB 最小验证 ====="
  date '+%Y-%m-%d %H:%M:%S %z'
  adb start-server
  echo "--- adb devices -l ---"
  adb devices -l
  echo "--- adb shell getprop ro.product.model (若无设备会失败) ---"
  set +e
  adb shell getprop ro.product.model
  local_exit=$?
  set -e
  echo "adb shell exitCode=$local_exit"
} > "$ADB_LOG" 2>&1 || true

cat > "$OUT_DIR/summary.md" <<EOS
# DYQ-3 端侧执行链路最小冒烟结果

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- BASE_URL: $BASE_URL
- 设备ID: $DEVICE_ID
- 任务UUID: $TASK_UUID
- 输出目录: $OUT_DIR

## 1. 设备注册/心跳记录
- register: HTTP $(cat "$RESP_DIR/register.code")
- heartbeat: HTTP $(cat "$RESP_DIR/heartbeat.code")
- pendingTaskCount: $PENDING_COUNT

## 2. 云端下发到端侧回传链路日志
- pending: HTTP $(cat "$RESP_DIR/pending.code")
- result: HTTP $(cat "$RESP_DIR/result.code")
- taskUuid: $TASK_UUID

## 3. 异常场景用户可见报错
- 无token: code=$NO_TOKEN_CODE, msg=$NO_TOKEN_MSG
- 无效token: code=$BAD_TOKEN_CODE, msg=$BAD_TOKEN_MSG
- 弱网/断网: curl_exit=$NETWORK_EXIT, err_file=$NETWORK_ERR_FILE

## 4. 弱网/断网错误原始输出
\`\`\`
$(cat "$NETWORK_ERR_FILE")
\`\`\`

## 5. ADB 最小验证记录
- 见: $ADB_LOG
EOS

echo "[DONE] 冒烟完成，证据见: $OUT_DIR" | tee -a "$RUN_LOG"
