#!/bin/bash
# PokeClaw 端侧设备 API 联调测试脚本
# 用于验证 Android 端与 dyq 后端设备接口的连通性
# 路径: /mnt/e/code/PokeClaw/scripts/device-api-integration-test.sh

set -e

# 配置
BASE_URL="${DYQ_BASE_URL:-http://192.168.250.3:8080}"
DEVICE_ID="${TEST_DEVICE_ID:-pokeclaw-test-$(date +%s)}"
DEVICE_NAME="${TEST_DEVICE_NAME:-PokeClaw测试机}"

echo "=========================================="
echo "PokeClaw 设备 API 联调测试"
echo "=========================================="
echo "后端地址: $BASE_URL"
echo "测试设备编号: $DEVICE_ID"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试计数
TESTS_PASSED=0
TESTS_FAILED=0

# 辅助函数: 打印测试结果
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ PASS${NC}: $2"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ FAIL${NC}: $2"
        ((TESTS_FAILED++))
    fi
}

# 辅助函数: 发送请求并检查响应
send_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local auth_header=$4
    
    local curl_cmd="curl -s -w \"\\nHTTP_CODE:%{http_code}\" --connect-timeout 10"
    
    if [ -n "$auth_header" ]; then
        curl_cmd="$curl_cmd -H \"Authorization: Bearer $auth_header\""
    fi
    
    if [ -n "$data" ]; then
        curl_cmd="$curl_cmd -H \"Content-Type: application/json\" -d '$data'"
    fi
    
    curl_cmd="$curl_cmd -X $method \"$BASE_URL$endpoint\""
    
    eval $curl_cmd 2>/dev/null || echo "ERROR:Connection failed"
}

# 辅助函数: 解析响应
parse_response() {
    local response=$1
    local http_code=$(echo "$response" | grep -o "HTTP_CODE:[0-9]*" | cut -d: -f2)
    local body=$(echo "$response" | sed 's/HTTP_CODE:[0-9]*$//')
    echo "$body"
    return ${http_code:-0}
}

echo "【测试1】服务连通性检查"
echo "------------------------"
# 尝试访问注册接口（不需要认证）
HEALTH_RESPONSE=$(send_request "POST" "/api/claw-device/register" '{"deviceId":"health-check"}' "")
if echo "$HEALTH_RESPONSE" | grep -q "HTTP_CODE:000\|Connection failed\|ERROR"; then
    echo -e "${YELLOW}⚠ 后端服务未启动或网络不可达${NC}"
    echo "请确保 dyq 后端服务已启动在 $BASE_URL"
    echo ""
    echo "后续测试将跳过，但可查看预期请求/响应格式"
    BACKEND_AVAILABLE=false
else
    echo -e "${GREEN}✓ 后端服务可访问${NC}"
    BACKEND_AVAILABLE=true
fi
echo ""

# 存储 token 的变量
DEVICE_TOKEN=""
REFRESH_TOKEN=""

echo "【测试2】设备注册"
echo "----------------"
REGISTER_DATA=$(cat <<EOF
{
    "deviceId": "$DEVICE_ID",
    "deviceName": "$DEVICE_NAME",
    "deviceModel": "Xiaomi 14",
    "androidVersion": "14",
    "appVersion": "0.7.0"
}
EOF
)

echo "请求: POST /api/claw-device/register"
echo "请求体: $REGISTER_DATA"
echo ""

if [ "$BACKEND_AVAILABLE" = true ]; then
    REGISTER_RESPONSE=$(send_request "POST" "/api/claw-device/register" "$REGISTER_DATA" "")
    echo "响应:"
    echo "$REGISTER_RESPONSE" | sed 's/HTTP_CODE:[0-9]*$//'
    
    # 提取 token
    DEVICE_TOKEN=$(echo "$REGISTER_RESPONSE" | grep -o '"deviceToken":"[^"]*"' | cut -d'"' -f4)
    REFRESH_TOKEN=$(echo "$REGISTER_RESPONSE" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$DEVICE_TOKEN" ]; then
        print_result 0 "设备注册成功，获取到 deviceToken"
    else
        print_result 1 "设备注册失败或响应格式异常"
    fi
else
    echo -e "${YELLOW}[模拟]${NC} 期望响应格式:"
    echo '{
  "code": 200,
  "msg": "success",
  "data": {
    "deviceToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 604800
  }
}'
fi
echo ""

if [ -n "$DEVICE_TOKEN" ]; then
    echo "【测试3】设备心跳"
    echo "----------------"
    HEARTBEAT_DATA='{"batteryLevel":85,"isCharging":false,"networkType":"wifi"}'
    echo "请求: POST /api/claw-device/heartbeat"
    echo "请求体: $HEARTBEAT_DATA"
    echo "Authorization: Bearer $DEVICE_TOKEN"
    echo ""
    
    HEARTBEAT_RESPONSE=$(send_request "POST" "/api/claw-device/heartbeat" "$HEARTBEAT_DATA" "$DEVICE_TOKEN")
    echo "响应:"
    echo "$HEARTBEAT_RESPONSE" | sed 's/HTTP_CODE:[0-9]*$//'
    
    if echo "$HEARTBEAT_RESPONSE" | grep -q '"code":200\|"code":0'; then
        print_result 0 "心跳发送成功"
    else
        print_result 1 "心跳发送失败"
    fi
    echo ""
    
    echo "【测试4】拉取待处理任务"
    echo "-----------------------"
    echo "请求: GET /api/claw-device/devices/$DEVICE_ID/pending-tasks"
    echo "Authorization: Bearer $DEVICE_TOKEN"
    echo ""
    
    PENDING_RESPONSE=$(send_request "GET" "/api/claw-device/devices/$DEVICE_ID/pending-tasks" "" "$DEVICE_TOKEN")
    echo "响应:"
    echo "$PENDING_RESPONSE" | sed 's/HTTP_CODE:[0-9]*$//'
    
    if echo "$PENDING_RESPONSE" | grep -q '"code":200\|"code":0'; then
        print_result 0 "任务拉取成功"
    else
        print_result 1 "任务拉取失败"
    fi
    echo ""
    
    echo "【测试5】任务结果上报"
    echo "--------------------"
    TASK_UUID="test-task-$(date +%s)"
    RESULT_DATA=$(cat <<EOF
{
    "status": "SUCCESS",
    "result": "任务执行成功",
    "executionTimeMs": 1234,
    "modelUsed": "local"
}
EOF
)
    echo "请求: POST /api/claw-device/tasks/$TASK_UUID/result"
    echo "请求体: $RESULT_DATA"
    echo "Authorization: Bearer $DEVICE_TOKEN"
    echo ""
    
    RESULT_RESPONSE=$(send_request "POST" "/api/claw-device/tasks/$TASK_UUID/result" "$RESULT_DATA" "$DEVICE_TOKEN")
    echo "响应:"
    echo "$RESULT_RESPONSE" | sed 's/HTTP_CODE:[0-9]*$//'
    
    if echo "$RESULT_RESPONSE" | grep -q '"code":200\|"code":0'; then
        print_result 0 "结果上报成功"
    else
        print_result 1 "结果上报失败"
    fi
    echo ""
    
    echo "【测试6】Token刷新"
    echo "----------------"
    REFRESH_DATA="{\"refreshToken\":\"$REFRESH_TOKEN\"}"
    echo "请求: POST /api/claw-device/token/refresh"
    echo "请求体: $REFRESH_DATA"
    echo ""
    
    REFRESH_RESPONSE=$(send_request "POST" "/api/claw-device/token/refresh" "$REFRESH_DATA" "")
    echo "响应:"
    echo "$REFRESH_RESPONSE" | sed 's/HTTP_CODE:[0-9]*$//'
    
    if echo "$REFRESH_RESPONSE" | grep -q '"code":200\|"code":0'; then
        print_result 0 "Token刷新成功"
    else
        print_result 1 "Token刷新失败"
    fi
    echo ""
fi

echo "=========================================="
echo "联调测试总结"
echo "=========================================="
echo -e "通过: ${GREEN}$TESTS_PASSED${NC}"
echo -e "失败: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ "$BACKEND_AVAILABLE" = false ]; then
    echo -e "${YELLOW}注意: 后端服务未启动，以上测试为接口格式演示${NC}"
    echo "请启动 dyq 后端服务后重新运行此脚本"
    exit 1
fi

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！${NC}"
    exit 0
else
    echo -e "${RED}存在失败的测试，请检查后端日志${NC}"
    exit 1
fi
