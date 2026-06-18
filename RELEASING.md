# Releasing PokeClaw

This repo now assumes a single stable release signing key.

Once a public APK is shipped with that key, every later public APK must use the same key or Android will reject in-place upgrades.

## 1. Prepare the stable keystore once

Generate one long-lived release keystore and keep it outside the repo.

Recommended local inputs:

```bash
export KEYSTORE_FILE=/absolute/path/to/pokeclaw-release.keystore
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=pokeclaw-release
export KEY_PASSWORD=...
```

`app/build.gradle.kts` reads these values from either:

1. environment variables, or
2. `local.properties`

Do not commit either the keystore or the secrets.

## 2. Mirror the same key into GitHub Actions

The tag-based release workflow expects these repo secrets:

- `ANDROID_KEYSTORE_B64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_B64` should be the base64-encoded keystore file:

```bash
base64 -w 0 "$KEYSTORE_FILE"
```

Without these secrets, `.github/workflows/release.yml` will fail closed and refuse to publish a public APK.

## 3. Prepare a release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`
2. Add the changelog entry in `README.md`
3. Run the release preflight:

```bash
python scripts/release_gate_check.py
python scripts/release_scripts_selftest.py
python scripts/commercial_readiness_audit.py
```

`commercial_readiness_audit.py` runs the local commercial gates together:
release helper self-tests, Play-safe release gate, direct/play APK verification,
Play AAB verification, and direct/play unit tests. It prints
`BLOCKED_EXTERNAL` for evidence that cannot be proven from the repo alone, such
as the long-lived public signer, production Cloud LLM API key, writable
WhatsApp/Telegram/Gmail accounts, and direct-channel SMS policy evidence. Use
`--fail-on-external-blockers` for a final release checklist run that should fail
until those external records are attached.

For a real public signing run, require signing inputs too:

```bash
python scripts/release_gate_check.py --require-signing
```

This validates that the keystore file exists and that `keytool` can read the configured alias with the provided store and key passwords.

For a Google Play-bound release, also run:

```bash
python scripts/release_gate_check.py --require-signing --play-store
```

This passes only when the repo has a Play-safe artifact path: the `play` flavor
must remove restricted SMS/Call Log permissions such as `SEND_SMS`,
`READ_CALL_LOG`, and `READ_PHONE_STATE`, remove `MissedCallReceiver`, and disable
missed-call follow-up at runtime. Use `--play-store-policy-approved` only if you
intentionally submit a restricted-permission artifact with concrete Play Console
approval, default-handler status, or approved exception evidence attached to the
release record.

4. Build the direct-channel APK locally first:

```bash
./gradlew :app:assembleDirectRelease
sha256sum app/build/outputs/apk/direct/release/*.apk
```

For Google Play, build and verify the Play App Bundle instead. Google Play
requires Android App Bundles for new apps, so the Play-safe artifact is the AAB,
not the APK:

```bash
./gradlew :app:bundlePlayRelease
python scripts/verify_release_artifact.py \
  --aab app/build/outputs/bundle/playRelease/app-play-release.aab \
  --expected-version X.Y.Z \
  --distribution play
```

The AAB verifier checks bundle structure, JAR signing, signer certificate
SHA-256, Play-safe source overlays, and the manifest of a bundletool-generated
universal APK. It automatically uses the Gradle dependency cache when possible;
pass `--bundletool /path/to/bundletool.jar` only when you need to force a
specific standalone bundletool jar.

5. Smoke-test the signed APK on a device:

```bash
python scripts/release_device_smoke.py \
  --apk app/build/outputs/apk/direct/release/PokeClaw_direct_vX.Y.Z_YYYYMMDD_HHMMSS.apk \
  --distribution direct
```

For a Play-bound APK, smoke-test the Play flavor and verify the missed-call UI is
absent. This uses the generated APK as a local runtime proxy for the Play AAB:

```bash
python scripts/release_device_smoke.py \
  --apk app/build/outputs/apk/play/release/PokeClaw_play_vX.Y.Z_YYYYMMDD_HHMMSS.apk \
  --distribution play
```

6. Smoke-test missed-call follow-up on an Android emulator:

```bash
python scripts/release_missed_call_smoke.py --apk app/build/outputs/apk/direct/release/PokeClaw_direct_vX.Y.Z_YYYYMMDD_HHMMSS.apk
```

This clean-installs the APK, verifies the default disabled path, grants emulator
`READ_PHONE_STATE`, `READ_CALL_LOG`, and `SEND_SMS`, enables the feature through
Settings, simulates `adb emu gsm` missed calls, and verifies the visible chat
status card. Android only populates `TelephonyManager.EXTRA_INCOMING_NUMBER` for
`PHONE_STATE` receivers that hold both `READ_PHONE_STATE` and `READ_CALL_LOG`.

Commercial distribution note: Google Play treats `READ_CALL_LOG` and `SEND_SMS`
as restricted SMS/Call Log permissions. The direct flavor keeps missed-call SMS
follow-up for non-Play distribution or approved policy cases; the Play flavor is
the Play-safe artifact and intentionally omits that feature.

7. Smoke-test in-place upgrade from the latest public APK:

```bash
python scripts/release_upgrade_smoke.py \
  --reference-apk /path/to/PokeClaw_previous_public.apk \
  --candidate-apk app/build/outputs/apk/direct/release/PokeClaw_direct_vX.Y.Z_YYYYMMDD_HHMMSS.apk \
  --expected-candidate-version X.Y.Z
```

8. Smoke-test the real Cloud LLM chat path with a production API key:

```bash
export POKECLAW_LLM_PROVIDER=CUSTOM
export POKECLAW_LLM_BASE_URL=https://your-openai-compatible-endpoint/v1
export POKECLAW_LLM_MODEL=your-model-id
export POKECLAW_LLM_API_KEY=...
python scripts/release_cloud_llm_smoke.py \
  --apk app/build/outputs/apk/direct/release/PokeClaw_direct_vX.Y.Z_YYYYMMDD_HHMMSS.apk \
  --distribution direct
```

For OpenAI's default endpoint, set `POKECLAW_LLM_PROVIDER=OPENAI`; the script defaults to `gpt-4o-mini` unless `POKECLAW_LLM_MODEL` is set.

9. Push `main`
10. Push the tag:

```bash
git tag -a vX.Y.Z -m "vX.Y.Z"
git push pokeclaw vX.Y.Z
```

The GitHub Actions workflow will then create the GitHub Release, upload the signed APK, and attach `SHA256SUMS.txt`.
It also builds and verifies the Play-safe AAB and keeps it as the workflow artifact
`play-release-aab` for Play Console upload operations.
The release workflow runs the signing-required preflight with `--play-store`, so
CI fails before building if the Play-safe flavor stops removing restricted
SMS/Call Log permissions.

The workflow also verifies the release APK signer certificate SHA-256 against the public stable signer first shipped in `v0.7.1`:

```text
e000d1d6555b8fab20c03a5d9ddeba83944f26eecf0b978ac7affc2eebd43186
```

Both the direct APK and Play AAB artifact are checked against this signer digest.
Do not change this unless you intentionally plan a signing-key migration and
document the upgrade impact for existing users.

## Optional: local upgrade smoke test

To verify that the next public build can upgrade in place over the current signed build, create a temporary local build with the same key and a higher version:

```bash
export POKECLAW_VERSION_CODE=15
export POKECLAW_VERSION_NAME=0.5.1-upgrade-test
./gradlew --no-daemon :app:assembleDirectRelease -x lintVitalRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease
```

Then install the signed baseline APK first, followed by the higher-version APK with `adb install -r ...`.

## 4. Known historical limitation

The old public debug-signing path and the later public `v0.5.0` APK were signed with different keys.

That mismatch is already shipped, so Android cannot retroactively upgrade those installs in place without the original lost signing key. For that cohort, the only honest path is:

- show the in-app update prompt
- explain that Android may require a one-time uninstall + reinstall

Stable signing for the public `v0.6.x` line prevents this problem from repeating.
