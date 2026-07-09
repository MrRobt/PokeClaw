# 细方向五：证据包、成功率指标、云端回执设计

> 文档编号：PC-RELIABILITY-005  
> 目标：让每次端侧执行都可复盘、可统计、可优化  

---

## 一、为什么要证据包

端侧任务失败时，如果只有一句“执行失败”，云端无法优化。必须把执行过程变成可复盘数据：

- 哪一步失败
- 模型输出了什么动作
- 校验器为什么拦截
- 工具执行前后页面是什么样
- 是否触发重试
- 是否存在权限、安全、网络问题

---

## 二、证据包结构

```json
{
  "taskId": "cloud-task-xxx",
  "deviceId": "device-xxx",
  "taskType": "SEND_MESSAGE",
  "startedAt": 0,
  "finishedAt": 0,
  "finalStatus": "FAILED",
  "finalErrorType": "TARGET_NOT_FOUND",
  "steps": [
    {
      "stepId": "LOCATE_CONTACT",
      "status": "FAILED",
      "attempts": 3,
      "lastAction": "TAP_TEXT",
      "lastErrorType": "TARGET_NOT_FOUND"
    }
  ],
  "actions": [
    {
      "round": 1,
      "actionType": "TAP_TEXT",
      "validationStatus": "ACCEPTED",
      "executionStatus": "FAILED",
      "errorType": "TARGET_NOT_FOUND",
      "durationMs": 450
    }
  ],
  "evidenceFiles": [
    {
      "type": "SCREENSHOT",
      "localPath": "...",
      "uploadedUrl": "..."
    }
  ],
  "metrics": {
    "modelRetryCount": 1,
    "toolRetryCount": 2,
    "verifyRetryCount": 1,
    "totalRounds": 6
  }
}
```

---

## 三、成功率指标

建议统计以下指标：

| 指标 | 含义 | 用途 |
|---|---|---|
| 任务成功率 | 成功任务 / 总任务 | 总体稳定性 |
| 首次动作合法率 | 第一轮模型输出合法动作比例 | 提示和模型质量 |
| 工具执行成功率 | 工具成功次数 / 工具调用次数 | 工具层稳定性 |
| 验收通过率 | 执行成功后验收通过比例 | 假成功识别能力 |
| 平均重试次数 | 每任务平均纠偏/工具重试 | 成本和稳定性 |
| 上报率 | 需要云端协助比例 | 端侧自主能力 |
| 同类失败复发率 | 同错误重复出现比例 | 经验记忆效果 |

---

## 四、云端回执分层

### 轻量回执

每次任务结束都上报：

```json
{
  "taskId": "xxx",
  "status": "SUCCESS",
  "summary": "消息已发送并验证",
  "executionTimeMs": 12800,
  "modelUsed": "local-gemma",
  "toolCalls": 7,
  "retryCount": 1
}
```

### 失败详情回执

失败时必须补充：

```json
{
  "failedStep": "LOCATE_CONTACT",
  "errorType": "TARGET_NOT_FOUND",
  "errorMessage": "未找到联系人张三",
  "lastVisibleTexts": ["搜索", "文件传输助手"],
  "suggestedAction": "请确认联系人是否存在或改用手机号"
}
```

### 完整证据包

用于调试、训练经验、复盘严重失败：

- 截图
- 控件树
- 动作轨迹
- 日志片段
- 模型输出原文
- 校验器拦截原因

---

## 五、和现有云端对接

已有文档 `docs/pokeclaw-dyq-integration-checklist.md` 中定义了结果回传字段：

```text
status
result	errorMessage
executionTimeMs
toolCalls
evidenceUrls
modelUsed
```

建议在不破坏现有接口的前提下扩展：

```text
errorType
failedStep
retryCount
actionTraceSummary
reliabilityScore
evidenceManifestUrl
```

如果云端短期不支持新字段，端侧可以先把扩展信息放入 `result` 的结构化摘要中，同时保持旧字段兼容。

---

## 六、日志要求

每次任务必须能通过日志还原：

```text
任务开始：任务编号、类型、来源
计划生成：必要步骤、完成条件
每轮观察：页面摘要编号
每轮模型：动作编号、动作类型
每轮校验：通过/拒绝及原因
每轮执行：工具结果、耗时、错误
每轮验收：通过/失败及证据
任务结束：最终状态、失败类型、回执状态
```

符合仓库现有 `CLAUDE.md` 的调试日志要求。

---

## 七、验收标准

- 每个任务都有唯一证据包编号。
- 每个失败任务都有失败步骤和错误类型。
- 每个成功任务都有验收证据，而不只是模型文本。
- 云端能看到端侧成功率、重试次数、失败类型分布。
- 同类失败能反向沉淀到经验库。
