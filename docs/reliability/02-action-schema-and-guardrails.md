# 细方向二：结构化动作协议与安全校验设计

> 文档编号：PC-RELIABILITY-002  
> 目标：让模型输出从自由文本变成可校验、可执行、可追踪的动作协议  

---

## 一、为什么必须结构化

真实手机执行不能靠模型写一段自然语言让系统猜。必须统一动作协议，否则会出现：

- 模型说“点一下那个按钮”，但执行器不知道哪个按钮。
- 模型输出不存在的工具名。
- 参数缺失，工具执行时才失败。
- 模型绕过安全边界执行危险操作。
- 执行日志无法统计成功率。

---

## 二、动作协议草案

建议所有模型动作统一为：

```json
{
  "actionId": "本轮动作编号",
  "type": "TAP_TEXT",
  "target": {
    "text": "发送",
    "bounds": null,
    "packageName": "com.tencent.mm",
    "confidence": 0.86
  },
  "parameters": {
    "text": null,
    "timeoutMs": 3000
  },
  "reason": "当前在聊天页，消息已输入，需要点击发送",
  "expectedChange": "聊天窗口出现刚发送的消息",
  "riskLevel": "LOW"
}
```

---

## 三、动作类型白名单

第一期动作类型建议控制在少量高价值动作：

| 动作类型 | 说明 | 是否第一期 |
|---|---|---|
| `OBSERVE_SCREEN` | 重新观察当前页面 | 是 |
| `TAP_TEXT` | 点击包含指定文本的控件 | 是 |
| `TAP_BOUNDS` | 点击指定坐标区域 | 是，但要求置信度和截图证据 |
| `INPUT_TEXT` | 输入文本 | 是 |
| `PRESS_BACK` | 返回 | 是 |
| `WAIT` | 等待页面变化 | 是 |
| `OPEN_APP` | 打开应用 | 是 |
| `SCROLL` | 滑动页面 | 是 |
| `REQUEST_CLOUD_HELP` | 请求云端协助 | 是 |
| `FINISH_TASK` | 请求完成任务 | 是，但必须验收器确认 |

明确禁止第一期支持：

- 支付确认
- 删除联系人/聊天记录
- 批量群发
- 修改系统安全设置
- 读取高敏隐私后自动外发
- 无用户确认的转账类动作

---

## 四、动作校验器

建议新增：

```text
ActionValidator
ActionSchemaValidator
ActionSafetyValidator
ActionPermissionValidator
ActionTargetValidator
```

校验顺序：

```text
格式校验 → 动作名白名单 → 参数完整性 → 权限校验 → 风险校验 → 当前步骤匹配校验
```

任何一层失败，都不能执行动作。

---

## 五、校验失败后的纠偏

校验失败不要直接判任务失败，要生成明确纠偏提示：

```text
你的动作不能执行，原因：缺少 target.text 或 target.bounds。
请只返回一个合法动作，动作类型必须来自白名单。
不要解释，不要输出自然语言总结。
```

纠偏次数计入模型错误预算。

---

## 六、工具结果标准化

建议扩展工具结果字段：

```json
{
  "success": false,
  "errorType": "TARGET_NOT_FOUND",
  "errorMessage": "当前页面没有找到文本：发送",
  "retryable": true,
  "evidence": {
    "screenshotPath": "...",
    "uiDumpPath": "...",
    "visibleTexts": ["输入框", "表情", "更多"]
  },
  "durationMs": 450
}
```

错误类型建议：

| 错误类型 | 含义 | 建议处理 |
|---|---|---|
| `INVALID_ACTION` | 模型动作格式错误 | 纠偏重试 |
| `UNKNOWN_ACTION` | 不存在的动作 | 纠偏重试 |
| `MISSING_PERMISSION` | 权限不足 | 引导用户开启权限 |
| `TARGET_NOT_FOUND` | 控件不存在 | 重新观察或换策略 |
| `APP_NOT_FOREGROUND` | 目标应用不在前台 | 打开应用 |
| `EXECUTION_TIMEOUT` | 工具执行超时 | 重试或上报 |
| `SAFETY_BLOCKED` | 安全拦截 | 停止并提示用户 |
| `VERIFY_FAILED` | 执行后验收失败 | 换策略或上报 |

---

## 七、现有代码接入点

| 路径 | 建议 |
|---|---|
| `app/src/main/java/io/agents/pokeclaw/tool/BaseTool.kt` | 工具声明补充动作类型、风险等级、必填参数 |
| `app/src/main/java/io/agents/pokeclaw/tool/ToolRegistry.kt` | 只允许注册动作白名单内工具 |
| `app/src/main/java/io/agents/pokeclaw/tool/ToolResult.kt` | 扩展错误分类、证据路径、可重试字段 |
| `app/src/main/java/io/agents/pokeclaw/task/TaskWhitelist.kt` | 继续作为任务级安全边界，和动作白名单分层 |

---

## 八、验收标准

- 模型输出非法动作时，执行器不会调用底层工具。
- 所有动作执行前都有校验日志。
- 所有工具结果都有统一错误类型。
- 危险动作能被稳定拦截。
- 云端回执能看到动作序列和失败原因。
