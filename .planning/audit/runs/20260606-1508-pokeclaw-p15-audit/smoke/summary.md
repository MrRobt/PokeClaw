# DYQ-3 端侧执行链路最小冒烟结果

- 时间: 2026-06-06 15:49:54 +0800
- BASE_URL: http://127.0.0.1:18500
- 设备ID: pokeclaw-dyq3-20260606-154951
- 任务UUID: 6dc9b5d7-b730-409f-ac76-a3457568385f
- 输出目录: .planning/audit/runs/20260606-1508-pokeclaw-p15-audit/smoke

## 1. 设备注册/心跳记录
- register: HTTP 200
- heartbeat: HTTP 200
- pendingTaskCount: 1

## 2. 云端下发到端侧回传链路日志
- pending: HTTP 200
- result: HTTP 200
- taskUuid: 6dc9b5d7-b730-409f-ac76-a3457568385f

## 3. 异常场景用户可见报错
- 无token: code=401, msg=缺少有效令牌
- 无效token: code=401, msg=令牌无效
- 弱网/断网: curl_exit=7, err_file=.planning/audit/runs/20260606-1508-pokeclaw-p15-audit/smoke/responses/heartbeat_network_down.err

## 4. 弱网/断网错误原始输出
```
curl: (7) Failed to connect to 127.0.0.1 port 9 after 0 ms: Could not connect to server
```

## 5. ADB 最小验证记录
- 见: .planning/audit/runs/20260606-1508-pokeclaw-p15-audit/smoke/adb_minimal.log
