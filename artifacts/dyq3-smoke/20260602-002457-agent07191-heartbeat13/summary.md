# DYQ-3 端侧执行链路最小冒烟结果

- 时间: 2026-06-02 00:25:10 +0800
- BASE_URL: http://127.0.0.1:18102
- 设备ID: pokeclaw-dyq3-20260602-002457
- 任务UUID: 07d85f61-f58f-4b49-927e-11f1176a738f
- 输出目录: artifacts/dyq3-smoke/20260602-002457-agent07191-heartbeat13

## 1. 设备注册/心跳记录
- register: HTTP 200
- heartbeat: HTTP 200
- pendingTaskCount: 1

## 2. 云端下发到端侧回传链路日志
- pending: HTTP 200
- result: HTTP 200
- taskUuid: 07d85f61-f58f-4b49-927e-11f1176a738f

## 3. 异常场景用户可见报错
- 无token: code=401, msg=缺少有效令牌
- 无效token: code=401, msg=令牌无效
- 弱网/断网: curl_exit=7, err_file=artifacts/dyq3-smoke/20260602-002457-agent07191-heartbeat13/responses/heartbeat_network_down.err

## 4. 弱网/断网错误原始输出
```
curl: (7) Failed to connect to 127.0.0.1 port 9 after 0 ms: Could not connect to server
```

## 5. ADB 最小验证记录
- 见: artifacts/dyq3-smoke/20260602-002457-agent07191-heartbeat13/adb_minimal.log
