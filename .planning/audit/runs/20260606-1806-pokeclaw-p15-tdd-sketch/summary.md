# DYQ-3 端侧执行链路最小冒烟结果

- 时间: 2026-06-06 18:07:11 +0800
- BASE_URL: http://127.0.0.1:18511
- 设备ID: pokeclaw-dyq3-20260606-180708
- 任务UUID: 6fe2f763-ac36-4f7c-b43d-05ed10327917
- 输出目录: .planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch

## 1. 设备注册/心跳记录
- register: HTTP 200
- heartbeat: HTTP 200
- pendingTaskCount: 1

## 2. 云端下发到端侧回传链路日志
- pending: HTTP 200
- result: HTTP 200
- taskUuid: 6fe2f763-ac36-4f7c-b43d-05ed10327917

## 3. 异常场景用户可见报错
- 无token: code=401, msg=缺少有效令牌
- 无效token: code=401, msg=令牌无效
- 弱网/断网: curl_exit=7, err_file=.planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/responses/heartbeat_network_down.err

## 4. 弱网/断网错误原始输出
```
curl: (7) Failed to connect to 127.0.0.1 port 9 after 0 ms: Could not connect to server
```

## 5. ADB 最小验证记录
- 见: .planning/audit/runs/20260606-1806-pokeclaw-p15-tdd-sketch/adb_minimal.log
