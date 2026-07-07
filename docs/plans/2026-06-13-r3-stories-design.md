# R3 Stories 设计文档

**日期**：2026-06-13
**作者**：Ralph
**状态**：已批准（用户通过 AskUserQuestion 选定 4 条 P1 + 1 条 P2）
**上游 PRD**：`.omc/prd.json`（30 条已 passes=true）

---

## 1. 背景

R2 完成了 14 条端云 sync + 16 条发散 story（30/30 通过）。本轮 R3 用户从 BACKLOG 中未覆盖项 + 头脑风暴选出 5 条新发散 story，全部 single-end 可独立开发，依赖现成 harness。

## 2. 5 条新发散 story

### US-D-017-TELEGRAM-HARDENING (P1)
**目标**：把 Telegram bot token 路径从「配置可用」升级为「第一类远程控制通道」——token 校验 / 轮询状态 / 双向消息 / E2E QA 入口。

**关键约束**：
- 不重复 BotFather 配置文档
- token 校验通过 Telegram `getMe` API 一次即可
- 端侧守护协程走 `getUpdates` 长轮询（默认 30s 长轮询超时）
- 收到 `/start` 或普通文本时直接路由到 `TaskOrchestrator.startNewTask(channel=Channel.TELEGRAM)`
- response 通过 `sendMessage` 回传 bot（chatId 来自 update）
- 失败/断线退避 1s/5s/30s

**AC**：
- `TelegramBotController.kt` 单例，启动/停止协程，`getMe` 校验 token
- `KVUtils` 增 `TELEGRAM_BOT_TOKEN` / `TELEGRAM_LAST_UPDATE_ID` / `TELEGRAM_CONNECTED`
- Settings 显示状态：未配置 / 已连接(bot username) / 失败(errorCode)
- 收到 update 时记录 `XLog.d('telegram: chatId=X text=Y')`
- 端任务列表显示 channel=`TELEGRAM` 区分
- 单测：`TelegramUpdateParser` 解析 3 种 update 类型

### US-D-018-USER-MEMORY (P1)
**目标**：用户显式记忆 + 自动建议（需审批）。可删除/导出，禁止存储 secrets/keys/recovery codes。

**关键约束**：
- 三种触发：(a) 用户主动 `/remember xxx` (b) 任务完成后弹「记住这次？」建议（默认不自动写入）(c) `MemorySuggester` 检测到的对话模式，需弹窗确认
- 严格过滤 secrets：`regex` 匹配 `token|password|secret|key|bearer|jwt|otp|2fa|recovery|seed|mnemonic`，命中直接 reject
- KV key：`user_memory_v1`，单条上限 1000 字符，总条数 200
- 删除 / 导出走 `UserMemoryStore.delete()` / `exportJson()`

**AC**：
- `UserMemoryStore.kt` 含 `add/memory/list/get/delete/exportJson`
- 写入时 secrets 过滤命中返回 `REJECTED_SECRET`，`XLog.w('memory: rejected secret pattern')`
- Settings 增「我的记忆」列表 + 「记住」入口 + 导出按钮
- TaskOrchestrator 注入记忆段（≤ 5 条最近，优先按 lastUsedAt）
- 单测：3 条 secrets 正则命中拒绝 + 2 条正常存入

### US-D-019-SCOPED-CHANNEL-RULES (P1)
**目标**：按 channel（WhatsApp / Telegram / Gmail / Browser / Phone / Cloud / Local）分别加载规则段，避免每条任务都把全部规则塞进 prompt。

**关键约束**：
- assets 目录增 `channel_rules_<channel>.md`，每段 ≤ 80 行
- `ChannelRuleLoader.kt` 仅加载目标 channel 的段 + 一段全局规则（`channel_rules_global.md`）
- `TaskOrchestrator.withRoleAugmentedConfig` 接入 `ChannelRuleLoader.loadFor(channel)`
- 现有 system prompt 不动，只在尾部 append channel 规则段

**AC**：
- 6 个 channel 各有 rules 文件（global/local/cloud/whatsapp/telegram/gmail/browser/phone 中至少 5 个）
- `loadFor(channel)` 返回 channel-specific 段 + global 段
- 不存在的 channel fallback 到 global only
- `XLog.d('channel-rules: channel=X lines=N')`
- 单测：6 个 channel 各自命中 / fallback

### US-D-020-CUSTOM-MODEL-SOURCE (P1)
**目标**：用户可自定义 litertlm 模型 URL + 校验和 + 镜像源，不仅限内置 catalog。

**关键约束**：
- URL 必须 HTTPS，host 必须在 allowlist 默认 `["huggingface.co", "github.com", "gitlab.com", "*.self-hosted.io"]`
- 可选 SHA-256 校验和，下载完成后校验
- 模型元数据：id/name/url/sha256?/sizeBytes?/minRamGb?/enabled
- KV key：`custom_model_sources_v1`，上限 10 条
- 复用 `LocalModelManager.downloadModel` 路径，仅扩展 sources

**AC**：
- `CustomModelSource.kt` data class + `CustomModelSourceStore.kt` CRUD
- `LocalModelManager.AVAILABLE_MODELS` 拼接 `customModelSources()` 形成完整 catalog
- URL 不在 allowlist 返回 `URL_NOT_ALLOWED`
- SHA-256 校验失败返回 `CHECKSUM_MISMATCH`，并删除已下载文件
- Settings「自定义模型源」UI：+ 添加 / 删除 / 启用开关
- `XLog.d('custom-model: id=X url=Y sha256=Z')`

### US-D-021-TASK-SCHEDULER (P2)
**目标**：cron 表达式 + 单次定时任务。AlarmManager 触发 → WorkManager 执行 → TaskOrchestrator.startNewTask。

**关键约束**：
- 三种类型：(a) `CRON` 表达式 `分 时 日 月 周` (b) `ONCE` 时间戳 (c) `INTERVAL` 间隔秒数（60 ~ 86400）
- AlarmManager 设闹钟 → BroadcastReceiver → `WorkManager.enqueueUniqueWork(ScheduledTaskWorker)`
- 调度持久化到 SQLite（`scheduled_tasks` 表）
- UI 列表：name / type / nextRunAt / lastRunAt / enabled

**AC**：
- `ScheduledTask.kt` data class + `ScheduledTaskDao.kt` SQLite CRUD
- `CronParser.kt` 解析 cron 表达式为下次触发时间
- `ScheduledTaskReceiver.kt` 接收 AlarmManager 触发
- `ScheduledTaskActivity.kt` 列表 + 添加对话框 + 启停 toggle
- 触发后调 `TaskOrchestrator.startNewTask(channel=LOCAL_SCHEDULED)`，task metadata 带 `scheduledTaskId`
- `XLog.i('scheduler: fire id=X name=Y')`

## 3. 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| Telegram 通道独立 vs 复用 BotFather | 独立 `TelegramBotController` 单例 | 不和现有 channel 耦合，Telegram 长轮询是常驻协程，不适合 Channel 短生命周期 |
| 记忆段注入位置 | `TaskOrchestrator.withRoleAugmentedConfig` 末尾追加 | 已验证 system prompt 在此处拼接，role/global-instructions 也走这条路 |
| Channel rules 段是否落地规则引擎 | 否，纯文本加载 | R3 聚焦「按 channel 分段加载」，规则引擎是后续 story |
| 自定义模型源 allowlist 粒度 | host 级（不支持 path 通配） | 简化校验，避免 path 通配引入的安全问题 |
| 任务调度器触发精度 | AlarmManager（min 1min 抖动）+ WorkManager（约束补足） | Doze 模式 + 跨重启正确唤醒 |

## 4. 风险与依赖

- Telegram 长轮询需要稳定网络；端侧无网络时自动暂停 + XLog 标记
- 自定义模型源 SHA-256 校验增加下载耗时（小文件可忽略，大文件 ~1s）
- 任务调度器在 Android 12+ 需用户授予 `SCHEDULE_EXACT_ALARM` 权限，本期不强制要求精确
- Channel rules 引入会让 system prompt 略微变长，但每段 ≤ 80 行可控

## 5. 实施顺序

依赖图：
```
US-D-017 TELEGRAM-HARDENING  ─┐
US-D-018 USER-MEMORY         ─┼─ 全部 P1 并行无依赖
US-D-019 SCOPED-CHANNEL-RULES─┤
US-D-020 CUSTOM-MODEL-SOURCE ─┘
                                ↓
US-D-021 TASK-SCHEDULER (依赖 TaskOrchestrator 已有 startNewTask，独立)
```

按 ralph 协议并行实现，每条 story 单独验证 acceptance criteria。