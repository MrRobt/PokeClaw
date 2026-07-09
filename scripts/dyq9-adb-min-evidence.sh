#!/usr/bin/env bash
# DYQ-9: 真机ADB最小证据补齐（注册-心跳-回传）
# 产出：设备注册/心跳/任务回传证据、ADB最小证据、异常场景证据

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${1:-$ROOT_DIR/artifacts/dyq9-adb-min/$TS}"
RESP_DIR="$OUT_DIR/responses"
RUN_LOG="$OUT_DIR/run.log"
SUMMARY="$OUT_DIR/summary.md"
ADB_LOG="$OUT_DIR/adb_minimal.log"
MOCK_LOG="$OUT_DIR/mock_server.log"

MOCK_PORT="${MOCK_PORT:-18080}"
BASE_URL="${DYQ_BASE_URL:-http://127.0.0.1:$MOCK_PORT}"
USE_MOCK_BACKEND="${USE_MOCK_BACKEND:-1}"
HEALTH_CHECK="${HEALTH_CHECK:-1}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"

ADB_REQUIRED="${ADB_REQUIRED:-0}"
ADB_SERIAL="${ADB_SERIAL:-}"
NETWORK_DOWN_BASE_URL="${NETWORK_DOWN_BASE_URL:-http://127.0.0.1:9}"

mkdir -p "$OUT_DIR" "$RESP_DIR"

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

assert_auth_error() {
  local name="$1"
  local http_code
  local biz_code
  http_code="$(cat "$RESP_DIR/${name}.code")"
  biz_code="$(json_get "$RESP_DIR/${name}.json" "code")"
  if [[ "$http_code" == "401" || "$biz_code" == "401" ]]; then
    echo "[PASS] $name 鉴权异常可见 http=$http_code biz=$biz_code" | tee -a "$RUN_LOG"
  else
    echo "[FAIL] $name 未返回预期鉴权错误 http=$http_code biz=$biz_code" | tee -a "$RUN_LOG"
    return 1
  fi
}

if [[ "$HEALTH_CHECK" == "1" ]]; then
  wait_health
else
  echo "[INFO] 跳过健康检查" | tee -a "$RUN_LOG"
fi

DEVICE_ID="pokeclaw-dyq9-$TS"
REGISTER_BODY="{\"deviceId\":\"$DEVICE_ID\",\"deviceName\":\"PokeClaw-DYQ9\",\"deviceModel\":\"MockModel\",\"androidVersion\":\"14\",\"appVersion\":\"0.7.0\"}"
HEARTBEAT_BODY='{"batteryLevel":73,"isCharging":false,"networkType":"wifi"}'

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
[[ -n "$TASK_UUID" ]] || { echo "[FAIL] pending 未返回 taskUuid" | tee -a "$RUN_LOG"; exit 1; }
echo "[PASS] pending 获取 taskUuid=$TASK_UUID" | tee -a "$RUN_LOG"

RESULT_BODY="{\"status\":\"SUCCESS\",\"result\":\"DYQ-9最小链路执行成功\",\"executionTimeMs\":288,\"modelUsed\":\"local\"}"
request result POST "/api/claw-device/tasks/$TASK_UUID/result" "$RESULT_BODY" "$DEVICE_TOKEN"
assert_http result 200
echo "[PASS] result 回传成功 taskUuid=$TASK_UUID" | tee -a "$RUN_LOG"

request heartbeat_no_token POST /api/claw-device/heartbeat "$HEARTBEAT_BODY"
assert_auth_error heartbeat_no_token

request heartbeat_bad_token POST /api/claw-device/heartbeat "$HEARTBEAT_BODY" "bad-token"
assert_auth_error heartbeat_bad_token

set +e
curl -sS --connect-timeout 2 --max-time 4 -o /dev/null \
  -X POST "$NETWORK_DOWN_BASE_URL/api/claw-device/heartbeat" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $DEVICE_TOKEN" \
  -d "$HEARTBEAT_BODY" 2>"$RESP_DIR/heartbeat_network_down.err"
NETWORK_EXIT=$?
set -e
if [[ "$NETWORK_EXIT" != "0" ]]; then
  echo "[PASS] 断网异常已触发 exit=$NETWORK_EXIT" | tee -a "$RUN_LOG"
else
  echo "[FAIL] 断网异常未触发，请检查 NETWORK_DOWN_BASE_URL" | tee -a "$RUN_LOG"
  exit 1
fi

capture_adb() {
  {
    echo "===== ADB 最小证据 ====="
    date '+%Y-%m-%d %H:%M:%S %z'
    adb start-server
    echo "--- adb devices -l ---"
    adb devices -l

    local serial="$ADB_SERIAL"
    if [[ -z "$serial" ]]; then
      serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
    fi

    if [[ -n "$serial" ]]; then
      echo "--- selected serial: $serial ---"
      adb -s "$serial" shell getprop ro.product.model
      adb -s "$serial" shell getprop ro.build.version.release
      adb -s "$serial" shell date
      adb -s "$serial" shell pidof io.agents.pokeclaw || true
      adb -s "$serial" logcat -d | grep -E "CloudNodeOrchestrator|DeviceCloudClient|register|heartbeat|pending|result" | tail -n 200 || true
      echo "adb_real_device=1"
    else
      echo "[WARN] 未检测到在线真机/模拟器"
      echo "adb_real_device=0"
      if [[ "$ADB_REQUIRED" == "1" ]]; then
        echo "[FAIL] ADB_REQUIRED=1 但无可用设备"
        return 1
      fi
    fi
  } > "$ADB_LOG" 2>&1
}

capture_adb

ADB_REAL_DEVICE="$(grep -E '^adb_real_device=' "$ADB_LOG" | tail -n1 | cut -d'=' -f2 || true)"

cat > "$SUMMARY" <<EOS
# DYQ-9 真机ADB最小证据汇总

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- BASE_URL: $BASE_URL
- 输出目录: $OUT_DIR
- deviceId: $DEVICE_ID
- taskUuid: $TASK_UUID
- adb_real_device: ${ADB_REAL_DEVICE:-unknown}

## 1. 注册-心跳-回传
- register HTTP: $(cat "$RESP_DIR/register.code")
- heartbeat HTTP: $(cat "$RESP_DIR/heartbeat.code")
- pending HTTP: $(cat "$RESP_DIR/pending.code")
- result HTTP: $(cat "$RESP_DIR/result.code")
- pendingTaskCount: $PENDING_COUNT

## 2. 异常可见性
- no-token HTTP: $(cat "$RESP_DIR/heartbeat_no_token.code"), bizCode: $(json_get "$RESP_DIR/heartbeat_no_token.json" "code")
- bad-token HTTP: $(cat "$RESP_DIR/heartbeat_bad_token.code"), bizCode: $(json_get "$RESP_DIR/heartbeat_bad_token.json" "code")
- network-down exit: $NETWORK_EXIT

## 3. ADB 最小证据
- 证据文件: $ADB_LOG
- 真机标记: ${ADB_REAL_DEVICE:-unknown}

## 4. 关键证据文件
- $RESP_DIR/register.json
- $RESP_DIR/heartbeat.json
- $RESP_DIR/pending.json
- $RESP_DIR/result.json
- $RESP_DIR/heartbeat_no_token.json
- $RESP_DIR/heartbeat_bad_token.json
- $RESP_DIR/heartbeat_network_down.err
EOS

echo "[DONE] DYQ-9 最小证据采集完成: $OUT_DIR" | tee -a "$RUN_LOG"
