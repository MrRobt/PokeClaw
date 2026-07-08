# PokeClaw Backlog

Items go in, get prioritized, get done, get crossed out. Simple.

Priority: `P0` = blocks users, fix now. `P1` = next up. `P2` = when we get to it. `P3` = nice to have.

---

## Bugs

- [ ] **P1** (自主迭代 P1-3 发现) Cloud 子系统硬编码私有兜底 endpoint `http://192.168.250.3:8080` 焊死在发布包里；应移除 / 改 debug-only / 默认无端点。见 `CLOUD_SUBSYSTEM_BOUNDARY.md` §3.1
- [ ] **P2** (自主迭代 P1-3) Cloud 子系统：disabled 时改惰性构造（现每次启动都构造 orchestrator/client）+ 加 `CLOUD_NODE_ENABLED` BuildConfig 硬关 + 补 reconstruction phase/owner，或抽独立 module。见 `CLOUD_SUBSYSTEM_BOUNDARY.md` §6
- [ ] **P1** Historical upgrade gap: users on the older public debug signing path still need a one-time uninstall + reinstall because the original public signing key is already lost
- [ ] **P2** K3-a: Auto-return fires on every service connect, not just user-initiated permission enable
- [ ] **P2** B2-a: No auto-return to PokeClaw after task completes in another app (e.g., stuck in YouTube)
- [ ] **P1** Investigate MediaTek/Samsung local-engine bring-up failures that still report OpenCL/LiteRT engine creation errors on some devices even after GPU→CPU fallback
- [ ] **P2** Settings screen: active model row breaks layout when the model name is long; keep the label/value aligned and truncate or wrap cleanly without shoving the left label into a narrow column

## Features

- [x] ~~**P0** (自主迭代) reliability salvage~~ — 2026-07-07 完成（E1）：从 origin/dev 抢救 ActionValidator 动作校验 + ExecutionTrace 执行追踪到 main（v0.7.2）。`compileDirectDebugKotlin` 通过 + `ActionValidatorTest` 4/4 通过。commit `29edd7a` on `feature/reliability-salvage`；E2 emulator smoke 因沙箱无 AVD 待补
- [x] ~~**P0** Missed-call auto follow-up~~ — implemented 2026-06-16: MissedCallReceiver检测未接来电，WorkManager调度SMS follow-up，Settings开关+模板配置，ComposeChatActivity状态显示，SMS-native API优先
- [x] ~~**P0** Production external automation intent: promote the debug-only task/chat broadcast into a user-enabled production API for Tasker, MacroDroid, Locale, and ADB-style callers. It should accept explicit package/component broadcasts with `task` / `chat` / base64 extras, preserve harness safety rules, and optionally return a result callback intent.~~ — implemented 2026-04-30; callback contract exists, Tasker/MacroDroid callback E2E remains a QA gap
- [ ] **P1** Persistent global instructions: add a user-editable local instructions layer that applies to new tasks/conversations without becoming a prompt dump. It must be short, inspectable, removable, local-first, and separate from hard safety/tool rules.
- [ ] **P1** Scoped app/channel rules: support rules scoped to apps or channels such as WhatsApp, Telegram, Gmail, Browser, and Phone so the harness loads only relevant guidance instead of stuffing every rule into every local-model context.
- [ ] **P1** Explicit user-approved memory: add manual "remember this" style memory first, then optional suggested memories only after user approval. Memory must be deletable/exportable and must not store secrets, bot tokens, API keys, or recovery codes.
- [ ] **P1** Telegram remote-control channel hardening: treat the Telegram bot token path as a first-class remote-control channel with clear setup, token validation, polling status, and E2E QA gates. Current QA can configure a bot token, but live send-to-bot E2E is blocked on the QA Telegram account being frozen/read-only.
- [ ] **P2** Voice input: add a prompt microphone button as an input method, preferably using an available cloud transcription path when the user has a cloud API key and a local/on-device option later. Wake-word/background listening is a separate higher-risk permission/battery design, not the MVP.
- [ ] **P1** Local model import UX: keep shared-storage `.litertlm` import easy and explain clearly why other apps' `Android/data/...` sandboxes (for example Edge Gallery) are not directly readable
- [ ] **P1** More small local model options: add 1B / 1.5B-class local models so lower-RAM phones can still run a useful on-device agent
- [ ] **P1** Custom local model sources: let users point PokeClaw at user-defined model URLs / hosted downloads instead of only the built-in catalog
- [ ] **P2** Google AI Core integration research: evaluate Android's official on-device AI / system model APIs as an optional local runtime path
- [ ] **P1** Structured monitor identifiers: let monitor setup keep a user-facing nickname while using a more stable identifier where possible (phone number / app-stable id / aliases) so WhatsApp/Telegram display-name drift stops breaking setup
- [ ] **P2** Chat keyboard dismissal polish: tapping non-button chatroom space should reliably clear focus and hide IME in both empty and non-empty conversations
- [ ] **P1** Structure-first UI matching: remove remaining language-specific text heuristics where the platform exposes a stable structural hook first (dialog positive buttons, send affordances, standard action widgets)
- [ ] **P1** Tinder automation: auto swipe + monitor matches + auto-reply using same monitor architecture as WhatsApp
- [x] ~~**P1** NLP Playbooks (Layer 2): 5 playbooks in system prompt (Search in App, Navigate Settings, Compose Email, Read Screen, Read Notifications)~~ — done 2026-04-08
- [x] ~~**P1** In-chat task auto-return~~ — done 2026-04-08
- [x] ~~**P2** Monitor stays in app~~ — done 2026-04-08, removed GLOBAL_ACTION_HOME
- [ ] **P2** Unified task registry: monitor + agent tasks tracked in same system (top bar, floating button, etc.)
- [ ] **P3** Rename chat session (H6): pencil icon in sidebar → InputDialog → update title in DB + markdown
- [ ] **P3** Floating button: use PokeClaw icon instead of "AI" text
- [ ] **P3** ChatViewModel extraction: move business logic out of ComposeChatActivity god class

### Agent-controls-Claw + Cloud-YOLO (per-software model center)

- [x] ~~**P1** App-side Agent→Claw + cloud per-software YOLO system~~ — 2026-07-08: `vision/`+`explore/`+`collect/`+`device/`+`cloud/modelhub`+`cloud/cloudphone`+`ui/console/VisionConsoleActivity`; cloud `../dyq/claw-yolo-hub` (FastAPI). Routing (active→category→generic), model cache/version-update, weak-label detector, auto-explorer (state-hash dedup + coverage), dataset upload by software_key, simulated training→candidate→promote/rollback. 19 JVM tests + 5 hub E2E green; `assembleDirectDebug` builds. See `docs/AGENT_CLAW_YOLO_SYSTEM.md`.
- [ ] **P1** Real on-device YOLO inference: wire ONNX/LiteRT into `vision/YoloModelBackend` (currently delegates to weak labels; detections already carry model id/version)
- [ ] **P1** Real cloud training: run gxe/ultralytics from `claw-yolo-hub` (`YOLOHUB_REAL_TRAINER=1`) where GPU is available; today training metrics are simulated from dataset volume/quality
- [ ] **P2** Remote observation: decode the cloud-phone screen-wall stream so `CloudPhoneActuator` gets a matching `DeviceObserver` (remote explore, not just remote control)
- [x] ~~**P1** Java `dyq-module-claw` YOLO 模型中心~~ — 2026-07-08 直接 Java 实现（非 proxy，逻辑等价 Python hub）：4 DO + 4 Mapper + `YoloHubService[Impl]` + `YoloDeviceController`(`/api/v1`,@PermitAll) + snake-case VO + `ClawYoloAuthorizeRequestsCustomizer` + SQL。codex 二审 + 独立复核**零编译错误**。已修 3 个真 bug：①多租户 NPE（4 张 YOLO 表加入 `dyq-server`/`-cloudphone`/`-hermes`/`-aigc-publish` 的 `tenant.ignore-tables`，否则设备端无租户上下文首调即 500 静默失败）②App `TrainingJobDto.metrics` 类型（`Map<String,Double>`→`Map<String,Any?>`，否则 `"simulated":true` 布尔令 Gson 抛异常静默返回 null）③`uploadSamples` 空 batch 判空。**待测试服务器 `mvn` 编译确认**。
- [ ] **P3** (dyq 历史遗留，codex 发现，与本次 YOLO 无关) 其它 server 变体的 `tenant.ignore-tables` 缺 `claw_device*` 三表豁免，设备端 `/api/claw-device/**` 有同样 NPE 风险——需确认哪些 binary 承接设备流量后统一补
- [ ] **P2** On-device E2E (E3) for the whole loop on a real cloud phone: identify→resolve→detect→explore→collect→upload→train→promote→update (blocked here: no adb/device in sandbox)

## QA Gaps

- [ ] **P0** Missed-call follow-up E2E: missed-call notification / phone-state trigger reaches PokeClaw, follow-up message is sent to the caller, and the result/status is visible in the same chatroom — code ready, blocked on 2nd device/SIM
- [ ] **P0** Production intent E2E: Tasker/MacroDroid-style explicit broadcast reaches PokeClaw in a release build, starts the requested task/chat, and never bypasses safety/global rules — code ready, blocked on Tasker/MacroDroid install
- [ ] **P1** Production intent callback E2E: when an external automation request includes `request_id` and `return_action`, PokeClaw broadcasts a completion/failure result that Tasker/MacroDroid can consume
- [ ] **P1** Telegram bot channel E2E: token configured -> polling connected -> user sends `/start` and a task to the bot -> PokeClaw receives the update -> returns a visible bot reply. Current QA is blocked by the handset Telegram account being frozen/read-only.
- [ ] **P1** C2: Auto-reply trigger E2E — needs 2nd device to send WhatsApp message to Girlfriend
- [ ] **P1** Release QA: verify locally signed `0.5.1+` public APK can upgrade in-place over the next signed public build once the stable key is installed in GitHub Actions
- [x] ~~**P1** M1-M12 QA: Cloud LLM complex tasks~~ — done 2026-04-08, 10/12 PASS
- [ ] **P2** K6: Verify each Settings permission row leads to correct system settings page
- [ ] **P2** Settings layout QA: verify long local/cloud model names render cleanly on the Settings screen across Pixel/Samsung widths
- [ ] **P2** Download free space check — done 2026-04-08 (StatFs before download)
- [ ] **P1** Local vague-task UX: in Local Task mode, prompt-only behavior is correct, but vague requests like `Copy that token to the clipboard` currently hang instead of failing fast with a clear request for the missing content/details

## Ideas / Research

- Monetization: two-tier (dev=free open source, consumer=China APK + premium features)
- YC application showcase
- Layer 2 NLP Playbooks as "App Cards" like DroidRun
- On-device LLM as competitive moat (first to ship with Gemma 4)
- Positioning: cloud/desktop-driven mobile-agent frameworks already exist; PokeClaw should own the phone-resident, local-first, model-slot harness that can run on a user's own Android device without a PC/cloud phone fleet.

---

## Done

_Move completed items here with date._

- [x] ~~2026-04-30: Production External Automation API~~ — user-enabled `RUN_TASK` / `RUN_CHAT` broadcasts, base64 extras, safety opt-in, and task terminal callback contract
- [x] ~~2026-04-08: Fix "Accessibility starting..." on every chat (A1-b)~~
- [x] ~~2026-04-08: Floating button IDLE→RUNNING in other apps (F3-b)~~
- [x] ~~2026-04-08: LiteRT-LM session conflict + GPU→CPU fallback (D1-a, D1-b)~~
- [x] ~~2026-04-08: Monitor permission check + auto-return after grant~~
- [x] ~~2026-04-08: Settings page: Notification Access row~~
- [x] ~~2026-04-08: Full QA pass 49/50 cases~~
- [x] ~~2026-04-08: Download free space check (StatFs before download)~~
- [x] ~~2026-04-08: Task detection keywords fix (check, compose, find, screen, notification, read my)~~
- [x] ~~2026-04-08: Compound task routing fix (skip Tier 1 for "and"/"then"/"after")~~
- [x] ~~2026-04-08: M1-M12 QA: 10/12 PASS, 2 PARTIAL (M9 camera, M12 system dialog)~~
- [x] ~~2026-04-08: NLP Playbooks Layer 2: 5 playbooks (Search, Settings, Email, Screen, Notifications)~~
- [x] ~~2026-04-08: Tinder research: UI structure documented, workflow designed, needs login~~
- [x] ~~2026-04-10: Chat bubble timestamps~~ — IG-style per-message footer landed for user + assistant bubbles, with hidden timestamp metadata persisted in markdown history so relaunch/reload keeps stable times
- [x] ~~2026-04-28: Release publishing stable signing path~~ — `v0.6.9` tag workflow produced a signed release APK and `SHA256SUMS.txt` through GitHub Actions
