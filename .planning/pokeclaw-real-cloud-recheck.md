# DYQ-230: PokeClaw 真实端云链路复验规划

## 目标
验证 PokeClaw 端侧与 DYQ 后端 (48080) 的真实链路：register → 任务领取 → 结果回传。

## 当前状态 (2026-06-05 06:06)

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 后端 48080 健康检查 | ❌ 未就绪 | 端口拒绝连接（连接失败: `curl (7) Could not connect`） |
| 上游依赖 DYQ-204 | 🔄 in_progress | dyq-server 启动修复 |
| 上游依赖 DYQ-229 | 🔄 in_progress | proxy_session_log DDL |
| 端侧契约 | ✅ 已定义 | `api-contracts/device.openapi.yaml` |
| 复验脚本 | ✅ 已创建 | `scripts/dyq230-real-cloud-recheck.sh` |
| Mock 18080 | ✅ 已恢复 | 重启实例后注册、心跳、任务拉取、结果回传可触达 |

## 阻塞分析

后端未就绪原因：
1. **DYQ-204**: `adminAuthServiceImpl` 注入失败导致 dyq-server 无法启动
2. **DYQ-229**: `proxy_session_log` 表 DDL 与 OpenAPI 前缀未完成

## 已知契约 (api/claw-device)

### 1. 设备注册
```
POST /api/claw-device/register
Content-Type: application/json

Request:
{
  "deviceId": "string",
  "deviceName": "string?",
  "deviceModel": "string?",
  "androidVersion": "string?",
  "appVersion": "string?",
  "publicKey": "string?"
}

Response (code=0):
{
  "code": 0,
  "data": {
    "deviceToken": "string",
    "refreshToken": "string",
    "expiresIn": number,
    "deviceId": "string"
  }
}
```

### 2. 心跳
```
POST /api/claw-device/heartbeat
Authorization: Bearer {deviceToken}
Content-Type: application/json

Request:
{
  "batteryLevel": number?,
  "isCharging": boolean?,
  "networkType": "wifi" | "cellular" | "offline"?,
  "signalStrength": number?
}

Response (code=0):
{
  "code": 0,
  "data": {
    "pendingTaskCount": number,
    "skillVersion": number?,
    "serverTime": number | string?
  }
}
```

### 3. 拉取任务
```
GET /api/claw-device/devices/{deviceId}/pending-tasks
Authorization: Bearer {deviceToken}

Response (code=0):
{
  "code": 0,
  "data": [
    {
      "taskUuid": "string",
      "uuid": "string?",
      "command": "string",
      "mode": "string?",
      "priority": "string?",
      "type": "string?",
      "payload": object?,
      "createdAt": number
    }
  ]
}
```

### 4. 结果回传
```
POST /api/claw-device/tasks/{taskUuid}/result
Authorization: Bearer {deviceToken}
Content-Type: application/json
X-Claw-Timestamp: number
X-Claw-Nonce: string
X-Claw-Signature: string (HMAC-SHA256)

Request:
{
  "status": "SUCCESS" | "FAILED" | "RUNNING" | "CANCELLED",
  "result": "string?",
  "errorMessage": "string?",
  "executionTimeMs": number?,
  "toolCalls": "string?",
  "evidenceUrls": "string?",
  "modelUsed": "string?",
  "errorCategory": "string?",
  "errorCode": "string?",
  "errorDetail": "string?",
  "recoverable": boolean?,
  "suggestedAction": "string?",
  "screenshotBase64": "string?",
  "logSnippet": "string?"
}

Response (code=0):
{
  "code": 0,
  "data": {
    "taskUuid": "string",
    "received": boolean
  }
}
```

## 可执行脚本

### 复验脚本
```bash
# 完整复验流程
bash scripts/dyq230-real-cloud-recheck.sh [输出目录]

# 环境变量
export DYQ_BASE_URL=http://127.0.0.1:48080
```

### 分步验证命令

#### Step 1: 健康检查
```bash
curl -sS -m 8 http://127.0.0.1:48080/actuator/health | python3 -m json.tool
```
**阻塞**: 后端未就绪时返回空或连接拒绝

#### Step 2: 设备注册
```bash
DEVICE_ID="pokeclaw-test-$(date +%s)"
curl -sS -X POST http://127.0.0.1:48080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d "{\"
    \"deviceId\": \"$DEVICE_ID\",\"
    \"deviceName\": \"PokeClaw Test\",\"
    \"deviceModel\": \"Android Test\",\"
    \"androidVersion\": \"13\",\"
    \"appVersion\": \"1.0.0-test\",\"
    \"ipAddress\": \"127.0.0.1\"\"
  }"
```
**期望**: HTTP 200, code=0, 返回 deviceToken/refreshToken

#### Step 3: 心跳
```bash
curl -sS -X POST http://127.0.0.1:48080/api/claw-device/heartbeat \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{\"batteryLevel\":85,\"isCharging\":true,\"networkType\":\"wifi\",\"signalStrength\":-45}'
```
**期望**: HTTP 200, code=0, data.pendingTaskCount ≥ 0

#### Step 4: 拉取任务
```bash
curl -sS http://127.0.0.1:48080/api/claw-device/devices/$DEVICE_ID/pending-tasks \
  -H "Authorization: Bearer $DEVICE_TOKEN"
```
**期望**: HTTP 200, code=0, data 为任务数组

#### Step 5: 结果回传
```bash
TASK_UUID="从Step 4获取"
TIMESTAMP=$(date +%s)
NONCE=$(openssl rand -hex 8)

curl -sS -X POST http://127.0.0.1:48080/api/claw-device/tasks/$TASK_UUID/result \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Claw-Timestamp: $TIMESTAMP" \
  -H "X-Claw-Nonce: $NONCE" \
  -H "X-Claw-Signature: test-signature-dyq230" \
  -d "{\"
    \"status\": \"SUCCESS\",\"
    \"result\": \"Task executed successfully\",\"
    \"executionTimeMs\": 1200,\"
    \"modelUsed\": \"test-model\"\"
  }"
```
**期望**: HTTP 200, code=0, data.received=true

## 依赖等待清单

| 依赖 | 状态 |  blocker | 最小验收条件 |
|------|------|----------|--------------|
| DYQ-204 | in_progress | dyq-server 无法启动 | `/actuator/health` 返回 UP |
| DYQ-229 | in_progress | proxy_session_log 表缺失 | `GET /api/claw-device/...` 不返回 500 |

## 执行记录

### 真实后端 (48080) - 2026-06-05 (首次)
```bash
bash scripts/dyq230-real-cloud-recheck.sh artifacts/dyq230-real/20260605-execution
```
**结果**: ❌ 阻塞于Step 1 - 健康检查
**原因**: `curl: (7) Failed to connect to 127.0.0.1 port 48080`
**证据**: `artifacts/dyq230-real/20260605-execution/health.json`

### 真实后端 (48080) - 2026-06-05 (复核)
```bash
bash scripts/dyq230-real-cloud-recheck.sh artifacts/dyq230-real/20260605-060621-realcheck
```
**时间**: 2026-06-05 06:06:22
**结果**: ❌ 阻塞于Step 1 - 健康检查
**原因**: DYQ-204/DYQ-229 未完成，127.0.0.1:48080 未监听
**响应**: `curl: (7) Failed to connect to 127.0.0.1 port 48080 after 0 ms: Could not connect to server`
**证据**: `artifacts/dyq230-real/20260605-060621-realcheck/health.json`

### Mock后端 (18080) - 2026-06-05
```bash
DYQ_BASE_URL=http://127.0.0.1:18080 bash scripts/dyq230-real-cloud-recheck.sh artifacts/dyq230-mock/20260605-060617-realcheck
```
**结果**: ⚠️ 5步可达（接口均返回HTTP 200，但code=200，与契约code=0存在差异）
- Step1 健康检查: code=200, status=UP
- Step2 设备注册: code=200, 获取deviceToken
- Step3 心跳: code=200, pendingTaskCount=1
- Step4 拉取任务: code=200, 获取1个任务（打开设置查看电量）
- Step5 结果回传: code=200, received=true

**历史**: 之前一次复测中出现注册`HTTP 500`，已通过重启 `python3 scripts/mock-dyq-backend.py` 后恢复（日志中记录 `/tmp/dyq-mock-18080.log`）。

**证据目录**: `artifacts/dyq230-mock/20260605-060617-realcheck/`
- health.json / health.pretty.json
- register.json
- heartbeat.json
- pending-tasks.json
- submit-result.json


## 关键发现

### Mock vs 真实后端契约差异
| 字段 | Mock (18080) | 契约定义 | 说明 |
|------|--------------|----------|------|
| 成功code | 200 | 0 | Mock使用HTTP状态码风格 |
| 失败code | - | 401 | 待验证 |

### 脚本修复记录
修复 `scripts/dyq230-real-cloud-recheck.sh` 3处Authorization头截断：
- 第 57-58 行: 心跳请求
- 第 74 行: 拉取任务请求
- 第 96 行: 结果回传请求

## 下一步行动

1. **等待 DYQ-204 完成**: 后端48080启动后重新执行真实链路复验
2. **等待 DYQ-229 完成**: proxy_session_log表就绪后验证结果回传落库
3. **契约对齐**: 确认真实后端使用code=0还是code=200表示成功

## 文件清单

| 文件 | 说明 |
|------|------|
| `api-contracts/device.openapi.yaml` | 接口契约 |
| `scripts/dyq230-real-cloud-recheck.sh` | 复验脚本 |
| `artifacts/dyq230-real/{timestamp}/` | 执行输出目录 |

## 变更记录

- 2026-06-05 (06:06): 复验脚本在模拟端口18080重启后端侧回放成功；真实端口48080仍拒绝连接（Step1失败）
- 2026-06-05 (复核): 端侧工程师阿甲执行复核，后端48080仍未就绪，上游DYQ-204/DYQ-229进行中
- 2026-06-05: 创建规划文档，确认后端未就绪阻塞
