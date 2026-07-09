# DYQ-3 心跳复核 20260604-232720-agent07191-heartbeat-now

## 前置
- 时间: 2026-06-04 23:27:20 CST
- 工作目录: /mnt/e/code/PokeClaw

## 脚本语法检查
- bash -n scripts/dyq3-endcloud-smoke.sh: PASS

## Mock 验收
- 命令: MOCK_PORT=18321 USE_MOCK_BACKEND=1 bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-232720-agent07191-heartbeat-now/mock
- 结果: - 见: artifacts/dyq3-smoke/20260604-232720-agent07191-heartbeat-now/mock/adb_minimal.log

## 真实后端验收
- 命令: USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://127.0.0.1:48080 HEALTH_PATH=/actuator/health bash scripts/dyq3-endcloud-smoke.sh artifacts/dyq3-smoke/20260604-232720-agent07191-heartbeat-now/real
- 退出码: 1

## 实时探测
```
== date ==
2026-06-04 23:28:09 CST
== lsof 48080 ==
== curl /actuator/health ==

== curl /health ==

== curl POST /api/claw-device/register ==

== adb devices -l ==
List of devices attached

```
- 真实后端验收: BLOCKED
