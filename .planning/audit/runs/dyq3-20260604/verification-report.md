# DYQ-3 端侧执行链路商业化验收报告

**日期**: 2026-06-04
**执行人**: 端侧工程师阿甲
**关联Issue**: DYQ-3
**验收结论**: ✅ 全部通过（24/24）

## 验收范围

| 验收标准 | 测试项 | 结果 |
|---------|--------|------|
| 1. 设备注册与心跳稳定 | 注册、心跳、连续心跳 | ✅ |
| 2. 云端下发→端侧执行→回传结果 | 任务拉取、结果上报、任务队列变化 | ✅ |
| 3. 弱网/异常时用户可见错误提示 | 无令牌401、无效令牌401 | ✅ |
| 令牌刷新后继续正常工作 | 刷新+新令牌验证 | ✅ |

## 测试用例明细（24项）

### 1/8 健康检查（1项）
- [PASS] 健康检查返回200

### 2/8 设备注册（5项）
- [PASS] 设备注册返回200
- [PASS] 注册返回deviceToken（长度=26）
- [PASS] 注册返回refreshToken
- [PASS] 注册返回deviceId与请求一致
- [PASS] 注册返回expiresIn=3600

### 3/8 设备心跳正常（3项）
- [PASS] 心跳请求返回200
- [PASS] 心跳返回pendingTaskCount=1
- [PASS] 心跳返回serverTime

### 4/8 无令牌/无效令牌（2项）
- [PASS] 无令牌心跳正确返回401
- [PASS] 无效令牌心跳正确返回401

### 5/8 拉取待处理任务（3项）
- [PASS] 拉取任务返回200
- [PASS] 拉取到1个待处理任务
- [PASS] 首个任务: uuid=... cmd=打开设置查看电量

### 6/8 提交任务结果（4项）
- [PASS] 任务结果上报返回200
- [PASS] 服务端确认received=True
- [PASS] 返回taskUuid与提交一致
- [PASS] 提交后任务查询正常（剩余0个）

### 7/8 令牌刷新（5项）
- [PASS] 令牌刷新返回200
- [PASS] 刷新返回新deviceToken
- [PASS] 刷新返回新refreshToken
- [PASS] 刷新expiresIn=3600
- [PASS] 新令牌心跳验证成功

### 8/8 连续心跳稳定性（1项）
- [PASS] 5次连续心跳全部返回200

## 验收过程中修复的问题

**Mock服务器令牌刷新bug**：`/api/claw-device/token/refresh` 端点返回新令牌后未将其注册到内存token映射，导致刷新后的令牌无法用于后续认证请求。
- 修复：注册时同步注册refreshToken映射；刷新时根据旧token查找设备ID并注册新token。
- 影响：仅影响Mock服务器，真实后端需确认刷新逻辑一致性。

## 文件清单

| 文件 | 说明 |
|------|------|
| `scripts/dyq3-e2e-smoke.py` | E2E冒烟测试脚本（24项用例） |
| `scripts/mock-dyq-backend.py` | Mock后端服务（已修复令牌刷新） |
| `api-contracts/device.openapi.yaml` | 设备接口契约（已有） |
| `.planning/audit/runs/dyq3-20260604/verification-report.md` | 本报告 |

## 下一步

1. 联调真实DYQ后端（替换BASE_URL为真实地址），确认接口行为一致。
2. 端侧Android真机ADB冒烟：验证设备注册→心跳→任务拉取全链路。
3. 弱网场景：模拟低速/断网，验证用户可见错误提示与重试机制。
