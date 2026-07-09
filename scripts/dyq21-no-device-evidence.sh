#!/usr/bin/env bash
# DYQ-21: 无真机替代证据包，覆盖注册、心跳、任务执行、结果回传和经验上报样本。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${1:-$ROOT_DIR/artifacts/dyq21-no-device-evidence/$TS}"
RESP_DIR="$OUT_DIR/responses"
TRACE_DIR="$OUT_DIR/traces"
RUN_LOG="$OUT_DIR/run.log"
SUMMARY="$OUT_DIR/summary.md"
MOCK_LOG="$OUT_DIR/mock_server.log"

MOCK_PORT="${MOCK_PORT:-18080}"
BASE_URL="${DYQ_BASE_URL:-http://127.0.0.1:$MOCK_PORT}"
USE_MOCK_BACKEND="${USE_MOCK_BACKEND:-1}"
HEALTH_CHECK="${HEALTH_CHECK:-1}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"
EXPERIENCE_PATH="${EXPERIENCE_PATH:-/api/claw-device/experiences/report}"

mkdir -p "$OUT_DIR" "$RESP_DIR" "$TRACE_DIR"

echo "[INFO] 输出目录: $OUT_DIR" | tee -a "$RUN_LOG"
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
  python3 -u "$ROOT_DIR/scripts/mock-dyq-backend.py" >"$MOCK_LOG" 2>&1 &
  MOCK_PID=$!
  echo "[INFO] mock后端已启动 pid=$MOCK_PID" | tee -a "$RUN_LOG"
else
  echo "[INFO] 使用外部后端: $BASE_URL" | tee -a "$RUN_LOG"
fi

wait_health() {
  local retry=0
  while (( retry < 30 )); do
    if curl -fsS "$BASE_URL$HEALTH_PATH" >/dev/null 2>&1; then
      echo "[PASS] 健康检查通过" | tee -a "$RUN_LOG"
      return 0
    fi
    retry=$((retry + 1))
    sleep 1
  done
  echo "[FAIL] 健康检查失败: $BASE_URL$HEALTH_PATH" | tee -a "$RUN_LOG"
  return 1
}

request() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local token="${5:-}"

  local resp_file="$RESP_DIR/${name}.json"
  local code_file="$RESP_DIR/${name}.code"

  if [[ -n "$body" && -n "$token" ]]; then
    curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
      -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d "$body" > "$code_file"
  elif [[ -n "$body" ]]; then
    curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
      -H "Content-Type: application/json" -d "$body" > "$code_file"
  elif [[ -n "$token" ]]; then
    curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
      -H "Authorization: Bearer $token" > "$code_file"
  else
    curl -sS -o "$resp_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" > "$code_file"
  fi

  echo "[HTTP] $name => $(cat "$code_file")" | tee -a "$RUN_LOG"
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
  local expected="$2"
  local actual
  actual="$(cat "$RESP_DIR/${name}.code")"
  if [[ "$actual" == "$expected" ]]; then
    echo "[PASS] $name HTTP=$expected" | tee -a "$RUN_LOG"
  else
    echo "[FAIL] $name 期望HTTP=$expected 实际=$actual" | tee -a "$RUN_LOG"
    return 1
  fi
}

if [[ "$HEALTH_CHECK" == "1" ]]; then
  wait_health
else
  echo "[INFO] 跳过健康检查" | tee -a "$RUN_LOG"
fi

DEVICE_ID="pokeclaw-dyq21-$TS"
REGISTER_BODY="{\"deviceId\":\"$DEVICE_ID\",\"deviceName\":\"PokeClaw-DYQ21-NoDevice\",\"deviceModel\":\"NoDeviceMock\",\"androidVersion\":\"14\",\"appVersion\":\"0.7.0\",\"capabilities\":[\"mock-register\",\"mock-heartbeat\",\"mock-task-execute\",\"mock-experience-report\"]}"
HEARTBEAT_BODY='{"batteryLevel":81,"isCharging":false,"networkType":"mock-wifi","currentTaskStatus":"IDLE"}'

request register POST /api/claw-device/register "$REGISTER_BODY"
assert_http register 200
DEVICE_TOKEN="$(json_get "$RESP_DIR/register.json" "data.deviceToken")"
[[ -n "$DEVICE_TOKEN" ]] || { echo "[FAIL] register 未返回 deviceToken" | tee -a "$RUN_LOG"; exit 1; }
echo "[PASS] register 返回 deviceToken" | tee -a "$RUN_LOG"

request heartbeat POST /api/claw-device/heartbeat "$HEARTBEAT_BODY" "$DEVICE_TOKEN"
assert_http heartbeat 200
PENDING_COUNT="$(json_get "$RESP_DIR/heartbeat.json" "data.pendingTaskCount")"
echo "[INFO] pendingTaskCount=$PENDING_COUNT" | tee -a "$RUN_LOG"

request pending GET "/api/claw-device/devices/$DEVICE_ID/pending-tasks" "" "$DEVICE_TOKEN"
assert_http pending 200
TASK_UUID="$(json_get "$RESP_DIR/pending.json" "data.0.uuid")"
TASK_TYPE="$(json_get "$RESP_DIR/pending.json" "data.0.type")"
[[ -n "$TASK_UUID" ]] || { echo "[FAIL] pending 未返回 taskUuid" | tee -a "$RUN_LOG"; exit 1; }
echo "[PASS] pending 获取 taskUuid=$TASK_UUID" | tee -a "$RUN_LOG"

cat > "$TRACE_DIR/task_execution_trace.json" <<EOS
{
  "deviceId": "$DEVICE_ID",
  "taskUuid": "$TASK_UUID",
  "taskType": "$TASK_TYPE",
  "mode": "NO_DEVICE_MOCK",
  "events": [
    {"status": "RECEIVED", "message": "无真机替代链路收到云端任务"},
    {"status": "RUNNING", "message": "模拟端侧执行 SIMPLE_ACTION/open_app"},
    {"status": "SUCCEEDED", "message": "模拟执行完成并准备回传结果"}
  ],
  "artifacts": [
    "responses/register.json",
    "responses/heartbeat.json",
    "responses/pending.json"
  ]
}
EOS

RESULT_BODY="{\"status\":\"SUCCESS\",\"result\":\"DYQ-21无真机替代执行成功：已模拟接收、执行并回传任务\",\"executionTimeMs\":245,\"modelUsed\":\"no-device-mock\",\"artifacts\":[\"$TRACE_DIR/task_execution_trace.json\"]}"
request result POST "/api/claw-device/tasks/$TASK_UUID/result" "$RESULT_BODY" "$DEVICE_TOKEN"
assert_http result 200
RESULT_RECEIVED="$(json_get "$RESP_DIR/result.json" "data.received")"
[[ "$RESULT_RECEIVED" == "True" || "$RESULT_RECEIVED" == "true" ]] || { echo "[FAIL] result 未确认 received=true" | tee -a "$RUN_LOG"; exit 1; }
echo "[PASS] result 回传成功 taskUuid=$TASK_UUID" | tee -a "$RUN_LOG"

EXPERIENCE_BODY="{\"taskUuid\":\"$TASK_UUID\",\"lessonType\":\"TASK_EXECUTION_SUCCESS\",\"outcome\":\"SUCCESS\",\"summary\":\"无真机阻塞期间，mock链路可稳定覆盖注册、心跳、任务执行、结果回传和经验上报契约。\",\"metrics\":{\"executionTimeMs\":245,\"retryCount\":0,\"pendingTaskCount\":$PENDING_COUNT},\"evidenceRefs\":[\"responses/register.json\",\"responses/heartbeat.json\",\"responses/pending.json\",\"responses/result.json\",\"traces/task_execution_trace.json\"]}"
request experience POST "$EXPERIENCE_PATH" "$EXPERIENCE_BODY" "$DEVICE_TOKEN"
assert_http experience 200
EXPERIENCE_RECEIVED="$(json_get "$RESP_DIR/experience.json" "data.received")"
EXPERIENCE_ID="$(json_get "$RESP_DIR/experience.json" "data.experienceId")"
[[ "$EXPERIENCE_RECEIVED" == "True" || "$EXPERIENCE_RECEIVED" == "true" ]] || { echo "[FAIL] experience 未确认 received=true" | tee -a "$RUN_LOG"; exit 1; }
echo "[PASS] experience 上报成功 experienceId=$EXPERIENCE_ID" | tee -a "$RUN_LOG"

request status GET /api/status
assert_http status 200
EXPERIENCE_COUNT="$(json_get "$RESP_DIR/status.json" "experiences")"

cat > "$SUMMARY" <<EOS
# DYQ-21 无真机替代证据包汇总

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- BASE_URL: $BASE_URL
- 输出目录: $OUT_DIR
- deviceId: $DEVICE_ID
- taskUuid: $TASK_UUID
- experienceId: $EXPERIENCE_ID

## 1. Mock注册、心跳、任务执行、结果回传
- register HTTP: $(cat "$RESP_DIR/register.code")
- heartbeat HTTP: $(cat "$RESP_DIR/heartbeat.code")
- pending HTTP: $(cat "$RESP_DIR/pending.code")
- result HTTP: $(cat "$RESP_DIR/result.code")
- pendingTaskCount: $PENDING_COUNT
- result.received: $RESULT_RECEIVED

## 2. 经验上报样本
- endpoint: $EXPERIENCE_PATH
- experience HTTP: $(cat "$RESP_DIR/experience.code")
- experience.received: $EXPERIENCE_RECEIVED
- mock experience count: $EXPERIENCE_COUNT

## 3. 关键证据文件
- $RESP_DIR/register.json
- $RESP_DIR/heartbeat.json
- $RESP_DIR/pending.json
- $RESP_DIR/result.json
- $RESP_DIR/experience.json
- $TRACE_DIR/task_execution_trace.json
- $MOCK_LOG
EOS

echo "[DONE] DYQ-21 无真机替代证据包完成: $OUT_DIR" | tee -a "$RUN_LOG"
