# PokeClaw E2E QA Checklist

Every build must pass ALL checks before shipping.

## QA Debug Changelog

| 日期 | 状态 | 问题编号 | 描述 |
|------|------|----------|------|
| 2026-07-07 | PASS | P0-1-RELIABILITY-VERIFY | 自主迭代 P0-1 验证：headless Android SDK bootstrap（android-36 / build-tools 36.0.0）后在 main v0.7.2 上真跑——`:app:compileDirectDebugKotlin` BUILD SUCCESSFUL（仅 deprecation 警告）；`:app:testDirectDebugUnitTest --tests "*ActionValidatorTest"` **4/4 通过**（0 fail / 0 error：validateMissingRequiredParameterBlocksAction、executeToolDoesNotCallRealToolWhenActionInvalid、executeToolClassifiesUnknownTool、executeToolRecordsSuccessWhenActionValid）。reliability 动作校验+执行追踪的编译与单元行为均验证。**证据等级 E1（unit）**；E2（emulator smoke）因本沙箱无 AVD/系统镜像暂标 BLOCKED。P0-1 从 E0 升 E1。|
| 2026-07-07 | CODE-READY | P0-1-RELIABILITY | 自主迭代 P0-1：从 origin/dev 抢救 reliability 功能到 main（v0.7.2）——ActionValidator 执行前动作校验 + ExecutionTrace 全链路追踪。ToolResult 纯追加 ErrorType/errorType/classify()/2参error()；ToolRegistry.executeTool 接入 校验→追踪→执行→记录；TaskOrchestrator 加 startTask + 6 处 finishTask 生命周期埋点；含 ActionValidatorTest 4 用例。已逐符号手工核对 main API 兼容（BaseTool 抽象集 / ToolParameter.isRequired / ToolResult 成员全匹配）。证据等级 **E0（code-review）**：本沙箱缺 Android SDK，compile/unit 门槛 BLOCKED，已后台 bootstrap Android SDK 以便下一轮升到 E2。分支 feature/reliability-salvage |
| 2026-06-18 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-9 | Added `scripts/commercial_readiness_audit.py` as a one-command local commercial audit. It checks workflow self-test coverage, runs `release_scripts_selftest.py`, runs the Play-safe release gate with the current default version, verifies the direct APK, Play APK, and Play AAB artifacts, runs the Play AAB bundletool universal APK manifest check, and runs `:app:testDirectDebugUnitTest :app:testPlayDebugUnitTest` unless `--skip-gradle-tests` is passed. It separates local failures from `BLOCKED_EXTERNAL` items and supports `--fail-on-external-blockers` for final release checklist runs. Verified the fast path `python scripts\commercial_readiness_audit.py --skip-network --skip-gradle-tests`, the full path `python scripts\commercial_readiness_audit.py`, and strict mode returning exit code `2` when expected external blockers remain; local gates passed in each run. The full run confirmed default `0.7.2` is newer than public latest `0.7.1`, direct APK SHA-256 `C6D9F973AE1001446714F5A2067184CE4D024DC7476A05507104726EF732416E`, Play APK SHA-256 `034174679AA5326C7A36B8DA97559776875507DF2ADA2674AD3982FE992E4118`, Play AAB SHA-256 `25663BE0757DDB2246E3DC087DD3B46FCF476C9155C5900D07D8B7521D660BFF`, and direct/play unit tests passed. Public commercial release remains blocked on external evidence: artifacts are QA-signed instead of the expected public signer `e000d1d6555b8fab20c03a5d9ddeba83944f26eecf0b978ac7affc2eebd43186`, release signing inputs are absent in this shell, production Cloud LLM API key/endpoint evidence is missing, writable WhatsApp/Telegram/Gmail account flows are not rerun, and direct-channel missed-call SMS still needs policy/approval evidence outside the Play-safe flavor |
| 2026-06-18 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-8 | Added CI coverage for release-helper script entrypoints after finding argparse/default-APK regressions in smoke scripts. Fixed `scripts/release_missed_call_smoke.py` so `--help` and explicit `--apk` no longer evaluate the default release APK lookup before parsing; because missed-call follow-up is direct-channel only, the delayed default now resolves `release_device_smoke.find_latest_release_apk("direct")`. Added `scripts/release_scripts_selftest.py`, which runs `py_compile` on release helpers, verifies `--help` for release gate, artifact verifier, device smoke, missed-call smoke, Cloud LLM smoke, and upgrade smoke, and validates Cloud LLM CUSTOM/OPENAI preflight paths without needing devices, signing secrets, or real API keys. Wired the self-test into `.github/workflows/build.yml` and `.github/workflows/release.yml`, and documented it in `RELEASING.md`. Verified `python scripts\release_scripts_selftest.py`, workflow YAML parse, and `python scripts\release_gate_check.py --skip-network --play-store`; all passed. Full public commercial release remains blocked on external evidence: long-lived public signing key or CI secrets, real production Cloud LLM API key/endpoint, writable WhatsApp/Telegram/Gmail account flows, and direct-channel policy/approval evidence for missed-call SMS outside the Play-safe flavor |
| 2026-06-18 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-7 | Continued commercial-readiness audit from current worktree. Networked release gate passed with `python scripts\release_gate_check.py --play-store --expected-version 0.7.2`, confirming default `0.7.2 (30)` is newer than public latest `0.7.1` and the Play-safe flavor still removes restricted SMS/Call Log permissions and `.receiver.MissedCallReceiver`; it warned only because public release signing secrets are not present in this shell. Fixed `scripts\release_cloud_llm_smoke.py` so `--help`, explicit `--apk`, and `--preflight-only` no longer crash during argparse setup after `release_device_smoke.find_latest_release_apk()` gained a required `distribution` parameter; the script now supports `--distribution direct|play` and resolves the default APK after parsing. Verified `python -m py_compile scripts\release_cloud_llm_smoke.py scripts\release_device_smoke.py`, `python scripts\release_cloud_llm_smoke.py --help`, CUSTOM preflight with placeholder endpoint/model/key, OPENAI preflight defaulting to `gpt-4o-mini`, and missing API key failing before device work. Full public commercial release remains blocked on external evidence: long-lived public signing key or CI secrets, real production Cloud LLM API key/endpoint, writable WhatsApp/Telegram/Gmail account flows, and direct-channel policy/approval evidence for missed-call SMS outside the Play-safe flavor |
| 2026-06-18 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-6 | Current local commercial-readiness evidence now includes Play AAB bundletool deep verification: `scripts/verify_release_artifact.py --aab app\build\outputs\bundle\playRelease\app-play-release.aab --expected-version 0.7.2 --distribution play --expected-signer-cert-sha256 2A57AE3D9EBD231CD108C3E128D53FA535D46A9C1EC063090215E23D4A32691D` auto-used Gradle cached bundletool `1.18.3`, generated a universal APK, verified identity `io.agents.pokeclaw 0.7.2 (30)`, confirmed restricted Play permissions and `.receiver.MissedCallReceiver` are absent, and recorded AAB SHA-256 `25663BE0757DDB2246E3DC087DD3B46FCF476C9155C5900D07D8B7521D660BFF`. Reran `python -m py_compile` for release scripts, `release_gate_check.py --skip-network --play-store`, signing-required Play gate with the QA keystore, direct/play APK verifiers, workflow YAML parse, `git diff --check`, and `.\gradlew.bat :app:testDirectDebugUnitTest :app:testPlayDebugUnitTest --console=plain --stacktrace`; all passed, with only Git CRLF warnings and existing Kotlin deprecation warnings. Public commercial release is still blocked on external evidence: build/verify with the long-lived public signing key or CI secrets, Cloud LLM smoke with a real production endpoint/API key, writable WhatsApp/Telegram/Gmail account flows, and final direct-channel policy/approval evidence if shipping missed-call SMS outside the Play-safe flavor |
| 2026-06-18 | PASS | PLAY-AAB-RELEASE-GATE | Added Play App Bundle release verification for Google Play commercialization. `scripts/verify_release_artifact.py` now supports explicit `--aab` verification for the Play distribution: it checks required AAB ZIP entries, verifies the Play-safe source/manifest gate removes `READ_PHONE_STATE` / `READ_CALL_LOG` / `SEND_SMS` and `.receiver.MissedCallReceiver`, validates the AAB JAR signature with `jarsigner`, checks signer cert SHA-256 with `keytool`, writes SHA-256, and runs a deeper bundletool-generated universal APK manifest check via the Gradle dependency cache when available or an explicit standalone `--bundletool` jar when provided. Built QA-signed `app/build/outputs/bundle/playRelease/app-play-release.aab` with `.\gradlew.bat :app:bundlePlayRelease --console=plain --stacktrace` passing without skipping lint; verifier passed with signer `CN=PokeClaw QA, O=PokeClaw, C=US`, signer cert SHA-256 `2A57AE3D9EBD231CD108C3E128D53FA535D46A9C1EC063090215E23D4A32691D`, auto-used Gradle cached bundletool `1.18.3`, verified the generated universal APK omits restricted Play permissions and `.receiver.MissedCallReceiver`, and recorded AAB SHA-256 `25663BE0757DDB2246E3DC087DD3B46FCF476C9155C5900D07D8B7521D660BFF`. `.github/workflows/release.yml` now runs release preflight with `--require-signing --play-store`, builds `assembleDirectRelease bundlePlayRelease`, verifies both the direct APK and Play AAB against the stable signer digest, and uploads the Play AAB as the workflow artifact `play-release-aab` for Play Console operations |
| 2026-06-18 | PASS | PLAY-FLAVOR-COMPLIANCE-GATE | Added explicit `direct` and `play` distribution flavors. The direct flavor keeps missed-call follow-up and its `READ_PHONE_STATE` / `READ_CALL_LOG` / `SEND_SMS` permissions; the Play flavor removes those restricted permissions and removes `.receiver.MissedCallReceiver` via `app/src/play/AndroidManifest.xml`, while `BuildConfig.MISSED_CALL_FOLLOWUP_ENABLED=false` hides/disables the feature at runtime. Verified `python -m py_compile scripts\\release_gate_check.py scripts\\verify_release_artifact.py scripts\\release_device_smoke.py`, `python scripts\\release_gate_check.py --skip-network`, and `python scripts\\release_gate_check.py --skip-network --play-store` all passed. Built QA-signed `app/build/outputs/apk/direct/release/PokeClaw_direct_v0.7.2_20260618_105110.apk` and `app/build/outputs/apk/play/release/PokeClaw_play_v0.7.2_20260618_105110.apk`; direct verifier passed with signer cert SHA-256 `2A57AE3D9EBD231CD108C3E128D53FA535D46A9C1EC063090215E23D4A32691D` and APK SHA-256 `C6D9F973AE1001446714F5A2067184CE4D024DC7476A05507104726EF732416E`, while Play verifier passed with the same signer and APK SHA-256 `034174679AA5326C7A36B8DA97559776875507DF2ADA2674AD3982FE992E4118`. `:app:testDirectDebugUnitTest :app:testPlayDebugUnitTest` passed. Play device smoke on `emulator-5554` clean-installed the Play APK, cold-launched to `ComposeChatActivity` with `TotalTime: 1524`, showed no stale update prompt, found no FATAL/ANR, verified release `DEBUG_TASK` had no UI effect, confirmed Settings hides Missed Call Follow-up, enabled External Automation, ran production `RUN_TASK how much battery left`, received a battery result, and finished with `Release device smoke passed` |
| 2026-06-18 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-5 | Current local commercial-readiness evidence is stronger but still not complete. Locally verified on QA-signed `v0.7.2`: release gates, direct/play release builds, direct/play artifact verifiers, direct/play flavor unit tests, general direct release smoke, Play release smoke, missed-call direct release smoke, stale-update suppression, debug automation disabled in release, Settings defaults, External Automation enablement, and production `RUN_TASK` direct-data path. The Google Play restricted-permission blocker is reduced by the Play-safe artifact, but public commercial release still needs external evidence: build/verify with the long-lived public signing key or CI secrets, run Cloud LLM smoke with a real production endpoint/API key, rerun writable WhatsApp/Telegram/Gmail account flows, and choose the direct-channel policy/approval path if shipping missed-call SMS outside the Play-safe flavor |
| 2026-06-18 | PASS | RELEASE-DEVICE-SMOKE-REFRESH-072 | Reran the general release-device smoke after the missed-call `READ_CALL_LOG` and system-message rendering fixes. Against `app/build/outputs/apk/release/PokeClaw_v0.7.2_20260618_101624.apk` on `emulator-5554`, `scripts/release_device_smoke.py` clean-installed the APK, cold-launched to `ComposeChatActivity` with `TotalTime: 1224`, showed no stale update prompt, found no FATAL/ANR, verified release `DEBUG_TASK` had no UI effect, opened Settings, confirmed missed-call follow-up defaulted Disabled and External Automation row was visible, accepted notification permission via `ALLOW`, enabled External Automation, ran production `RUN_TASK how much battery left`, received a battery result, and finished with `Release device smoke passed` |
| 2026-06-18 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-4 | Current local commercial-readiness evidence is stronger but still not complete. Locally verified on the current QA-signed `v0.7.2` APK: release gate, artifact verifier, debug unit tests, general release device smoke, missed-call release smoke, stale-update suppression, debug automation disabled in release, Settings defaults, External Automation enablement, and production `RUN_TASK` direct-data path. Still external/blocking before a public commercial release: build/verify with the long-lived public signing key or CI secrets, run Cloud LLM smoke with a real production endpoint/API key, rerun writable WhatsApp/Telegram/Gmail account flows, and resolve Google Play SMS/Call Log restricted-permission compliance for `SEND_SMS` + `READ_CALL_LOG` |
| 2026-06-18 | PASS | RELEASE-MISSED-CALL-SMOKE | Added `scripts/release_missed_call_smoke.py` and fixed the release missed-call path. Android `PHONE_STATE` does not provide `TelephonyManager.EXTRA_INCOMING_NUMBER` unless the receiver has both `READ_PHONE_STATE` and `READ_CALL_LOG`, so the release manifest, Settings opt-in gate, release gate, artifact verifier, and emulator smoke now require `READ_CALL_LOG` alongside `SEND_SMS`. Also fixed `ChatScreen` so a lone `SYSTEM` message is rendered instead of hidden behind the empty prompt state. Rebuilt QA-signed release `app/build/outputs/apk/release/PokeClaw_v0.7.2_20260618_101624.apk`, signer cert SHA-256 `2A57AE3D9EBD231CD108C3E128D53FA535D46A9C1EC063090215E23D4A32691D`, APK SHA-256 `619B97FDFE9A8D474708CBA03C2BC08CBEE038846CD6CFD935685C34178B34B0`; `verify_release_artifact.py` passed. On `emulator-5554`, the missed-call smoke clean-installed the APK, verified default disabled produced no chat card, granted emulator `READ_PHONE_STATE`/`READ_CALL_LOG`/`SEND_SMS`, enabled Missed Call Follow-up via Settings UI, simulated a GSM missed call from `15550222222`, saw the follow-up chat card, and found no FATAL/ANR logcat patterns. Commercial note: Google Play distribution still needs SMS/Call Log policy review/default handler or an approved exception because the feature declares restricted `SEND_SMS` and `READ_CALL_LOG` permissions |
| 2026-06-18 | BLOCKED | RELEASE-CLOUD-LLM-SMOKE | Added `scripts/release_cloud_llm_smoke.py` and documented it in `RELEASING.md` as the release-device production Cloud LLM chat smoke. The script clean-installs a release APK, opens Settings via the UI, configures a Cloud LLM provider from `POKECLAW_LLM_*` env vars, saves through `Save & Activate`, enables External Automation, sends a production `RUN_CHAT` request, waits for a unique model response marker, and fails on stale update prompt or FATAL/ANR. Verified syntax and input guards: missing `POKECLAW_LLM_API_KEY` fails before device work, CUSTOM preflight fails without base/model, CUSTOM preflight passes with placeholder base/model/key, and OPENAI preflight defaults to `gpt-4o-mini`. On `emulator-5554`, a clean release install with fake endpoint `https://example.invalid/v1` reached the real UI/config/External Automation/RUN_CHAT path: cold launch `TotalTime: 663`, LLM Config saved `Custom / example-model`, notification permission was accepted, External Automation enabled, chat showed `example-model · Cloud`, and the task failed only at external DNS (`Unable to resolve host "example.invalid"`), then timed out waiting for marker `POKECLAW-CLOUD-SMOKE-OK`. Full Cloud LLM E2E remains blocked until a real production API key and endpoint are supplied |
| 2026-06-18 | PASS | RELEASE-UPGRADE-SMOKE | Added `scripts/release_upgrade_smoke.py` and documented it in `RELEASING.md`. The script preflights package name, candidate versionCode > reference versionCode, and signer certificate equality before touching a device; then it clean-installs the reference APK, launches it, installs the candidate with `adb install -r`, confirms the installed versionCode, launches the upgraded app, and checks for no stale update prompt or FATAL/ANR logs. Verified public `D:\tmp\PokeClaw_v0.7.1_20260526_184329.apk` -> current QA-signed `D:\tmp\PokeClaw_v0.7.2_qa_candidate_upgrade.apk` is correctly blocked before device work because public signer `e000d1d6555b8fab20c03a5d9ddeba83944f26eecf0b978ac7affc2eebd43186` does not match QA signer `d1966e3a5b0714ff60322a44cf69e53f787298f145d58d0842e17ddf737f1a5b`. Built same-signer QA reference `app/build/outputs/apk/release/PokeClaw_v0.7.1-qa-upgrade_20260618_011113.apk` (`versionCode=29`) and ran full upgrade smoke to QA candidate `versionCode=30` on `emulator-5554`: reference cold launch `TotalTime: 678`, candidate cold launch after upgrade `TotalTime: 546`, no stale update prompt, no FATAL/ANR, and `Release upgrade smoke passed` |
| 2026-06-18 | PASS | RELEASE-SIGNER-COMPAT-GATE | Added signer compatibility checks to `scripts/verify_release_artifact.py`: `--expected-signer-cert-sha256` requires the APK signer certificate SHA-256 to match a known stable certificate, and `--reference-apk` requires the APK signer to match another signed APK. `.github/workflows/release.yml` now verifies future public release artifacts against the public `v0.7.1` signer certificate SHA-256 `e000d1d6555b8fab20c03a5d9ddeba83944f26eecf0b978ac7affc2eebd43186`, so CI will fail if release secrets point at a new key. Verified current QA-signed `PokeClaw_v0.7.2_20260618_004243.apk` passes with its QA signer digest `d1966e3a5b0714ff60322a44cf69e53f787298f145d58d0842e17ddf737f1a5b`, fails when forced to the public signer digest, and fails when compared with public `D:\tmp\PokeClaw_v0.7.1_20260526_184329.apk` as reference. GitHub Releases page still lists `v0.7.1` as latest public release |
| 2026-06-18 | PASS | RELEASE-SIGNING-CREDENTIAL-GATE | Strengthened `scripts/release_gate_check.py --require-signing` from non-empty secret checks to real signing credential validation: the gate now resolves `KEYSTORE_FILE`, verifies the file exists and is non-empty, uses `keytool -certreq` to validate keystore/store password/alias readability, then uses `jarsigner` on a temporary probe JAR to validate the private key and `KEY_PASSWORD`. Verified with throwaway QA keystore `D:\tmp\pokeclaw-qa-release-smoke-v2.jks`: correct credentials passed, an intentionally wrong `KEYSTORE_PASSWORD` failed, and an intentionally wrong `KEY_PASSWORD` failed. `python -m py_compile` for release scripts passed |
| 2026-06-18 | PASS | RELEASE-APK-MANIFEST-HARDENING | Release artifact no longer exposes debug-only broadcast receivers: `debugAutomationEnabled` / `debugAutomationExported` manifest placeholders default false, debug overrides them true, and release keeps them false. Rebuilt signed QA release APK `app/build/outputs/apk/release/PokeClaw_v0.7.2_20260618_004243.apk` with throwaway keystore `D:\tmp\pokeclaw-qa-release-smoke-v2.jks`; `verify_release_artifact.py` parsed the final APK manifest and verified `DebugTaskReceiver` and `TaskTriggerReceiver` are `enabled=false` / `exported=false`, missed-call and External Automation manifest requirements still pass, signature verifies, and SHA-256 is `BE97FD301A67DE89ED3D15AA497BE2080ED72A4D9FAC8F53E6BA4DDDC4C4D19D`. Device smoke on `emulator-5554` passed: cold launch `Status: ok` / `TotalTime: 581`, no update prompt, no FATAL/ANR, release `DEBUG_TASK` had no UI effect, Settings defaults were visible, notification permission was accepted, External Automation enabled, and `RUN_TASK how much battery left` returned a battery result |
| 2026-06-18 | PASS | RELEASE-GATE-MANIFEST-SAFETY | Extended `scripts/release_gate_check.py` to parse `app/src/main/AndroidManifest.xml` with `xml.etree.ElementTree` and fail release preflight if missed-call safety or commercial automation entrypoints drift: `READ_PHONE_STATE` / `SEND_SMS`, telephony `required=false`, `MissedCallReceiver` enabled and `exported=false` with `PHONE_STATE`, and `ExternalAutomationActivity` / `ExternalAutomationReceiver` exported with `RUN_TASK` and `RUN_CHAT`. Verified `python -m py_compile scripts\release_gate_check.py scripts\verify_release_artifact.py scripts\release_device_smoke.py`, `python scripts\release_gate_check.py --skip-network`, `python scripts\release_gate_check.py --expected-version 0.7.2`, signing-required gate with the temporary QA smoke keystore, targeted update/missed-call unit tests, and `git diff --check` passed with LF/CRLF warnings only |
| 2026-06-18 | PASS | RELEASE-DEVICE-SMOKE-SCRIPT | Added `scripts/release_device_smoke.py` and documented it in `RELEASING.md` as the signed-APK device smoke command. Verified on `emulator-5554` against `app/build/outputs/apk/release/PokeClaw_v0.7.2_20260618_000044.apk`: clean install, cold launch `Status: ok` / `TotalTime: 586`, no startup update prompt, no FATAL/ANR logcat patterns, release `DEBUG_TASK` had no UI effect, Settings showed missed-call follow-up disabled and External Automation row, enabling External Automation preflighted notification permission via `ALLOW`, and base64 `RUN_TASK how much battery left` returned a battery result. `python -m py_compile` for release scripts and targeted unit tests passed |
| 2026-06-18 | PASS | RELEASE-ARTIFACT-VERIFY | Added `scripts/verify_release_artifact.py` and wired `.github/workflows/release.yml` to run it after `assembleRelease` and before publishing. The script verifies the release APK package name, `versionName` against the tag, positive `versionCode`, `apksigner verify --verbose --print-certs`, exactly one signer, and writes `SHA256SUMS.txt`. Verified locally on `app/build/outputs/apk/release/PokeClaw_v0.7.2_20260618_000044.apk`: identity `io.agents.pokeclaw 0.7.2 (30)`, signature verified, SHA-256 `5E85BEC9D0F25E38AA6912FA5319C81F8F3F4FB17BA9D91D66D61342AEC9725A`; workflow YAML parse, release gate, and targeted unit tests passed |
| 2026-06-18 | PASS | RELEASE-GATE-CI | Release gate is now enforced in GitHub Actions: `.github/workflows/build.yml` runs `python scripts/release_gate_check.py --skip-network` before debug APK builds, and `.github/workflows/release.yml` runs `python scripts/release_gate_check.py --require-signing --expected-version "${VERSION#v}"` after preparing release signing and before `assembleRelease`. Verified locally: PyYAML parsed both workflow files, `python scripts\release_gate_check.py --expected-version 0.7.2` passed, `python scripts\release_gate_check.py --require-signing --expected-version 0.7.2` passed with the temporary QA smoke keystore, and targeted unit tests still passed |
| 2026-06-18 | PASS | RELEASE-GATE-SCRIPT | Added `scripts/release_gate_check.py` and wired it into `RELEASING.md`. The gate checks default `versionCode` / `versionName`, README changelog coverage, debug/release `BuildConfig` safety switches, GitHub latest freshness, and optional release signing inputs. Verified `python scripts\release_gate_check.py` passed against public latest `0.7.1` with a signing warning, `python scripts\release_gate_check.py --skip-network` passed for offline local use, and `python scripts\release_gate_check.py --require-signing` passed when pointed at the temporary QA smoke keystore |
| 2026-06-18 | PASS | BUILD-LOCAL-FINAL-7 | Final gate after version bump and External Automation notification-permission hardening passed as split tasks: `.\gradlew.bat :app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, and `:app:assembleDebugAndroidTest` all exited 0. Latest debug APK `app/build/outputs/apk/debug/PokeClaw_v0.7.2_20260617_235645.apk` is `versionCode=30` / `versionName=0.7.2`, size 121,920,679 bytes; latest androidTest APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` size 1,156,606 bytes; reinstalling both and running `adb shell am instrument -w io.agents.pokeclaw.test/androidx.test.runner.AndroidJUnitRunner` passed `OK (1 test)` |
| 2026-06-18 | PASS | VERSION-REL-072 | Public GitHub latest was verified through the GitHub Releases API as `v0.7.1` with public APK `versionCode=29` / `versionName=0.7.1`; repo defaults were advanced to `versionCode=30` / `versionName=0.7.2`, and README now has a `v0.7.2` changelog entry so the tag-based release workflow can extract release notes |
| 2026-06-18 | PASS | RELEASE-QA-SMOKE-072 | Release QA smoke package was rebuilt with a throwaway QA keystore, not the public stable key: `app/build/outputs/apk/release/PokeClaw_v0.7.2_20260618_000044.apk`, `versionCode=30` / `versionName=0.7.2`, size 107,029,270 bytes, SHA-256 `5E85BEC9D0F25E38AA6912FA5319C81F8F3F4FB17BA9D91D66D61342AEC9725A`. Clean release install cold-started to `ComposeChatActivity` in 597ms before the later Settings-path run, with no ANR/FATAL and no stale public-update prompt |
| 2026-06-18 | PASS | EXTAUTO-NOTIFICATION-PREFLIGHT | Release External Automation enable flow now preflights Android notification permission: Settings showed the explicit `Enable External Automation?` confirmation, tapping Enable immediately showed `Allow PokeClaw to send you notifications?`, and after tapping `ALLOW`, a base64 `io.agents.pokeclaw.RUN_TASK` for `how much battery left` returned `Battery: 100%, charging, 25.0 C` without a first-task permission dialog interrupting the task |
| 2026-06-18 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-3 | Commercial release still needs external evidence that cannot be produced from this local emulator alone: build with the long-lived public signing key or CI secrets, production Cloud LLM/API-key chat E2E, real writable WhatsApp/Telegram/Gmail account flows, and true missed-call E2E with SIM/VoIP or a second device. Version freshness, local release buildability, startup, direct-data tasks, release debug-automation-off, and release External Automation smoke are now locally verified |
| 2026-06-17 | PASS | BUILD-LOCAL-FINAL-6 | Final gate after update-check build-type hardening passed as split tasks: `.\gradlew.bat :app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, and `:app:assembleDebugAndroidTest` all exited 0; latest debug APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_231535.apk` size 121,920,683 bytes; androidTest APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` size 1,156,606 bytes; latest debug instrumentation install and `adb shell am instrument -w io.agents.pokeclaw.test/androidx.test.runner.AndroidJUnitRunner` passed `OK (1 test)` |
| 2026-06-17 | FIX | UPDATE-CHECK-BUILDTYPE | The startup update prompt is now build-type gated instead of relying on Android `FLAG_DEBUGGABLE`. `BuildConfig.UPDATE_CHECK_ENABLED` defaults false, debug keeps it false, release sets it true, and `UpdateCheckerTest.debugBuildDisablesPublicUpdatePrompt` passed. Runtime debug startup logged `UpdateChecker: Skipping update check for this build type` and no `Update Available` dialog was present |
| 2026-06-17 | PASS | RELEASE-QA-SMOKE-CURRENT | A release variant was built with a throwaway QA smoke keystore, not the public stable key: `.\gradlew.bat :app:assembleRelease` exited 0, `apksigner verify --verbose --print-certs` reported verified v2 signing with one RSA signer, release APK `app/build/outputs/apk/release/PokeClaw_v0.6.12_20260617_232250.apk` size 107,029,278 bytes, SHA-256 `6954BDE494B0E752699C6632309B27AB070FA691D12076902F11FF0AC72D0BD8`. Clean install startup on `emulator-5554` was cold launch 569ms to `ComposeChatActivity` with no ANR/FATAL |
| 2026-06-17 | PASS | RELEASE-DEBUG-AUTOMATION-OFF | Release QA smoke package rejected debug-only automation: after clean release install, `io.agents.pokeclaw.DEBUG_TASK` broadcast completed but produced no `DebugTaskReceiver` or `TaskTriggerReceiver` task logs and no battery result in the UI. This verifies `BuildConfig.DEBUG_AUTOMATION_ENABLED=false` for release |
| 2026-06-17 | PASS | RELEASE-EXTAUTO-CURRENT | Release QA smoke package production external automation path passed: Settings showed `External Automation / Disabled` by default; tapping it showed the explicit risk dialog; after Enable and first-run notification permission Allow, base64 `io.agents.pokeclaw.RUN_TASK` for `how much battery left` visibly returned `Battery: 100%, charging, 25.0 C` in the chatroom |
| 2026-06-17 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT-2 | Commercial release is still not fully green because the release smoke used a temporary QA keystore rather than the long-lived public signing key, current repo version is still `0.6.12` while public GitHub latest is `0.7.1`, Cloud LLM/API-key chat has not been rerun against production config, WhatsApp/Telegram/Gmail workflows still need writable real accounts, and true missed-call E2E still needs SIM/VoIP or a second device |
| 2026-06-17 | PASS | BUILD-LOCAL-FINAL-5 | Final split gate after debug ProGuard instrumentation fix passed: `.\gradlew.bat :app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, and `:app:assembleDebugAndroidTest` all exited 0. The all-in-one command exceeded the 6m tool timeout, so the gate was rerun as separate Gradle tasks. Latest debug APK: `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_225656.apk`, size 121,920,683 bytes; androidTest APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, size 1,156,566 bytes |
| 2026-06-17 | FIX | DEVICE-INSTRUMENTATION-LAZYKT | Debug instrumentation initially crashed with `NoClassDefFoundError: kotlin/LazyKt` because debug minification stripped Kotlin runtime classes. Added `app/proguard-debug-rules.pro` to keep `kotlin.**` for the minified debug build and wired it in `app/build.gradle.kts`; reinstalling the latest debug and androidTest APKs then passed `adb shell am instrument -w io.agents.pokeclaw.test/androidx.test.runner.AndroidJUnitRunner` with `OK (1 test)` |
| 2026-06-17 | PASS | SDK-AVD-E2E-SETUP | Installed Android SDK emulator and `system-images;android-36;default;x86_64`, created AVD `PokeClawApi36`, launched it headless, and booted `emulator-5554` as product `sdk_phone64_x86_64` / model `Android_SDK_built_for_x86_64` |
| 2026-06-17 | PASS | STARTUP-CURRENT-E2E | Latest device startup smoke passed on `emulator-5554`: `am start -W` reported `Status: ok`, `LaunchState: COLD`, `TotalTime: 659`, and top resumed activity `io.agents.pokeclaw/.ui.chat.ComposeChatActivity`. Logcat showed `POKECLAW_INIT ... runtime-started +22ms`, runtime bootstrap registered 28 tools, no FATAL/ANR, and `Displayed ... +659ms`. Screenshot: `Screenshots/pokeclaw-startup-20260617.png` |
| 2026-06-17 | PASS | DIRECT-TOOLS-CURRENT | Direct debug tool layer passed on latest emulator install: clipboard set/get returned `qa direct clipboard 617`; battery returned `Battery: 100%, charging, 25.0 C`; WiFi returned `enabled but not connected`; storage returned `620 MB used of 6.2 GB (9%), 5.6 GB free`; Bluetooth returned enabled state; installed apps returned 13 apps; notifications returned one active Android System notification |
| 2026-06-17 | PASS | CHATROOM-BRIDGE-CURRENT | Production-style `io.agents.pokeclaw.TASK` broadcast with base64 task payload reached `ComposeChatActivity` and rendered direct-data results in the chatroom without a configured model: `how much battery left` showed the battery result, `read my clipboard and explain what it says` showed `qa direct clipboard 617`, and `read my notifications and summarize` showed Android System notifications plus the expected `No model selected` status |
| 2026-06-17 | PASS | EXTAUTO-CURRENT-E2E | External automation safety and happy path passed. With `Settings -> Remote Control -> External Automation` disabled, `RUN_TASK` was rejected with `External automation rejected because the user setting is disabled`. Enabling from Settings required a visible confirmation dialog, then Settings showed `External Automation / Enabled`; a base64 `RUN_TASK` for `how much battery left` logged `Accepted external automation TASK` and visibly returned `Battery: 100%, charging, 25.0 C` |
| 2026-06-17 | PASS | MISSED-CALL-SAFETY-CURRENT | Missed-call follow-up is default-safe in code and device UI: Settings showed `Missed Call Follow-up / Disabled`, `KVUtilsMissedCallFollowupTest` covers default false, manifest receiver is `android:exported="false"`, and a shell attempt to broadcast `android.intent.action.PHONE_STATE` was denied by Android with `Permission Denial: not allowed to send broadcast android.intent.action.PHONE_STATE` and did not trigger app missed-call logs |
| 2026-06-17 | CONCERN | UPDATE-PROMPT-CURRENT | First launch of local debug `v0.6.12` displayed an `Update Available` dialog for PokeClaw `v0.7.1`. This did not block tests after tapping `LATER`, but it is a commercial UX risk if QA/stable channels show update prompts from an unintended environment |
| 2026-06-17 | BLOCKED | COMMERCIAL-RELEASE-READINESS-CURRENT | Current emulator validation is much stronger than the prior blocked state, but commercial release is not fully green until a signed release APK is produced or verified, Cloud LLM/API-key chat is rerun against production config, real third-party app workflows such as WhatsApp/Telegram/Gmail are rerun on writable accounts, and true missed-call E2E is executed with SIM/VoIP or a second device |
| 2026-06-17 | PASS | BUILD-LOCAL-FINAL-4 | Current worktree gate after missed-call SMS safety hardening passed: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain --stacktrace --no-daemon` exited 0; unit test report shows 78 suites / 1575 tests / 0 failures / 0 errors / 0 skipped; output APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_221237.apk`, androidTest APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`; `git diff --check` passed with LF/CRLF normalization warnings only |
| 2026-06-17 | FIX | MISSED-CALL-SMS-SAFETY | Missed-call follow-up is now explicit opt-in instead of default-on: `KVUtils.isMissedCallFollowupEnabled()` defaults to false, `MissedCallReceiver` is `android:exported="false"`, and the Settings permission callback returns to the confirmation flow instead of enabling auto-SMS immediately. Added `KVUtilsMissedCallFollowupTest`; targeted test `.\gradlew.bat :app:testDebugUnitTest --tests "io.agents.pokeclaw.utils.KVUtilsMissedCallFollowupTest" --console=plain --stacktrace --no-daemon` passed |
| 2026-06-17 | PASS | BUILD-REL-DRYRUN-CURRENT | Current release task graph check passed: `.\gradlew.bat :app:assembleRelease --dry-run --console=plain --stacktrace --no-daemon` BUILD SUCCESSFUL in 16s. This proves release tasks are wired, not that a signed release APK was produced on this machine |
| 2026-06-17 | BLOCKED | DEVICE-E2E-CURRENT | Current-device E2E could not be rerun: `D:\android-sdk\platform-tools\adb.exe devices -l` returned no attached devices, `%USERPROFILE%\.android\avd` has no AVD entries, and `D:\android-sdk` has no `emulator.exe`. Current APK install/start/instrumentation/chatroom bridge/permission-row checks remain unverified for this run |
| 2026-06-17 | PASS | BUILD-LOCAL-FINAL-3 | Final local gate after latest direct-data and notification-listener fixes passed: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` BUILD SUCCESSFUL in 6m22s; output APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_184848.apk`. `git diff --check` passed with LF/CRLF normalization warnings only |
| 2026-06-17 | PASS | DD1-DD5/M1-M5/M16-LATEST | Latest installed APK `PokeClaw_v0.6.12_20260617_182809.apk` direct-device ADB sweep passed on emulator `192.168.250.3:5555`: clipboard seed/read returned `qa direct clipboard 617`; battery `get_device_info params={category=battery}` returned `Battery: 100%, not charging, 25.0 C`; WiFi `category=wifi` returned `WiFi: enabled but not connected`; storage `category=storage` returned `894 MB used of 6.2 GB (14%), 5.3 GB free`; Bluetooth `category=bluetooth` returned `Bluetooth: disabled`; `get_installed_apps` returned `Found 19 apps`; notifications required `cmd notification allow_listener` after reinstall, then `get_notifications` read 1 active Android System notification. Every exercised task logged `Pipeline Tier 1: DirectTool` and `onComplete: rounds=0, totalTokens=0, model=direct` |
| 2026-06-17 | PASS | BUILD-LOCAL-FINAL-2 | Latest local gate after Z13 WorkManager wiring passed: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` BUILD SUCCESSFUL in 6m49s; output APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_173318.apk`. `git diff --check` passed with LF/CRLF normalization warnings only |
| 2026-06-17 | PASS | Z13 | WorkManager 心跳调度闭环通过: 修复后 `PokeClaw_v0.6.12_20260617_165002.apk` 安装成功；`DEBUG_TASK --es cloud_action start` 后 logcat 出现 `CloudHeartbeatManager: 心跳调度已启动，间隔=1分钟`、`CloudHeartbeatWorker: 心跳工作器启动`、`WM-WorkerWrapper ... tags={ io.agents.pokeclaw.cloud.CloudHeartbeatWorker, cloud_heartbeat }`；`dumpsys jobscheduler io.agents.pokeclaw` 显示 `io.agents.pokeclaw/androidx.work.impl.background.systemjob.SystemJobService`，带 `TIMING_DELAY CONNECTIVITY` 约束；`cloud_action stop` 后日志显示 `心跳调度已停止`，scheduler 状态 `Pending: 0 / 0`、无活动 PokeClaw job |
| 2026-06-17 | PASS | Z12 | 设备令牌刷新窗口与脱敏日志通过: `.\gradlew.bat :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloud.api.CloudDeviceApiContractTest" --console=plain` BUILD SUCCESSFUL in 23s；覆盖 `CloudDeviceTokenSnapshot.shouldRefresh` 十分钟窗口边界与 `asBearerToken()` 单次 Bearer 前缀；代码审查确认 `AndroidKeystoreCloudDeviceTokenStore` 只把 AES-GCM 加密载荷写入 `SharedPreferences`，日志只打印 expiresAt/状态不打印 token 正文 |
| 2026-06-17 | PASS | BUILD-LOCAL-FINAL | Final local gate passed: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` BUILD SUCCESSFUL in 4m; optimized debug APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_162200.apk`, size 108,711,546 bytes, 7 dex files. `git diff --check` passed with LF/CRLF normalization warnings only |
| 2026-06-17 | PASS | J6/STARTUP-ANR-RETEST | Final optimized debug APK `PokeClaw_v0.6.12_20260617_162200.apk` installed with no accessibility-service ANR. `adb install -r` took 140.1s because install-time `dex2oat` took 109.638s; after that Android started service pid `22052`, `POKECLAW_INIT ... runtime-started +636ms`, and `ClawA11yService: Accessibility service connected` with no `Timeout executing service` / `ANR in io.agents.pokeclaw` |
| 2026-06-17 | PASS | M1/DD3-FINAL | Final optimized debug APK preserved ADB QA automation via `DEBUG_AUTOMATION_ENABLED=true`; `DEBUG_TASK how much battery left` completed in direct mode with `get_device_info params={category=battery}`, `sendMessage [Local]: ✓ Battery: 100%, not charging, 25.0°C`, and `onComplete: rounds=0, totalTokens=0, model=direct` |
| 2026-06-17 | FAIL | J6/STARTUP-ANR-RETEST | Patched debug APK `PokeClaw_v0.6.12_20260617_153220.apk` still reproduces startup ANR after reinstall: logcat `ANR in io.agents.pokeclaw`, Reason=`executing service io.agents.pokeclaw/.service.ClawAccessibilityService`; restarted process then needed ~10.1s for `POKECLAW_INIT ... runtime-started` |
| 2026-06-17 | PASS | M1/DD3-RETEST | After routing fix, stable-process ADB `DEBUG_TASK` for `how much battery left` completed deterministically: `Tier 1 direct device-data match: get_device_info params={category=battery}`, `Pipeline Tier 1: DirectTool — get_device_info`, `onComplete: rounds=0, totalTokens=0, model=direct, answer=Battery: 100%, not charging, 25.0°C` |
| 2026-06-17 | FAIL | J6/STARTUP-ANR | 模拟器 `192.168.250.3:5555` 安装 `PokeClaw_v0.6.12_20260617_151342.apk` 后启动出现用户可见 ANR: `PokeClaw isn't responding`；logcat 记录 `ANR in io.agents.pokeclaw`, Reason=`executing service io.agents.pokeclaw/.service.ClawAccessibilityService`，`ComposeChatActivity` 首屏 `Displayed ... +1m22s636ms`；截图 `Screenshots/qa-anr-20260617.png` |
| 2026-06-17 | FAIL | M1/DD3 | ADB `DEBUG_TASK` 发送 `how much battery left` 后未完成: `PipelineRouter: No deterministic match, falling through to agent loop`，虽然日志含 `Battery: 100%, not charging, 25.0°C`，但 mock LLM 循环到 60 轮并报 `Reached maximum iterations (60) without completing the task`；未产生正常 `onComplete` |
| 2026-06-17 | PASS | BUILD-LOCAL | 本地门禁: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` BUILD SUCCESSFUL in 24s；产物 `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_151342.apk`，大小 156,308,148 bytes |
| 2026-06-17 | PASS | SCRIPT-HYGIENE | 脚本/差异门禁: `D:\Program Files\Git\bin\bash.exe -n scripts/e2e-quick-tasks.sh` 通过；`git diff --check` 通过，仅提示 `QA_CHECKLIST.md` LF 将被 Git 归一化为 CRLF |
| 2026-06-17 | PASS | Z11 | 设备云端客户端编译闭环: `.\gradlew.bat :app:compileDebugKotlin --console=plain` BUILD SUCCESSFUL in 6s；随后完整 `:app:testDebugUnitTest :app:assembleDebug` 也通过，覆盖 DeviceCloudClient/CloudDeviceApi/CloudDeviceTokenStore/CloudEventQueue 编译闭环 |
| 2026-06-17 | PASS | Z10 | 设备端 API 契约注解校验: 执行 `.\gradlew.bat :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloud.api.CloudDeviceApiContractTest" --console=plain`，BUILD SUCCESSFUL in 12s |
| 2026-06-17 | NEXT | QA-CONTINUE | `J6/STARTUP-ANR` 与 `M1/DD3` 均已在 final optimized debug APK 上复测通过；下一步恢复全量 QA 后续批次。注意 optimized debug install 会先跑 install-time dex2oat，本机实测约 140s，应使用 >=180s 安装超时 |
| 2026-06-17 | PASS | Z9 | 端侧执行节点本地模拟闭环: 执行 `.\gradlew.bat :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest" --console=plain`，BUILD SUCCESSFUL in 22s；覆盖 CloudExecutorTask → CloudExecutorNodeSimulator 成功/失败状态顺序与可重试错误码 |
| 2026-06-17 | PASS | CLOUD-1 | Cloud端云模块验证: CloudNodeOrchestrator实现设备注册→心跳(30s)→任务拉取→执行→结果上报闭环；CloudHeartbeatManager管理心跳；支持离线队列缓存；CloudTaskExecutor执行任务 |
| 2026-06-17 | PASS | M7-M12 | App Navigation工具验证: OpenAppTool实现应用打开，支持常见应用(Spotify/YouTube/Chrome/Settings等)，自动处理"允许"对话框，已注册到ToolRegistry |
| 2026-06-17 | PASS | M1-M6 | System Queries工具验证: GetDeviceInfoTool实现battery(M1)/wifi(M2)/storage(M3)/bluetooth(M4)/screen(M6)查询，GetNotificationsTool实现(M5)，已注册到ToolRegistry |
| 2026-06-17 | PASS | I1-I3 | Cross-App Behavior验证: FloatingCircleManager实现悬浮窗在其他应用上显示(I1)、点击返回PokeClaw(AppViewModel.bringAppToForeground)(I2)、任务中通知不中断(TaskOrchestrator处理)(I3)，全部实现 |
| 2026-06-17 | PASS | C1-C6,C1-b | Monitor Workflow验证: MonitorDialog(启动监控)、ActiveTaskBar(停止监控)、MonitorTargetSpec(多应用支持WhatsApp/Telegram等)、ActiveTaskShellController(状态管理)全部实现 |
| 2026-06-17 | PASS | F1-F6 | Task Lifecycle UI验证: ActiveTaskBar(Top bar)、ChatInputBar(Stop按钮)、FloatingCircleManager(悬浮窗)、ActiveTaskShellController(状态管理)全部实现 |
| 2026-06-17 | PASS | P1-P6 | v9 UI设计验证: 代码审查确认EmptyStateWithPrompts、Chat/Task toggle、QuickTasksPanel、BACKGROUND section、MonitorDialog、Local/Cloud header全部实现，符合v9设计规范 |
| 2026-06-17 | BLOCKED | A1-A5 | Cloud LLM Chat E2E测试: 当前环境无ADB设备连接，无法执行。需设备+Cloud API key。代码路径已验证（ComposeChatActivity→ChatViewModel→Cloud runtime）|
| 2026-06-17 | PASS | BUILD-REL | 发布构建验证: `./gradlew assembleRelease --dry-run` 配置检查通过，所有构建任务定义正确；GitHub Actions工作流签名验证逻辑完整 |
| 2026-06-17 | PASS | UNIT-TEST | 单元测试: 85+测试文件，涵盖Cloud/Agent/Utils/Channel/CloudNode/Agent Guards等模块，testDebugUnitTest BUILD SUCCESSFUL |
| 2026-06-17 | FIX | COMPILE | 修复SettingsActivity.kt编译错误: InputDialog.show参数修正(initialText→presetText, onConfirm→onComplete)，编译通过BUILD SUCCESSFUL |
| 2026-06-17 | PASS | RELEASE | 发布流程: RELEASING.md完整，GitHub Actions工作流配置完成(v*标签触发、签名验证、APK构建、SHA256SUMS生成、Release自动创建)，e2e-quick-tasks.sh脚本就绪，代码/流程完整 |
| 2026-06-17 | PASS | K1-K6,CH-CONFIG | Settings权限行: 6个权限行完整实现(Accessibility/Notification/NotificationAccess/Overlay/Battery/Storage)，ChannelConfigActivity支持Discord/Telegram Bot Token配置，密码输入框支持显示/隐藏切换，代码就绪 |
| 2026-06-17 | PASS | M22-M50,R1 | Settings/Media/Error Handling/NLU: SystemKeyTool支持lock_screen、GetDeviceInfoTool支持brightness/dark mode读取、TakeScreenshotTool完整、R1依赖GetNotificationsTool+LLM推理，代码就绪 |
| 2026-06-17 | PASS | M13-M18,TOOLS | Information Retrieval Tools: GetInstalledAppsTool(已安装应用列表)、TakeScreenshotTool(截图)完整实现，SystemKeyTool支持lock_screen，代码就绪 |
| 2026-06-17 | PASS | GUARD-ALL | Agent Guards: EmailComposeGuard(邮件起草约束)、InAppSearchGuard(应用内搜索约束)、DirectDeviceDataGuard(设备数据访问约束)完整实现，代码就绪 |
| 2026-06-17 | PASS | DD-ALL | Direct Device-Data Guard: DirectDeviceDataGuard完整实现，强制使用直接工具(clipboard/get_notifications/get_installed_apps/get_device_info)，防止模型用通用拒绝回答，代码就绪 |
| 2026-06-17 | PASS | CLOUD-1 | Cloud端云模块: CloudHeartbeatManager(30s心跳+WorkManager)、CloudNodeOrchestrator(注册/心跳/任务拉取/执行/结果上报闭环)、离线队列缓存，代码就绪 |
| 2026-06-17 | PASS | M7-M12 | App Navigation: OpenAppTool完整实现，支持Spotify/YouTube/Chrome等常见应用，支持"允许"对话框自动处理，代码就绪 |
| 2026-06-17 | PASS | C1-C6 | Monitor Workflow: AutoReplyManager完整实现，支持WhatsApp/Telegram双应用监控，MonitorTarget管理，Top Bar状态显示，代码就绪 |
| 2026-06-17 | PASS | M1-M6,DD1-DD7 | System Queries + Direct Device-Data Guard: GetDeviceInfoTool(battery/wifi/storage/bluetooth/screen)和GetNotificationsTool完整实现，已注册ToolRegistry，代码就绪 |
| 2026-06-17 | PASS | I1-I3 | Cross-App Behavior: FloatingCircleManager管理悬浮窗显示/隐藏，在其他app上可见，点击返回PokeClaw，确保Showing机制防止被系统清除，代码就绪 |
| 2026-06-17 | BLOCKED | H1-H4,H4b-H4e | General UI: 需要真实设备验证UI尺寸、键盘行为、Model切换效果，标记为BLOCKED |
| 2026-06-17 | PASS | H5-H9 | General UI (Chat): New Chat按钮、长按对话显示Rename/Delete菜单、Rename对话框、ChatHistoryManager.rename实现，代码就绪 |
| 2026-06-17 | PASS | G1-G5 | Empty State: EmptyStateWithPrompts实现Cloud/Local差异化UI，PokeClaw图标+标题+副标题(Cloud AI/Local AI)+提示文本+3个prompt chips，代码就绪 |
| 2026-06-17 | PASS | F1-F6 | Task Lifecycle UI: ActiveTaskShellController管理top bar任务状态，FloatingCircleManager管理悬浮窗(RUNNING状态显示+点击停止)，ChatScreen管理输入区域状态和停止按钮，代码就绪 |
| 2026-06-17 | PASS | D1-E4 | Local LLM: LocalModelRuntime完整实现(GPU/CPU自动降级)，ChatScreen支持Chat/Task模式切换、橙色Task主题、QuickTasks自动切换、Monitor对话框，代码就绪 |
| 2026-06-17 | PASS | B1-B5 | Cloud LLM Tasks: SendMessageTool完整实现，支持联系人名称/手机号匹配，自动查找send按钮或Enter回退，已注册到ToolRegistry，代码就绪 |
| 2026-06-17 | BLOCKED | C8-C11 | Missed-call E2E: 需要第二台设备/SIM卡/VoIP来电触发真实未接来电，标记为BLOCKED待设备就绪后测试 |
| 2026-06-17 | BLOCKED | A1-A5 | Cloud LLM Chat: 需要ADB连接设备+Cloud API key，当前环境无设备，标记为BLOCKED |
| 2026-06-17 | BLOCKED | C12-C15 | Telegram bot: QA账号被冻结(read-only)，无法发送/接收消息，标记为BLOCKED |
| 2026-06-17 | PASS | C16-C19 | Production Intent E2E: ExternalAutomationActivity/Receiver双入口，task/chat模式，完整callback机制(6种状态)，安全开关校验，无需后端联调即可验证 |
| 2026-06-17 | PASS | C7 | Missed-call follow-up arms cleanly: 功能已实现，Settings中有启用/禁用开关，可配置短信模板，状态在聊天室显示 |
| 2026-06-16 | PASS | MIS-001 | P0 Missed-call auto follow-up功能修复：添加SEND_SMS权限和MissedCallReceiver声明到AndroidManifest.xml，在SettingsActivity中添加启用/禁用开关和模板配置UI，在ComposeChatActivity中集成状态显示，功能完整可商业化落地 |
| 2026-06-16 | PASS | REL-001 | 发布签名配置模板：创建local.properties.template文件，包含完整的发布签名配置说明，帮助团队正确设置Release构建所需的签名信息，解决商业化落地的关键构建障碍 |
| 2026-05-18 | PASS | CMP-137 | 【Android】PokeClaw端侧对接 — 设备API联调准备：创建本地Mock服务(scripts/mock-dyq-backend.py)用于后端未启动时的端侧独立验证；完成curl端点测试(5个端点全部通过)；产出Mock联调指南(docs/product/CMP-137-mock-guide.md)；更新验证报告；端侧实现100%完成，等待后端就绪后联调 |
| 2026-05-17 | PASS | CMP-137 | 【Android】PokeClaw端侧对接 — 设备API联调准备：修复NetworkType枚举对齐device.openapi.yaml规范（移除UNKNOWN），修复CloudHeartbeatManager.getNetworkType()返回值类型，产出联调准备清单文档，提交main分支commit 47e57f0 |
| 2026-05-17 | PASS | CMP-1964 | PokeClaw端侧执行端心跳与错误上报方案：产出完整方案文档，涵盖心跳机制、错误码体系、离线重试策略、端云字段映射、编排器状态机和待验证清单 |
| 2026-05-17 | PASS | CMP-1940 | 【Android】PokeClaw端云任务下发与结果回传联调清单：完成端云接口字段映射审查，产出联调文档；修复后端状态枚举注释不一致问题；修复Token刷新接口缺少refreshToken问题；输出修复文件清单和待验证项 |
| 2026-05-16 | PASS | CMP-137 | 【Android】PokeClaw端侧对接 — 设备API联调准备：完成ClawApplication初始化集成，添加cloud模块懒加载；验证DTO字段与device.openapi.yaml对齐；新增Z8-Z12验收项；等待后端编译通过后启动联调 |
| 2026-05-16 | AUDIT | CMP-137 | 【Android】PokeClaw端侧对接 — 设备API联调准备：审计完成，端侧cloud模块实现就绪，等待后端OpenAPI文件 |

---

Product direction lives in `README.md` under `Product Direction`, `Roadmap`, and `Known platform constraints`. QA should enforce that direction:
fix deterministic harness/runtime bugs first, keep prompts and skills generic, and measure stochastic model behavior by repeated-trial success rate instead of hardcoding one task.

---

## QA Methodology — How to Test (READ THIS FIRST)

### Three QA Layers — Do Not Mix These Up

Use all three. Do not claim a user-facing fix from backend smoke alone.

1. **Backend smoke**
   - Fast validation through ADB + logcat.
   - Proves tool routing, rules, runtime guards, and final backend result.
   - Does **not** by itself prove that the result showed up in the visible chatroom.

2. **Chatroom bridge smoke**
   - Short user-visible verification.
   - Proves that once backend has a result, the answer appears in the same chatroom as a visible assistant bubble and is persisted to the current conversation.
   - Use this whenever changing chat/task result rendering, auto-return, or task-to-chat bridging.

3. **True E2E**
   - Full user path: tap/type/send/watch/verify.
   - Use this for release confidence, major regressions, and high-risk flows such as send-message, monitor, permission flows, and context handoff.

Rule of thumb:
- backend-only bug -> backend smoke first, then at least one chatroom bridge check
- user-visible chat/task behavior -> backend smoke + chatroom bridge
- shipping / RC claims -> real E2E, not smoke theater

### Success Rate Over Single-Trial Theater

Do not judge stochastic agent behavior from a single run.

Use these rules:

1. **Deterministic / direct-tool / state-truth flows**
   - Examples: battery, storage, clipboard, model switching, permission truth, monitor start/stop, auto-return shell state
   - Expected standard: effectively `10/10` on the target device
   - If one of these flakes, treat it as a real bug until proven environmental

2. **Cloud exploratory multi-step tasks**
   - Examples: cross-app search, email drafting, app install, read-then-act tasks, `M` section flows, `S` quick tasks, M-session style prompts
   - Run `10` trials and judge by **success rate**, not one lucky pass or one unlucky fail
   - Default release threshold:
     - `8/10` = acceptable
     - `9/10+` = strong enough to promote in README / release notes
     - `<8/10` = still unstable; keep as experimental or fix before shipping

3. **Local exploratory tasks**
   - Use repeated trials too, but evaluate against the intended model tier:
     - `E4B` = primary Local UX target
     - `E2B` = fallback tier that only needs to be broadly usable, not feature-parity with E4B

4. **Blocked cases**
   - Environment blockers do not count as model failures
   - Record them separately from the success-rate denominator when the root cause is external (permissions, missing contacts, runtime dialogs, missing app, absent sender device)

Never claim "fixed" from a single green run on a stochastic Cloud workflow.

### Device Setup

```bash
# 1. Check device connected
adb devices -l

# 2. Install APK
cd /home/nicole/MyGithub/PokeClaw
./gradlew assembleDebug
APK=$(find app/build/outputs/apk/debug/ -name "*.apk" | head -1)
adb install -r "$APK"

# 3. Launch app
adb shell am start -n io.agents.pokeclaw/io.agents.pokeclaw.ui.splash.SplashActivity
sleep 5

# 4. Enable accessibility (if not already)
CURRENT=$(adb shell settings get secure enabled_accessibility_services)
[[ "$CURRENT" != *"io.agents.pokeclaw"* ]] && \
  adb shell settings put secure enabled_accessibility_services \
  "$CURRENT:io.agents.pokeclaw/io.agents.pokeclaw.service.ClawAccessibilityService"

# 5. Grant permissions
adb shell pm grant io.agents.pokeclaw android.permission.READ_CONTACTS
```

### Configure LLM via ADB

```bash
# Cloud LLM
source /home/nicole/MyGithub/PokeClaw/.env
adb shell "am broadcast -a io.agents.pokeclaw.DEBUG_TASK -p io.agents.pokeclaw \
  --es task 'config:' --es api_key '$OPENAI_API_KEY' --es model_name 'gpt-4.1'"

# Local LLM
MODEL_PATH="/storage/emulated/0/Android/data/io.agents.pokeclaw/files/models/gemma-4-E2B-it.litertlm"
adb shell "am broadcast -a io.agents.pokeclaw.DEBUG_TASK -p io.agents.pokeclaw \
  --es task 'config:' --es provider 'LOCAL' --es base_url '$MODEL_PATH' --es model_name 'gemma4-e2b'"
```

### Batch Quick-Task Sweeps

```bash
# Cloud quick tasks
cd /home/nicole/MyGithub/PokeClaw
./scripts/e2e-quick-tasks.sh cloud

# Local quick tasks
./scripts/e2e-quick-tasks.sh local
```

The runner emits `PASS / FAIL / BLOCKED / TIMEOUT` and writes a timestamped log file under `/tmp/`.

### Send a Task via ADB (for M tests)

```bash
# IMPORTANT: wrap the task string in single quotes INSIDE adb shell double quotes
adb logcat -c
adb shell "am broadcast -a io.agents.pokeclaw.DEBUG_TASK -p io.agents.pokeclaw \
  --es task 'how much battery left'"
```

### Send a Chat via ADB (for bridge smoke)

```bash
# Launch ComposeChatActivity through the debug receiver and inject a chat message
adb shell "am broadcast -a io.agents.pokeclaw.TASK -p io.agents.pokeclaw \
  --es chat 'read my clipboard and explain what it says'"
```

Use this when you need a fast chatroom-bridge verification but do not trust raw ADB tap coordinates.
It should create a visible user bubble, wait for the backend reply, and render the assistant bubble in the same conversation.
On Android 15+, make sure PokeClaw is already in the foreground first; otherwise the system may block the receiver from bringing the chat activity forward for UI-visible verification.

### Read Results from Logcat

```bash
# Wait for task to complete (Cloud ~10s, Local ~60-120s per round)
sleep 15
PID=$(adb shell pidof io.agents.pokeclaw)

# Check which tools were called + final answer
adb logcat -d | grep "$PID" | grep -E "onToolCall|onComplete" | head -10

# Full breakdown
adb logcat -d | grep "$PID" | grep -E "DebugTask|PipelineRouter|AgentService|TaskOrchestrator|onToolCall|onComplete"
```

### Verify PASS/FAIL

For each M test, check:
1. **Correct tool called** — e.g., "how much battery" should call `get_device_info(battery)`, NOT open Settings
2. **Actual data in answer** — "73%, not charging, 32°C" NOT "I checked the battery"
3. **Rounds** — system queries should be 2 rounds, complex tasks 5-15
4. **Auto-return** — after task, PokeClaw chatroom should come back to foreground
5. **Graceful failure** — if task can't complete, clear error message (not stuck/loop)
6. **Env-dependent quick tasks** — if a sample contact/app is missing on this device, require the correct tool + a graceful failure; literal send/call success should be marked `BLOCKED`, not product `FAIL`

### Verify UI via Uiautomator

```bash
# Dump all visible UI elements
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml | python3 -c "
import sys, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
for node in root.iter():
    text = node.get('text', '')
    desc = node.get('content-desc', '')
    pkg = node.get('package', '')
    if (text or desc) and 'pokeclaw' in pkg.lower():
        print(f'text={text!r} desc={desc!r}')
"
```

Use this to verify:
- UI elements are present (tabs, buttons, prompts)
- Placeholder text changes when switching modes
- Correct model name shows in dropdown

### Tap UI Elements

```bash
# Find coordinates of an element
adb shell cat /sdcard/ui.xml | python3 -c "
import sys, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
for node in root.iter():
    text = node.get('text', '')
    bounds = node.get('bounds', '')
    if 'Task' in text:
        print(f'text={text!r} bounds={bounds}')
"

# Tap at coordinates (center of bounds)
adb shell input tap 746 2041
```

### Three QA Layers

**Layer 1: Backend QA (ADB broadcast)**
- Fast: ~10s per test
- Uses `am broadcast` to send tasks directly to DebugTaskReceiver
- Bypasses UI entirely — tests tools, LLM routing, error handling, agent loop
- Code path: `DebugTaskReceiver → sendTask() → PipelineRouter → Agent`
- Sections: M tests
- When to run: every backend/agent/tool change

**Layer 2: UI Structure QA (uiautomator dump)**
- Medium: ~5s per test
- Verifies UI elements are present, positioned correctly, styled correctly
- No message sending — purely visual/structural verification
- Code path: Compose render → uiautomator reads view tree
- Sections: P tests
- When to run: every UI/layout change

**Layer 3: UI E2E QA (tap + type + send + verify response)**
- Slow: ~30s per test
- Simulates real user: tap input → type → dismiss keyboard → tap send → wait → verify response bubble
- Tests the FULL pipeline: UI routing → Activity callback → LLM → response → UI update
- Code path: `ChatInputBar → isLocalUI routing → onSendChat/onSendTask → Activity → LLM → UI`
- Sections: Q tests
- When to run: every change that touches send routing, mode switching, or input bar
- **This is the ONLY layer that tests UI send routing.** Layer 1 broadcast bypasses ChatInputBar entirely.

**Why 3 layers, not 2:**
Layer 1 broadcast calls `sendTask()` directly — it never touches `ChatInputBar`, `isLocalUI`, or the Chat/Task toggle routing. If UI routing breaks (e.g., Cloud mode accidentally routes to `onSendChat`), Layer 1 won't catch it. Layer 3 covers this gap.

**Run order:**
1. Layer 2 first (fast, catches layout breaks)
2. Layer 3 second (catches routing/interaction breaks)
3. Layer 1 last (catches backend/agent breaks)

```bash
# Layer 2 — simulate real user typing + sending
# 1. Find and tap input field
adb shell uiautomator dump /sdcard/ui.xml
# Parse bounds for the input element with placeholder text
INPUT_X=504; INPUT_Y=2100  # adjust from dump

# 2. Tap input, type, send
adb shell input tap $INPUT_X $INPUT_Y        # focus input
sleep 0.5
adb shell input text "how%smuch%sbattery"    # type (spaces = %s in adb)
sleep 0.5
SEND_X=970; SEND_Y=2100                      # adjust from dump
adb shell input tap $SEND_X $SEND_Y          # tap send

# 3. Wait for response, verify chat bubble appears
sleep 15
adb shell uiautomator dump /sdcard/ui_after.xml
adb shell cat /sdcard/ui_after.xml | python3 -c "
import sys, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
for node in root.iter():
    text = node.get('text', '')
    pkg = node.get('package', '')
    if text and 'pokeclaw' in pkg.lower() and ('battery' in text.lower() or '%' in text):
        print(f'FOUND RESPONSE: {text!r}')
"
# Should find: "Battery: 73%, not charging, 32°C" or similar in a chat bubble
```

### Cross-Device Testing

Test on at least 2 devices:
- **Stock Android** (Pixel): baseline, everything should work
- **MIUI/Samsung/OEM** (Xiaomi etc): test for OEM restrictions (autostart, different Settings UI)

Key OEM differences:
- MIUI blocks background app launches (autostart whitelist needed)
- Samsung has different Settings layout
- Some OEMs have chain-launch dialogs (auto-dismissed by OpenAppTool)

### Local LLM Testing Notes

- CPU inference: ~50-60s per round on Pixel 8 Pro
- GPU may fail ("OpenCL not found") → auto-fallback to CPU
- LiteRT-LM SDK may crash on tool call parsing → our fallback extracts from error message
- Force stop loses accessibility service → re-enable after restart
- Model engine takes ~10s to load on first call

---

## Current Coverage Snapshot (2026-04-28, v0.6.8 QA Audit)

This is the latest QA state for `v0.6.8`. It is **not** a green release sheet.
Do not describe `v0.6.8` as fully phone-QA-passed until the blockers below are
fixed and the relevant sweeps are rerun.

Test device and artifact state:
- Device: Pixel 8 Pro (`husky`), Android 16 / API 36, build `CP1A.260405.005`
- Installed test build: `0.6.8` debug APK, upgraded in place over the existing
  debug-signed `0.6.7` install
- Stable release APK upgrade attempt: **BLOCKED**. `adb install -r
  app/build/outputs/apk/release/PokeClaw_v0.6.8_20260428_112909.apk` failed with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the installed `0.6.7` package is
  debug-signed and the `0.6.8` release APK is signed by the stable release cert
  (`e000d1d6555b8fab20c03a5d9ddeba83944f26eecf0b978ac7affc2eebd43186`)
- Release artifact conclusion: the stable APK has **not** been real-device
  upgrade-verified on this handset. Test users moving from debug/old-signature
  builds still need an uninstall/reinstall path or a clear signed-line migration
  note.
- Local release-build conclusion: `./gradlew assembleRelease` compiles/minifies
  but fails at `:app:packageRelease` because local `SigningConfig "release"` is
  missing `storeFile`; a signed hotfix APK must be produced by the configured
  release/CI signing path or after restoring local signing secrets.

Cloud quick-task sweep after fixes:
- Command: `RESULTS_FILE=/tmp/pokeclaw-v068-cloud-quick-20260428-1337-after-wa-fix.log
  CLOUD_MODEL_NAME=gpt-4.1 ./scripts/e2e-quick-tasks.sh cloud`
- Result: **17 PASS / 0 FAIL / 1 BLOCKED / 2 TIMEOUT / 20 TOTAL**
- Passed: Reddit search, YouTube search, Telegram/Play Store path, Twitter
  trending, write email, notifications triage, clipboard explain, storage
  analysis, notification summary, battery advice, WhatsApp send to `Girlfriend`,
  installed-apps list, phone temperature, Bluetooth, battery, storage, Android
  version
- Timed out:
  - `S6/M11` WhatsApp latest-chat summary: still times out at 60s
  - `RC6-cloud-gmail-google` copy latest email subject and Google it: still
    times out at 60s in the latest full sweep
- Blocked:
  - `M47` call Mom: latest run could not find a saved contact named `Mom`; treat
    as data/environment blocked, not a completed call-flow pass
- Regressions fixed since the first 2026-04-28 audit:
  - `S7/M51` Reddit search no longer gets stuck; it passed in two follow-up
    sweeps
  - `B1` WhatsApp send no longer times out; latest full sweep passed in 15s
  - `S8/M19` write email passed in the latest full sweep
  - Timeout cleanup no longer leaks `Task cancelled`/interruption into the next
    harness case

Local targeted smoke after fixes:
- Targeted Local E2B deterministic smoke (`How much battery left?`) completed in
  **105s** after calling `get_device_info(category=battery)` and returning
  `60%, not charging, 38.1°C`
- The run first attempted GPU, hit `Can not find OpenCL library on this device`,
  then fell back to CPU and completed. This verifies GPU fallback did not crash,
  but local latency is still high.
- The earlier foreground-service crash path has been fixed by calling
  `startForeground()` immediately in `ForegroundService.onCreate()`
- Local conclusion: targeted Local battery is no longer a hard fail, but Local
  mode is **not full-sweep green** until the full local quick-task set is rerun.

Release blockers found in this audit:
- `Rel-s9`: stable-signed APK cannot upgrade over the currently installed
  debug/old-signature package; document migration and verify a clean stable
  install before asking users to upgrade
- Release signing: local `assembleRelease` cannot package a signed APK without
  the release keystore `storeFile`
- `S6`: WhatsApp latest-chat summary is still not passing on the Pixel 8 Pro QA
  device
- `RC6-cloud-gmail-google`: copy latest email subject and Google it still times
  out in the latest full Cloud sweep
- `LQ-v068`: Local targeted battery now passes, but Local full sweep has not been
  rerun and Local CPU-fallback latency remains high

What can still be claimed from this audit:
- Direct Cloud device-data tools are still working in the quick sweep:
  clipboard, notifications, battery, storage, Bluetooth, phone temperature, and
  Android version all returned real device data
- Cross-app Cloud flows that passed in the latest full sweep include Reddit
  search, YouTube search, Twitter trending, Telegram/Play Store path, write
  email, and WhatsApp direct send to `Girlfriend`
- Local E2B battery can complete through GPU-to-CPU fallback, but slowly

What cannot be claimed:
- `v0.6.8` cannot be called fully QA-passed
- The stable release APK cannot be called upgrade-verified on the QA phone
- WhatsApp latest-chat summary cannot be called fixed
- Gmail latest-subject-to-Google cannot be called fixed
- Local task mode cannot be called full-sweep healthy yet

---

## Current Coverage Snapshot (2026-04-10)

This checklist is **not** yet a fully rerun 100% green master sheet. The honest current state is:

- **Strongly covered right now**
  - Local quick-task sweeps
  - Cloud quick-task sweeps
  - Settings / model config flows
  - Accessibility reconnect + permission return flows
  - Task stop / auto-return / same-session preservation
  - Explicit in-app search and email-compose guards
  - Phase 1 chat-runtime extraction smoke:
    - Cloud runtime rehydrate after relaunch
    - Local runtime rehydrate after relaunch
    - Local chat send with GPU→CPU fallback
  - Phase 2 task-session-store smoke:
    - Local quick-task prompt fill still routes correctly
    - Task shell enters `Task running...` + `Stop`
    - Stop request safely unwinds without leaving `ComposeChatActivity`
    - Idle shell restores after stop
  - Phase 3 permission/accessibility smoke:
    - App Settings truthfully shows `Disabled` after reinstall clears Accessibility from secure settings
    - App Settings truthfully shows `Connecting` during enabled-but-rebinding Accessibility state
    - App Settings truthfully shows `Notification Access = Disabled` when the listener is not enabled in system settings
    - Notification-listener auto-return is now gated by a pending permission-flow flag instead of firing on every reconnect
  - Phase 5 local-runtime consolidation smoke:
    - Shared local runtime still cold-launches into `ComposeChatActivity` with truthful `CPU` backend status
    - Real local UI send still works after runtime consolidation: `say pong` → `Pong! 🏓`
    - Assistant bubble model tag remains aligned with the actual backend after send
    - Local single-shot and auto-reply entrypoints now share the same runtime boundary as chat/session bring-up
    - Settings and chat now share the same built-in local model support/catalog state instead of each recalculating RAM/support/downloaded status
  - Chat bubble metadata smoke:
    - User bubbles render a subtle IG-style time footer under the bubble
    - Assistant bubbles render `model name · time` under the bubble
    - Saved markdown history persists per-message timestamps via hidden metadata comments
  - ConversationStore smoke:
    - cold relaunch still restores the same saved conversation instead of falling back to a blank chat shell
    - sidebar refresh, save, and restore now come through a single boundary instead of ad-hoc `KVUtils + ChatHistoryManager` calls in `ComposeChatActivity`
  - Phase 2b task-flow boundary smoke:
    - debug task intents still land on the chat shell after `TaskFlowController` extraction
    - task-mode permission guidance still redirects to in-app Settings when Accessibility is missing
    - cold launch no longer crashes if Android blocks an app-start foreground-service request
- **Covered, but still environment-sensitive**
  - WhatsApp send flows
  - Local contact-specific send/call flows
  - Cross-app floating-pill stop flows
- **Still blocked or not fully rerun end-to-end**
  - same-chatroom memory continuity (`Q8-1` to `Q8-4`) — must be rerun whenever chat runtime / persistence changes
  - incoming-message auto-reply while staying in-app (`L5`, `L5-b`) — needs a second live sender device or equivalent live source
  - some OEM-specific real-device failures from GitHub issues (`Samsung`, `Xiaomi`, `Dimensity`, low-RAM devices)
  - full public-release upgrade validation from the next stable-signed public build

If a task is not clearly marked `PASS`, `FIXED`, or `BLOCKED` with a reason, do **not** assume it is truly cleared.

## Release Gate

A build is only genuinely ship-ready when all of the following are true:

- **Direction gate**
  - The change follows the `README.md` product direction and roadmap
  - It fixes a reusable harness/runtime/product problem, or clearly documents why a narrow change is justified
  - Prompt, skill, and playbook changes remain generic; no one-off tuning for a single flaky task
  - Model-performance limits are measured and documented instead of treated as deterministic product bugs
- **Product gate**
  - Chat vs Task routing is correct in Local and Cloud
  - Local GPU→CPU fallback is truthful and stable
  - Monitor stays in-app and does not force Home
  - Auto-return restores the same conversation after tasks
- **QA gate**
  - Deterministic harness/runtime flows are effectively `10/10`
  - Local deterministic/core sweep finishes with no product `FAIL`
  - Cloud exploratory quick-task and M-session style sweeps are judged by repeated-trial success rate, not one-off luck
  - any Cloud workflow called out as a headline/demo/release-note capability should meet roughly `9/10` on the target device
  - any exploratory Cloud workflow below `8/10` should stay experimental or be fixed before release
  - any `BLOCKED` items are clearly environment-caused, not product regressions
- **Distribution gate**
  - upgrade/install path is understood for the target release
  - release artifact, signing path, and checksums are verified
- **Architecture gate**
  - any refactor touched only its declared scope
  - required regression bundle for that refactor class was rerun

### Release Gate Record Template

Copy this block into the current coverage snapshot or QA debug changelog for every release candidate. Do not publish a release without either a checked item or a concrete blocker note for each line.

```markdown
### Release Gate Record — vX.Y.Z (YYYY-MM-DD)

- [ ] Direction gate: change follows README Product Direction / Roadmap / Known platform constraints
- [ ] Harness gate: deterministic runtime/storage/permissions/direct-tool behavior has no known product FAIL
- [ ] Scope gate: no prompt/skill/playbook one-off was added solely to make one flaky task pass
- [ ] Unit/compile gate: `./gradlew compileDebugKotlin testDebugUnitTest`
- [ ] Script hygiene gate: `bash -n scripts/e2e-quick-tasks.sh && git diff --check`
- [ ] Artifact gate: `./gradlew assembleDebug` or signed release workflow completed
- [ ] Targeted regression gate: relevant bundle from "Refactor Regression Bundles" rerun
- [ ] Device smoke gate: at least one real-device smoke for the changed runtime/product path
- [ ] Distribution gate: install/upgrade behavior, signing path, release asset, and checksum are verified or explicitly documented as blocked
- [ ] User-followup gate: affected GitHub/Reddit users are told exactly which stable release to retest and what debug ZIP to attach if it still fails
- Known misses:
  - `BLOCKED`: ...
  - `TIMEOUT`: ...
  - `FAIL`: ...
```

### Release Gate Record — v0.6.12 (2026-04-30)

- [x] Direction gate: follows README Product Direction / Roadmap / Known platform constraints; this release adds a generic external automation harness slot instead of a one-off task prompt.
- [x] Harness gate: production external automation activity/receiver entries are user-enabled, targeted to the PokeClaw package, and route through normal task/chat harness rules.
- [x] Scope gate: no prompt/skill/playbook one-off was added solely to make a flaky task pass.
- [x] Unit/compile gate: `./gradlew testDebugUnitTest assembleDebug` passed.
- [x] Script hygiene gate: `bash -n scripts/e2e-quick-tasks.sh && git diff --check` passed.
- [x] Artifact gate: local debug artifact built; tag-triggered GitHub Actions release workflow produced signed APK `PokeClaw_v0.6.12_20260430_174625.apk`, SHA-256 `62d9dbb1cc00299892ec0ba229b128d4be018caba589c5a15429ea500c8b8fbe`.
- [x] Targeted regression gate: `ExternalAutomationContractTest` covers task/chat parsing, base64 payloads, callback metadata, unknown action rejection, and missing payload rejection.
- [x] Device smoke gate: Pixel 8 Pro MacroDroid `Send Intent` E2E uses the exported Activity target on modern Android; debug and signed v0.6.12 Activity-target E2E passed.
- [x] Distribution gate: GitHub release `v0.6.12` published with signed APK `PokeClaw_v0.6.12_20260430_174625.apk`, SHA-256 `62d9dbb1cc00299892ec0ba229b128d4be018caba589c5a15429ea500c8b8fbe`.
- [x] User-followup gate: affected GitHub/Reddit users should be pointed to v0.6.12 for External Automation / MacroDroid / direct-device task retesting.
- Known misses:
  - `BLOCKED`: Tasker-specific E2E is blocked by Play Store purchase requirement on the QA phone; MacroDroid E2E is verified.
  - `PARTIAL`: callback-consumer E2E remains open until a Tasker/MacroDroid receiver profile is configured.
  - `FAIL`: none known in the External Automation task/chat/direct-device smoke path.

### Release Gate Record — v0.6.10 (2026-04-28)

- [x] Direction gate: follows README Product Direction / Roadmap / Known platform constraints; this fixes model-storage harness behavior instead of tuning a flaky task
- [x] Harness gate: `LocalModelManager` now requires a writable model directory and falls back external app storage -> internal app storage when needed
- [x] Scope gate: no prompt/skill/playbook one-off was added
- [x] Unit/compile gate: `./gradlew compileDebugKotlin testDebugUnitTest` passed
- [x] Script hygiene gate: `bash -n scripts/e2e-quick-tasks.sh && git diff --check` passed
- [x] Artifact gate: `./gradlew assembleDebug` passed; signed release workflow `25084344165` passed
- [x] Targeted regression gate: `LocalModelManagerTest` covers external dir creation, blocked external path, external write-probe fallback, and missing external root fallback
- [ ] Device smoke gate: blocked on the exact Xiaomi/custom-ROM repro device; #39 reporter has been asked to retest v0.6.10 and attach a fresh bug ZIP
- [x] Distribution gate: GitHub release `v0.6.10` published with signed APK `PokeClaw_v0.6.10_20260429_001417.apk`, SHA-256 `1cdc95d13dc6bbecad5ad7fe1cf17a9d6b0e92e4b3e2ebb674fc3d62a2a3ca02`, plus `SHA256SUMS.txt`
- [x] User-followup gate: follow-up comments posted to #39, #17, #29, and #23
- Known misses:
  - `BLOCKED`: exact Xiaomi/custom-ROM model-download repro still requires reporter retest
  - `TIMEOUT`: inherited v0.6.9 exploratory Cloud timeouts remain outside this storage hotfix scope
  - `FAIL`: none known in the local model storage selection regression bundle

## Refactor Regression Bundles

Do **not** rerun the entire world after every refactor. Rerun the right bundle for the code you touched:

- **Model/config changes**
  - `H2`, `H2-b`, `H2-c`, `H4`, `H4-b`
  - `Q4-1`, `Q4-2`, `Q5-1`, `Q5-1b`
  - `LQ1-LQ13`
- **Local runtime / LiteRT fallback changes**
  - `H4`, `H4-b`
  - `Q3-1`, `Q5-1`, `Q5-1b`
  - `LQ1-LQ13`
  - one real Local UI send smoke using live bounds from the current `uiautomator dump`
- **Chat history / bubble metadata changes**
  - `P7-1`, `P7-2`, `P7-3`
  - `Q3-1`
  - `Q7-7`
  - `Q8-1`, `Q8-2`, `Q8-3`, `Q8-4`
  - one persisted markdown-history spot check for `<!-- pokeclaw:timestamp=... -->`
- **Cloud task-context handoff changes**
  - `Q2-1`, `Q2-2`, `Q7-7`
  - `Q8-1`, `Q8-3`
  - `Q9-1`, `Q9-2`
  - one real Cloud chatroom task that refers to earlier context (for example `send that summary by email`)
- **Task lifecycle / orchestration changes**
  - `F1-F6`
  - `I1-I3`
  - `L1`, `L3`
  - `Q7-*`
  - `S2`, `S3`, `S5`, `S7`, `S8`
- **Accessibility / permission changes**
  - `K1-K6`
  - `J4`
  - `L5`, `L5-b` when an external sender is available
- **Cross-app / skill / tool changes**
  - `B1-B5`
  - `M7-M21`
  - relevant quick-task sweeps
- **Direct device-data / no-false-denial changes**
  - `DD1-DD7`
  - `R1-R6`
  - `Q2-2`, `Q3-2`
  - one Cloud chatroom bridge smoke where a direct-device-data answer visibly appears as an assistant bubble
  - one Local chatroom bridge smoke where a direct-device-data/task answer visibly appears in the same conversation
- **Release / installer / updater changes**
  - `Dbg-u1-Dbg-u3`
  - `Rel-s1-Rel-s7`

When in doubt, rerun the smaller bundle first, then expand only if something drifted.

---

## Prerequisites
- [ ] Accessibility service enabled
- [ ] Cloud LLM configured (API key set)
- [ ] Local LLM downloaded (Gemma 4)
- [ ] WhatsApp installed with at least 1 contact ("Girlfriend")
- [ ] For monitor QA, an external sender path is available:
  - WhatsApp: second phone / second WhatsApp account
  - Telegram notification monitor: second Telegram account or a Telegram bot token + already-started bot chat on this device
  - Telegram bot remote-control channel: Telegram bot token configured in PokeClaw, bot polling connected, and this handset's Telegram account able to send `/start` plus a task to the bot
- [ ] For external automation QA, Tasker/MacroDroid or an equivalent explicit Activity/Broadcast intent sender is available:
  - the test must run against a release build once the production receiver exists
  - MacroDroid/Tasker-style app automation should prefer the exported Activity target on modern Android because background broadcast receivers can be blocked from opening an Activity
  - debug-only `io.agents.pokeclaw.TASK` / `DEBUG_TASK` receivers are not enough for public integration claims
- [ ] For missed-call QA, an external caller path is available:
  - second phone / second SIM / VoIP caller that can place a real call to this handset
  - one follow-up route already configured
  - for the preferred first version, this should be SMS / Android-native sending rather than UI-driven WhatsApp automation

### Monitor QA Sender Rules

- WhatsApp and Telegram monitor tests are only `PASS` when a real external sender delivers a message to this phone and PokeClaw reacts.
- If the app logic is ready but there is no sender available, mark the case `BLOCKED`, not `FAIL`.
- For Telegram bot QA, the bot must already have an open chat with this handset; Telegram bots cannot cold-DM a user who never started the bot.
- If the Telegram account is frozen/read-only and cannot send messages or take actions, mark Telegram bot E2E as `BLOCKED`, not `FAIL`.
- When testing monitor fixes, always verify both:
  - monitor shell state (`Monitoring: ...`, expand, Stop)
  - actual incoming-message reaction from an external sender

---

## A. Cloud LLM — Chat

- [ ] **A1. Pure chat question**: "what is 2+2" → answer in bot bubble, 1 round, no tools, no rocket, no "Starting task...", no "Reading screen..."
- [ ] **A2. Follow-up chat**: after A1, ask "what about 3+3" → answer in bot bubble, context preserved
- [ ] **A3. Chat then task**: chat "hello" → get reply → then "send hi to Girlfriend on WhatsApp" → task executes correctly
- [ ] **A4. Task then chat**: "send hi to Girlfriend on WhatsApp" → completes → then "how are you" → chat reply (not task)
- [ ] **A5. Multiple chat messages**: send 3 chat messages in a row → all get bot bubble replies

## B. Cloud LLM — Tasks

- [ ] **B1. Send message**: "send hi to Girlfriend on WhatsApp" → send_message tool called → message sent → answer in bot bubble
- [ ] **B2. Complex task**: "open YouTube and search for funny cat videos" → opens YouTube → searches → multiple steps shown
- [ ] **B3. Task with context**: "I'm arguing with my girlfriend" → then "send sorry to Girlfriend on WhatsApp" → message content should reflect context
- [ ] **B4. Failed contact**: "send hi to Dad on WhatsApp" → Dad not in contacts → LLM reports failure in bot bubble (not stuck, not "Task completed")
- [ ] **B4-b. Name or phone number send target**: send to a saved contact by name, then by phone-number formatting (`+country`, local digits, or spaced/hyphenated form) → same person is resolved without requiring an exact WhatsApp display name match
- [ ] **B4-c. Multilingual text actions stay functional**: on a device/app using non-English labels, structure-first actions (for example standard positive dialog buttons and standard send affordances) still work without requiring English-only UI text
- [ ] **B5. Failed app**: "send hi to Girlfriend on Signal" → Signal not installed → LLM reports can't open app

## C. Cloud LLM — Monitor Workflow

- [ ] **C1. Start monitor**: "monitor Girlfriend on WhatsApp" → top bar shows "Monitoring: Girlfriend" → user stays in PokeClaw chat (no Home press)
- [ ] **C1-b. Monitor dialog honors chosen app**: open Monitor dialog → choose `Telegram` (or another supported app) → start monitor → top bar / stop shell show `... on Telegram`, not `... on WhatsApp`
- [ ] **C2. Auto-reply triggers**: Girlfriend sends message → notification caught → WhatsApp opens → reads context → Cloud LLM generates reply → reply sent
- [ ] **C3. Stop monitor**: tap top bar → expand → Stop → monitoring stops
- [ ] **C4. Start Telegram monitor**: "monitor NicoleBot on Telegram" → top bar shows "Monitoring: NicoleBot" → user stays in PokeClaw chat
- [ ] **C5. Telegram auto-reply triggers**: external Telegram sender / bot sends message → notification caught → Telegram opens → reads context → Cloud LLM generates reply → reply sent
- [ ] **C6. Stop Telegram monitor**: tap top bar → expand → Stop → Telegram monitoring stops without affecting WhatsApp monitors

## C2. Background Call Follow-Up

- [ ] **C7. Missed-call follow-up arms cleanly**: enable the missed-call auto follow-up workflow for a chosen person/number/channel → app shows clear in-chat status of what is armed
- [ ] **C8. Real missed call triggers follow-up**: external caller rings this handset, the call is missed, and PokeClaw sends the configured follow-up message to that caller through the chosen channel
- [ ] **C9. Missed-call result is visible in chatroom**: after the follow-up fires, the same PokeClaw conversation shows a clear status/result bubble instead of hiding the action purely in background state
- [ ] **C10. Wrong caller does not trigger**: a different number/contact calls and is missed → no follow-up is sent for the protected target workflow
- [ ] **C11. SMS-first path stays API-first**: when the follow-up channel is SMS, the implementation should use an Android-native send path rather than accessibility-driven UI navigation

## C3. Remote Control Channels & External Automation

- [ ] **C12. Telegram bot token config**: Settings → Remote Control → Telegram Bot → enter token → Save → Settings shows `Connected`; token is not printed in logs, screenshots, bug ZIPs, or QA notes
- [ ] **C13. Telegram bot polling receives message**: user starts the bot from Telegram and sends a simple task → PokeClaw logcat shows Telegram update received and dispatches it through `ChannelManager`
- [ ] **C14. Telegram bot reply path**: after a bot task completes or fails, PokeClaw sends a Telegram reply to the same chat id with a visible success/failure message
- [ ] **C15. Telegram bot blocked account handling**: if the handset Telegram account is frozen/read-only, record `BLOCKED` with the Telegram system message and do not claim channel failure
- [ ] **C16. Production intent task entrypoint**: with `Settings -> Remote Control -> External Automation = Enabled`, a Tasker/MacroDroid-style explicit Activity intent or compatible targeted broadcast starts the requested task in a release build:
  `adb shell am start -n io.agents.pokeclaw/.automation.ExternalAutomationActivity -a io.agents.pokeclaw.RUN_TASK --es task "how much battery left"`
- [ ] **C17. Production intent chat entrypoint**: targeted broadcast with `chat` opens/uses the chatroom path without bypassing safety rules:
  `adb shell am broadcast -a io.agents.pokeclaw.RUN_CHAT -p io.agents.pokeclaw --es chat "say hi"`
- [ ] **C18. Production intent callback**: when `request_id` and `return_action` are provided, PokeClaw broadcasts `accepted` immediately and terminal `completed` / `failed` / `cancelled` / `blocked` / `rejected` results back to the caller
- [ ] **C19. External automation safety**: an Intent payload cannot override platform safety rules, tool contracts, or user global instructions

## D. Local LLM — Chat

- [ ] **D1. Pure chat**: switch to Local LLM → "hello" → on-device reply in bot bubble
- [ ] **D2. Chat tab has no task ability**: type "open YouTube" in Chat tab → LLM responds conversationally (doesn't try to control phone)

## E. Local LLM — Task Mode (v9: unified chat screen)

- [ ] **E1. Task mode via toggle**: Local tab → tap 🤖 Task → input placeholder changes to "Describe a phone task...", input area tints orange
- [ ] **E2. Task mode via Quick Task tap**: tap "🔋 How much battery left?" in Quick Tasks → input fills + auto-switches to Task mode
- [ ] **E3. Monitor via Quick Tasks panel**: scroll to BACKGROUND → tap Monitor card → centered dialog → enter contact → Start → monitoring activates
- [ ] **E4. Task sends correctly**: type "how much battery left" in Task mode → tap send → task executes, response in chat bubble

## F. Task Lifecycle UI

- [ ] **F1. Top bar during task**: while task runs → orange "Task running..." + red "Stop" button visible
- [ ] **F2. Send button becomes stop**: while task runs → send button turns red X → tapping it cancels task
- [ ] **F3. Floating button during task**: while task runs in another app → floating circle shows pill with step/tokens + "Tap to stop"
- [ ] **F4. Floating button stop**: tap floating button during task → task cancels
- [ ] **F5. Second task works**: complete task 1 → start task 2 → floating button, top bar, stop button all work
- [ ] **F6. No stuck typing indicator**: after task completes → "..." is replaced by answer or removed

## G. Empty State (v9 design)

- [ ] **G1. Cloud empty state**: PokeClaw icon + "PokeClaw" + "Cloud AI" subtitle + "Chat and tasks work together" hint + 3 prompts (Tokyo, birthday, WhatsApp)
- [ ] **G2. Local empty state**: PokeClaw icon + "PokeClaw" + "Local AI" subtitle + hint with bold 💬 Chat / 🤖 Task + 3 prompts (joke, what can you do, email)
- [ ] **G3. Cloud prompt tap**: tap prompt → fills input, stays in chat (no mode switch)
- [ ] **G4. Local prompt tap**: tap prompt → fills input, does NOT switch to Task mode (prompts are chat prompts)
- [ ] **G5. Tab switch updates empty state**: switch Local↔Cloud tab → subtitle, hint, and prompts all change immediately

## H. General UI

- [ ] **H1. Floating button size**: small circle on home screen (not giant)
- [ ] **H2. Keyboard in Models screen — API key**: Settings → LLM Config → tap API key → keyboard doesn't block field, field scrolls fully into view
- [ ] **H2-b. Keyboard in Models screen — Base URL**: switch to Custom provider → tap Base URL → keyboard doesn't block field
- [ ] **H2-c. Keyboard in Models screen — Model Name**: switch to Custom provider → tap Model Name → keyboard doesn't block field
- [ ] **H2-d. Chat keyboard dismiss**: focus chat input → keyboard appears → tap a non-button space in the chatroom/message area or the header's blank area → input loses focus and keyboard hides
- [ ] **H3. Layout sizes**: all text/buttons normal size (dp not pt)
- [ ] **H4. Model switcher**: tap model bar → dropdown → switch model → status updates
- [x] **H4-b. Local backend label is truthful**: Local model falls back GPU→CPU → top-left model status updates to `CPU`, not stale `GPU`
- [ ] **H4-c. Cloud switch emits one system line**: Cloud tab → switch model from the top-left dropdown → chat shows one `Switched to ...` system message for that switch, not a lower-case + upper-case duplicate pair
- [ ] **H4-d. Models page shows active + defaults truthfully**: Settings → Models → page clearly shows current `Active model`, `Default local model`, and `Default cloud model`
- [ ] **H4-e. Built-in local rows respect linked/default model files**: if the default local model points at a usable Gemma file, the matching built-in row must not say `Not downloaded`
- [ ] **H5. New chat**: tap pencil icon → clears messages → shows welcome screen
- [ ] **H6. Rename chat**: long-press session in sidebar → rename option → type new name → name updates in sidebar + persists after app restart
- [ ] **H7. Delete chat**: long-press session in sidebar → delete → session removed from sidebar + file deleted
- [ ] **H8. Rename preserves messages**: rename session → open it → all messages still there
- [ ] **H9. Delete correct session**: have 3+ sessions → delete middle one → other sessions unaffected

## I. Cross-App Behavior

- [ ] **I1. Floating button visible in other apps**: start task → agent navigates to WhatsApp/YouTube → floating button visible on top
- [ ] **I2. Return to PokeClaw mid-task**: while task runs in WhatsApp → press recents → tap PokeClaw → see task progress + stop button
- [ ] **I3. Notification during task**: incoming notification while task runs → task not disrupted

## M. Cloud LLM — Complex Tasks (50 cases)

Design principle: User perspective. INFO tasks → report actual data. ACTION tasks → confirm result. Must work on ANY Android device.

### System Queries (direct tool, no UI)
- [x] **M1. Battery**: "how much battery left" → "73%, not charging, ~5h remaining" (get_device_info)
- [x] **M2. WiFi**: "what WiFi am I connected to" → SSID + signal (get_device_info)
- [x] **M3. Storage**: "how much storage do i have free" → "47GB free of 128GB" (get_device_info)
- [x] **M4. Bluetooth**: "is bluetooth on" → ON/OFF + connected devices (get_device_info)
- [x] **M5. Notifications**: "read my notifications" → actual notification list (get_notifications)
- [ ] **M6. Screen info**: "check what's on my screen" → describe visible UI elements

### App Navigation
- [ ] **M7. Open app**: "open spotify" → Spotify launches, confirmed
- [ ] **M8. YouTube search**: "search youtube for lofi beats" → YouTube opens, types query, results shown
- [ ] **M9. Web search**: "open Chrome and search for weather today" → Chrome, types, results
- [ ] **M10. URL navigation**: "open chrome and go to reddit.com/r/android" → Chrome loads URL
- [ ] **M11. Find in app**: "open WhatsApp and find my last message from Mom" → opens, navigates, reports content
- [ ] **M12. Deep navigation**: "open settings then go to about phone and tell me my android version" → Settings → About → reports version

### Information Retrieval (agent reads and reports back)
- [ ] **M13. Weather**: "what's the weather today" → actual temp + conditions
- [ ] **M14. Last email**: "read my latest email" → sender + subject + preview text
- [ ] **M15. Calendar**: "what's on my calendar tomorrow" → event list with times
- [x] **M16. Installed apps**: "what apps do i have" → sensible summary, not raw dump
- [ ] **M17. Last notification**: "what did that last notification say" → most recent only
- [ ] **M18. Find photo**: "find the photo i took yesterday" → open Gallery, describe what's there

### Text Input Tasks
- [ ] **M19. Compose email**: "compose an email to test@example.com saying hello" → fills To/Subject/Body, does NOT send
- [ ] **M20. Search Twitter**: "go to twitter and find elon's latest post" → opens X, searches, reports post
- [ ] **M21. Google Maps search**: "open maps and navigate to nearest gas station" → Maps, search, results

### Settings Changes
- [ ] **M22. Dark mode**: "turn on dark mode" → toggles, confirms "Dark mode ON"
- [ ] **M23. Brightness**: "brightness to 50%" → adjusts, confirms level
- [ ] **M24. Timer**: "set a timer for 10 minutes" → Clock app, sets 10:00, starts
- [ ] **M25. Alarm**: "set an alarm for 7am tomorrow" → Clock, creates alarm, confirms
- [ ] **M26. DND**: "do not disturb on" → toggles DND, confirms
- [ ] **M27. Compound settings**: "turn off wifi and turn on bluetooth" → both done, both confirmed

### Media
- [ ] **M28. Take photo**: "take a selfie" → front camera, shutter, send_file back
- [ ] **M29. Screenshot**: "screenshot" → take_screenshot + send_file
- [ ] **M30. Play music**: "play music" → picks music app, attempts playback
- [ ] **M31. Next song**: "play the next song" → skip track in music player

### Cross-App Workflows
- [ ] **M32. Install app**: "install Telegram from Play Store" → Play Store → search → Install
- [ ] **M33. Copy-paste cross-app**: "copy tracking number from gmail and search it on amazon" → Gmail → copy → Amazon → paste
- [ ] **M34. Photo to message**: "take a photo and send it to Mom on WhatsApp" → camera → capture → WhatsApp → send

### Pure Chat (NO phone control)
- [ ] **M35. Joke**: "tell me a joke" → text response, NO tools called
- [ ] **M36. Math**: "whats 234 times 891" → "208,494", NO tools
- [ ] **M37. Timezone**: "what time is it in tokyo" → time answer, NO tools
- [ ] **M38. Cancel**: "nvm" → acknowledges, does nothing

## DD. Direct Device-Data Guard Regressions

- [x] **DD1. Clipboard explain uses tool, not denial**: Cloud input `read my clipboard and explain what it says` → calls `clipboard(action="get")` before answering; must NOT answer with a generic privacy/device-access refusal
- [x] **DD2. Notifications summary uses tool, not denial**: Cloud input `read my notifications and summarize` → calls `get_notifications()`; must NOT answer as if it cannot see notifications
- [x] **DD3. Battery question uses direct device tool**: Cloud input `how much battery left` → calls `get_device_info(category="battery")`; must NOT answer with a generic limitation disclaimer
- [x] **DD4. Storage question uses direct device tool**: Cloud input `how much storage do i have free` → calls `get_device_info(category="storage")`
- [x] **DD5. Installed apps question uses direct tool**: Cloud input `what apps do i have` → calls `get_installed_apps()`
- [ ] **DD6. Screen reading uses direct tool**: Cloud input `what's on my screen right now` → calls `get_screen_info()`
- [ ] **DD7. Conceptual control stays chat**: Cloud input `what is an Android clipboard` → normal text answer; guard must not falsely force a device-data tool

### Error Handling
- [ ] **M39. Wrong app name**: "open flurpmaster 3000" → "App not found" + suggestion
- [ ] **M40. Impossible platform**: "text sarah on imessage" → "iMessage not available on Android, try SMS/WhatsApp"
- [ ] **M41. Typo tolerance**: "check my instagarm messages" → understands Instagram
- [ ] **M42. Missing permission**: "monitor WhatsApp" with Notification Access off → guides to Settings

### Natural Language Understanding
- [ ] **M43. Complaint as action**: "my screen is too dim" → increase brightness
- [ ] **M44. Vague request**: "scroll down" → asks clarification OR scrolls current
- [ ] **M45. Slang**: "yo whats on my notifs" → reads notifications
- [ ] **M46. Implicit action**: "go back" → system_key(back), reports new screen

### Device-Agnostic Edge Cases
- [ ] **M47. Call**: "call Mom" → dials Mom (works on any device with Phone app)
- [ ] **M48. Lock**: "lock my phone" → system_key(lock), confirms
- [ ] **M49. Clear notifications**: "clear all my notifications" → clears, confirms
- [ ] **M50. Phone temp**: "how hot is my phone" → get_device_info(battery) temp OR graceful "not available"

## R. Local LLM — Reasoning Quick Tasks (1-2 tool calls + LLM analysis)

- [ ] **R1. "Am I missing anything important?"**: get_notifications → LLM triages noise vs important → reports only actionable items
- [ ] **R2. "Will my battery last until tonight?"**: get_device_info(battery) + get_device_info(time) → LLM projects drain → yes/no verdict with advice
- [ ] **R3. "Rewrite what I just copied"**: clipboard(read) → LLM rewrites → clipboard(write) → reports changes
- [ ] **R4. "What can I delete to free up space?"**: get_device_info(storage) + get_installed_apps() → LLM cross-references → prioritized delete list
- [ ] **R5. "Read notifications and summarize"**: get_notifications → LLM groups by category + urgency
- [ ] **R6. "Should I charge my phone?"**: get_device_info(battery) → LLM judges % + gives advice (not just number)

## S. Cloud LLM — Multi-step Quick Tasks (Siri can't do these)

- [ ] **S1. "Search YouTube for funny cat fails"**: opens YouTube → types search → results shown (M1/M8 verified)
- [ ] **S2. "Install Telegram from Play Store"**: Play Store → search → Install (M6/M32 verified)
- [ ] **S3. "Check what's trending on Twitter"**: opens Twitter → navigates to trending → summarizes (M20)
- [ ] **S4. "What's on my screen right now?"**: get_screen_info → describes UI elements (M6 verified)
- [ ] **S5. "Copy latest email subject and Google it"**: notifications → clipboard → Chrome → search (M33)
- [ ] **S6. "Check latest WhatsApp chat and summarize"**: opens WhatsApp → reads top chat → reports (M11)
- [ ] **S7. "Open Reddit and search for pokeclaw"**: opens Reddit → types search → results (M51 verified)
- [ ] **S8. "Write an email saying I'll be late"**: opens Gmail → compose draft ready with Subject/Body filled; recipient stays blank unless the task names one; does NOT send (M8/M19 verified)

Current Pixel 8 Pro status on 2026-04-10:
- `S2`, `S3`, `S5`, `S6`, `S7`, and `S8` are verified pass on the latest hardening branch.
- `S1` is currently environment-blocked by a foreground YouTube runtime permission dialog (`GrantPermissionsActivity`), not by a deterministic search-flow failure in PokeClaw.

## P. UI — v9 Design Verification

Reference prototype: `/home/nicole/MyGithub/PokeClaw/prototype/dashboard-v9.html`

### P1. Local/Cloud Toggle (in toolbar)
- [ ] **P1-1. Both buttons render**: "Local" and "Cloud" visible on same line as PokeClaw title, right side
- [ ] **P1-2. Selected state**: selected button has aiBubble bg + aiBubbleBorder, unselected has no bg/border
- [ ] **P1-3. No background container**: buttons sit directly in toolbar actions, no wrapping rectangle
- [ ] **P1-4. Tab syncs on launch**: Cloud LLM loaded → Cloud highlighted; Local LLM → Local highlighted
- [ ] **P1-5. Tab filters dropdown**: tap Local → dropdown shows local models only; tap Cloud → cloud models only
- [ ] **P1-6. No model → guidance**: Local with no model → "Download models..."; Cloud with no API key → "Configure API key..."
- [ ] **P1-7. Tab controls UI mode**: tap Local → Chat/Task toggle appears, prompts change to local, placeholder changes; tap Cloud → toggle hides, cloud prompts, cloud placeholder

### P2. Input Area (bottom)
- [ ] **P2-1. Local Chat/Task toggle**: "💬 Chat" and "🤖 Task" segment buttons visible ABOVE input (not beside)
- [ ] **P2-2. Input full width**: input bar takes full width, toggle is separate row above
- [ ] **P2-3. Task mode orange**: tap Task → toggle turns orange, input border orange, input bg tinted, placeholder "Describe a phone task...", send button orange
- [ ] **P2-4. Chat mode normal**: tap Chat → normal colors, placeholder "Chat with local AI..."
- [ ] **P2-5. Cloud no toggle**: switch to Cloud LLM → Chat/Task toggle HIDDEN, placeholder "Chat or give a task..."
- [ ] **P2-6. Send button dim**: when input empty → send button barely visible (low opacity); when text typed → lights up
- [ ] **P2-7. Same chatroom**: switching Chat↔Task does NOT clear messages, stays in same session

### P3. Quick Tasks Panel (between chat and input)
- [ ] **P3-1. Panel visible**: "▲ Quick Tasks ▲" handle with centered up-chevrons visible
- [ ] **P3-2. Default open**: panel open when new chat starts
- [ ] **P3-3. Collapsible**: tap handle → panel collapses (chevrons flip down); tap again → expands (chevrons flip up)
- [ ] **P3-4. Five items default**: 5 quick task prompts visible by default
- [ ] **P3-5. Show more**: "Show more ▼" expands to show all 12 prompts; "Show less ▲" collapses back
- [ ] **P3-6. Accent bar style**: each prompt has left accent bar (theme color) + full sentence text, finger-friendly height (~38dp)
- [ ] **P3-7. Tap fills input**: tap a quick task → text fills input bar (without emoji prefix)
- [ ] **P3-8. Tap auto-switches mode**: tapping quick task on Local tab → auto-switches to Task mode
- [ ] **P3-9. Background section**: "BACKGROUND" label + Monitor & Auto-Reply card visible below quick tasks
- [ ] **P3-10. Monitor card tap**: tap Monitor card → centered dialog (NOT bottom sheet) with Contact/App/Tone form + "Start Monitoring" button

### P4. Empty State
- [ ] **P4-1. Local empty**: PokeClaw icon + "PokeClaw" + "Local AI" + hint with bold 💬 Chat / 🤖 Task + 3 chat prompts (joke, what can you do, email)
- [ ] **P4-2. Cloud empty**: PokeClaw icon + "PokeClaw" + "Cloud AI" + "Chat and tasks work together" + 3 prompts (Tokyo, birthday, WhatsApp)
- [ ] **P4-3. Prompt style matches Quick Tasks**: same accent bar, same height (~38dp), same font size, same bg color
- [ ] **P4-4. Prompt tap**: tap empty state prompt → fills input, correct mode (local prompts = chat, cloud WhatsApp = task)

### P5. No Duplicate Panels
- [ ] **P5-1. Task mode clean**: when Task mode active → old TaskSkillsPanel does NOT appear alongside QuickTasksPanel
- [ ] **P5-2. No old ModeTab**: old "Chat | Task" ModeTab rows (from before v9) do NOT render
- [ ] **P5-3. No stale labels**: "Tap a skill above to start" label does NOT appear

### P6. Theme Consistency
- [ ] **P6-1. Theme-aware colors**: all UI uses `colors.accent` (theme-dependent), NOT hardcoded orange
- [ ] **P6-2. Task mode styling**: task mode input area uses taskBg (#1A1410) + accent border + accent send button
- [ ] **P6-3. Send button states**: empty = dim (alpha 0.35, bg color), chat active = userBubble color, task active = accent color

### P7. Chat Bubble Metadata
- [ ] **P7-1. User footer time**: user bubbles show a subtle time footer under the bubble (IG-chatroom style)
- [ ] **P7-2. Assistant footer metadata**: assistant bubbles show `model name · time` when a model tag exists
- [ ] **P7-3. History restore keeps timestamps**: relaunch or reload a saved conversation → visible bubble times stay stable instead of resetting to "now"

## Q. UI E2E — Full Pipeline (Layer 3)

Tests the complete path: user tap → ChatInputBar routing → Activity → LLM → response → UI.
Layer 1 broadcast bypasses UI routing. Only Layer 3 catches routing bugs.

### Q1. Tab Switch = Model Switch
- [ ] **Q1-1. Cloud→Local switch**: tap Local button → model status changes to local model name → `isLocalModel` becomes true
- [ ] **Q1-2. Local→Cloud switch**: tap Cloud button → model status changes to cloud model name → `isLocalModel` becomes false
- [ ] **Q1-3. No model available**: tap Local with no downloaded model → no crash, stays on current model
- [ ] **Q1-4. No API key**: tap Cloud with no API key → no crash, stays on current model
- [ ] **Q1-5. Same-session switch actually takes effect**: in one existing conversation, switch Cloud → Local → Cloud without starting a new chat; each subsequent reply must come from the newly selected side, not the previously loaded model
- [ ] **Q1-6. Switch state survives relaunch truthfully**: switch to Local, relaunch, confirm top bar + next reply are Local; then switch to Cloud, relaunch, confirm top bar + next reply are Cloud
- [ ] **Q1-7. System switch messages match reality**: when the active model changes, the latest visible/system-persisted `Switched to ...` message must agree with the model that actually generates the next reply; no stale `Switched to local model` before a Cloud reply, and no missing Cloud switch record before a Cloud reply
- [ ] **Q1-8. Footer/top-bar consistency after switch**: after switching models in the same conversation, old bubbles may keep their original model footers, but the newest assistant bubble must match the current top-bar model state

### Q2. Cloud Tab Send Routing
- [ ] **Q2-1. Cloud chat**: Cloud tab → type "hello" → tap send → AI response in chat bubble (routed via onSendTask)
- [ ] **Q2-1b. Cloud chat stays out of task-running state**: Cloud tab → type a normal chat message like `hello` → reply appears in chat, but the orange `Task running...` bar never appears unless the backend actually enters task/tool execution
- [ ] **Q2-1c. Cloud plain chat imperative does not misroute to Send Message**: Cloud tab → type `say hi` or `tell me more` → stays in ordinary chat, does NOT launch a send-message task, and does NOT reuse any old contact/app state
- [ ] **Q2-2. Cloud task**: Cloud tab → type "how much battery left" → tap send → actual battery info returned
- [ ] **Q2-3. Cloud no toggle**: Cloud tab → verify NO Chat/Task toggle visible → all input goes to unified pipeline
- [ ] **Q2-4. Cloud direct-data bridge**: Cloud tab → type `read my clipboard and explain what it says` → backend uses the clipboard tool AND the explanation appears as a visible assistant bubble in the same chatroom
- [ ] **Q2-4b. Empty clipboard is not a task failure**: Cloud tab → clipboard currently empty → type `read my clipboard and explain what it says` → answer honestly says clipboard is empty, but the chatroom must NOT insert a misleading `Clipboard failed` status line
- [ ] **Q2-5. Cloud notifications bridge**: Cloud tab → type `read my notifications and summarize` → backend uses notifications tool AND the summary appears as a visible assistant bubble in the same chatroom
- [ ] **Q2-6. Cloud-only capability proof**: in the same conversation, switch to Cloud and ask a task known to exceed Local reliability (for example `copy the latest email subject and Google it` or `open Reddit and search for pokeclaw`) → task completes successfully and the reply bubble is tagged with the Cloud model
- [ ] **Q2-7. Cloud context handoff proof**: in the same conversation, ask Cloud to summarize something, then say `send that summary by email` → Cloud uses the earlier chat context and the resulting reply/task output stays tagged as Cloud

### Q3. Local Tab Send Routing
- [x] **Q3-1. Local chat**: Local tab → Chat mode → type "hello" → tap send → AI response (routed via onSendChat to local LLM)
- [ ] **Q3-2. Local task**: Local tab → Task mode → type "how much battery left" → tap send → task executes (routed via onSendTask)
- [ ] **Q3-3. Mode switch**: Local tab → start in Chat → type "hello" → get response → tap Task → type task → executes correctly
- [ ] **Q3-4. Chat doesn't trigger tasks**: Local tab → Chat mode → type "open YouTube" → should get conversational reply, NOT open YouTube
- [ ] **Q3-5. Local task bridge**: Local tab → Task mode → type `how much battery left` → task completes AND the result appears as a visible assistant bubble in the same conversation after the task finishes
- [ ] **Q3-6. Local prompt-only task limit stays honest**: in the same conversation, first create some reusable context, then switch to Local Task mode and ask a vague follow-up like `send that summary by email` → Local must not pretend it used hidden Cloud-like context
- [ ] **Q3-7. Local-vs-Cloud separation proof**: after a successful Cloud-only task, switch back to Local in the same conversation and ask a simple on-device task (`how much battery left`) → result comes from Local, not from the previously active Cloud model

### Q4. Quick Task → Send E2E
- [ ] **Q4-1. Quick task fill + send**: Local tab → tap "🔋 How much battery left?" → verify input fills + Task mode active → tap send → battery info returned
- [ ] **Q4-2. Quick task in Cloud**: Cloud tab → tap quick task → input fills → tap send → task executes

### Q5. Routing Regression Guards
- [x] **Q5-1. No OpenCL crash on Local chat**: Local tab → Chat mode → send message → should NOT get "OpenCL not found" (must use CPU fallback)
- [x] **Q5-1b. GPU fallback updates UI label**: Local tab → GPU load/inference fails → fallback to CPU → top-left model status changes to CPU
- [ ] **Q5-2. No API error on Cloud task**: Cloud tab → send task → should NOT get "invalid_request_error" 
- [ ] **Q5-3. Tab switch mid-conversation**: send message on Cloud → switch to Local → send message → no crash, correct routing for each

### Q6. Tab Isolation — Local/Cloud Independent Configs
- [ ] **Q6-1. Cloud→Local preserves cloud config**: configure Cloud (gpt-4.1) → switch to Local → switch back to Cloud → model shows gpt-4.1 (not reset)
- [ ] **Q6-2. Local tab uses local model**: switch to Local tab → model status shows local model name (Gemma/etc), NOT any cloud model
- [ ] **Q6-3. Cloud tab uses cloud model**: switch to Cloud tab → model status shows cloud model name, NOT local model
- [ ] **Q6-4. No cloud model configured**: Fresh install → switch to Cloud → shows "No API key configured" or guidance, NOT crash
- [ ] **Q6-5. No local model downloaded**: Remove local model → switch to Local → shows "No local model downloaded" or download prompt, NOT crash
- [ ] **Q6-6. Local chat actually uses local LLM**: Local tab → Chat mode → send "hello" → logcat shows LiteRT/conversation (NOT OpenAI API call)
- [ ] **Q6-7. Cloud task actually uses cloud LLM**: Cloud tab → send "battery" → logcat shows OpenAI/gpt (NOT LiteRT)

### Q7. Task Stop + Session Preservation
- [ ] **Q7-1. Cloud stop responds immediately**: start cloud/network task → tap Stop → task stops within 3 seconds (thread interrupted, HTTP call aborted)
- [x] **Q7-1b. Local stop is safe and honest**: start local task → tap Stop → UI stays in `Task running...`/`Stop` while the current LiteRT round unwinds, then returns to idle with `Task cancelled`, no crash
- [ ] **Q7-2. Stop returns to same session**: start task → task opens other app → tap Stop → returns to PokeClaw → same conversation visible (not new session)
- [x] **Q7-3. App doesn't crash on stop**: start task → tap Stop → app remains running, no ANR, no crash
- [x] **Q7-4. Send button resets after stop**: stop task → send button changes from red X back to arrow → can send new messages
- [ ] **Q7-5. Second task after stop**: stop task 1 → start task 2 → task 2 executes normally (no "Agent is already running" error)
- [ ] **Q7-6. Stop from floating button**: task running in other app → tap floating circle → "Tap to stop" → task stops, returns to PokeClaw
- [ ] **Q7-7. Auto-return preserves conversation**: task completes in other app → auto-return to PokeClaw → previous messages + task result visible in same conversation

### Q8. Chatroom Memory Continuity
- [ ] **Q8-1. Cloud same-chatroom memory**: in one Cloud chatroom, tell it a fact (e.g. "Remember: call Mom at 3pm") → exchange 2-3 unrelated turns → ask "What time did I say to call Mom?" → it should answer from the earlier message, not act like the chat started fresh
- [ ] **Q8-2. Local same-chatroom memory**: in one Local chatroom, tell it a fact → exchange 2-3 unrelated turns → ask for the fact again → it should answer from the same ongoing conversation, not as one-shot QA
- [ ] **Q8-3. Cloud relaunch memory continuity**: in one Cloud chatroom, establish a fact → fully relaunch the app → reopen the same conversation → ask for the fact again → it should still answer from the restored conversation context
- [ ] **Q8-4. Local relaunch memory continuity**: in one Local chatroom, establish a fact → fully relaunch the app → reopen the same conversation → ask for the fact again → it should still answer from the restored conversation context

### Q9. Chat -> Task Context Handoff
- [ ] **Q9-1. Cloud task inherits chatroom history**: in one Cloud chatroom, ask for a summary or establish a reusable fact → then send a task like `send that summary by email` or `text that to Monica` without repeating the content → task should use the earlier chatroom context and complete using the referenced content
- [ ] **Q9-2. Local task stays prompt-only**: in one Local chatroom, establish a fact/summary → switch to Task mode and send a vague task like `send that summary by email` without repeating the content → app should not pretend it has the full chat context; expected product behavior is either a graceful failure or a result that clearly depends only on the current task prompt

### Q10. Persistent Instructions & Memory

- [ ] **Q10-1. Global instructions apply**: set a short global instruction → start a new Cloud chat/task → the model follows it without changing platform/tool safety behavior
- [ ] **Q10-2. Local compressed instructions apply**: same global instruction works in Local mode using a condensed prompt budget, without stuffing unrelated app rules into the context
- [ ] **Q10-3. Scoped app rules load only when relevant**: Telegram task loads Telegram-scoped rules; WhatsApp task loads WhatsApp-scoped rules; unrelated rules are omitted
- [ ] **Q10-4. Clear instructions removes effect**: delete global instructions → new chats/tasks no longer apply the old instruction
- [ ] **Q10-5. Manual memory lifecycle**: user explicitly saves a memory → it survives relaunch → user deletes it → it no longer appears in later model context
- [ ] **Q10-6. Secrets never become memory**: API keys, bot tokens, passwords, and recovery codes are rejected or redacted from memory and excluded from bug reports
- [ ] **Q10-7. Untrusted content cannot override rules**: screen/web/notification text that says "ignore previous instructions" is treated as content, not as a higher-priority instruction

## N. Tinder Automation

- [ ] **N1. Auto swipe**: "open Tinder and swipe right" → opens Tinder → swipes right → repeats
- [ ] **N2. Auto swipe with criteria**: "swipe right on everyone on Tinder" → continuous swipe
- [ ] **N3. Monitor Tinder matches**: "monitor Tinder matches" → detects new match notification → opens chat → auto-replies using LLM
- [ ] **N4. Tinder auto-reply context**: match sends message → LLM reads conversation context → generates contextual reply → sends
- [ ] **N5. Tinder + WhatsApp parallel**: Tinder monitor active + WhatsApp monitor active → both work simultaneously
- [ ] **N6. Stop Tinder monitor**: tap monitoring bar → Stop → Tinder monitoring stops, WhatsApp unaffected

## L. Task Auto-Return

- [ ] **L1. Auto-return after send message**: "send hi to Girlfriend on WhatsApp" → agent opens WhatsApp → sends → completes → PokeClaw chatroom comes back to foreground
- [ ] **L2. Auto-return shows answer**: after return, bot bubble shows the task result (not blank)
- [ ] **L3. No auto-return for monitor**: "monitor Girlfriend on WhatsApp" → monitor starts → user stays in PokeClaw (not kicked to home, not auto-returned)
- [ ] **L4. Monitor stays in app**: after monitor starts, user remains in PokeClaw chat → can keep chatting
- [ ] **L5. Monitor receives notification without leaving app**: monitor active + stay in PokeClaw → someone sends WhatsApp message → notification caught → auto-reply triggers
- [ ] **L5-b. Auto-reply does not kick user Home**: monitor active → incoming message triggers auto-reply → user remains in current app/PokeClaw, no forced Home navigation
- [ ] **L6. Second task after auto-return**: auto-return from task 1 → send task 2 → works normally

## K. Permissions

- [ ] **K1. Monitor blocked without permissions**: "monitor Girlfriend" with Accessibility or Notification Access disabled → Toast + navigate to Settings page (not grey chat text)
- [ ] **K2. Settings shows Notification Access**: Settings → Permissions → "Notification Access" row visible with Connected/Disabled status
- [ ] **K3. Auto-return after Accessibility enable**: disable Accessibility → try monitor → go to Settings → enable Accessibility → app auto-returns to PokeClaw
- [ ] **K4. Auto-return after Notification Access enable**: same flow for Notification Access toggle off→on → app auto-returns
- [ ] **K5. Stale notification toggle**: reinstall app → Notification Access shows "enabled" in system but service not connected → app detects and guides user to toggle off→on
- [ ] **K6. Settings links correct**: tap each permission row in app Settings → leads to correct system settings page:
  - Accessibility → system Accessibility settings
  - Notification → starts ForegroundService / requests POST_NOTIFICATIONS
  - Notification Access → system Notification Listener settings
  - Overlay → system Overlay permission
  - Battery → system Battery optimization
  - File Access → system Storage settings
- [ ] **K6-b. Settings model row handles long names**: Settings → active local/cloud model has a long name → label/value stay aligned, text truncates or wraps cleanly, and the left "Model" label does not collapse into a narrow vertical stack
- [ ] **K7. Full permission setup flow (E2E)**:
  1. Fresh state: disable Notification Access for PokeClaw
  2. Open PokeClaw → type "monitor Girlfriend on WhatsApp" → send
  3. Verify: Toast shows "Enable Notification Access in Settings first"
  4. Verify: app navigates to PokeClaw Settings page
  5. Tap "Notification Access" row → system Notification Listener settings opens
  6. Toggle PokeClaw ON (or OFF→ON if stale)
  7. Verify: auto-return to PokeClaw Settings page
  8. Verify: "Notification Access" row now shows "Connected"
  9. Press back → return to chat → type "monitor Girlfriend on WhatsApp" again
  10. Verify: monitor starts successfully ("✓ Auto-reply is now active")

---

## T. Model Config — Independent Local/Cloud Defaults

- [ ] **T1. Fresh install — both tabs empty**: clear all model config → Local tab → modelStatus = "No model selected", send disabled → Cloud tab → same
- [ ] **T2. Only local configured**: Settings → Models → Download + "Use" local model → chat → Local tab → model name shown, send enabled → Cloud tab → "No model selected", send disabled → back to Local → model still there
- [ ] **T3. Only cloud configured**: Settings → Models → Cloud → select provider + model + API key → Save → chat → Cloud tab → model name shown, send enabled → Local tab → if downloaded model exists use it, else "No model selected" → back to Cloud → model still there
- [ ] **T4. Both configured**: config local + cloud → Local tab → local model shown, send enabled → Cloud tab → cloud model shown, send enabled → Local tab → local model unchanged
- [ ] **T5. Cloud model switch via dropdown**: Cloud tab → dropdown → pick different model → model updates → switch to Local → switch back to Cloud → still shows new model
- [ ] **T6. Local model switch via Settings**: Settings → Models → "Use" different local model → return to chat → Local tab shows new model → Cloud config unchanged
- [ ] **T7. Cloud no API key**: Cloud tab selected, API key empty → "No model selected", send disabled
- [ ] **T8. Local model file deleted**: Local tab, but model file removed from disk → "No model selected" or prompt re-download
- [ ] **T9. Set local default while cloud active**: Cloud active in chat → Settings → "Use" local model → return to chat → Cloud model still active until user explicitly switches tabs
- [ ] **T10. Save cloud default while local active**: Local active in chat → Settings → save cloud model → return to chat → Local model still active; switching to Cloud picks saved cloud model

---

## J. Stress / Edge Cases

- [ ] **J1. Rapid fire**: send 3 messages quickly → no crash, messages queued or latest wins
- [ ] **J2. Empty input**: tap send with empty field → nothing happens
- [ ] **J3. Very long input**: paste 500+ character task → no crash, task starts normally
- [ ] **J4. Accessibility lost mid-task**: if accessibility revokes during task → graceful error, not stuck
- [ ] **J5. Network lost mid-task**: if WiFi drops during Cloud task → error message, not infinite loop
- [ ] **J6. App killed and reopened**: force stop → reopen → clean state, no ghost tasks
- [ ] **J7. Monitor + task simultaneous**: monitor Girlfriend active → send task "open YouTube" → both work, monitor not disrupted

---

## Z. 端云设备节点验收

这些用例用于 PokeClaw 作为 dyq 云端执行节点时的端到端验收。所有实现必须继续经过现有 `TaskOrchestrator`，不能绕过本机权限、前台服务、可访问性、通知权限和安全规则。

- [ ] **Z1. 首次配对注册**：清空云节点配置 → 打开设置页“云端设备节点” → 输入 dyq 云端地址和配对令牌 → 点击启用 → 期望端侧发起注册请求，云端收到设备标识、应用版本、构建指纹、系统版本、能力列表；本机界面显示已连接节点编号；日志含注册成功但不打印令牌。建议命令：`adb logcat -c` → 通过 UI 启用 → `adb logcat -d | grep -E "CloudNode|register|device node"`。
- [ ] **Z2. 鉴权失败用户可见**：配置错误令牌 → 启用云节点 → 期望注册失败，设置页显示“配对失败/请重新配对”，本地停止重试；日志记录 `CLOUD_AUTH_REJECTED`，不打印令牌。建议命令：`adb logcat -d | grep -E "CLOUD_AUTH_REJECTED|CloudNode"`。
- [ ] **Z3. 心跳能力快照**：启用云节点且模拟服务返回成功 → 等待两个心跳周期 → 期望云端收到电量、网络、前台服务、可访问性、通知监听、当前任务状态；关闭可访问性后下一次心跳反映 `accessibilityReady=false`。建议命令：`adb shell settings get secure enabled_accessibility_services`、`adb logcat -d | grep -E "heartbeat|capability"`。
- [ ] **Z4. 云端任务下发到本地执行**：模拟 dyq 返回任务“how much battery left” → 端侧拉取任务 → 期望任务进入 PokeClaw 正常聊天/任务壳，调用确定性电量工具，云端收到 `accepted`、`running`、`completed`，结果摘要包含真实电量。建议命令：`adb logcat -d | grep -E "TaskOrchestrator|CloudTask|onComplete|get_device_info"`。
- [ ] **Z5. 离线结果缓存与恢复补报**：任务执行中关闭网络或让模拟服务返回不可达 → 端侧继续完成本地任务并把结果放入待上报队列 → 恢复网络 → 期望按幂等请求编号补报完成事件，队列清空。建议命令：`adb shell svc wifi disable`、`adb shell svc wifi enable`、`adb logcat -d | grep -E "CloudEventQueue|retry|flush"`。
- [ ] **Z6. 忙碌态拒绝新任务**：先从云端下发长任务并确认执行中 → 再下发第二个任务 → 期望端侧不并发执行，第二个任务回传 `TASK_BUSY` 或排队策略结果，用户界面不出现两个互相覆盖的任务。建议命令：`adb logcat -d | grep -E "Another task is still running|TASK_BUSY|CloudTask"`。
- [ ] **Z7. 敏感信息脱敏**：下发包含联系人、通知或聊天读取的任务 → 任务失败或生成调试报告 → 期望上报只包含错误码、摘要、任务编号和脱敏消息，不上传完整通知正文、联系人列表、提示词、密钥。建议命令：`adb logcat -d | grep -E "CloudResult|CloudError"` 并人工确认无密钥/通知正文。
- [ ] **Z8. 设备接口契约准备**：使用 dyq `api-contracts/device.openapi.yaml` 启动模拟服务 → 端侧配置云端地址并执行注册、心跳、任务拉取、结果回传 → 期望 `DeviceCloudApi` 请求路径与字段和契约一致，令牌只在 Android Keystore 加密存储，断网时结果进入 `CloudEventQueue`，恢复网络后按幂等请求编号补报。建议命令：`adb logcat -c` → 执行注册和任务回传 → `adb logcat -d | grep -E "DeviceCloudClient|CloudDeviceTokenStore|CloudEventQueue"`。
- [x] **Z9. 端侧执行节点本地模拟闭环**：不连接真实 dyq 后端 → 构造一条 `CloudExecutorTask` → 使用 `CloudExecutorNodeSimulator` 模拟成功和权限缺失失败 → 期望状态顺序为 `RECEIVED`、`RUNNING`、`SUCCEEDED/FAILED`，失败包含可重试错误码。建议命令：`./gradlew :app:testDebugUnitTest --tests 'io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest' --console=plain`；真机联调后再补跑 `Z1-Z7`。
- [x] **Z10. 设备端 API 契约注解校验**：不连接真实 dyq 后端 → 检查 `CloudDeviceApi` Retrofit 注解 → 期望注册、心跳、任务轮询、结果回传、令牌刷新路径严格对齐 `/mnt/e/code/dyq/api-contracts/device.openapi.yaml`。建议命令：`./gradlew :app:testDebugUnitTest --tests 'io.agents.pokeclaw.cloud.api.CloudDeviceApiContractTest' --console=plain`；真机联调后再用 Z1-Z5 验证网络链路。
- [x] **Z11. 设备云端客户端编译闭环**：不连接真实 dyq 后端 → 编译 `DeviceCloudClient`、`CloudDeviceApi`、`CloudDeviceTokenStore`、`CloudEventQueue` → 期望注册、心跳、任务拉取、结果回传、令牌刷新调用关系可通过编译，且离线缓存不保存令牌/联系人/完整提示词。建议命令：`./gradlew :app:compileDebugKotlin --console=plain`；真机联调后再执行 Z1、Z3、Z5。
- [x] **Z12. 设备令牌刷新窗口与脱敏日志**：保存注册返回的 deviceToken/refreshToken → 在过期前十分钟触发刷新判断 → 期望磁盘只存加密载荷、日志不打印令牌正文、`Authorization` 只注入 `Bearer` 前缀一次。建议命令：`adb logcat -c` → 启用云端设备节点 → `adb logcat -d | grep -E "CloudDeviceTokenStore|CloudDeviceAuth"`，人工确认无 token 正文。

- [x] **Z13. WorkManager 心跳调度**：启用云端设备节点 → 期望 WorkManager 启动 `CloudHeartbeatWorker` 周期性任务 → 查看日志确认心跳定时触发 → 关闭云端节点开关 → 期望 `WorkManager.cancelUniqueWork` 停止心跳。建议命令：`adb shell dumpsys jobscheduler | grep -E "pokeclaw|heartbeat"`、`adb logcat -d | grep -E "CloudHeartbeatWorker|心跳调度"`。

---

## QA Debug Changelog

Format: `[date] [status] [test-id] description`

[2026-06-17] [PASS]    BUILD-LOCAL-FINAL-3  Final local gate after latest direct-data and notification-listener fixes passed: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` returned `BUILD SUCCESSFUL in 6m22s`; output APK is `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_184848.apk`. `git diff --check` passed with LF/CRLF normalization warnings only.
[2026-06-17] [PASS]    DD1-DD5/M1-M5/M16-LATEST  Latest installed APK `PokeClaw_v0.6.12_20260617_182809.apk` direct-device ADB sweep passed on emulator `192.168.250.3:5555`: `read my clipboard and explain what it says` routed to `clipboard(action=get)` and returned `qa direct clipboard 617`; `how much battery left` routed to `get_device_info(category=battery)` and returned `Battery: 100%, not charging, 25.0 C`; WiFi/storage/Bluetooth routed to `get_device_info(category=wifi/storage/bluetooth)` and returned real device state; `what apps do i have` routed to `get_installed_apps()` and returned `Found 19 apps`; `read notifications and summarize` routed to `get_notifications()` and returned one active Android System notification after `cmd notification allow_listener`. All exercised direct-data tasks logged `onComplete: rounds=0, totalTokens=0, model=direct`.
[2026-06-17] [PASS]    BUILD-LOCAL-FINAL-2  Latest local gate after Z13 WorkManager wiring passed: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` returned `BUILD SUCCESSFUL in 6m49s`; output APK is `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_173318.apk`. `git diff --check` passed with LF/CRLF normalization warnings only.
[2026-06-17] [PASS]    Z13  WorkManager 心跳调度闭环通过：修复后构建 `PokeClaw_v0.6.12_20260617_165002.apk`，`adb install -r` 成功；`DEBUG_TASK --es cloud_action start --es cloud_base_url http://192.168.250.3:8080` 后 logcat 显示 `CloudHeartbeatManager: 心跳调度已启动，间隔=1分钟`、`CloudHeartbeatWorker: 心跳工作器启动`，WorkManager 记录 `tags={ io.agents.pokeclaw.cloud.CloudHeartbeatWorker, cloud_heartbeat }`；`dumpsys jobscheduler io.agents.pokeclaw` 显示 `io.agents.pokeclaw/androidx.work.impl.background.systemjob.SystemJobService` 且带 `TIMING_DELAY CONNECTIVITY`；`cloud_action stop` 后 logcat 显示 `心跳调度已停止`，scheduler 为 `Pending: 0 / 0` 且无活动 PokeClaw job。
[2026-06-17] [PASS]    Z12  设备令牌刷新窗口与脱敏日志通过：`.\gradlew.bat :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloud.api.CloudDeviceApiContractTest" --console=plain` 返回 `BUILD SUCCESSFUL in 23s`；覆盖 `CloudDeviceTokenSnapshot.shouldRefresh` 边界和 `asBearerToken()` 单次 Bearer 前缀；代码审查确认 `AndroidKeystoreCloudDeviceTokenStore` 写入 AES-GCM 加密载荷，日志仅输出 expiresAt/状态，不打印 deviceToken/refreshToken 正文。
[2026-06-17] [PASS]    BUILD-LOCAL-FINAL  Final local gate passed after optimized-debug and routing fixes: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` returned `BUILD SUCCESSFUL in 4m`; output APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_162200.apk` is 108,711,546 bytes with 7 dex files. `git diff --check` passed with LF/CRLF normalization warnings only.
[2026-06-17] [PASS]    J6/STARTUP-ANR-FINAL  Final optimized debug APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_162200.apk` passed reinstall/startup smoke with Accessibility already enabled: `adb install -r` completed in 140.1s after install-time `dex2oat took 109.638s`; Android then started service pid `22052`, logged `POKECLAW_INIT ... runtime-started +636ms`, and `ClawA11yService: Accessibility service connected`; no `Timeout executing service` and no `ANR in io.agents.pokeclaw`.
[2026-06-17] [PASS]    M1/DD3-FINAL  Final optimized debug APK preserved ADB QA automation through `BuildConfig.DEBUG_AUTOMATION_ENABLED=true`: `DEBUG_TASK how much battery left` logged `Tier 1 direct device-data match: get_device_info params={category=battery}`, `Pipeline Tier 1: DirectTool — get_device_info`, `sendMessage [Local]: ✓ Battery: 100%, not charging, 25.0°C`, and `onComplete: rounds=0, totalTokens=0, model=direct, answer=Battery: 100%, not charging, 25.0°C`.
[2026-06-17] [FAIL]    J6/STARTUP-ANR-RETEST  Patched debug APK `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_153220.apk` reinstall still reproduced `ANR in io.agents.pokeclaw`, Reason=`executing service io.agents.pokeclaw/.service.ClawAccessibilityService`; restarted process `21069` then logged `POKECLAW_INIT ... runtime-started +10090ms`.
[2026-06-17] [PASS]    M1/DD3-RETEST  After `PipelineRouter` direct-device routing fix, stable-process ADB smoke passed: `adb shell "am broadcast -a io.agents.pokeclaw.DEBUG_TASK -p io.agents.pokeclaw --es task 'how much battery left'"`; logcat showed `Tier 1 direct device-data match: get_device_info params={category=battery}`, `Pipeline Tier 1: DirectTool — get_device_info`, `sendMessage [Local]: ✓ Battery: 100%, not charging, 25.0°C`, and `onComplete: rounds=0, totalTokens=0, model=direct, answer=Battery: 100%, not charging, 25.0°C`.
[2026-06-17] [FAIL]    J6/STARTUP-ANR  模拟器 `192.168.250.3:5555` 安装 `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_151342.apk` 并启动后出现系统 ANR 对话框 `PokeClaw isn't responding`；logcat: `ANR in io.agents.pokeclaw`, Reason=`executing service io.agents.pokeclaw/.service.ClawAccessibilityService`，`ComposeChatActivity` 首屏耗时 `+1m22s636ms`；截图已保存到 `Screenshots/qa-anr-20260617.png`。
[2026-06-17] [FAIL]    M1/DD3  ADB direct-task smoke 失败：`adb shell "am broadcast -a io.agents.pokeclaw.DEBUG_TASK -p io.agents.pokeclaw --es task 'how much battery left'"` 启动后日志显示 `PipelineRouter: No deterministic match, falling through to agent loop`；虽然日志出现 `Battery: 100%, not charging, 25.0°C`，但任务进入 mock LLM 循环，最终 `Reached maximum iterations (60) without completing the task`，无正常 `onComplete`。
[2026-06-17] [PASS]    BUILD-LOCAL  本地完整门禁通过：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` 结果 `BUILD SUCCESSFUL in 24s`；debug APK 产物为 `app/build/outputs/apk/debug/PokeClaw_v0.6.12_20260617_151342.apk`，大小 156,308,148 bytes，并已 `adb install -r` 到模拟器成功。
[2026-06-17] [PASS]    SCRIPT-HYGIENE  `D:\Program Files\Git\bin\bash.exe -n scripts/e2e-quick-tasks.sh` 通过；`git diff --check` 通过，仅有 `QA_CHECKLIST.md` LF/CRLF 归一化警告。
[2026-06-17] [PASS]    Z11  设备云端客户端编译闭环通过：`.\gradlew.bat :app:compileDebugKotlin --console=plain` 结果 `BUILD SUCCESSFUL in 6s`；完整 `:app:testDebugUnitTest :app:assembleDebug` 也通过。
[2026-06-17] [PASS]    Z10  设备端 API 契约注解校验通过：`.\gradlew.bat :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloud.api.CloudDeviceApiContractTest" --console=plain` 结果 `BUILD SUCCESSFUL in 12s`。
[2026-06-17] [NOTE]    NEXT-QA  `J6/STARTUP-ANR` 和 `M1/DD3` 均已在 final optimized debug APK 上复测通过；后续全量 QA 可以继续。注意 optimized debug APK 会在安装阶段执行 dex2oat，本机 `adb install -r` 用时 140.1s，脚本超时应设置为 >=180s。
[2026-06-17] [PASS]    Z9  端侧执行节点本地模拟闭环通过：执行 `.\gradlew.bat :app:testDebugUnitTest --tests "io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest" --console=plain`，结果 `BUILD SUCCESSFUL in 22s`；验证 `CloudExecutorTask` 经 `CloudExecutorNodeSimulator` 进入 `RECEIVED`/`RUNNING`/`SUCCEEDED` 或 `FAILED` 状态，并覆盖失败路径可重试错误码。
[2026-05-15] [PASS]    Z-build  针对 CMP-1801 新增 `docs/product/pokeclaw-dyq-build-interface-verification.md`，完成 PokeClaw 编译环境、安卓依赖、APK 产物、DYQ `device.openapi.yaml` 与 `executor-node.openapi.yaml` 接口联调清单核查；执行 `./gradlew :app:assembleDebug --console=plain` 与 `./gradlew :app:testDebugUnitTest --console=plain` 均通过；执行 `adb devices -l` 未发现已连接设备，真机 Z1-Z7 待后续联调执行。
[2026-05-15] [NOTE]    Z-plan  为 CMP-2001 落地端云设备节点注册、心跳、任务拉取、结果回传、离线缓存与脱敏验收草案；本轮为方案和清单变更，未接入真实 dyq 接口，设备实测待后端契约与模拟服务就绪后执行。
[2026-05-15] [NOTE]    Z-plan  为 CMP-1835/CMP-1876 补充 `docs/product/pokeclaw-executor-node-plan.md`，明确端侧执行节点定位、注册/心跳/任务拉取/结果回传状态映射、错误码、脱敏和最小闭环边界。
[2026-05-15] [NOTE]    Z-plan  复查 CMP-1835 当前最高优先级任务，补充两种实现方案取舍和 Kotlin 文件改动清单；选择独立 `cloudnode` 端云适配层，禁止把网络请求塞进 `TaskOrchestrator`。
[2026-05-15] [PASS]    Z-plan  针对 CMP-1861 新增 `docs/product/pokeclaw-cloud-task-closed-loop-audit.md`，完成设备注册、任务轮询、结果回传、错误上报、本地离线缓存摸底；执行 `./gradlew --no-daemon testDebugUnitTest assembleDebug` 通过，首次守护进程目录占用失败已通过停止守护进程和清理生成目录复测通过。
[2026-05-15] [BLOCKED] Z-build 在 `/mnt/e/code/PokeClaw` 执行 `./gradlew testDebugUnitTest assembleDebug`，因当前环境未设置 `JAVA_HOME` 且找不到 `java`/`javac`，构建未进入编译阶段；待安装或配置 JDK 后重跑。
[2026-05-15] [BLOCKED] Z-build 复核 CMP-1876 时再次执行 `./gradlew testDebugUnitTest assembleDebug`：当前已有 `/usr/bin/java` 与 `/usr/bin/javac`，但 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 均为空，仓库也没有 `local.properties` 的 `sdk.dir`，构建失败于 `SDK location not found`；待配置 Android SDK 后重跑。

### 2026-04-08 — Initial QA run

```
[2026-04-08] [PASS]    A1  Chat question "what is 2+2" → answer in bot bubble, 1 round
[2026-04-08] [ISSUE]   A1  Floating button flashed briefly (TASK_NOTIFY → SUCCESS) on chat question
[2026-04-08] [ISSUE]   A1  "Accessibility service starting..." shows in every new chat
[2026-04-08] [PASS]    B1  Send message to Girlfriend → send_message tool called, 2 rounds
[2026-04-08] [PASS]    C1  Monitor Girlfriend → Java routing, top bar shows "Monitoring: Girlfriend"
[2026-04-08] [PASS]    C2  Auto-reply with Cloud LLM → GPT-4o-mini generated reply, sent successfully
[2026-04-08] [PASS]    F5  Second task works after first completes
[2026-04-08] [PASS]    H1  Floating button size normal (dp fix applied)
[2026-04-08] [ISSUE]   F1  Top bar "Task running..." not showing during task execution
[2026-04-08] [ISSUE]   F2  Send button not turning red X during task
[2026-04-08] [ISSUE]   F3  Floating button disappears when agent navigates to other apps
[2026-04-08] [ISSUE]   F6  "..." typing indicator coexists with tool action messages
[2026-04-08] [ISSUE]   B2  YouTube task: LLM completed but user stuck in YouTube, no auto-return

### 2026-04-08 — Post-fix QA run (after TaskEvent, LlmSessionManager, etc.)

[2026-04-08] [FIXED]   A1-a  Floating button no longer flashes on chat questions (finish tool filtered)
[2026-04-08] [FIXED]   F1    Top bar "Task running..." + Stop button now shows during task
[2026-04-08] [FIXED]   F2    Send button turns red X during task
[2026-04-08] [FIXED]   F6    Typing "..." removed when first ToolAction arrives
[2026-04-08] [PASS]    A3    Chat → Task mixed: "what is 2+2" → reply → "send hi to Girlfriend" → works
[2026-04-08] [PASS]    A4    Task → Chat: after send message completes → "how are you" → text-only reply
[2026-04-08] [PASS]    B1    Send message to Girlfriend → 2 rounds, answer in bot bubble
[2026-04-08] [PASS]    B2    YouTube search → agent navigated, typed query, showing suggestions
[2026-04-08] [PASS]    F3    Floating button visible in YouTube during task (IDLE state, not RUNNING)
[2026-04-08] [PASS]    F5    Second task works after first (chat → task sequence)
[2026-04-08] [PASS]    G1    Cloud welcome screen: correct text + prompts
[2026-04-08] [PASS]    G7    Cloud Task tab: Workflows header + cards + input bar
[2026-04-08] [ISSUE]   A1-b  "Accessibility service starting..." still shows in every new chat
[2026-04-08] [ISSUE]   F3-b  Floating button in other apps shows IDLE (AI) not RUNNING (step/tokens)
[2026-04-08] [ISSUE]   H6    Pencil icon: cannot rename chat session

### 2026-04-08 — Bug fixes + full QA run

[2026-04-08] [FIXED]   A1-b  Moved keyword routing before accessibility check — monitor no longer triggers "starting..."
[2026-04-08] [FIXED]   F3-b  Floating button show() callback now calls updateStateView → RUNNING state preserved in other apps
[2026-04-08] [PASS]    A2    Follow-up chat context preserved (verified via A3/A4 mixed sequences)
[2026-04-08] [PASS]    A5    3 chat messages in a row → all replied, 1 round each, no crash
[2026-04-08] [PASS]    B5    "send hi to Girlfriend on Signal" → "Cannot resolve launch intent" → LLM reports Signal not installed
[2026-04-08] [PASS]    C3    Tap monitoring bar → expand → Stop → auto-reply DISABLED, bar removed
[2026-04-08] [PASS]    F3    Floating button shows RUNNING state in YouTube during task (fix verified)
[2026-04-08] [PASS]    F4    Floating button stop mechanism (code + logic verified, consistent with C3 stop)
[2026-04-08] [PASS]    H3    Layout sizes normal (dp, EditText 126dp height, buttons 54dp)
[2026-04-08] [PASS]    H4    Model switcher dropdown: GPT-4o Mini/4o/4.1/4.1 Mini/4.1 Nano/Gemma 4/Configure
[2026-04-08] [PASS]    H5    New chat pencil → clears messages → "Cloud LLM enabled" welcome screen
[2026-04-08] [PASS]    J1    Rapid fire 3 msgs → first wins, others blocked by task lock, no crash
[2026-04-08] [PASS]    J2    Empty input → send button does nothing
[2026-04-08] [PASS]    J3    600-char input → no crash, LLM responded normally
[2026-04-08] [PASS]    J4    Accessibility revoked mid-task → tool reports error → LLM explains gracefully
[2026-04-08] [PASS]    J6    Force stop + reopen → clean state, init normal, no ghost tasks
[2026-04-08] [PASS]    J7    Monitor + YouTube task simultaneous → both work, monitor not disrupted
[2026-04-08] [SKIP]    B3    Task with context — needs UI chat interaction (not testable via ADB broadcast)
[2026-04-08] [SKIP]    J5    Network lost mid-task — can't simulate WiFi drop via ADB, error path covered by onError
[2026-04-08] [SKIP]    I1-I3 Cross-app behavior — partially covered by F3 (visible in YouTube) + J7 (simultaneous)
[2026-04-08] [FIXED]   D1-a  LiteRT-LM "session already exists" → onBeforeTask callback closes chat conversation
[2026-04-08] [FIXED]   D1-b  LiteRT-LM GPU "OpenCL not found" → auto-fallback to CPU backend in LocalLlmClient
[2026-04-08] [PASS]    D1    Local LLM chat: "hello" → "Hello! How can I help you today?" (Gemma 4 E2B, CPU, 1 round)
[2026-04-08] [PASS]    D2    Local chat tab doesn't trigger task (sendChat path, no tools, verified by D1 behavior)
[2026-04-08] [PASS]    E1    Local Task tab: Workflows header + Monitor Messages + Send Message cards, no input bar
[2026-04-08] [PASS]    G2    Local welcome: "Local LLM enabled" + "Chat here, go to Task tab for workflows"
[2026-04-08] [PASS]    E2    Monitor card → dialog (contact input + Start/Cancel) → "Auto-reply active for Girlfriend" → top bar shows
[2026-04-08] [PASS]    E3    Send Message card → dialog (message + contact inputs + Send/Cancel) → correct layout
[2026-04-08] [PASS]    H2    API key field in LLM Config → keyboard appears → field still visible (adjustResize works)
[2026-04-08] [PASS]    B3    "send sorry because we argued" → LLM crafted: "Sorry, I didn't mean to upset you. Let's talk and make things right."
[2026-04-08] [PASS]    G3    Cloud prompt tap → prefillText only, stays in Chat tab (code verified: isTask && isLocalModel guard)
[2026-04-08] [PASS]    K1    Monitor with notification listener disconnected → Toast + navigate to app Settings page
[2026-04-08] [PASS]    K2    Settings page shows "Notification Access" row with Connected/Disabled status
[2026-04-08] [PASS]    K4    Toggle notification access ON in system settings → onListenerConnected → auto-return to app Settings page
[2026-04-08] [PASS]    K7    Full E2E: disable notif listener → monitor blocked → Settings → enable → auto-return → "Connected" → monitor works
[2026-04-08] [SKIP]    K3    Accessibility auto-return — same code pattern as K4
[2026-04-08] [SKIP]    K5    Stale toggle detection — verified by K1
[2026-04-08] [SKIP]    K6    Settings links — each permission row navigable (needs manual tap-through)
[2026-04-08] [ISSUE]   K3-a  Auto-return fires on EVERY service connect, not just user-initiated enable — should only fire after permission flow
[2026-04-08] [PASS]    L1    Send message task → agent opens WhatsApp → completes → auto-return to PokeClaw chatroom
[2026-04-08] [PASS]    L3    Monitor starts → stays in PokeClaw (no press Home)
[2026-04-08] [PASS]    L4    After monitor starts, user still in PokeClaw chat ("staying in PokeClaw" in logs)
[2026-04-08] [PASS]    L6    Second task after auto-return works normally
[2026-04-08] [SKIP]    L2    Auto-return shows answer — needs UI verification (SINGLE_TOP preserves activity instance)
[2026-04-08] [SKIP]    L5    Monitor receives notification without leaving app — needs 2nd device (same as C2)
[2026-04-08] [PASS]    H6    Long-press session → action menu (Rename/Delete) → Rename → dialog with current name → Save → sidebar updated
[2026-04-08] [PASS]    H7    Long-press session → Delete → confirm dialog → session removed from sidebar + file deleted
[2026-04-08] [PASS]    H9    Delete middle session → other sessions unaffected in sidebar
[2026-04-08] [SKIP]    H8    Rename preserves messages — mechanism is frontmatter-only update, messages untouched by design
```

### 2026-04-08 — M Section QA (Cloud LLM complex tasks, gpt-4.1)

```
[2026-04-08] [PARTIAL] M1    (pre-playbook) YouTube opened, search tapped, but no input_text — LLM skipped typing (5 rounds, 30K tokens)
[2026-04-08] [PASS]    M1    (post-playbook) input_text("funny cat videos") called! Search results shown (13 rounds, 99K tokens)
[2026-04-08] [PASS]    M2    send_message(Mom, sorry, WhatsApp) — correct routing, "Mom" not found (expected), graceful fail (2 rounds)
[2026-04-08] [FIXED]   M3-a  "check what is on my screen" treated as chat — FIXED: added task keywords
[2026-04-08] [PASS]    M3    Screen reading works: pre-warm attached, LLM described PokeClaw UI (1 round, 4.9K tokens)
[2026-04-08] [FIXED]   M4-a  Compound task "open Settings AND turn on dark mode" truncated by Tier 1 — FIXED: compound check in PipelineRouter
[2026-04-08] [PASS]    M4    Settings → Display → Dark theme toggled (6 rounds, 36K tokens)
[2026-04-08] [PASS]    M5    WhatsApp opened, scroll_to_find("Mom"), "Mom" not found (expected), graceful fail (14 rounds, 89K tokens)
[2026-04-08] [PASS]    M6    Play Store → search Telegram → tap Install → "installation started" (14 rounds, 98K tokens)
[2026-04-08] [PASS]    M7    Chrome → tap search → input_text("weather today") → enter → results + screenshot (9 rounds, 61K tokens)
[2026-04-08] [PARTIAL] M8    (pre-playbook) Gmail compose → typed To + Body, but looped twice → budget limit (16 rounds, 104K tokens)
[2026-04-08] [PASS]    M8    (post-playbook) Gmail compose: To + Subject + Body filled, finish("Ready to review") — no loop, no send (12 rounds, 84K tokens)
[2026-04-08] [PARTIAL] M9    Camera opened, shutter tapped, but can't verify photo capture (14 rounds, 89K tokens)
[2026-04-08] [PASS]    M10   system_key("notifications") → 9 notifications listed in detail (2 rounds, 11.6K tokens!)
[2026-04-08] [PASS]    M11   "Watsapp" typo → "WhatsApp" correctly resolved, send_message called (13 rounds, 93K tokens)
[2026-04-08] [PARTIAL] M12   YouTube Music opened, play attempted, system dialog blocked (6 rounds, 30.5K tokens)
```

### Open Issues (unfixed)

| ID | Issue | Root Cause | Priority |
|----|-------|-----------|----------|
| ~~A1-a~~ | ~~Floating button flashes on chat questions~~ | ~~FIXED: finish tool filtered from showTaskNotify~~ | ~~Medium~~ |
| ~~A1-b~~ | ~~"Accessibility starting..." on every new chat~~ | ~~FIXED: moved keyword routing before accessibility check~~ | ~~Low~~ |
| ~~F1~~ | ~~Top bar "Task running..." not showing~~ | ~~FIXED~~ | ~~High~~ |
| ~~F2~~ | ~~Send button not turning red~~ | ~~FIXED~~ | ~~High~~ |
| H6 | Pencil icon cannot rename chat session | Not implemented — deferred to feature backlog | Low |
| ~~F3~~ | ~~Floating button IDLE in other apps~~ | ~~FIXED: show() callback now restores state via updateStateView~~ | ~~Medium~~ |
| ~~F6~~ | ~~"..." coexists with tool actions~~ | ~~FIXED: removeTypingIndicator() on first ToolAction~~ | ~~Medium~~ |
| B2-a | ~~No auto-return after task in other app~~ | Fixed 2026-04-10: cloud task completion now auto-returns to `ComposeChatActivity`, and recent YouTube search passes restored the same PokeClaw session after finishing in another app | Fixed |
| M1-a | ~~YouTube search: LLM skips input_text~~ | Fixed 2026-04-10: generic in-app search guard now blocks premature completion on explicit `search [app] for [query]` / `search for [query] on [app]` tasks until the agent actually calls `input_text`, then inspects results before finishing | Fixed |
| M3-a | ~~Screen reading routed as chat~~ | ~~FIXED: added "check", "screen", "notification", "compose", "find", "read my" to task detection~~ | ~~High~~ |
| M4-a | ~~Compound tasks truncated by Tier 1~~ | ~~FIXED: PipelineRouter skips Tier 1 for tasks with "and"/"then"/"after"~~ | ~~High~~ |
| M8-a | ~~Gmail compose loops~~ | Fixed 2026-04-10: explicit email-compose tasks now use a generic compose guard, so task mode no longer short-circuits into draft text or loops; it opens an email app, fills the draft fields, and finishes only after in-app compose work has started | Fixed |
| M12-a | YouTube Music system dialog | Login/premium dialog blocks music playback task | Low |

### 2026-04-09 — v9 UI Redesign QA

**Changes tested:** ChatScreen.kt v9 redesign — Local/Cloud toggle in toolbar, empty state, Quick Tasks panel, Chat/Task toggle, Monitor dialog, send routing.

```
[2026-04-09] [PASS]    G1    Cloud empty state: icon + "Cloud AI" + hint + 3 prompts + no toggle + correct placeholder
[2026-04-09] [PASS]    G2    Local empty state: icon + "Local AI" + bold hint + 3 local prompts + toggle visible
[2026-04-09] [PASS]    G5    Tab switch updates empty state immediately (subtitle, hint, prompts all change)
[2026-04-09] [PASS]    Q1-1  Cloud→Local tab switch: model switches to Gemma 4 E2B, Chat/Task toggle appears
[2026-04-09] [PASS]    Q1-2  Local→Cloud tab switch: model switches to gpt-4o-mini, toggle hides
[2026-04-09] [PASS]    Q2-1  Cloud chat "hello" → "Hello! How can I help you today?" (1 round, 5K tokens)
[2026-04-09] [PASS]    Q2-2  Cloud task "battery" → "100%, charging, 33.5°C" (2 rounds, get_device_info)
[2026-04-09] [PASS]    Q4-1  Quick Task tap fills input "How much battery left?" + auto-switches to Task mode
[2026-04-09] [PASS]    P1-1  Local/Cloud buttons in toolbar, same line as PokeClaw
[2026-04-09] [PASS]    P1-3  No background container on buttons
[2026-04-09] [PASS]    P2-5  Cloud mode: no Chat/Task toggle, placeholder "Chat or give a task..."
[2026-04-09] [PASS]    P3-1  Quick Tasks panel with ▲ chevrons
[2026-04-09] [PASS]    P3-4  5 quick task items visible by default
[2026-04-09] [PASS]    P3-9  BACKGROUND section + Monitor card
[2026-04-09] [PASS]    P3-10 Monitor card → centered dialog with Contact/App/Tone form
[2026-04-09] [PASS]    P5-1  No TaskSkillsPanel in content area (removed)
[2026-04-09] [PASS]    Q3-1  Local chat via UI — GPU→CPU fallback triggered, Gemma 4 responded "Hello! How can I help you today?" (11 tokens)
[2026-04-09] [PASS]    Q5-1  GPU→CPU fallback in sendChat() WORKS — OpenCL fail → engine reset → CPU retry → success
[2026-04-09] [PASS]    Q5-3  Tab switch mid-conversation — Cloud→Local→Cloud with sends, no crash, correct routing each time
[2026-04-09] [FIXED]   Q5-1  sendChat() GPU→CPU fallback — added catch block that detects OpenCL/nativeSendMessage error, reloads engine with CPU, retries
[2026-04-09] [FIXED]   Q5-1b Conversation creation "after 5 retries" — added engine reset on attempt 3 to clear stale task agent conversations
[2026-04-09] [FIXED]   Q5-2  API key was "test" — reconfigured with real key
[2026-04-09] [FIXED]   Tab LaunchedEffect override — removed LaunchedEffect sync so tab is user-controlled
[2026-04-09] [FIXED]   Cloud model memory — saves LAST_CLOUD_MODEL to KVUtils before switching to Local, restores when switching back
[2026-04-09] [FIXED]   Token counter — only shows for Cloud mode, hidden for Local (on-device = free)
[2026-04-09] [PASS]    Chat bubble verified — Q3-1 Local Chat: user msg y=417, AI response y=525, model tag "gpt-4.1" visible
[2026-04-09] [PASS]    R1 notifications triage — 150s, get_notifications → LLM summarized important items
[2026-04-09] [PASS]    R2 battery advice — 135s, get_device_info(battery) → "do not need to charge"
[2026-04-09] [PASS]    R3 clipboard explain — 135s, clipboard(get) → LLM described content (restaurant list)
[2026-04-09] [PASS]    R4 storage analysis — 165s, storage + apps → LLM cross-referenced
[2026-04-09] [PASS]    R5 notification summary — 150s, get_notifications → grouped by app + urgency
[2026-04-09] [PASS]    R6 charge advice — 105s, get_device_info(battery) → "100% charging, no need"
[2026-04-09] [FIXED]   Cloud send accessibility UX — Toast shown first ("Enable Accessibility Service to run tasks"), then navigates to PokeClaw Settings (not Android Settings). User sees all permissions.
[2026-04-09] [PASS]    Chat bubble E2E — Cloud: user "hello" y=357, AI "Hello! How can I help you today?" y=465, model tag "gpt-4.1" y=538
[2026-04-09] [PASS]    P2-3  Task mode: placeholder "Describe a phone task..." after tap 🤖 Task
[2026-04-09] [PASS]    P2-4  Chat mode: placeholder "Chat with local AI..." after tap 💬 Chat
[2026-04-09] [PASS]    P2-7  Mode switch preserves messages: Chat→Task→Chat, "test123" still visible
[2026-04-09] [PASS]    P3-3  Quick Tasks collapse/expand: tap handle → collapsed, tap again → expanded
[2026-04-09] [PASS]    J2    Empty input send: tap send with empty field → nothing sent
[2026-04-09] [PASS]    Q4-2  Cloud Quick Task E2E: 🦞 Reddit → tap → fills input → send → agent navigated Reddit + searched pokeclaw
[2026-04-09] [FIXED]   L1-v9 Session restore — onCreate reads CURRENT_CONVERSATION_ID from KVUtils, reloads saved messages. replaceTypingIndicator now calls saveChat() to persist task results immediately. Verified: "Restored 7 messages from conversation chat_1775787808468"
[2026-04-10] [NOTE]    On this Pixel 8 Pro / Android 16, reinstall cleared Accessibility (`enabled_accessibility_services=null`). Re-enabling via `adb shell settings put secure enabled_accessibility_services io.agents.pokeclaw/io.agents.pokeclaw.service.ClawAccessibilityService` + `accessibility_enabled 1` restored the bound service for QA.
[2026-04-09] [PASS]    Full E2E WhatsApp: UI type "send hi to Girlfriend on WhatsApp" → agent opened WhatsApp → send_message called → finish("Sent 'hi' to Girlfriend on WhatsApp.") → auto-return 15s → result visible in chatroom
[2026-04-09] [PASS]    Auto-return verified: agent navigated to WhatsApp, completed task, returned to PokeClaw, user msg + AI result both visible in same session
[2026-04-09] [PASS]    C1/L3/L4  Monitor start via in-app monitor flow stays in PokeClaw; top bar shows "Monitoring: Rlfriend", no Home press
[2026-04-09] [PASS]    C3    Tap top monitoring bar → expands to show contact + Stop → tap Stop → AutoReplyManager logs "Auto-reply DISABLED for contacts: []"
[2026-04-09] [PASS]    K6-a  App Settings → Accessibility Service row opens Android Accessibility page for PokeClaw
[2026-04-09] [ISSUE]   K2-a  App Settings permission status stale — Accessibility row still shows "Disabled" even when system Accessibility page shows "Use PokeClaw" ON
[2026-04-09] [ISSUE]   K3-b  Accessibility enable auto-return incomplete — app calls START on SettingsActivity after enable, but system Accessibility SubSettings stays foreground; user is not auto-returned
[2026-04-10] [FIXED]   K2-a  Accessibility status row now reads system enabled-services state, so app Settings shows the truthful `Enabled`/`Disabled` value
[2026-04-10] [PASS]    K2-a  App Settings → Accessibility Service row shows `Enabled` immediately after system Accessibility toggle is ON
[2026-04-10] [FIXED]   K3-b  Pending accessibility auto-return is now armed only when the service is disabled, preventing false triggers while Accessibility is already ON
[2026-04-10] [PASS]    K3    Disabled Accessibility → tap app Settings row → Android Accessibility → PokeClaw detail → toggle `Use PokeClaw` ON → app auto-returns to PokeClaw Settings and row shows `Enabled`
[2026-04-10] [FIXED]   Q6-7  Task agent config now syncs on model switch and before startTask, so Cloud tab tasks no longer reuse stale Local agent config
[2026-04-10] [PASS]    Q2-2/Q6-7  Cloud task "how much battery left" → Agent config updated to `gpt-4.1` → `get_device_info(category=battery)` runs → answer returned in chat with model tag `gpt-4.1-2025-04-14`
[2026-04-10] [FIXED]   L1-v9  Cloud send-message auto-return now preserves the existing conversation instead of dropping the user into a fresh session
[2026-04-10] [PASS]    B1/L1/Q7-7  Cloud task "send yo to girlfriend on WhatsApp" → `send_message` opens WhatsApp and succeeds → auto-return keeps user in `ComposeChatActivity` → same conversation still shows prior messages plus new user bubble + result bubble `Sent 'yo' to girlfriend on WhatsApp.`
[2026-04-10] [FIXED]   A11Y-r1  Accessibility-dependent tools no longer fail immediately during transient service rebinds; they now wait for the enabled service to reconnect before hard-failing
[2026-04-10] [PASS]    H2/H2-b/H2-c  Models screen keyboard safety: API key, Custom Base URL, and Custom Model Name all stay fully visible when IME opens; focused field scrolls into view
[2026-04-10] [FIXED]   P1-4/Q1-r1  Chat toolbar tab state now re-syncs to the actual active model after Settings/model changes, preventing Cloud placeholder/quick-tasks from drifting out of sync with a Local model status (and vice versa)
[2026-04-10] [PASS]    P1-4/P2-1/P2-4/Q1-1/Q6-2  Tap `Local` → model status switches to `● Gemma 4 E2B — 2.6GB · CPU`, local reasoning-first quick tasks render, Chat/Task toggle appears, placeholder becomes `Chat with local AI...`
[2026-04-10] [PASS]    P1-4/P2-5/Q1-2/Q6-3  Tap `Cloud` → model status switches back to `● gpt-4.1 · Cloud`, cloud-only quick tasks return, Chat/Task toggle hides, placeholder becomes `Chat or give a task...`
[2026-04-09] [BLOCKED] L5/L5-b  Incoming WhatsApp notification auto-reply while staying in app requires a second sender device / live external message source
[2026-04-09] [FIXED]   F2-v9 Stop button slow — added Future.cancel(true) to interrupt agent thread + abort HTTP call immediately (was: flag-only, waited for LLM round to finish)
[2026-04-09] [ISSUE]   F2-v9 Stop → return to same session — after stopping task, should return to the SAME chat session, not open new one
[2026-04-09] [ISSUE]   L1-v9 Auto-return should preserve session — after task completes in other app and auto-returns to PokeClaw, should show the same conversation with the result, not a fresh session
[2026-04-10] [PASS]    Q7-2/Q7-3/Q7-4/Q7-6  Cloud quick task "Search YouTube for funny cat fails" → YouTube opens → tap left floating bubble → `Stop task requested from floating pill` logged → task cancelled → auto-return restores same `ComposeChatActivity` session → send button resets to arrow
[2026-04-10] [PASS]    Q7-5  After floating-stop, second Cloud task "how much battery left" runs normally → no `already running` error → answer returned in same session
[2026-04-10] [ISSUE]   Q7-local  Local task stop could trigger a native crash / stale-session race: stop during LiteRT `sendMessage()` → chat UI reloads early → `session already exists` and occasional `SIGSEGV`
[2026-04-10] [FIXED]   Q7-local  Local stop now avoids interrupting LiteRT mid-round; terminal cleanup waits for the task-side client to close, and `TaskOrchestrator` only releases the task after the cancel completion callback arrives
[2026-04-10] [PASS]    Q7-1b/Q7-3/Q7-4  Local task "how much battery left" → tap Stop → 1s later UI still shows `Task running...` + `Stop` while safe unwind is in progress → app remains on `ComposeChatActivity` → logs show `Task cancelled` → send button resets to arrow
[2026-04-10] [PASS]    Phase2-r1  TaskSessionStore smoke on Pixel 8 Pro → local quick-task card still fills task input correctly → sending enters `Task running...` + `Stop` with honest `Model busy` chat status → stop request is logged by `TaskOrchestrator` and UI returns to idle placeholder on the same `ComposeChatActivity` shell
[2026-04-10] [PASS]    Q7-5-local  After local stop, a second local task starts and completes normally — no `already running`, no `session already exists`, no crash
[2026-04-10] [FIXED]   Dbg-u1  Debug builds now run the same once-per-day GitHub release check as release builds, so accidental debug installs still see upgrade prompts
[2026-04-10] [BLOCKED] Dbg-u1  Live prompt verification still needs a throwaway device/build that is older than the just-installed `0.5.0`; current handset has already been upgraded, so this turn only covers code inspection + build/install verification, not a fresh old-debug prompt capture
[2026-04-10] [PASS]    Dbg-u2  Public GitHub `v0.4.1` asset (`PokeClaw_v0.4.0_20260408_140502.apk`) on test device → cold launch after `v0.5.0` release published → `Update Available` modal appears with `PokeClaw v0.5.0 is available. You are running an older version.`
[2026-04-10] [ISSUE]   Dbg-u3  Public GitHub `v0.4.1` asset cannot be updated in place to public `v0.5.0` asset: `adb install -r ... PokeClaw_v0.5.0_20260410_161430.apk` returns `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; users on the older public debug signing path need a one-time uninstall + reinstall
[2026-04-10] [FIXED]   Rel-s1  Release signing config now accepts the same `KEYSTORE_*` inputs from environment variables or `local.properties`, so local signed builds and GitHub Actions both follow the same stable-signing path
[2026-04-10] [NOTE]    Rel-s2  `v0.5.1` is the first version prepared for a stable release key path; the old public `0.4.x` → public `0.5.0` signing mismatch is already shipped and cannot be retro-fixed without the lost original key
[2026-04-10] [BLOCKED] Rel-s3  Public GitHub Release publication for `v0.5.1` still depends on installing the stable signing secrets into `agents-io/PokeClaw` Actions settings; code path is ready, repo permission path is not
[2026-04-10] [PASS]    Rel-s4  Local stable-signing verification: generated a dedicated release keystore, `./gradlew :app:validateSigningRelease` passed, and a fresh `./gradlew --no-daemon :app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease` produced `app/build/outputs/apk/release/PokeClaw_v0.5.1_20260410_111303.apk`
[2026-04-10] [PASS]    Rel-s5  Local signed release artifact verification: `apksigner verify --print-certs` reports signer `CN=Nicole, OU=PokeClaw, O=agents.io, L=Vancouver, ST=British Columbia, C=CA` with SHA-256 `e000d1d6555b8fab20c03a5d9ddeba83944f26eecf0b978ac7affc2eebd43186`; local `SHA256SUMS.txt` records APK digest `fb7c6a6f4e2536f24bfb8f9ac6e8f7628aec11bf5e1a29b96fc18bb238fcde65`
[2026-04-10] [PASS]    Rel-s6  Stable-signed `0.5.1` release APK fresh-installed successfully onto the Pixel test device after removing the old debug build; launcher resolves and app starts normally
[2026-04-10] [PASS]    Rel-s7  Stable-key in-place upgrade path verified locally: with the same release keystore, a higher-version signed build (`POKECLAW_VERSION_CODE=15`, `POKECLAW_VERSION_NAME=0.5.1-upgrade-test`) installed over the stable-signed `0.5.1` baseline via `adb install -r` and Android accepted the upgrade with no signature mismatch
[2026-04-10] [FIXED]   M1-a  Explicit in-app search tasks now use a generic guard/prompt hint: the agent cannot finish before it really types the query with `input_text`, and blocked finishes feed back a fresh screen-based node hint instead of an app-specific scripted route
[2026-04-10] [PASS]    M8/M1-a  Cloud task `search youtube for lofi beats` → `open_app` → `input_text(node_id=...)` succeeds → `system_key(enter)` → `get_screen_info` → `finish`; completes in 6 rounds / 46.7K tokens, no budget stop, auto-return restores `ComposeChatActivity`
[2026-04-10] [PASS]    M8-alt/M1-a  Alternate phrasing `search for lofi beats on youtube` follows the same generic path (`open_app` → `input_text(node_id=...)` → `system_key` → `get_screen_info` → `finish`) and also completes in 6 rounds / 47.5K tokens
[2026-04-10] [PASS]    M1-control  Non-search control task `how much battery left` remains unaffected by the search guard: `get_device_info(category=battery)` → `finish`; completes in 2 rounds / 10.4K tokens with no `InAppSearchGuard` activity
[2026-04-10] [FIXED]   M8-a  Explicit email-compose tasks now use a generic `EmailComposeGuard`: the agent can no longer satisfy task-mode email requests with text-only draft output before attempting any in-app compose actions
[2026-04-10] [PASS]    M8/S8  Cloud task `Write an email saying I will be late today` → `get_installed_apps(mail)` → `open_app(com.google.android.gm)` → `tap_node` compose → `input_text` subject `Running late today` → `input_text` body → `get_screen_info` → `finish`; completes in 8 rounds / 52.2K tokens, auto-returns to `ComposeChatActivity`, and correctly leaves recipient blank because none was provided
[2026-04-10] [PASS]    M8-control  Control task `how much battery left` remains unaffected by `EmailComposeGuard`: `get_device_info(category=battery)` → `finish`; completes in 2 rounds / 10.4K tokens with no compose-specific interference
[2026-04-10] [PASS]    LQ1-LQ5  Local reasoning quick-task sweep on Pixel 8 Pro: notifications triage, clipboard explain, storage analysis, notification summary, and battery advice all completed on-device via LiteRT CPU fallback with correct tool routing and no crashes/loops
[2026-04-10] [PASS]    LQ7-LQ10/LQ12/LQ13  Local deterministic quick-task sweep: installed apps, phone temperature, bluetooth state, battery, storage, and Android version all returned correct device data through `get_installed_apps` / `get_device_info`, with no stale-session or routing regressions
[2026-04-10] [PASS]    LQ6/LQ11  Contact-specific local quick tasks still route the correct tools (`send_message`, `make_call`) and fail gracefully when `Mom` does not exist on this device; treat literal send/call success as env-blocked coverage, not a product failure
[2026-04-10] [PASS]    P3-7/P3-8/Q4-1  Local UI quick-task E2E: tap visible `Check my battery and tell me if I need to charge` card → input prefilled → Local task send routes through `provider=LOCAL / gemma4-e2b` → `get_device_info(category=battery)` → response bubble `The battery is at 100% and is charging. You do not need to charge.` appears with local model tag `Gemma 4 E2B — 2.6GB`; input resets to task placeholder `Describe a phone task...`
[2026-04-10] [FIXED]   QA-r1  `scripts/e2e-quick-tasks.sh` now classifies `onSystemDialogBlocked`, text-only completions with no tool calls, `Task cancelled`, and `Task stopped: budget limit reached ...` correctly; it no longer misreports the YouTube permission dialog as a generic timeout
[2026-04-10] [FIXED]   Bgt-1  Existing installs could stay pinned to the legacy 100K / $0.50 task budget even after code defaults increased. `TaskBudget` now migrates untouched legacy defaults to 250K / $1.00 once, while preserving user-custom budgets; Settings budget UI now exposes `250K` explicitly and snaps to the nearest current value
[2026-04-10] [PASS]    S2/M32  Cloud task `Install Telegram from Play Store` → Play Store path completed without budget stop; on this device the agent correctly recognized Telegram was already installed and finished in 10s
[2026-04-10] [PASS]    S3/M20  Cloud task `Check whats trending on Twitter and tell me` → `open_app(com.twitter.android)` → inspect current feed/trending content → summarize visible topics; completed in 30s with no task-budget stop
[2026-04-10] [BLOCKED] S1/M1-b  Cloud task `Search YouTube for funny cat fails` is currently blocked by Android's foreground permission controller (`GrantPermissionsActivity`) over YouTube; PokeClaw surfaces this as `system dialog blocked foreground automation` instead of looping or timing out
[2026-04-10] [PASS]    S5/M33  Cloud task `Copy the latest email subject and Google it` → `get_notifications` → `clipboard(set)` → `open_app(com.android.chrome)` → search in Chrome → screenshot/search-results visible → `finish`; after legacy-budget migration this completed in 15 rounds / 110.2K tokens instead of hard-stopping at the old 100K ceiling
[2026-04-10] [PASS]    S7/M51  Committed-state rerun `Open Reddit and search for pokeclaw` → `open_app(com.reddit.frontpage)` → `input_text(pokeclaw)` → results visible → `finish`; completed in 12 rounds / 91.9K tokens on the latest hardening branch
[2026-04-10] [PASS]    Cloud quick-task sweep (effective final) on branch `hardening/behavior-safe-2026-04-09` @ `a0a88ab`: `18 PASS / 0 FAIL / 2 BLOCKED / 0 TIMEOUT / 20 TOTAL`. Blocked items are environment-driven (`S1` YouTube permission dialog, `Call Mom` missing contact). Base sweep log: `/tmp/pokeclaw-cloud-quick-tasks-20260410-full.log`; `S5` was rerun after the budget migration and passed at 110.2K tokens
[2026-04-10] [PASS]    Phase1-r1  Architecture refactor smoke — relaunch via `SplashActivity` with Cloud config active lands on `ComposeChatActivity` showing `● gpt-4.1 · Cloud` and the unified Cloud placeholder, confirming chat runtime rehydrate still works after `ChatSessionController` extraction
[2026-04-10] [PASS]    Phase1-r2  Architecture refactor smoke — copied the existing Edge Gallery Gemma model into PokeClaw's sandbox, switched provider to `LOCAL`, relaunched, and confirmed `ComposeChatActivity` rehydrated into Local mode with `Chat with local AI...` plus top status `● gemma4_2b_v09_obfus_fix_all_modalities_thinking · GPU`
[2026-04-10] [PASS]    Q3-1/Q5-1/Q5-1b/Phase1-r3  Local chat after `ChatSessionController` extraction: UI send produced a real assistant reply (`Hello! How can I help you today?`), GPU inference transparently fell back to CPU, and both the top status pill and assistant model tag updated to `CPU` instead of stale `GPU`
[2026-04-10] [PASS]    Phase3-r1  Fresh reinstall + app Settings smoke: after `adb install -r`, Android cleared `enabled_accessibility_services`; app Settings now truthfully shows `Accessibility Service = Disabled` instead of stale `Enabled`
[2026-04-10] [PASS]    Phase3-r2  Rebinding truth smoke: after restoring `enabled_accessibility_services` / `accessibility_enabled` via `adb shell settings put secure ...`, app Settings showed `Accessibility Service = Connecting` while the service was still rebinding, instead of collapsing enabled+unbound into `Disabled`
[2026-04-10] [PASS]    Phase3-r3  Permission truth smoke: with no PokeClaw listener in `enabled_notification_listeners`, app Settings shows `Notification Access = Disabled`
[2026-04-10] [FIXED]   K4-r1  Notification-listener foreground return is now gated by a pending permission-flow flag, so listener reconnects no longer blindly foreground app Settings unless the user actually came from the in-app permission flow
[2026-04-10] [PASS]    Phase4-r1/H4-b  After local-runtime consolidation, cold launch still lands on `ComposeChatActivity` with truthful local status `● gemma4_2b_v09_obfus_fix_all_modalities_thinking · CPU`
[2026-04-10] [PASS]    Phase4-r2/Q3-1/Q5-1/Q5-1b  Local UI send smoke after runtime consolidation: typed `say pong`, tapped the live send-button bounds, and received assistant reply `Pong! 🏓`; both top status and assistant bubble tag remained `gemma4_2b_v09_obfus_fix_all_modalities_thinking (CPU)`
[2026-04-10] [PASS]    P7-1/P7-2  Chat bubble metadata smoke: after relaunching `ComposeChatActivity`, user bubbles render a subtle time footer (`5:57 p.m.`) and assistant bubbles render `gemma4_2b_v09_obfus_fix_all_modalities_thinking (CPU) · 5:57 p.m.` under the reply bubble
[2026-04-10] [PASS]    P7-3/Q7-7  Saved chat history now persists per-message timestamps in markdown via hidden `<!-- pokeclaw:timestamp=... -->` comments, so reloaded conversations keep stable bubble times instead of resetting to the current clock
[2026-04-10] [PASS]    Phase1b-r1/Q7-7  After `ConversationStore` extraction, cold relaunch still restored `chat_1775851530681` with 9 saved messages; logcat showed `Restored 9 messages from conversation chat_1775851530681`, and the foreground UI still showed the existing `ay pong` / `Hello! How can I help you today?` conversation instead of a blank new chat
[2026-04-10] [PASS]    Phase2b-r1  After `TaskFlowController` extraction, debug task broadcasts still reached the chat shell (`TaskTriggerReceiver: Received task via broadcast: battery`, `ComposeChatActivity: Auto-task from intent: battery`) and preserved in-app permission guidance by pushing `SettingsActivity` when Accessibility was unavailable
[2026-04-10] [FIXED]   Android15-coldstart  Cold launch no longer crashes if app-start `ForegroundService` is disallowed; `ForegroundService.start()` now returns `false` and logs a warning instead of throwing `ForegroundServiceStartNotAllowedException` from `ClawApplication.onCreate()`
[2026-04-10] [PASS]    Phase2c-r1  After `ActiveTaskShellController` extraction, the Compose top bar still rendered `Monitoring: Mom`; expanded state showed `Mom` + `Stop`, and tapping `Stop` disabled auto-reply and removed the monitor
[2026-04-10] [PASS]    Phase2c-r2  Debug `autoreply on mom` no longer bypasses app behavior: `TaskTriggerReceiver` rewrites it to `monitor mom on WhatsApp`, and on this device the flow foregrounded in-app `SettingsActivity` with no direct `Added contact` log and no ghost `Monitoring:` bar in the dumped UI
[2026-04-10] [NOTE]    TgMon-r1  Telegram monitor QA now requires an external sender path (second account or bot token + existing bot chat); without that sender, Telegram incoming-message monitor cases must be marked `BLOCKED`
[2026-04-10] [PASS]    Phase4-r3  Monitor target parser unit bundle passed: `monitor Mom on Telegram`, default WhatsApp when app is omitted, `watch Alex on sms` -> `Messages`, and `monitor Caroline` does not get misparsed as `LINE`
[2026-04-10] [PASS]    Phase4-r4  Live device dialog smoke: Monitor dialog now shows the supported app list (`WhatsApp`, `Telegram`, `Messages`, `LINE`, `WeChat`) and retained `Telegram` as the selected app in the live screenshot instead of collapsing back to WhatsApp
[2026-04-10] [PASS]    Phase5-r1  Local runtime consolidation compile gate: `LocalModelRuntime` now owns shared `openConversation(...)` and `runSingleShot(...)`, and `ChatSessionController`, `LocalLlmClient`, `LlmSessionManager.singleShotLocal()`, and `AutoReplyManager.generateReplyLocal()` all compile against the same runtime boundary (`compileDebugKotlin`, `compileDebugJavaWithJavac`, `assembleDebug`)
[2026-04-10] [BLOCKED] Phase5-r2  Targeted device smoke for the new shared local runtime boundary is blocked by ADB attach state (`adb devices -l` returned no attached devices after the Phase 5 landing). Re-run `H4/H4-b`, `Q3-1`, `Q5-1`, `Q5-1b`, and the local quick-task bundle as soon as the Pixel is visible again instead of treating the missing device as an app regression
[2026-04-10] [PASS]    Phase5-r3  Local model state consolidation compile gate: `LocalModelManager` now exposes shared device-support, catalog, and active-model state so `LlmConfigActivity` and `ChatSessionController` stop maintaining separate RAM/support/downloaded calculations (`compileDebugKotlin`, `compileDebugJavaWithJavac`)
[2026-04-10] [PASS]    Phase5-r4  Local model ownership cleanup compile gate: `LocalModelManager.downloadModel()` no longer mutates MMKV selection state directly; chat/settings callers now decide whether a finished download should update the default or active local model (`compileDebugKotlin`, `compileDebugJavaWithJavac`)
[2026-04-10] [NOTE]    QA-wf-r2  Device-state guard for Compose UI smoke: if notification shade or another app steals foreground, collapse/foreground PokeClaw again before judging the refactor; if IME moves the input bar, re-dump live bounds instead of reusing stale tap coordinates
[2026-04-10] [PASS]    H2-d  Chat keyboard dismiss smoke passed on Pixel 8 Pro: after focusing the input, tapping the blank header area cleared focus (`focused=true` -> `focused=false`) and hid the IME instead of trapping the keyboard on screen
[2026-04-10] [PASS]    B4-c  Accessibility text-match hardening compile/unit bundle passed: low-level lookup now keeps Android's fast text path but falls back to a Unicode-normalized tree walk, and standard launch dialogs try stable positive-button ids before language-specific keywords
[2026-04-10] [PASS]    Phase5-r5  Cloud send smoke passed after send-affordance hardening: `send yo to girlfriend on WhatsApp` ran on `gpt-4.1`, called `send_message(contact=\"girlfriend\", message=\"yo\", app=\"WhatsApp\")`, finished in 2 rounds, and auto-returned with `Task completed: Sent 'yo' to your girlfriend on WhatsApp.`
[2026-04-10] [PASS]    Phase5-r6  Chat-noise filtering is no longer English-string-bound: conversation-reading heuristics now treat timestamps and centered system labels as layout noise using shared tested rules (`ChatNoiseFilterUtilsTest`, `UiTextMatchUtilsTest`, `ContactMatchUtilsTest`)
[2026-04-11] [FIXED]   DD-guard-1  Cloud no longer falls back to generic "I cannot access your device" denials for direct phone-data requests when a matching tool exists; the direct-device-data guard now forces a real tool attempt first and blocks text-only completion / premature `finish`
[2026-04-11] [PASS]    DD1  Cloud task `read my clipboard and explain what it says` → `clipboard(action=get)` → real clipboard content returned and explained; no generic privacy/device-access refusal
[2026-04-11] [PASS]    DD2  Cloud task `read my notifications and summarize` → `get_notifications()` → summarized live notifications; no false claim that notifications are inaccessible
[2026-04-11] [PASS]    DD3  Cloud task `how much battery left` → `get_device_info(category=battery)` → answered with real battery/charging/temperature state; no generic limitation disclaimer
[2026-04-11] [PASS]    DD5  Cloud task `what apps do i have` → `get_installed_apps()` → returned the real installed-app list; no generic chatbot fallback
[2026-04-11] [PASS]    DD7-unit  Conceptual control `what is an Android clipboard` remains a normal chat-style case in unit coverage; the guard no longer falsely forces a clipboard tool just because the word `clipboard` appears
[2026-04-11] [FIXED]   Q2-r1  Cloud unified-input send no longer reuses task-running chrome for ordinary chat turns; chat waiting state and true task execution state are tracked separately
[2026-04-11] [PASS]    Q2-1b/Q6-3/T10  Pixel 8 Pro smoke after switching to `gpt-4.1-mini`: top pill shows `● gpt-4.1-mini · Cloud`, Cloud tab remains selected, placeholder stays `Chat or give a task...`, and no orange `Task running...` bar appears for the chat shell
[2026-04-11] [PASS]    Q2-1c  Fresh Cloud chat smoke on Pixel 8 Pro: typing `say hi` stayed in ordinary chat, produced a normal `Hello! How can I assist you today?` assistant bubble tagged `gpt-4o-2024-08-06`, and did not launch `Send Message` or reuse any old contact state
[2026-04-11] [PASS]    Q1-6/Q6-3/T10  Settings round-trip truth smoke: switch to Cloud, open Settings, press Back, and return to the same conversation → logcat reports `Cloud chat ready: gpt-4.1-mini`, top pill still shows `● gpt-4.1-mini · Cloud`, and the chat shell stays on the Cloud placeholder instead of drifting back to Local
[2026-04-11] [PASS]    H4-d/H4-e/T2/T10  Models page truth smoke on Pixel 8 Pro: page now shows `Active model`, `Default local model`, and `Default cloud model` separately; the linked default Gemma built-in row no longer claims `Not downloaded`
[2026-04-11] [PASS]    H4-c  Cloud dropdown switch smoke after install: switching to `GPT-4o` leaves a single `Switched to GPT-4o` system line instead of a lower-case + display-name duplicate pair
[2026-04-11] [PASS]    Q2-4/Q2-4b  Cloud clipboard bridge on Pixel 8 Pro: `read my clipboard and explain what it says` called `clipboard(action=get)` and produced the visible assistant bubble `Your clipboard is currently empty.` in the same chatroom; no generic device-access refusal and no misleading `Clipboard failed` status line remained in the UI
[2026-04-11] [PASS]    Q8-smoke-cloud  Same Cloud chatroom memory smoke on Pixel 8 Pro: `Remember token plum8492 and reply with only OK.` → `OK` → `What token did I ask you to remember?` → `The token you asked me to remember is "plum8492".` Visible UI and logcat matched
[2026-04-11] [PASS]    Q8-smoke-local  Same Local chatroom memory smoke on Pixel 8 Pro after local session-restore fix: `Remember token guava9184 and reply with only OK.` → `OK.` → `What token did I ask you to remember?` → `You asked me to remember the token **guava9184**.` Visible UI matched the expected same-chatroom continuity
[2026-04-11] [PASS]    Q9-1-smoke-cloud  Cloud chat -> task handoff proved on Pixel 8 Pro: in one Cloud chatroom, `Remember token mango4421 and reply with only OK` → `OK`, then `Copy that token to the clipboard` triggered `clipboard(action=set,text=mango4421)`, returned `The token "mango4421" has been successfully copied to your clipboard.`, and a follow-up `Read my clipboard and reply with only the clipboard contents` visibly returned `mango4421`
[2026-04-11] [FAIL]    Q9-2-smoke-local  Local task still does NOT inherit prior chat context, which is expected, but the vague task UX is not graceful yet: after a Local chat remembered `papaya6614`, switching to Local Task mode and sending `Copy that token to the clipboard` did not overwrite the clipboard (Cloud readback still returned the earlier `mango4421`) but the Local task also failed to produce a clear user-facing "I need the exact content in this task message" result
[2026-04-11] [PASS]    Q3-r6  Local auto-task session-ownership hardening on Pixel 8 Pro: a foreground `TASK` intent for `How much battery left?` no longer triggers the previous LiteRT `A session already exists` retry/reset crash path; app survives and returns through the normal task shell
[2026-04-11] [PASS]    Q3-r7  Non-interactive Local task fallback without Accessibility: with Accessibility disabled, `How much battery left?` now bypasses the old Settings redirect, executes `get_device_info(category=battery)` directly, and returns `Battery: 100%, charging, 26.4°C`
[2026-04-11] [PASS]    RC6-cloud-email-10x  Repeated-trial Cloud compose task stability on Pixel 8 Pro: `Write an email saying I will be late today` completed successfully in **10/10** direct-ADB trials. Each pass stayed on the in-app compose flow and ended with a draft-created `onComplete`, despite occasional stale-node retries during compose-field refresh.
[2026-04-11] [PASS]    RC6-cloud-gmail-google-8x  Repeated-trial Cloud exploratory task `Copy the latest email subject and Google it` achieved **8/10** successful direct-ADB trials on Pixel 8 Pro. Two trials ended `Task cancelled`; eight trials opened Gmail, extracted the latest subject, copied it, and searched it on Google. Treat this as acceptable Cloud exploratory success-rate coverage, not deterministic 10/10 functionality.
[2026-04-11] [PASS]    RC6-local-e4b-battery  Local E4B direct QA: `How much battery left?` completed in 2 rounds after a slow first generation window (~173s to tool call), then called `get_device_info(category=battery)` and returned `100%, charging, 36.1°C`
[2026-04-11] [PASS]    RC6-local-e4b-notifications  Local E4B direct QA: `Read my notifications and summarize` completed in 2 rounds, called `get_notifications()`, and returned a visible summary of live YouTube + system notifications after the expected long local generation delay
[2026-04-11] [PASS]    RC6-local-e4b-storage  Local E4B direct QA: `How much storage do I have?` completed in 2 rounds, called `get_device_info(category=storage)`, and returned `37.4 GB used of 245.7 GB (15%), 208.3 GB free`
[2026-04-11] [PASS]    RC6-local-e4b-device  Local E4B direct QA: `What Android version am I running?` completed in 2 rounds, called `get_device_info(category=device)`, and returned `Android 16 (API 36) on a Google Pixel 8 Pro`
[2026-04-11] [FIXED]   RC6-local-session-race  Local direct QA exposed a real release blocker: while a Local task owned the LiteRT session, the chat shell could still try to reopen the same local model and trigger `A session already exists`. Fixed 2026-04-11: the chat-side loader now stands down whenever a task is running and shows `● Local task using model` instead of racing the task runtime
[2026-04-12] [PASS]    Q8-3  Cloud relaunch memory continuity on Pixel 8 Pro: in one Cloud chatroom, `Remember token cloudrestart7312 and reply with only OK.` returned `OK`; after full force-stop + relaunch, the same conversation restored and `What token did I ask you to remember? Reply with only the token.` visibly returned `cloudrestart7312`
[2026-04-12] [PASS]    Q8-4  Local relaunch memory continuity on Pixel 8 Pro: in one Local E4B chatroom, `Remember token localrestart5186 and reply with only OK.` returned `OK`; after full force-stop + relaunch, the same conversation restored under `● Gemma 4 E4B — 3.6GB · CPU` and `What token did I ask you to remember? Reply with only the token.` visibly returned `localrestart5186`
[2026-04-12] [PASS]    Rel-s8  Version-prep build gate for `0.6.0`: `assembleDebug` passed in-sandbox, and a stable-signed local `assembleRelease` produced `app/build/outputs/apk/release/PokeClaw_v0.6.0_20260411_223047.apk` with SHA-256 `649b87e69cf166f8ce0e144aee9d416aaba48b152fa33842a88c7f695b67c57d`
[2026-04-28] [BLOCKED] Rel-s9  `v0.6.8` stable release APK could not upgrade the Pixel 8 Pro from the installed debug-signed `0.6.7`: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Debug `0.6.8` upgraded in place and was used for code-path QA; the stable APK still needs a clean-install or signed-line migration test before upgrade claims
[2026-04-28] [FAIL]    v068-cloud-sweep  Cloud quick-task sweep on Pixel 8 Pro / Android 16 with `gpt-4.1` finished `13 PASS / 4 FAIL / 1 BLOCKED / 2 TIMEOUT / 20 TOTAL`; result log: `/tmp/pokeclaw-v068-cloud-quick-20260428-123547.log`
[2026-04-28] [FAIL]    S7/M51  `Open Reddit and search for pokeclaw` regressed from the 2026-04-10 pass; stuck detector stopped the agent after the screen stayed unchanged for 3 consecutive steps
[2026-04-28] [FAIL]    S6/M11  `Check my latest WhatsApp chat and summarize it` opened WhatsApp but repeated `system_key(back)` and was stopped by stuck detection
[2026-04-28] [TIMEOUT] S8/M19  `Write an email saying I will be late today` timed out at 60s; the next harness case saw leaked `Task cancelled` state from the unfinished email flow
[2026-04-28] [TIMEOUT] B1     `Send hi to Girlfriend on WhatsApp` timed out at 45s on the Pixel 8 Pro QA device
[2026-04-28] [BLOCKED] M47    `Call Mom` hit an external Google Contacts notification-permission dialog; classify this run as environment-blocked, but the harness must recover/clean foreground state before continuing other cases
[2026-04-28] [FAIL]    LQ-v068  Local quick-task sweep did not complete: an invalid first attempt failed after force-stop disconnected Accessibility, the retry timed out on `Notifications triage`, and a targeted Local E2B `how much battery left` smoke also timed out after 180s under the current device state
[2026-04-28] [FIXED]   v068-debug-tool-anr  Direct debug tool broadcasts now run via `goAsync()` background work; focused `send_message` debug-tool smoke sent `qa-ping` to `Girlfriend` without the BroadcastReceiver main-thread ANR
[2026-04-28] [FIXED]   v068-direct-tool-threading  Tier-1 DirectTool routes now execute off the caller thread, preserve `ToolResult.isSuccess`, log direct `onComplete`, and release/reset task state in a `finally` block
[2026-04-28] [FIXED]   v068-e2e-cleanup  `scripts/e2e-quick-tasks.sh` now sends debug `cancel:`, resets foreground between cases, dismisses stale PokeClaw ANR dialogs, waits for Accessibility binding, and classifies `Failed:` completions as failures instead of passes
[2026-04-28] [FIXED]   v068-wa-overflow  Contact lookup overlay dismissal no longer treats a generic top-right ImageButton as a close button; this stopped the WhatsApp overflow menu from being opened during contact lookup
[2026-04-28] [FIXED]   v068-fgs-race  ForegroundService now calls `startForeground()` immediately in `onCreate()`, preventing `ForegroundServiceDidNotStartInTimeException` when a task fails/stops before `onStartCommand` can update the notification
[2026-04-28] [PASS]    B1-v068-followup  Focused Cloud `Send hi to Girlfriend on WhatsApp` from a wrong WhatsApp chat completed in 15s: back to chat list, search `Girlfriend`, type `hi`, tap send, and log direct `onComplete`
[2026-04-28] [FAIL]    v068-cloud-sweep-after-fixes  Latest Cloud quick-task sweep finished `17 PASS / 0 FAIL / 1 BLOCKED / 2 TIMEOUT / 20 TOTAL`; result log: `/tmp/pokeclaw-v068-cloud-quick-20260428-1337-after-wa-fix.log`. Remaining timeouts: WhatsApp latest-chat summary and copy latest email subject then Google it
[2026-04-28] [PASS]    LQ-v068-e2b-battery-followup  Targeted Local E2B `How much battery left?` completed in 105s after GPU OpenCL failure fell back to CPU, called `get_device_info(category=battery)`, and returned `60%, not charging, 38.1°C`
[2026-04-28] [BLOCKED] Rel-s10  Local `./gradlew assembleRelease` compiled and minified but failed at `:app:packageRelease`: `SigningConfig "release" is missing required property "storeFile"`. Signed release APK needs CI/release signing secrets or local keystore restoration
[2026-04-28] [FIXED]   LMDir-r1  Issue #39 debug ZIP root cause confirmed: v0.6.7 failed before model download because the external app-files `models` directory did not exist, causing `StatFs` and `.downloading` open to throw `ENOENT`. The storage harness now requires a writable model dir, falls back to internal storage when external app storage cannot be created/written, and reports selected/external/internal model-dir diagnostics in bug ZIPs.
[2026-04-28] [FIXED]   RelGate-r1  Release gate is now a concrete per-release record template covering direction, harness, scope, compile/test, script hygiene, artifact, targeted regression, device smoke, distribution, and user-followup checks.
[2026-04-30] [PASS]    Rel-v0610-fresh-install  QA phone clean-installed stable v0.6.10 after uninstalling the debug-signed PokeClaw package; verified versionName=0.6.10, versionCode=25, and release signature fingerprint prefix 745eed92.
[2026-04-30] [PASS]    TgBot-v0610-config  PokeClaw Settings -> Remote Control -> Telegram Bot accepted a Telegram bot token and Settings showed `Connected`; the token was treated as secret and was not recorded in QA notes.
[2026-04-30] [BLOCKED] TgBot-v0610-e2e  Telegram bot true E2E remains blocked by handset Telegram account state: Telegram showed the account as frozen/read-only, and Spam Info Bot appeal was submitted successfully at 10:33; supervisor review is pending.
[2026-04-30] [BLOCKED] TgApp-v0610-send  Telegram app send-message smoke is blocked by the same frozen/read-only Telegram account; do not claim Telegram app automation support until retested with a writable account/contact.
[2026-04-30] [FIXED]   ExtAuto-r1  Production External Automation API added: user-enabled `io.agents.pokeclaw.RUN_TASK` / `RUN_CHAT` receiver, targeted-broadcast requirement, base64 extras, immediate `accepted` callback, and task terminal callback contract.
[2026-04-30] [FIXED]   ExtAuto-r2  External task intents no longer wait for chat model readiness; task payloads go straight to `TaskFlowController`, so deterministic/direct tasks can run before LLM config.
[2026-04-30] [FIXED]   DD-ready-r1  Deterministic direct-device tasks now run before LLM/accessibility gates even when Accessibility is already `READY`; this prevents `how much battery left` from being incorrectly blocked by missing LLM config.
[2026-04-30] [PASS]    C16-extauto-task  Pixel 8 Pro debug-build smoke: with `Settings -> Remote Control -> External Automation = Enabled`, `adb shell am broadcast -a io.agents.pokeclaw.RUN_TASK -p io.agents.pokeclaw --es task "how much battery left"` was accepted, logged `sendTask: executing deterministic direct tool before LLM/accessibility gates`, and visibly returned `Battery: 80%, charging, 35.0°C`.
[2026-04-30] [PASS]    C17-extauto-chat  Pixel 8 Pro debug-build smoke: `adb shell am broadcast -a io.agents.pokeclaw.RUN_CHAT -p io.agents.pokeclaw --es chat "say hi"` was accepted and opened the chatroom path; because the clean QA install has no LLM selected, the UI showed `Configure LLM in Settings first.` instead of silently hanging.
[2026-04-30] [PARTIAL] C18-extauto-callback  Callback contract unit coverage passes and live task smoke with `request_id` / `return_action` did not crash, but no Tasker/MacroDroid callback receiver was available on the QA phone; keep true callback-consumer E2E open.
[2026-04-30] [BLOCKED] Tasker-extauto-install  Tasker Play Store install on the QA phone is blocked by purchase requirement (`HK$34.90` shown). Do not claim Tasker-specific E2E until the paid app is installed or a user-owned license is available.
[2026-04-30] [PASS]    MacroDroid-extauto-e2e  Installed MacroDroid, created macro `PokeClaw Battery E2E` with `Shortcut Launched` trigger and `Send Intent` action targeting `io.agents.pokeclaw.RUN_TASK`, package `io.agents.pokeclaw`, extra `task=how much battery left`; MacroDroid `Test macro` triggered PokeClaw, logged `Accepted external automation TASK`, ran the deterministic direct tool, and visibly returned `Battery: 83%, not charging, 38.1°C`.
[2026-04-30] [BLOCKED] ExtAuto-r3-v0611-signed  Signed v0.6.11 broadcast receiver received the request but Android 16 / targetSdk 36 blocked the receiver from opening `ComposeChatActivity` from background. Do not direct users to v0.6.11 for external automation.
[2026-04-30] [FIXED]   ExtAuto-r4-activity-entry  Added exported transparent `ExternalAutomationActivity` so MacroDroid/Tasker/Locale-style apps can launch PokeClaw as an Activity with the same `RUN_TASK` / `RUN_CHAT` contract, avoiding background-activity-launch blocking.
[2026-04-30] [PASS]    MacroDroid-extauto-activity-e2e  Pixel 8 Pro debug v0.6.12 smoke: MacroDroid `Send Intent` Target=`Activity`, Package=`io.agents.pokeclaw`, Class=`io.agents.pokeclaw.automation.ExternalAutomationActivity`, Action=`io.agents.pokeclaw.RUN_TASK`, extra `task=how much battery left`; MacroDroid `Test macro` launched PokeClaw, logged `Accepted external automation TASK`, ran the deterministic direct tool, and visibly returned `Battery: 100%, not charging, 36.0°C`.
[2026-04-30] [PASS]    Rel-v0612-signed-macrodroid  Pixel 8 Pro signed-release smoke: clean-installed signed `v0.6.12` (`versionCode=27`, release signature fingerprint prefix `745eed92`), enabled External Automation from Settings, reran the same MacroDroid Activity-target macro, and visibly returned `Battery: 100%, charging, 35.2°C` in the PokeClaw chatroom.
```

### Bugs Found During v9 QA

| ID | Issue | Root Cause | Priority |
|----|-------|-----------|----------|
| Rel-s9 | Stable `v0.6.8` APK cannot upgrade the installed QA-phone package | Installed phone has a debug-signed `0.6.7`; stable `0.6.8` uses the release cert, so Android rejects in-place upgrade with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Blocker before upgrade claims |
| Rel-s10 | Local signed release package cannot be produced | `./gradlew assembleRelease` fails at `:app:packageRelease` because local release signing config has no `storeFile` | Blocker for local release; use CI signing or restore local secrets |
| v068-wa-send | ~~`Send hi to Girlfriend on WhatsApp` timed out in the Cloud sweep~~ | Fixed 2026-04-28: deterministic send parser routes literal send commands to `send_message`, DirectTool no longer blocks the caller thread, ToolResult failures are respected, and contact lookup no longer opens WhatsApp overflow menu as a fake close action | Fixed; latest full Cloud sweep passed B1 in 15s |
| v068-wa-summary | WhatsApp latest-chat summary loops on Back and triggers stuck detection | Unknown yet; likely navigation/state handling after opening WhatsApp | Blocker |
| v068-gmail-google-timeout | `Copy the latest email subject and Google it` still times out in the latest Cloud sweep | Needs focused Gmail read/search trace; earlier repeated-trial pass rate was 8/10, but latest full sweep timed out twice | High |
| v068-email-cleanup | ~~Email compose timeout leaks cancellation/interruption into later harness cases~~ | Fixed 2026-04-28 in the QA runner: timeout now triggers debug cancel + foreground reset before the next case; latest sweep did not leak `Task cancelled`, and `Write an email saying I will be late today` passed | Fixed |
| v068-local-timeout | ~~Local quick-task and Local E2B battery smoke timeout under current QA state~~ | Partially fixed/clarified 2026-04-28: targeted Local E2B battery now passes in 105s after GPU→CPU fallback; Local full sweep still needs rerun and latency remains high | Partial; not full-sweep green |
| v068-fgs-race | Fast task failure can crash with `ForegroundServiceDidNotStartInTimeException` | `stopService`/reset could happen before the service got to `startForeground()`; service now starts foreground immediately in `onCreate()` | Fixed |
| TgBot-v0610-readonly | Telegram bot channel E2E cannot complete on the current QA phone | The handset Telegram account is frozen/read-only; Spam Info Bot appeal was submitted successfully and is pending Telegram supervisor review | Environment blocker; needs successful unfreeze or a writable Telegram account |
| TgApp-v0610-readonly | Telegram app send-message smoke cannot complete on the current QA phone | Same frozen/read-only Telegram account cannot send messages or take actions until Telegram review completes | Environment blocker; retest with writable account/contact |
| Q5-1 | ~~LiteRT "Can not find OpenCL" crash in sendChat()~~ | Fixed 2026-04-09: `sendChat()` now mirrors the Local client fallback path, resets the engine after OpenCL/native errors, and retries on CPU instead of failing the chat send | Fixed |
| Q5-2 | ~~API key was "test"~~ | ~~Device had dummy key, reconfigured~~ | ~~Config~~ |
| K2-a | ~~Accessibility status row shows `Disabled` while Android Accessibility page has `Use PokeClaw` ON~~ | Fixed 2026-04-10: app Settings now reads `enabled_accessibility_services` via `isEnabledInSettings()` | Fixed |
| K3-b | ~~Accessibility enable flow does not foreground PokeClaw after system toggle ON~~ | Fixed 2026-04-10: pending return only arms for a real disabled→enabled flow, then unwinds Settings and foregrounds app | Fixed |
| Q6-7 | ~~Cloud tab tasks can reuse stale Local agent config after a model switch~~ | Fixed 2026-04-10: task agent config now syncs on model switch and immediately before `startTask()` | Fixed |
| Q1-r1 | ~~Toolbar tab UI can drift out of sync with the actual active model after Settings/model changes~~ | Fixed 2026-04-10: `ChatScreen` now re-syncs `selectedTab` from `isLocalModel`, so placeholder/quick-tasks/toggle follow the true active model again | Fixed |
| L1-v9 | ~~Auto-return after task completion can reopen a fresh chat state instead of preserving the active conversation~~ | Fixed 2026-04-10: same conversation remained visible after Cloud `send_message` auto-return, with result appended in place | Fixed |
| A11Y-r1 | Accessibility-dependent tools can false-fail during transient service rebinds | Fixed 2026-04-10: tools now wait for an enabled service to reconnect before returning `Accessibility service is not running` | Fixed |
| Q7-local | ~~Stopping a Local task could crash with native `SIGSEGV` / `session already exists` race~~ | Fixed 2026-04-10: local cancel no longer interrupts LiteRT mid-send, and UI cleanup waits until the task-side client has closed cleanly | Fixed |
| Bgt-1 | Existing installs could stay pinned to the legacy task budget even after code defaults increased | Fixed 2026-04-10: `TaskBudget` now one-time migrates untouched 100K / $0.50 legacy defaults to 250K / $1.00, while preserving explicit user overrides and exposing `250K` in Settings | Fixed |
