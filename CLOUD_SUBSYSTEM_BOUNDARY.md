# Cloud Subsystem Boundary (端云子系统边界)

Status: **quarantined / unverified** · Owner: **TBD** · Last audited: 2026-07-07

This document draws an explicit boundary around the on-device **cloud / edge-cloud
("端云") subsystem** that ships inside the PokeClaw APK. It exists because that
subsystem is large, is **not mentioned at all** in `ARCHITECTURE_RECONSTRUCTION.md`,
and has **never been verified against a real backend** — yet it wires into
`ClawApplication` startup. Until it has an owner and a QA gate, treat it as
quarantined: documented, gated off by default, and forbidden from affecting the
local-first product.

## 1. What it is (inventory)

On-device client for a device↔cloud task platform (register → heartbeat → pull
task → execute locally → report result), plus a generated "Lobster" cloud API
surface and vendor-billing UI shells.

| Package | Files | Role |
|---|---:|---|
| `cloud/` | 71 | Device cloud client, enrollment, HMAC auth, token store, heartbeat, orchestrator, offline queue, self-heal, WebSocket, `lobster/` API suite |
| `cloudnode/` | 4 | Executor-node contract + bridge |
| `server/` | 2 | On-device config/LAN server |
| `scheduler/` | 6 | Task scheduling |
| `role/` | 4 | Lobster role sync |
| `job/` | 1 | Background job glue |
| `memory/` | 2 | Cloud-backed memory |

Key classes: `CloudNodeOrchestrator`, `CloudHeartbeatManager` (30s heartbeat +
WorkManager), `RetrofitDeviceCloudClient`, `ClawNodeEnrollmentManager`,
`TokenManager` / `AndroidKeystoreCloudDeviceTokenStore` (AES-GCM in Keystore),
`CloudTaskExecutor` / `LocalAgentTaskExecutor`, `SelfHealManager`,
`CloudWebSocketClient`, `CloudEventQueue` (offline queue), `cloud/lobster/**`
(command/memory/personality/profile/skill-marketplace), `ui/settings/VendorBilling*`.

## 2. How it is triggered, and current gating

Entry point: `ClawApplication.initCloudNode()` (called on runtime bootstrap).

- `cloud_enabled` (KV setting) — **default `false`**. Gates the only network-facing
  actions: `heartbeatManager.startHeartbeat()` + `cloudOrchestrator.start()`.
  When false, `initCloudNode()` constructs the objects but calls
  `stopHeartbeat()` and never starts the orchestrator → **no enrollment, no
  heartbeat, no cloud network traffic**.
- `BuildConfig.CLOUD_WS_ENABLED` — **default `false`** (app/build.gradle.kts).
  Gates the WebSocket path.
- Debug-only: `DebugTaskReceiver` can start/stop heartbeat via `cloud_action`
  (disabled in release via `DEBUG_AUTOMATION_ENABLED=false`).

**Invariant "off by default" currently holds** at the network level: a fresh
install performs no cloud enrollment or heartbeat.

## 3. Known issues (do not let these rot)

1. **Hardcoded private dev endpoint.** `initCloudNode()` falls back to
   `http://192.168.250.3:8080` (the internal "dyq" backend from `.planning/`)
   when `cloud_base_url` is unset. A private LAN IP must not ship in a public
   build — gate it to debug or remove it.
2. **Eager construction when disabled.** The orchestrator, Retrofit client,
   token store, and offline queue are built on **every** startup even when
   `cloud_enabled=false`. Construct lazily, only when enabled.
3. **Default backend env = PRODUCTION** (`https://claw.agents.io`) in
   `ClawNodeEnrollmentManager`. If enabled without an explicit env it targets prod.
4. **Unverified.** No real-backend E2E has ever passed (see `CMP-137` reports:
   backend `192.168.250.3:8080` never came up; client is mock-only). Evidence
   level is at best E1 (unit/mock).
5. **Undocumented.** `ARCHITECTURE_RECONSTRUCTION.md` does not mention this
   subsystem — it has no phase, no ownership boundary, no QA mapping.
6. **Inert commercial shells.** `VendorBillingRegistry` seeds vendors with
   `creditCost = null`; there is no live pricing or payment path.

## 4. Boundary invariants (the contract)

Any change touching this subsystem MUST preserve all of these:

- **Off by default.** `cloud_enabled` defaults false; no enrollment, heartbeat,
  telemetry, or cloud network call happens without it being explicitly true.
- **Explicit consent.** Enabling cloud enrollment requires a deliberate,
  inspectable user action (a Settings opt-in), the same bar as External Automation.
- **Local-first independence.** The local-first product (chat, task, tools,
  accessibility, local model) MUST be fully functional with the entire cloud
  subsystem disabled or absent. No local path may hard-depend on `cloud/**`.
- **No private endpoints in shipping builds.** No hardcoded internal/dev IPs or
  hosts in release; endpoints come from config, defaulting to none (not prod).
- **Secrets in Keystore only.** Device tokens / HMAC keys stay in the Android
  Keystore (AES-GCM); never logged in cleartext (use the log sanitizer).
- **No cloud PASS without E3.** No cloud capability may be logged as `PASS`
  above E1 until a real backend E2E runs (see `QA_CHECKLIST.md` Evidence Levels).

## 5. Ownership & QA mapping

| Concern | Owner | QA status |
|---|---|---|
| Device cloud client / enrollment / heartbeat | **TBD** | real-backend E2E = **none** (mock-only) |
| Lobster API suite (marketplace/profile/…) | **TBD** | none |
| Vendor billing UI | **TBD** | inert (null pricing) |
| Config/LAN server (`server/`) | **TBD** | localhost-bind only (see v0.3.2 security fix) |

## 6. Recommended path (follow-up, tracked in BACKLOG)

1. Remove/relocate the hardcoded `192.168.250.3:8080` fallback; default to no
   endpoint. Consider a `CLOUD_NODE_ENABLED` BuildConfig (default false) ANDed
   with the `cloud_enabled` setting for a hard release-level off switch.
2. Lazily construct cloud objects only when enabled.
3. Either give this subsystem a real owner + reconstruction phase + real-backend
   QA gate, **or** extract it to a separate module/repo so the OSS app stays lean
   and honest.

Until then: **quarantined**. Build on the local-first harness, not on this.
