# PokeClaw 手机控制任务白名单与失败回传验收口径

> 文档编号：POK-TASK-WH-001  
> 版本：v1.0  
> 关联 Issue：CMP-2016  
> 适用主线：PokeClaw 安卓端（端侧执行节点）

---

## 一、文档目的

定义 PokeClaw 作为小龙虾云端中枢下属执行节点时，手机控制任务的 MVP 白名单、失败回传字段、安全提示规范，确保端侧任务范围可控、用户可理解、云端可学习。

---

## 二、任务白名单（MVP 阶段）

### 2.1 白名单设计原则

1. **低风险优先**：优先支持只读、查询类操作，避免高风险系统级修改
2. **用户可见**：所有操作必须能在 UI 上有可见反馈，禁止静默后台操作
3. **可回滚**：支持的操作应有明确取消/回退路径
4. **云端可控**：任务执行前必须经云端指令确认，端侧不自主决策

### 2.2 任务白名单分级

| 等级 | 任务类别 | 示例 | 风险等级 | 用户确认 |
|:-----|:---------|:-----|:---------|:---------|
| P0 | 信息查询类 | 查看电量、存储空间、网络状态、已安装应用列表 | 低 | 不需要 |
| P0 | 系统设置查询 | 查看亮度、音量、WiFi 连接状态、蓝牙状态 | 低 | 不需要 |
| P1 | 简单 UI 操作 | 滑动屏幕、点击坐标、返回/主页/多任务键 | 中 | 建议确认 |
| P1 | 应用启动/切换 | 打开指定应用、切换到后台应用 | 中 | 建议确认 |
| P2 | 简单输入操作 | 在已聚焦输入框中输入文本 | 中 | 必须确认 |
| P3 | 系统设置修改 | 调整亮度、音量（限范围） | 中高 | 必须确认 |
| P3 | 文件操作 | 读取指定路径文件（限沙箱内） | 中高 | 必须确认 |

### 2.3 禁止任务清单（红线）

以下任务类型**严禁**在 MVP 阶段执行：

| 类别 | 具体行为 | 禁止原因 |
|:-----|:---------|:---------|
| 支付类 | 自动填写支付密码、确认支付、转账 | 资金安全风险 |
| 敏感信息 | 自动读取短信验证码、银行 App 信息、聊天记录 | 隐私泄露风险 |
| 系统级修改 | 修改系统分区、刷机、Root、卸载系统应用 | 设备损坏风险 |
| 批量操作 | 批量删除联系人、批量卸载应用、批量发送消息 | 误操作影响大 |
| 未知来源 | 安装 APK、启用未知来源应用、修改安全设置 | 恶意软件风险 |
| 远程控制 | 自动接听电话、自动发送短信、自动拨打电话 | 通信安全风险 |

---

## 三、失败回传字段规范

### 3.1 失败分类体系

```
失败大类（errorCategory）
├── DEVICE_ERROR          // 设备端错误
│   ├── PERMISSION_DENIED     // 权限被拒绝
│   ├── SERVICE_UNAVAILABLE   // 服务不可用（如无障碍服务未开启）
│   ├── RESOURCE_NOT_FOUND    // 资源未找到（如指定应用未安装）
│   └── DEVICE_BUSY           // 设备正忙（如用户正在操作）
├── TASK_ERROR            // 任务执行错误
│   ├── INVALID_COMMAND       // 无效指令
│   ├── EXECUTION_TIMEOUT     // 执行超时
│   ├── UNSUPPORTED_ACTION    // 不支持的操作（非白名单任务）
│   └── EXECUTION_FAILED      // 执行失败（具体原因在 errorDetail）
├── NETWORK_ERROR         // 网络相关错误
│   ├── OFFLINE               // 设备离线
│   ├── SERVER_UNREACHABLE    // 服务器不可达
│   └── SYNC_FAILED           // 同步失败
└── USER_ERROR            // 用户相关
    ├── USER_CANCELLED        // 用户取消
    └── USER_BLOCKED          // 用户阻止（如拒绝权限请求）
```

### 3.2 失败回传数据结构

对齐 device.openapi.yaml 的 TaskResultRequest，扩展错误字段：

```kotlin
data class TaskResultRequest(
    // 基础字段（已存在）
    val status: String,                 // SUCCESS, FAILED, RUNNING, CANCELLED
    val result: String?,                // 执行结果文本
    val errorMessage: String?,          // 错误信息（用户可读）
    val executionTimeMs: Long?,         // 执行耗时
    val toolCalls: String?,             // 工具调用记录（JSON）
    val evidenceUrls: String?,          // 证据 URL 列表（JSON）
    val modelUsed: String?,             // 使用的模型
    
    // 新增失败回传字段
    val errorCategory: String?,          // 错误大类（见 3.1）
    val errorCode: String?,              // 错误码（见 3.3）
    val errorDetail: String?,            // 详细错误信息（技术层面）
    val recoverable: Boolean?,           // 是否可重试
    val suggestedAction: String?,        // 建议用户操作
    val screenshotBase64: String?,       // 失败时的截图（可选）
    val logSnippet: String?              // 相关日志片段
)
```

### 3.3 错误码定义

| 错误码 | 所属大类 | 含义 | 建议操作 |
|:-------|:---------|:-----|:---------|
| E001 | DEVICE_ERROR | 无障碍服务未开启 | 引导用户开启无障碍服务 |
| E002 | DEVICE_ERROR | 缺少必要权限 | 申请权限并引导用户授权 |
| E003 | DEVICE_ERROR | 指定应用未安装 | 提示用户安装应用 |
| E004 | DEVICE_ERROR | 设备存储空间不足 | 提示用户清理空间 |
| E005 | DEVICE_ERROR | 设备电量过低 | 提示用户充电后重试 |
| E101 | TASK_ERROR | 指令解析失败 | 云端检查指令格式 |
| E102 | TASK_ERROR | 非白名单任务 | 云端调整任务范围 |
| E103 | TASK_ERROR | 任务执行超时 | 分解任务或延长超时 |
| E104 | TASK_ERROR | 目标元素未找到 | 检查 UI 状态后重试 |
| E105 | TASK_ERROR | 任务被用户中断 | 记录中断原因 |
| E201 | NETWORK_ERROR | 设备无网络连接 | 提示用户检查网络 |
| E202 | NETWORK_ERROR | 服务器连接失败 | 稍后自动重试 |
| E203 | NETWORK_ERROR | 认证令牌过期 | 自动刷新令牌 |

---

## 四、用户可理解的安全提示

### 4.1 提示分级

| 级别 | 场景 | 提示方式 | 用户操作 |
|:-----|:-----|:---------|:---------|
| INFO | 正常执行 | Toast 提示 | 无需操作 |
| WARN | 可能需要确认 | Snackbar + 撤销按钮 | 可撤销 |
| CONFIRM | 敏感操作前 | 对话框确认 | 必须确认/取消 |
| BLOCK | 高风险/禁止操作 | 对话框 + 阻止执行 | 无法继续 |

### 4.2 标准提示文案

```kotlin
object SecurityPrompts {
    // 信息类
    const val TASK_STARTED = "正在执行：{taskName}"
    const val TASK_COMPLETED = "已完成：{taskName}"
    
    // 警告类
    const val PERMISSION_REQUIRED = "需要{permission}权限才能继续"
    const val UNSUPPORTED_ACTION = "暂不支持此操作：{action}"
    
    // 确认类
    const val CONFIRM_SYSTEM_CHANGE = "即将修改系统设置：{setting}\n是否继续？"
    const val CONFIRM_INPUT_TEXT = "即将在「{appName}」输入：\n{text}\n是否确认？"
    
    // 阻止类
    const val BLOCKED_PAYMENT = "为了您的资金安全，暂不支持自动支付操作"
    const val BLOCKED_SENSITIVE = "此操作涉及敏感信息，请在设备上手动完成"
    const val BLOCKED_UNKNOWN_APP = "检测到尝试安装未知来源应用，已阻止"
}
```

### 4.3 云端学习任务提示

当任务失败需要云端小龙虾学习时，上报的提示需包含：

1. **用户层面**：简洁说明失败原因和建议操作
2. **云端层面**：结构化错误码和上下文，供中枢学习
3. **日志层面**：完整执行轨迹和决策路径

---

## 五、验收用例

### 5.1 白名单验证用例

| 用例编号 | 任务描述 | 期望结果 | 验收标准 |
|:---------|:---------|:---------|:---------|
| TC-001 | 查询设备电量 | 返回电量百分比 | 数值准确、单位正确 |
| TC-002 | 查询存储空间 | 返回可用/总空间 | 数值与系统设置一致 |
| TC-003 | 查看已安装应用列表 | 返回应用名称列表 | 包含用户安装的应用 |
| TC-004 | 滑动屏幕操作 | 执行向上滑动 | 页面正确滚动 |
| TC-005 | 打开指定应用 | 应用启动成功 | 应用包名正确、启动成功 |
| TC-006 | 尝试执行支付操作 | 任务被拒绝 | 返回错误码 E102 |
| TC-007 | 尝试读取短信内容 | 任务被拒绝 | 返回错误码 E102 |
| TC-008 | 尝试修改系统分区 | 任务被拒绝 | 返回错误码 E102 |

### 5.2 失败回传验证用例

| 用例编号 | 失败场景 | 期望回传字段 | 验证点 |
|:---------|:---------|:-------------|:-------|
| TC-F001 | 无障碍服务未开启 | E001, errorCategory=DEVICE_ERROR | 正确识别错误类型 |
| TC-F002 | 网络断开时执行 | E201, recoverable=true | 标记为可重试 |
| TC-F003 | 执行非白名单任务 | E102, suggestedAction=联系管理员 | 提供解决建议 |
| TC-F004 | 任务超时 | E103, executionTimeMs 有值 | 记录实际耗时 |
| TC-F005 | 用户取消任务 | E105, status=CANCELLED | 正确记录取消状态 |

### 5.3 安全提示验证用例

| 用例编号 | 场景 | 期望提示 | 验证点 |
|:---------|:-----|:---------|:-------|
| TC-S001 | 执行系统设置修改前 | 显示确认对话框 | 用户必须确认 |
| TC-S002 | 尝试支付相关操作 | 显示阻止提示 | 任务不执行 |
| TC-S003 | 执行白名单查询任务 | 显示 Toast | 不打扰用户 |
| TC-S004 | 权限被拒绝 | 显示引导提示 | 提供解决方案 |

---

## 六、与云端中枢的交互流程

### 6.1 正常任务执行流程

```
云端小龙虾中枢
    ↓ 下发任务指令（含 taskUuid、command、mode）
PokeClaw 端侧
    ↓ 验证任务在白名单内
    → 执行前：上报 RUNNING 状态
    ↓ 执行任务
    → 执行后：上报 SUCCESS/FAILED + 结果数据
云端小龙虾中枢
    ↓ 汇总执行结果
    → 更新经验库
```

### 6.2 失败任务处理流程

```
PokeClaw 端侧
    ↓ 任务执行失败
    → 捕获完整错误信息
    → 分类错误类型
    → 生成用户提示
    → 上报 FAILED + errorCategory + errorCode + errorDetail
云端小龙虾中枢
    ↓ 接收失败报告
    → 解析失败原因
    → 更新任务重试策略
    → 沉淀失败经验
    → 优化后续任务分配
```

---

## 七、文件变更清单

| 文件路径 | 变更类型 | 说明 |
|:---------|:---------|:-----|
| `/mnt/e/code/PokeClaw/docs/product/task-whitelist-and-error-handling-spec.md` | 新增 | 本文档 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt` | 扩展 | 新增错误回传字段 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/CloudTaskExecutor.kt` | 修改 | 增加白名单校验和错误分类 |
| `/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/task/TaskWhitelist.kt` | 新增 | 白名单定义文件 |

---

## 八、待验证清单

- [ ] 文档已落文件到 `/mnt/e/code/PokeClaw/docs/product/`
- [ ] 白名单分级设计已通过产品评审
- [ ] 错误码定义已与后端阿诚对齐
- [ ] CloudModels.kt 已扩展错误回传字段
- [ ] CloudTaskExecutor 已实现白名单校验
- [ ] 验收用例已同步给测试阿星
- [ ] QA_CHECKLIST.md 已更新相关测试项

---

## 九、附录：参考文档

- [AGENTS.md](/mnt/e/code/dyq/AGENTS.md) — 团队规则与三条主线
- [THREE_MAIN_GOALS.md](/mnt/e/code/dyq/.planning/pm/current/THREE_MAIN_GOALS.md) — 三主线目标作战图
- [device.openapi.yaml](/mnt/e/code/dyq/api-contracts/device.openapi.yaml) — 设备管理 API 契约
- [CloudModels.kt](/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt) — 端侧数据模型
- [DeviceCloudClient.kt](/mnt/e/code/PokeClaw/app/src/main/java/io/agents/pokeclaw/cloud/DeviceCloudClient.kt) — 云端客户端实现

---

**文档完成时间**：2026-05-16  
**负责人**：PM龙哥  
**状态**：待评审
