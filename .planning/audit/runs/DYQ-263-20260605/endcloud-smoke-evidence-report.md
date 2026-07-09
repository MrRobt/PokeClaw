# DYQ-263: PokeClaw P1.4 端云最小冒烟脚本证据包

**执行时间**: 2026-06-05 07:03:24 +0800  
**执行者**: 端侧工程师阿甲 (Agent 07191ab1-bfe0-4159-8660-5eafc91a9342)  
**目标**: 整理 register→heartbeat→任务领取→结果回传 的最小冒烟脚本证据  
**依赖**: DYQ-204 (dyq-server dev启动修复, critical), DYQ-5 (三端闭环验收, high)

---

## 1. 执行命令

```bash
# 真实云端可达性检查
curl -s --connect-timeout 3 "http://127.0.0.1:48080/actuator/health"
# 结果: [真实云端不可达] 48080未就绪

# 冒烟脚本执行（使用Mock后端）
bash scripts/dyq3-endcloud-smoke.sh \
  /mnt/e/code/PokeClaw/artifacts/dyq263-smoke-20260605-070306
```

---

## 2. Mock vs 真实边界标注

| 层级 | 组件 | 本次测试 | 真实环境 | 阻塞原因 |
|------|------|----------|----------|----------|
| **后端** | DYQ-Server | Mock (port 18080) | http://localhost:48080 | DYQ-204修复中 |
| **端侧** | PokeClaw App | 未涉及真机 | 需真机安装 | 不在本Issue范围 |
| **网络** | 心跳链路 | 本地loopback | 真实网络 | 后端未就绪 |
| **ADB** | 设备操作 | 仅检查server | 需连接真机 | 无设备连接 |

**关键边界声明**:
- 本证据包使用 `mock-dyq-backend.py` 模拟48080接口行为
- Mock实现了与真实后端相同的API契约（register/heartbeat/pending-tasks/result）
- 真实云端不可达时，本脚本可用于验证端侧代码逻辑正确性
- **不能**将Mock结果等同于真实闭环验证通过

---

## 3. 阻塞点明确

### 3.1 当前阻塞
```
[BLOCKED] 真实云端48080/register链路
├── 检查命令: curl http://127.0.0.1:48080/actuator/health
├── 实际结果: Connection refused
├── 阻塞原因: DYQ-204 (adminAuthServiceImpl注入失败修复中)
└── 影响范围: 无法验证真实端云闭环
```

### 3.2 解阻塞条件
- DYQ-204 完成 → dyq-server:dev 可正常启动
- DYQ-205 (如有) 完成 → register/heartbeat接口稳定
- 重新执行脚本时设置 `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://localhost:48080`

---

## 4. 结果摘要

### 4.1 冒烟链路验证（全PASS）

| 步骤 | API | HTTP状态 | body.code | 结果 |
|------|-----|----------|-----------|------|
| 1. 设备注册 | POST /api/claw-device/register | 200 | 200 | PASS |
| 2. 心跳 | POST /api/claw-device/heartbeat | 200 | 200 | PASS |
| 3. 任务拉取 | GET /api/claw-device/devices/{id}/pending-tasks | 200 | 200 | PASS |
| 4. 结果回传 | POST /api/claw-device/tasks/{uuid}/result | 200 | 200 | PASS |

### 4.2 异常场景验证（全PASS）

| 场景 | 预期 | 实际 | 结果 |
|------|------|------|------|
| 无token请求 | code=401 | code=401, msg=缺少有效令牌 | PASS |
| 无效token请求 | code=401 | code=401, msg=令牌无效 | PASS |
| 网络断连 | curl exit≠0 | exit=7 (Failed to connect) | PASS |

### 4.3 ADB验证（INFO）
- adb server 启动正常
- 无设备连接（exitCode=1，预期行为）
- 有设备时可执行shell命令

---

## 5. 证据路径

```
artifacts/dyq263-smoke-20260605-070306/
├── summary.md                    # 执行摘要
├── smoke_run.log                 # 完整运行日志
├── mock_server.log               # Mock后端日志
├── adb_minimal.log               # ADB验证日志
└── responses/                    # HTTP响应原始文件
    ├── register.json             # 注册响应（含deviceToken）
    ├── register.code             # HTTP 200
    ├── heartbeat.json            # 心跳响应
    ├── heartbeat.code            # HTTP 200
    ├── pending.json              # 任务列表（含taskUuid）
    ├── result.json               # 结果回传响应
    ├── heartbeat_no_token.json   # 无token错误响应
    ├── heartbeat_bad_token.json  # 无效token错误响应
    └── heartbeat_network_down.err # 网络错误原始输出
```

---

## 6. 提交信息

### 6.1 本次未提交的文件（原因说明）

| 文件 | 状态 | 原因 |
|------|------|------|
| `scripts/dyq3-endcloud-smoke.sh` | 已存在 | 无需修改，功能完整 |
| `scripts/mock-dyq-backend.py` | 已存在 | 无需修改，Mock稳定 |
| `artifacts/dyq263-smoke-*/` | 未跟踪 | 生成产物，不提交git |

### 6.2 需提交的文档

本报告位于: `.planning/audit/runs/DYQ-263-20260605/endcloud-smoke-evidence-report.md`

---

## 7. 阻塞/下一棒

### 7.1 当前阻塞
- **真实云端48080未就绪** → 等待DYQ-204完成

### 7.2 下一棒行动（由DYQ-5验收方执行）
1. 等待DYQ-204状态变为 `done`
2. 重新执行: `USE_MOCK_BACKEND=0 DYQ_BASE_URL=http://localhost:48080 bash scripts/dyq3-endcloud-smoke.sh`
3. 验证真实端云闭环
4. 对比Mock与真实响应差异（如有）

### 7.3 手动验证真实云端命令（待解阻塞后使用）

```bash
# 真实云端健康检查
curl -s http://localhost:48080/actuator/health | jq

# 真实设备注册
curl -s -X POST http://localhost:48080/api/claw-device/register \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test-device","deviceName":"Test"}'

# 完整冒烟测试（真实后端）
USE_MOCK_BACKEND=0 \
DYQ_BASE_URL=http://localhost:48080 \
bash scripts/dyq3-endcloud-smoke.sh \
  artifacts/dyq263-realcloud-$(date +%Y%m%d-%H%M%S)
```

---

## 验收六件套检查清单

- [x] **改动文件**: 无代码改动，仅生成证据报告
- [x] **执行命令**: 见第1节
- [x] **结果摘要**: 见第4节（Mock链路全PASS）
- [x] **证据路径**: 见第5节
- [ ] **提交号+推送分支**: 待报告合并后提供
- [x] **阻塞/下一棒**: 见第7节（依赖DYQ-204/205）

---

**报告生成**: 2026-06-05 07:15:00 +0800  
**Agent**: 端侧工程师阿甲  
**Issue**: DYQ-263
