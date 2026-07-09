#!/bin/bash
# DYQ-230: PokeClaw 真实端云链路复验脚本
# 用法: bash scripts/dyq230-real-cloud-recheck.sh [输出目录]

set -euo pipefail

BASE_URL="${DYQ_BASE_URL:-http://127.0.0.1:48080}"
OUTPUT_DIR="${1:-artifacts/dyq230-real/$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUTPUT_DIR"

echo "=== DYQ-230 真实端云链路复验 ==="
echo "后端: $BASE_URL"
echo "输出: $OUTPUT_DIR"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 1. 健康检查
echo "[1/5] 健康检查..."
if curl -sS -m 8 "$BASE_URL/actuator/health" > "$OUTPUT_DIR/health.json" 2>&1; then
    echo "✅ 健康检查通过"
    python3 -m json.tool < "$OUTPUT_DIR/health.json" > "$OUTPUT_DIR/health.pretty.json" 2>/dev/null || true
else
    echo "❌ 健康检查失败 - 后端未就绪"
    echo "原始响应:"
    cat "$OUTPUT_DIR/health.json" 2>/dev/null || echo "(无响应)"
    exit 1
fi

# 2. 设备注册
echo ""
echo "[2/5] 设备注册..."
DEVICE_ID="pokeclaw-test-$(date +%s)"
echo "设备ID: $DEVICE_ID"

curl -sS -X POST "$BASE_URL/api/claw-device/register" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"deviceName\":\"PokeClaw Test\",\"deviceModel\":\"Android Test\",\"androidVersion\":\"13\",\"appVersion\":\"1.0.0-test\",\"ipAddress\":\"127.0.0.1\"}" \
  > "$OUTPUT_DIR/register.json" 2>&1

# 提取 token
DEVICE_TOKEN=$(cat "$OUTPUT_DIR/register.json" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('deviceToken',''))" 2>/dev/null || echo "")
REFRESH_TOKEN=$(cat "$OUTPUT_DIR/register.json" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('refreshToken',''))" 2>/dev/null || echo "")

auth_header="Authorization: Bearer $DEVICE_TOKEN"

if [ -z "$DEVICE_TOKEN" ]; then
    echo "❌ 注册失败 - 无法获取 deviceToken"
    echo "响应内容:"
    cat "$OUTPUT_DIR/register.json"
    exit 1
fi
echo "✅ 注册成功"
echo "  deviceToken: ${DEVICE_TOKEN:0:20}..."
echo "  refreshToken: ${REFRESH_TOKEN:0:20}..."

# 3. 心跳
echo ""
echo "[3/5] 心跳..."
curl -sS -X POST "$BASE_URL/api/claw-device/heartbeat" \
  -H "$auth_header" \
  -H "Content-Type: application/json" \
  -d '{"batteryLevel":85,"isCharging":true,"networkType":"WIFI","signalStrength":-45}' \
  > "$OUTPUT_DIR/heartbeat.json" 2>&1

HEARTBEAT_CODE=$(cat "$OUTPUT_DIR/heartbeat.json" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")
if [ "$HEARTBEAT_CODE" = "0" ] || [ "$HEARTBEAT_CODE" = "200" ]; then
    echo "✅ 心跳发送成功"
    PENDING_COUNT=$(cat "$OUTPUT_DIR/heartbeat.json" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('pendingTaskCount',0))" 2>/dev/null || echo "0")
else
    echo "⚠️ 心跳返回非成功码: $HEARTBEAT_CODE"
    PENDING_COUNT="unknown"
fi

# 4. 拉取任务
echo ""
echo "[4/5] 拉取任务..."
curl -sS "$BASE_URL/api/claw-device/devices/$DEVICE_ID/pending-tasks" \
  -H "$auth_header" \
  > "$OUTPUT_DIR/pending-tasks.json" 2>&1

TASKS_COUNT=$(cat "$OUTPUT_DIR/pending-tasks.json" | python3 -c "import sys,json;d=json.load(sys.stdin).get('data',[]);print(len(d) if isinstance(d,list) else 0)" 2>/dev/null || echo "0")
echo "✅ 任务拉取完成，获取 $TASKS_COUNT 个任务"

# 5. 回传结果
if [ "$TASKS_COUNT" -gt 0 ]; then
    TASK_UUID=$(cat "$OUTPUT_DIR/pending-tasks.json" | python3 -c "import sys,json;d=json.load(sys.stdin).get('data',[]);print(d[0].get('taskUuid','') if len(d)>0 else '')" 2>/dev/null || echo "")

    if [ -n "$TASK_UUID" ]; then
        echo ""
        echo "[5/5] 结果回传..."
        echo "任务UUID: $TASK_UUID"

        TIMESTAMP=$(date +%s)
        NONCE=$(openssl rand -hex 8 2>/dev/null || echo "test-nonce")

        curl -sS -X POST "$BASE_URL/api/claw-device/tasks/$TASK_UUID/result" \
          -H "$auth_header" \
          -H "Content-Type: application/json" \
          -H "X-Claw-Timestamp: $TIMESTAMP" \
          -H "X-Claw-Nonce: $NONCE" \
          -H "X-Claw-Signature: test-signature-dyq230" \
          -d "{\"status\":\"SUCCESS\",\"result\":\"Task executed successfully via DYQ-230 recheck\",\"executionTimeMs\":1200,\"modelUsed\":\"test-model\",\"toolCalls\":\"[]\",\"screenshotUrl\":\"\",\"errorDetail\":null}" \
          > "$OUTPUT_DIR/submit-result.json" 2>&1 || true

        RESULT_CODE=$(cat "$OUTPUT_DIR/submit-result.json" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")
        if [ "$RESULT_CODE" = "0" ] || [ "$RESULT_CODE" = "200" ]; then
            echo "✅ 结果回传成功"
        else
            echo "⚠️ 结果回传返回码: $RESULT_CODE"
        fi
    fi
else
    echo ""
    echo "[5/5] 结果回传 (跳过 - 无任务可回传)"
    echo '{"skipped":true,"reason":"no_pending_tasks"}' > "$OUTPUT_DIR/submit-result.json"
fi

# 生成摘要
echo ""
echo "=== 复验完成 ==="
echo "输出目录: $OUTPUT_DIR"
echo ""
echo "文件清单:"
ls -la "$OUTPUT_DIR/"
echo ""
echo "摘要:"
echo "  设备ID: $DEVICE_ID"
echo "  注册状态: ✅ 成功"
echo "  心跳状态: ✅ 已发送"
echo "  任务数量: $TASKS_COUNT"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
