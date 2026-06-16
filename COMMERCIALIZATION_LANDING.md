# PokeClaw 商业化完整闭环落地文档

## 一、项目概述

### 1.1 产品定位
PokeClaw是一个**端云协同的AI手机代理平台**，通过Android客户端（PokeClaw App）和云端中枢（Claw模块）实现手机自动化操作、AI任务执行、技能市场、计费结算等完整商业化闭环。

### 1.2 核心价值主张
- **对企业**：让AI成为数字员工，实现24小时自动化运营
- **对个人**：让每个人的手机都变成智能助手
- **对创作者**：创建AI技能获得收益分成
- **对平台**：构建AI能力变现的生态闭环

### 1.3 技术架构
```
┌─────────────────────────────────────────────────────────────┐
│                    PokeClaw Android App                     │
│  • 设备注册/心跳    • 任务执行    • 截图上传    • WebSocket  │
├─────────────────────────────────────────────────────────────┤
│                    Claw 云端中枢                            │
│  • 任务编排    • 设备管理    • 技能市场    • 记忆系统       │
├─────────────────────────────────────────────────────────────┤
│                    业务支撑模块                              │
│  • Agent模块    • AI员工    • 云手机    • 社交媒体         │
├─────────────────────────────────────────────────────────────┤
│                    商业化基础设施                            │
│  • 计费结算    • 套餐订阅    • 支付    • 技能分成         │
└─────────────────────────────────────────────────────────────┘
```

## 二、用户角色与权限

### 2.1 用户角色定义

| 角色 | 描述 | 核心权限 | 目标用户 |
|------|------|----------|----------|
| **企业管理员** | 企业账号管理者 | 设备管理、员工管理、计费查看、数据报表 | 中小企业主、团队负责人 |
| **运营人员** | 日常操作执行者 | 任务创建、技能安装、设备监控 | 运营专员、客服人员 |
| **技能创作者** | 技能开发与发布者 | 技能创建、审核提交、收益查看 | 开发者、内容创作者 |
| **普通用户** | 基础功能使用者 | 技能使用、任务执行、个人设置 | 个人用户、试用用户 |
| **平台管理员** | 平台运维管理者 | 用户管理、内容审核、系统配置 | 平台运营团队 |

### 2.2 权限矩阵

| 功能模块 | 企业管理员 | 运营人员 | 技能创作者 | 普通用户 | 平台管理员 |
|----------|------------|----------|------------|----------|------------|
| 设备绑定 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 技能安装 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 任务创建 | ✅ | ✅ | ❌ | ✅ | ✅ |
| 技能发布 | ❌ | ❌ | ✅ | ❌ | ✅ |
| 计费管理 | ✅ | ❌ | ❌ | ❌ | ✅ |
| 数据报表 | ✅ | ❌ | ✅ | ❌ | ✅ |
| 内容审核 | ❌ | ❌ | ❌ | ❌ | ✅ |

## 三、商业化闭环流程

### 3.1 用户获取流程

```
用户访问 → 注册账号 → 获得试用额度 → 绑定设备 → 安装技能 → 执行任务 → 体验价值
    ↓           ↓           ↓           ↓           ↓           ↓           ↓
  官网/App   手机号验证   新手礼包    扫码/输入码  技能市场    首个任务    付费转化
```

**关键指标**：
- 注册转化率：>80%
- 设备绑定率：>70%
- 首个任务完成率：>60%
- 试用到付费转化率：>15%

### 3.2 价值交付流程

```
需求识别 → 技能选择 → 任务配置 → 云端编排 → 设备执行 → 结果反馈 → 价值验证
    ↓           ↓           ↓           ↓           ↓           ↓           ↓
  业务场景   技能市场    参数填写    状态机流转   轮询获取    截图/日志    效果评估
```

**任务状态机**：
```
PENDING → CLAIMED → RUNNING → COMPLETED
    ↓         ↓         ↓
    └─────────┴─────────┘
              ↓
           FAILED
```

### 3.3 商业化变现流程

```
用户价值验证 → 套餐选择 → 支付购买 → 额度激活 → 使用扣费 → 账单生成 → 续费/升级
    ↓           ↓           ↓           ↓           ↓           ↓           ↓
  试用期结束   基础/专业/企业  支付宝/微信  Token/时长/次数  实时扣费    月度账单    生命周期管理
```

**计费模式**：
1. **按Token计费**：AI模型调用消耗
2. **按时长计费**：设备使用时长
3. **按次计费**：任务执行次数
4. **套餐包月**：固定额度包月

### 3.4 生态扩展流程

```
技能创作者入驻 → 技能开发 → 审核上架 → 用户使用 → 收益分成 → 创作者提现
    ↓           ↓           ↓           ↓           ↓           ↓
  实名认证    开发文档    安全审核    市场推广    平台30%     T+3结算
                                  创作者70%
```

## 四、核心功能模块

### 4.1 设备管理模块

**功能清单**：
- [ ] 设备注册（JWT认证）
- [ ] 心跳保活（30秒间隔）
- [ ] 设备状态监控
- [ ] 批量设备管理
- [ ] 设备分组与标签

**API接口**：
```java
// 设备注册
POST /api/claw-device/register
{
  "deviceName": "Pixel 8 Pro",
  "androidVersion": "14",
  "deviceModel": "Pixel 8 Pro"
}

// 心跳上报
POST /api/claw-device/heartbeat
{
  "batteryLevel": 85,
  "isCharging": false,
  "networkType": "wifi"
}

// 任务轮询
GET /api/claw-device/agent/tasks/poll?limit=1&timeout=30
```

**数据模型**：
```sql
CREATE TABLE claw_device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id VARCHAR(64) UNIQUE NOT NULL,
  device_name VARCHAR(128),
  user_id BIGINT,
  tenant_id BIGINT,
  status ENUM('ONLINE', 'OFFLINE', 'BUSY'),
  battery_level INT,
  network_type VARCHAR(32),
  last_heartbeat_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME ON UPDATE CURRENT_TIMESTAMP
);
```

### 4.2 技能市场模块

**功能清单**：
- [ ] 技能浏览与搜索
- [ ] 技能详情查看
- [ ] 技能安装与卸载
- [ ] 技能评价与评分
- [ ] 技能版本管理
- [ ] 技能审核流程

**API接口**：
```java
// 获取推荐技能
GET /api/claw/skills/recommended?scenario=whatsapp&topK=10

// 安装技能
POST /api/claw-device/skills/install
{
  "skillId": 123,
  "deviceId": "device_001"
}

// 获取技能执行配置
GET /api/claw/skills/{skillId}/execution?version=1
```

**数据模型**：
```sql
CREATE TABLE claw_skill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  category VARCHAR(64),
  scope ENUM('GLOBAL', 'TENANT', 'USER'),
  status ENUM('DRAFT', 'PENDING', 'ACTIVE', 'REJECTED'),
  trigger_patterns JSON,
  steps JSON,
  fallback_goal TEXT,
  usage_count INT DEFAULT 0,
  avg_rating DECIMAL(3,2),
  creator_id BIGINT,
  tenant_id BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME ON UPDATE CURRENT_TIMESTAMP
);
```

### 4.3 任务编排模块

**功能清单**：
- [ ] 任务创建与提交
- [ ] 任务状态机管理
- [ ] 任务优先级调度
- [ ] 任务超时处理
- [ ] 任务结果回收
- [ ] 任务日志记录

**API接口**：
```java
// 创建任务
POST /api/claw/tasks
{
  "deviceId": "device_001",
  "command": "send_message",
  "commandPayload": {
    "contact": "Mom",
    "message": "Hi",
    "app": "WhatsApp"
  },
  "priority": "normal"
}

// 获取任务详情
GET /api/claw/tasks/{taskUuid}

// 更新任务状态
PUT /api/claw/tasks/{taskUuid}/status
{
  "status": "COMPLETED",
  "result": "Message sent successfully"
}
```

**数据模型**：
```sql
CREATE TABLE claw_device_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_uuid VARCHAR(64) UNIQUE NOT NULL,
  device_id VARCHAR(64),
  commercial_task_id VARCHAR(64),
  command VARCHAR(128),
  command_payload JSON,
  mode VARCHAR(32),
  source_scenario VARCHAR(64),
  executor_type VARCHAR(32),
  status ENUM('PENDING', 'CLAIMED', 'RUNNING', 'COMPLETED', 'FAILED'),
  priority VARCHAR(16) DEFAULT 'normal',
  assigned_at DATETIME,
  started_at DATETIME,
  completed_at DATETIME,
  result TEXT,
  error_message TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME ON UPDATE CURRENT_TIMESTAMP
);
```

### 4.4 计费结算模块

**功能清单**：
- [ ] 套餐管理
- [ ] 额度管理
- [ ] 使用量记录
- [ ] 实时扣费
- [ ] 账单生成
- [ ] 支付对接
- [ ] 退款处理

**API接口**：
```java
// 记录使用量
POST /api/billing/usage
{
  "userId": 123,
  "businessType": "AI_TASK",
  "quantity": 100,
  "unit": "token",
  "amount": 0.01
}

// 扣费
POST /api/billing/deduct
{
  "userId": 123,
  "amount": 0.01,
  "businessType": "AI_TASK",
  "description": "GPT-4调用"
}

// 获取账单
GET /api/billing/bills?userId=123&startDate=2026-01-01&endDate=2026-01-31
```

**数据模型**：
```sql
-- 账户余额表
CREATE TABLE billing_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNIQUE NOT NULL,
  tenant_id BIGINT,
  balance DECIMAL(10,4) DEFAULT 0,
  frozen_amount DECIMAL(10,4) DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME ON UPDATE CURRENT_TIMESTAMP
);

-- 使用记录表
CREATE TABLE billing_usage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  tenant_id BIGINT,
  business_type VARCHAR(64),
  quantity DECIMAL(10,4),
  unit VARCHAR(32),
  amount DECIMAL(10,4),
  description TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 账单表
CREATE TABLE billing_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_no VARCHAR(64) UNIQUE NOT NULL,
  user_id BIGINT NOT NULL,
  tenant_id BIGINT,
  bill_type ENUM('RECHARGE', 'CONSUME', 'REFUND'),
  amount DECIMAL(10,4),
  status ENUM('PENDING', 'PAID', 'REFUNDED'),
  payment_method VARCHAR(32),
  payment_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 4.5 AI员工市场模块

**功能清单**：
- [ ] AI员工列表
- [ ] 员工详情查看
- [ ] 员工雇佣
- [ ] 任务分配
- [ ] 执行监控
- [ ] 效果评估

**API接口**：
```java
// 获取AI员工列表
GET /api/ai-employee/market?page=1&size=10&category=customer_service

// 雇佣AI员工
POST /api/ai-employee/employment
{
  "employeeId": 456,
  "duration": 30,
  "packageId": 789
}

// 分配任务
POST /api/ai-employee/task
{
  "employmentId": 101,
  "taskType": "CUSTOMER_SERVICE",
  "taskData": {
    "platform": "whatsapp",
    "contact": "customer_001"
  }
}
```

## 五、实施计划

### 5.1 阶段一：基础功能（1-2个月）

**目标**：完成核心功能闭环，支持基本商业化

| 任务 | 负责人 | 工期 | 交付物 |
|------|--------|------|--------|
| 设备注册与心跳 | 后端开发 | 1周 | API接口、数据库 |
| 任务创建与执行 | 后端开发 | 2周 | 状态机、调度器 |
| 技能市场基础 | 前端+后端 | 2周 | 技能列表、安装 |
| 计费基础 | 后端开发 | 1周 | 额度管理、扣费 |
| App核心功能 | 移动端开发 | 2周 | 注册、绑定、执行 |

**验收标准**：
- 设备绑定成功率 > 95%
- 任务执行成功率 > 85%
- 计费准确率 100%

### 5.2 阶段二：商业化完善（2-3个月）

**目标**：完善计费体系，支持多种变现模式

| 任务 | 负责人 | 工期 | 交付物 |
|------|--------|------|--------|
| 套餐管理 | 后端开发 | 1周 | 套餐CRUD、订阅 |
| 支付对接 | 后端开发 | 2周 | 支付宝、微信支付 |
| 技能创作者分成 | 后端开发 | 1周 | 收益计算、提现 |
| 数据报表 | 后端+前端 | 2周 | 仪表盘、统计 |
| AI员工基础 | 后端开发 | 2周 | 员工列表、雇佣 |

**验收标准**：
- 支付成功率 > 98%
- 分成计算准确率 100%
- 报表数据延迟 < 5分钟

### 5.3 阶段三：生态扩展（3-6个月）

**目标**：构建完整生态，支持大规模商业化

| 任务 | 负责人 | 工期 | 交付物 |
|------|--------|------|--------|
| 云手机管理 | 后端开发 | 3周 | 实例管理、任务调度 |
| 社交媒体集成 | 后端开发 | 2周 | 多平台发布、互动 |
| 技能审核系统 | 后端+前端 | 2周 | 审核流程、安全检查 |
| 内容审核 | 后端开发 | 1周 | 违规检测、人工审核 |
| 开放API | 后端开发 | 2周 | 开发者文档、SDK |

**验收标准**：
- 云手机实例启动率 > 98%
- 社交媒体发布成功率 > 95%
- 技能审核周期 < 24小时

## 六、技术实现细节

### 6.1 前端实现（PokeClaw App）

**核心组件**：
```kotlin
// 设备注册
class DeviceRegistrationManager {
    fun register(deviceInfo: DeviceInfo): RegistrationResult {
        // 1. 调用后端注册接口
        // 2. 保存设备Token
        // 3. 启动心跳服务
    }
}

// 任务执行
class TaskExecutionService {
    fun pollTasks(): List<Task> {
        // 1. 长轮询获取任务
        // 2. 解析任务命令
        // 3. 执行工具调用
        // 4. 回传结果
    }
}

// WebSocket通信
class ClawWebSocketManager {
    fun connect() {
        // 1. 建立WebSocket连接
        // 2. 处理心跳响应
        // 3. 接收任务推送
        // 4. 发送ACK确认
    }
}
```

### 6.2 后端实现（dyqbackupdd）

**核心服务**：
```java
// 任务编排服务
@Service
public class ClawTaskServiceImpl implements ClawTaskService {
    public String createTask(ClawTaskCreateReqDTO req) {
        // 1. 创建任务记录
        // 2. 分配到设备队列
        // 3. 触发任务调度
        // 4. 返回任务UUID
    }
    
    public boolean completeTask(String taskUuid, String result) {
        // 1. 更新任务状态
        // 2. 记录执行结果
        // 3. 触发计费
        // 4. 发送完成通知
    }
}

// 设备管理服务
@Service
public class ClawDeviceServiceImpl implements ClawDeviceService {
    public boolean heartbeat(String deviceId, ClawDeviceHeartbeatReqVO req) {
        // 1. 更新设备状态
        // 2. 检查待执行任务
        // 3. 返回任务数量
        // 4. 更新心跳时间
    }
}
```

### 6.3 数据库设计

**核心表结构**：
```sql
-- 用户表
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) UNIQUE NOT NULL,
  phone VARCHAR(20),
  email VARCHAR(128),
  tenant_id BIGINT,
  status ENUM('ACTIVE', 'DISABLED'),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 租户表
CREATE TABLE sys_tenant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  package_id BIGINT,
  status ENUM('ACTIVE', 'DISABLED'),
  expire_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 套餐表
CREATE TABLE billing_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  price DECIMAL(10,2),
  duration_days INT,
  token_quota BIGINT,
  duration_quota INT,
  task_quota INT,
  status ENUM('ACTIVE', 'DISABLED'),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 七、风险与应对

### 7.1 技术风险

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 设备兼容性问题 | 部分设备无法正常工作 | 建立设备兼容性测试矩阵，优先支持主流机型 |
| 网络不稳定 | 任务执行中断 | 实现断线重连、任务续传、本地缓存 |
| 模型调用失败 | AI功能不可用 | 多模型降级、本地模型备选、错误重试 |
| 安全漏洞 | 数据泄露、恶意攻击 | 安全审计、渗透测试、数据加密 |

### 7.2 业务风险

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 用户付费意愿低 | 收入不达预期 | 提供免费试用、价值教育、阶梯定价 |
| 技能质量参差不齐 | 用户体验差 | 建立审核机制、用户评价、质量监控 |
| 竞争对手模仿 | 市场份额下降 | 持续创新、构建壁垒、深耕垂直场景 |
| 政策监管变化 | 业务受限 | 合规先行、政策跟踪、灵活调整 |

### 7.3 运营风险

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 客服压力大 | 用户满意度下降 | AI客服辅助、知识库建设、自助服务 |
| 内容违规 | 平台封禁 | 内容审核、敏感词过滤、人工复核 |
| 资金安全 | 用户投诉 | 资金托管、对账机制、退款保障 |

## 八、成功指标

### 8.1 北极星指标

| 指标 | 定义 | 目标 |
|------|------|------|
| **日活跃用户数（DAU）** | 每日使用平台的用户数 | 持续增长 |
| **付费转化率** | 试用用户转化为付费用户的比例 | >15% |
| **用户留存率** | 7日/30日留存率 | >40%/>20% |
| **ARPU** | 每用户平均收入 | 持续提升 |

### 8.2 关键业务指标

| 维度 | 指标 | 目标 |
|------|------|------|
| **用户增长** | 日新增用户数 | 持续增长 |
| **激活转化** | 注册到首个任务完成率 | >60% |
| **付费转化** | 试用到付费转化率 | >15% |
| **留存率** | 7日/30日留存率 | >40%/>20% |
| **收入** | ARPU（每用户平均收入） | 持续提升 |
| **满意度** | NPS（净推荐值） | >50 |

### 8.3 技术指标

| 指标 | 目标 |
|------|------|
| API响应时间 | <200ms |
| 任务执行成功率 | >90% |
| 系统可用性 | >99.9% |
| 数据备份频率 | 每日 |

## 九、总结

本落地文档详细描述了PokeClaw商业化完整闭环的实施计划，包括：

1. **用户角色与权限**：明确5类用户角色及其权限矩阵
2. **商业化闭环流程**：从用户获取到变现的完整流程
3. **核心功能模块**：设备管理、技能市场、任务编排、计费结算、AI员工市场
4. **实施计划**：分3个阶段，6个月内完成
5. **技术实现细节**：前后端核心组件和数据库设计
6. **风险与应对**：技术、业务、运营三类风险及应对措施
7. **成功指标**：北极星指标、业务指标、技术指标

通过本方案的实施，PokeClaw将构建一个完整的AI手机代理商业化生态，实现**B2B2C**的商业模式闭环。