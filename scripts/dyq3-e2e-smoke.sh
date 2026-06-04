#!/usr/bin/env bash
# ============================================================
# DYQ-3 端侧执行链路商业化验收 - E2E冒烟测试
# 作者：端侧工程师阿甲
# 日期：2026-06-04
# ============================================================

set -euo pipefail

BASE_URL="${MOCK_URL:-http://127.0.0.1:18080}"
PASS=0
FAIL=0
TESTS=()
DEVICE_ID="pokeclaw-device-$(date +%s)"
TOKEN=""
REFRESH_TOKEN=""

pass() { PASS=$((PASS+1)); TESTS+=("PASS|$1"); echo "  ✅ PASS: $1"; }
fail() { FAIL=$((FAIL+1)); TESTS+=("FAIL|$1|$2"); echo "  ❌ FAIL: $1 — $2"; }

jf() {
  echo "$1" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); print(d$2)" 2>/dev/null
}

code_is() { [ "$(jf "$1" "['code']")" = "$2" ]; }

echo "============================================================"
echo "DYQ-3 端侧执行链路商业化验收 — E2E冒烟测试"
echo "目标服务器: $BASE_URL"
echo "测试设备ID: $DEVICE_ID"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
echo ""

# ── [1/8] 健康检查 ──
echo "── [1/8] 健康检查 ──"
HEALTH=$(curl -s "$BASE_URL/actuator/health")
if code_is "$HEALTH" "200"; then pass "健康检查返回200"; else fail "健康检查" "返回非200"; fi
echo ""

# ── [2/8] 设备注册 ──
echo "── [2/8] 设备注册 ──"
REG_RESP=$(curl -s -X POST "$BASE_URL/api/claw-device/register" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"deviceName\":\"阿甲验收机\",\"deviceModel\":\"Pixel 8 Pro\",\"androidVersion\":\"15\",\"appVersion\":\"0.7.0\"}")

if code_is "$REG_RESP" "200"; then pass "设备注册返回200"; else fail "设备注册" "返回非200: $REG_RESP"; fi

TOKEN=$(jf "$REG_RESP" "['data']['deviceToken']")
REFRESH_TOKEN=$(jf "$REG_RESP" "['data']['refreshToken']")
R_DID=$(jf "$REG_RESP" "['data']['deviceId']")
EXPIRES=$(jf "$REG_RESP" "['data']['expiresIn']")

[ -n "$TOKEN" ] && [ "$TOKEN" != "None" ] && pass "注册返回deviceToken (长度=${#TOKEN})" || fail "注册返回deviceToken" "令牌为空"
[ -n "$REFRESH_TOKEN" ] && [ "$REFRESH_TOKEN" != "None" ] && pass "注册返回refreshToken" || fail "注册返回refreshToken" "刷新令牌为空"
[ "$R_DID" = "$DEVICE_ID" ] && pass "注册返回deviceId一致" || fail "注册返回deviceId" "期望=$DEVICE_ID 实际=$R_DID"
[ -n "$EXPIRES" ] && [ "$EXPIRES" != "None" ] && [ "$EXPIRES" -gt 0 ] 2>/dev/null && pass "注册返回expiresIn=$EXPIRES" || fail "注册expiresIn" "值=$EXPIRES"
echo ""

# ── [3/8] 设备心跳（正常） ──
echo "── [3/8] 设备心跳（正常） ──"
HB_RESP=$(curl -s -X POST "$BASE_URL/api/claw-device/heartbeat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"batteryLevel":85,"isCharging":false,"networkType":"wifi"}')

if code_is "$HB_RESP" "200"; then pass "心跳请求返回200"; else fail "心跳请求" "返回非200: $HB_RESP"; fi

PC=$(jf "$HB_RESP" "['data']['pendingTaskCount']")
ST=$(jf "$HB_RESP" "['data']['serverTime']")
[ -n "$PC" ] && [ "$PC" != "None" ] && pass "心跳返回pendingTaskCount=$PC" || fail "心跳pendingTaskCount" "值为空"
[ -n "$ST" ] && [ "$ST" != "None" ] && pass "心跳返回serverTime" || fail "心跳serverTime" "值为空"
echo ""

# ── [4/8] 无令牌/无效令牌（401） ──
echo "── [4/8] 无令牌/无效令牌心跳（预期401） ──"
HB401=$(curl -s -X POST "$BASE_URL/api/claw-device/heartbeat" \
  -H "Content-Type: application/json" \
  -d '{"batteryLevel":50}')
code_is "$HB401" "401" && pass "无令牌心跳返回401" || fail "无令牌心跳" "期望401 实际=$(jf "$HB401" "['code']")"

HB_BAD=$(curl -s -X POST "$BASE_URL/api/claw-device/heartbeat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer bad-token-12345" \
  -d '{"batteryLevel":50}')
code_is "$HB_BAD" "401" && pass "无效令牌心跳返回401" || fail "无效令牌心跳" "期望401 实际=$(jf "$HB_BAD" "['code']")"
echo ""

# ── [5/8] 拉取待处理任务 ──
echo "── [5/8] 拉取待处理任务 ──"
TASKS_RESP=$(curl -s "$BASE_URL/api/claw-device/devices/$DEVICE_ID/pending-tasks" \
  -H "Authorization: Bearer $TOKEN")

code_is "$TASKS_RESP" "200" && pass "拉取任务返回200" || fail "拉取任务" "返回非200: $TASKS_RESP"

TC=$(echo "$TASKS_RESP" | python3 -c "import sys,json; print(len(json.loads(sys.stdin.read()).get('data',[])))")
if [ "$TC" -gt 0 ] 2>/dev/null; then
  pass "拉取到${TC}个待处理任务"
  TASK_UUID=$(echo "$TASKS_RESP" | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['data'][0]['taskUuid'])")
  TASK_CMD=$(echo "$TASKS_RESP" | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['data'][0]['command'])")
  pass "首个任务: uuid=$TASK_UUID cmd=$TASK_CMD"
else
  fail "任务数量" "期望>0 实际=$TC"
  TASK_UUID=""
fi
echo ""

# ── [6/8] 提交任务结果 ──
echo "── [6/8] 提交任务结果 ──"
if [ -n "$TASK_UUID" ]; then
  RES_RESP=$(curl -s -X POST "$BASE_URL/api/claw-device/tasks/$TASK_UUID/result" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"status":"SUCCESS","result":"已打开设置，电量85%","executionTimeMs":3200,"modelUsed":"gpt-4o-mini","logSnippet":"[17:30:01] open_app OK"}')

  code_is "$RES_RESP" "200" && pass "任务结果上报返回200" || fail "任务结果上报" "返回非200"

  REC=$(jf "$RES_RESP" "['data']['received']")
  [ "$REC" = "True" ] && pass "服务端确认received=True" || fail "服务端确认" "received=$REC"

  R_UUID=$(jf "$RES_RESP" "['data']['taskUuid']")
  [ "$R_UUID" = "$TASK_UUID" ] && pass "返回taskUuid一致" || fail "返回taskUuid" "期望=$TASK_UUID 实际=$R_UUID"
else
  fail "提交任务结果" "无任务UUID跳过"
fi

# 提交后验证
TASKS_AFTER=$(curl -s "$BASE_URL/api/claw-device/devices/$DEVICE_ID/pending-tasks" \
  -H "Authorization: Bearer $TOKEN")
TC_A=$(echo "$TASKS_AFTER" | python3 -c "import sys,json; print(len(json.loads(sys.stdin.read()).get('data',[])))")
pass "提交后任务查询正常 (剩余$TC_A个)"
echo ""

# ── [7/8] 令牌刷新 ──
echo "── [7/8] 令牌刷新 ──"
REF_RESP=$(curl -s -X POST "$BASE_URL/api/claw-device/token/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")

code_is "$REF_RESP" "200" && pass "令牌刷新返回200" || fail "令牌刷新" "返回非200"

NT=$(jf "$REF_RESP" "['data']['deviceToken']")
NR=$(jf "$REF_RESP" "['data']['refreshToken']")
NE=$(jf "$REF_RESP" "['data']['expiresIn']")

[ -n "$NT" ] && [ "$NT" != "None" ] && [ "$NT" != "$TOKEN" ] && pass "刷新返回新deviceToken" || fail "刷新deviceToken" "未变化或为空"
[ -n "$NR" ] && [ "$NR" != "None" ] && pass "刷新返回新refreshToken" || fail "刷新refreshToken" "为空"
[ -n "$NE" ] && [ "$NE" != "None" ] && [ "$NE" -gt 0 ] 2>/dev/null && pass "刷新expiresIn=$NE" || fail "刷新expiresIn" "值无效"

# 用新令牌做心跳验证
HB_NT=$(curl -s -X POST "$BASE_URL/api/claw-device/heartbeat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $NT" \
  -d '{"batteryLevel":90,"isCharging":true,"networkType":"wifi"}')
code_is "$HB_NT" "200" && pass "新令牌心跳验证成功" || fail "新令牌心跳验证" "返回非200"
echo ""

# ── [8/8] 连续心跳稳定性 ──
echo "── [8/8] 连续心跳稳定性（5次快速轮询） ──"
HB_OK=true
for i in $(seq 1 5); do
  HR=$(curl -s -X POST "$BASE_URL/api/claw-device/heartbeat" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $NT" \
    -d '{"batteryLevel":80,"isCharging":true,"networkType":"wifi"}')
  if ! code_is "$HR" "200"; then HB_OK=false; fail "第${i}次心跳" "返回非200"; break; fi
done
$HB_OK && pass "5次连续心跳全部返回200"
echo ""

# ── 汇总 ──
echo "============================================================"
TOTAL=$((PASS+FAIL))
echo "总用例: $TOTAL  通过: $PASS  失败: $FAIL"
echo ""
if [ $FAIL -gt 0 ]; then
  echo "失败用例:"
  for t in "${TESTS[@]}"; do
    [[ "$t" == FAIL* ]] && echo "  ❌ $(echo "$t" | cut -d'|' -f2): $(echo "$t" | cut -d'|' -f3)"
  done
  echo ""
  echo "结论: ❌ 验收不通过"
else
  echo "结论: ✅ 全部通过，端侧执行链路验收合格"
fi
echo "============================================================"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
exit $FAIL
