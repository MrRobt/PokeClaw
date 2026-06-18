#!/usr/bin/env python3
"""Release-device smoke test for missed-call follow-up.

This test uses the production Settings UI to opt in, then simulates an emulator
GSM missed call and verifies the chat status card. It is intended for Android
emulators only because it grants SEND_SMS and uses `adb emu gsm`.
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import release_device_smoke


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = release_device_smoke.PACKAGE
CHAT_ACTIVITY = f"{PACKAGE}/io.agents.pokeclaw.ui.chat.ComposeChatActivity"
TEST_PHONE_NUMBER = "15550222222"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"OK: {message}")


def warn(message: str) -> None:
    print(f"WARN: {message}")


def scroll_up(smoke: release_device_smoke.Smoke) -> None:
    width, height = smoke.screen_size()
    x = width // 2
    smoke.shell(f"input swipe {x} {int(height * 0.35)} {x} {int(height * 0.88)} 500", timeout=15)
    time.sleep(0.6)


def reset_scroll_top(smoke: release_device_smoke.Smoke) -> None:
    for _ in range(8):
        scroll_up(smoke)


def launch_chat(smoke: release_device_smoke.Smoke, wait_seconds: float = 6.0) -> None:
    smoke.launch()
    time.sleep(wait_seconds)
    smoke.maybe_tap_notification_allow()
    leave_settings_if_needed(smoke)
    text = smoke.visible_text()
    if "Update Available" in text:
        fail("Release startup showed stale update prompt")
    if is_settings_screen(text):
        print(text)
        fail("Settings screen remained visible when chat was expected")
    if "Settings" not in text:
        print(text)
        fail("Chat screen did not become visible after launch")


def is_settings_screen(text: str) -> bool:
    return (
        "Permissions" in text
        and "Accessibility Service" in text
        and "LLM Config" in text
    )


def leave_settings_if_needed(smoke: release_device_smoke.Smoke) -> None:
    for _ in range(3):
        text = smoke.visible_text()
        if not is_settings_screen(text):
            return
        smoke.shell("input keyevent KEYCODE_BACK", timeout=15)
        time.sleep(1.2)


def grant_runtime_permission(smoke: release_device_smoke.Smoke, permission: str) -> None:
    output = smoke.shell(f"pm grant {PACKAGE} {permission}", timeout=15, check=False)
    if "Exception" in output or "not a changeable permission type" in output:
        print(output)
        fail(f"Could not grant {permission}")


def grant_missed_call_permissions(smoke: release_device_smoke.Smoke) -> None:
    grant_runtime_permission(smoke, "android.permission.READ_PHONE_STATE")
    grant_runtime_permission(smoke, "android.permission.READ_CALL_LOG")
    grant_runtime_permission(smoke, "android.permission.SEND_SMS")
    ok("Granted emulator READ_PHONE_STATE, READ_CALL_LOG, and SEND_SMS permissions")


def scroll_to_missed_call_row(smoke: release_device_smoke.Smoke) -> None:
    reset_scroll_top(smoke)
    for _ in range(8):
        if smoke.find_node("Missed Call Follow-up"):
            return
        smoke.scroll_down()
    fail("Missed Call Follow-up row not found in Settings")


def row_state(smoke: release_device_smoke.Smoke) -> str | None:
    nodes = smoke.dump_ui()
    title = next((node for node in nodes if node.label == "Missed Call Follow-up"), None)
    if not title:
        return None
    for state in ["Enabled", "Disabled"]:
        for node in nodes:
            if node.label == state and abs(node.cy - title.cy) <= 160:
                return state
    return None


def assert_missed_call_state(smoke: release_device_smoke.Smoke, expected: str) -> None:
    scroll_to_missed_call_row(smoke)
    actual = row_state(smoke)
    if actual != expected:
        print(smoke.visible_text())
        fail(f"Missed Call Follow-up row state {actual!r} did not match {expected!r}")
    ok(f"Missed Call Follow-up row is {expected}")


def open_settings(smoke: release_device_smoke.Smoke) -> None:
    text = smoke.visible_text()
    if is_settings_screen(text):
        return
    if not smoke.find_node("Settings"):
        launch_chat(smoke, wait_seconds=4.0)
    smoke.open_settings()


def enable_missed_call_followup(smoke: release_device_smoke.Smoke) -> None:
    open_settings(smoke)
    assert_missed_call_state(smoke, "Disabled")
    smoke.tap_label("Missed Call Follow-up")
    time.sleep(1.0)

    if smoke.find_node("Enable Missed Call Follow-up?", contains=True):
        smoke.tap_label("Enable")
        time.sleep(1.0)
    else:
        print(smoke.visible_text())
        fail("Missed Call Follow-up confirmation dialog did not appear")

    smoke.maybe_tap_notification_allow()
    time.sleep(1.0)
    if smoke.find_node("SMS Template"):
        smoke.tap_label("Save")
        time.sleep(1.0)

    assert_missed_call_state(smoke, "Enabled")
    ok("Enabled Missed Call Follow-up through Settings UI")


def simulate_missed_call(smoke: release_device_smoke.Smoke, phone_number: str) -> None:
    smoke.adb_cmd("emu", "gsm", "cancel", phone_number, timeout=15, check=False)
    output = smoke.adb_cmd("emu", "gsm", "call", phone_number, timeout=15, check=False)
    if "KO" in output or "ERROR" in output.upper():
        print(output)
        fail("Could not start emulator GSM call")
    time.sleep(2.0)
    output = smoke.adb_cmd("emu", "gsm", "cancel", phone_number, timeout=15, check=False)
    if "KO" in output or "ERROR" in output.upper():
        print(output)
        fail("Could not cancel emulator GSM call")
    ok(f"Simulated missed GSM call from {phone_number}")


def followup_card_visible(text: str) -> bool:
    return (
        "Missed Call Follow-up" in text
        and (
            "Missed call follow-up sent to" in text
            or "Missed call follow-up failed for" in text
        )
    )


def assert_no_followup_card(smoke: release_device_smoke.Smoke) -> None:
    launch_chat(smoke, wait_seconds=4.0)
    text = smoke.visible_text()
    if followup_card_visible(text):
        print(text)
        fail("Missed-call follow-up card appeared while feature was disabled")
    ok("Disabled missed-call flow produced no chat follow-up card")


def wait_for_followup_card(
    smoke: release_device_smoke.Smoke,
    timeout_seconds: int,
) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        launch_chat(smoke, wait_seconds=3.0)
        text = smoke.visible_text()
        if followup_card_visible(text):
            ok("Missed-call follow-up chat card appeared")
            return
        time.sleep(3.0)
    print(smoke.visible_text())
    fail("Timed out waiting for missed-call follow-up chat card")


def assert_emulator(smoke: release_device_smoke.Smoke) -> None:
    output = smoke.shell("getprop ro.kernel.qemu", timeout=15, check=False).strip()
    if output != "1":
        fail("This smoke requires an Android emulator because it uses `adb emu gsm`")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--adb", default=release_device_smoke.default_adb())
    parser.add_argument("--serial", help="ADB serial. Defaults to adb's selected device")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--phone-number", default=TEST_PHONE_NUMBER)
    parser.add_argument("--timeout-seconds", type=int, default=75)
    args = parser.parse_args()

    smoke = release_device_smoke.Smoke(args.adb, args.serial)
    assert_emulator(smoke)

    apk = (args.apk or release_device_smoke.find_latest_release_apk("direct")).resolve()
    if not apk.exists():
        fail(f"APK not found: {apk}")

    if not args.skip_install:
        smoke.install_clean(apk)

    smoke.clear_logcat()
    launch_chat(smoke)
    smoke.assert_no_crash_logs()

    simulate_missed_call(smoke, args.phone_number)
    time.sleep(10.0)
    assert_no_followup_card(smoke)

    grant_missed_call_permissions(smoke)
    enable_missed_call_followup(smoke)
    simulate_missed_call(smoke, args.phone_number)
    wait_for_followup_card(smoke, args.timeout_seconds)
    smoke.assert_no_crash_logs()
    ok("Release missed-call smoke passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
