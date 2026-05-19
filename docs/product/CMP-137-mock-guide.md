# CMP-137 本地Mock联调指南

## 概述

本文档用于在真实DYQ后端(192.168.250.3:8080)未启动时，通过本地Mock服务验证PokeClaw端侧设备API实现。

## 快速开始

### 1. 启动Mock服务

```bash
cd /mnt/e/code/PokeClaw
python3 scripts/mock-dyq-backend.py
```

服务将监听 `http://0.0.0.0:18080`

### 2. 修改端侧配置

临时修改端侧代码中的Base URL指向Mock服务：

```kotlin
// app/src/main/java/io/agents/pokeclaw/cloud/di/CloudModule.kt
// 将 baseUrl 改为:
private const val MOCK_BASE_URL = "http://10.0.2.2:18080/"  // 模拟器访问宿主机
// 或真实设备访问开发机IP
private const val MOCK_BASE_URL = "http://192.168.x.x:18080/"
```

### 3. 测试curl命令

```bash
# 1. 设备注册
curl -X POST http://localhost:18080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "pokeclaw-mock-001",
    "deviceName": "Mock测试设备",
    "deviceModel": "Xiaomi 14",
    "androidVersion": "14",
    "appVersion": "0.7.0"
  }'

# 2. 设备心跳
curl -X POST http://localhost:18080/api/claw-device/heartbeat \
  -H "Authorization: Bearer mock-device-token-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "batteryLevel": 85,
    "isCharging": true,
    "networkType": "wifi"
  }'

# 3. 拉取任务
curl -X GET "http://localhost:18080/api/claw-device/devices/pokeclaw-mock-001/pending-tasks" \
  -H "Authorization: Bearer mock-device-token-xxxx"

# 4. 上报结果
curl -X POST "http://localhost:18080/api/claw-device/tasks/task-uuid-xxx/result" \
  -H "Authorization: Bearer mock-device-token-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SUCCESS",
    "result": "任务执行成功",
    "executionTimeMs": 5000
  }'
```

### 4. 验证日志

Mock服务会输出详细日志，便于验证端侧请求格式是否正确：

```
[注册] 设备: pokeclaw-mock-001, 名称: Mock测试设备
[心跳] 设备: pokeclaw-mock-001, 电量: 85%, 网络: wifi
[拉取任务] 设备: pokeclaw-mock-001, 任务数: 1
[上报结果] 任务: task-xxx, 状态: SUCCESS
```

## Mock行为说明

| 端点 | 行为 |
|------|------|
| /register | 生成mock令牌，注册设备 |
| /heartbeat | 更新设备状态，每5次心跳生成一个测试任务 |
| /pending-tasks | 返回该设备的待处理任务列表 |
| /result | 更新任务状态，记录结果 |
| /token/refresh | 生成新令牌对 |

## 切换到真实后端

当真实后端(192.168.250.3:8080)启动后：

1. 停止Mock服务 (Ctrl+C)
2. 恢复端侧配置中的Base URL
3. 重新编译安装APK
4. 执行真实联调

## 注意事项

1. Mock服务使用内存存储，重启后数据丢失
2. 令牌验证为简化实现，真实后端有更严格的JWT验证
3. Mock不模拟网络延迟和错误情况（可扩展）
4. 仅用于验证端侧请求格式和基本流程

## 待验证清单（使用Mock）

- [ ] 设备注册请求格式正确
- [ ] 心跳请求携带正确字段
- [ ] Token自动注入到请求头
- [ ] 任务拉取响应解析正确
- [ ] 任务结果上报格式正确
- [ ] Token刷新流程正确
- [ ] 离线降级逻辑触发（手动断网测试）

---

**文档路径**: `/mnt/e/code/PokeClaw/docs/product/CMP-137-mock-guide.md`
**脚本路径**: `/mnt/e/code/PokeClaw/scripts/mock-dyq-backend.py`
