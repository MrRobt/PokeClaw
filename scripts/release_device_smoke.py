#!/usr/bin/env python3
"""Device smoke test for a release PokeClaw APK.

Requires one connected Android device or emulator. The script performs a clean
install, verifies startup, release-only safety behavior, and a production
External Automation direct-data task.
"""

from __future__ import annotations

import argparse
import base64
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_APK_DIR = ROOT / "app" / "build" / "outputs" / "apk"
PACKAGE = "io.agents.pokeclaw"
SPLASH_ACTIVITY = f"{PACKAGE}/io.agents.pokeclaw.ui.splash.SplashActivity"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"OK: {message}")


def warn(message: str) -> None:
    print(f"WARN: {message}")


def default_adb() -> str:
    found = shutil.which("adb")
    if found:
        return found
    candidates = [
        os.environ.get("ADB"),
        os.environ.get("ANDROID_HOME") and str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb"),
        os.environ.get("ANDROID_SDK_ROOT") and str(Path(os.environ["ANDROID_SDK_ROOT"]) / "platform-tools" / "adb"),
        "D:\\android-sdk\\platform-tools\\adb.exe",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    fail("Could not find adb. Set ADB or ANDROID_HOME.")


def find_latest_release_apk(distribution: str) -> Path:
    search_dir = DEFAULT_APK_DIR / distribution / "release"
    apks = sorted(search_dir.glob("*.apk"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not apks:
        fail(f"No {distribution} release APK found under {search_dir.relative_to(ROOT)}")
    return apks[0]


@dataclass
class UiNode:
    text: str
    desc: str
    bounds: tuple[int, int, int, int]
    resource_id: str = ""
    class_name: str = ""

    @property
    def cx(self) -> int:
        return (self.bounds[0] + self.bounds[2]) // 2

    @property
    def cy(self) -> int:
        return (self.bounds[1] + self.bounds[3]) // 2

    @property
    def label(self) -> str:
        return self.text or self.desc


class Smoke:
    def __init__(self, adb: str, serial: str | None):
        self.adb = adb
        self.serial = serial

    def adb_cmd(self, *args: str, timeout: int = 30, check: bool = True) -> str:
        cmd = [self.adb]
        if self.serial:
            cmd.extend(["-s", self.serial])
        cmd.extend(args)
        result = subprocess.run(
            cmd,
            cwd=ROOT,
            text=True,
            encoding="utf-8",
            errors="replace",
            capture_output=True,
            timeout=timeout,
        )
        output = (result.stdout or "") + (result.stderr or "")
        if check and result.returncode != 0:
            print(output)
            fail(f"adb command failed: {' '.join(cmd)}")
        return output

    def shell(self, command: str, timeout: int = 30, check: bool = True) -> str:
        return self.adb_cmd("shell", command, timeout=timeout, check=check)

    def clear_logcat(self) -> None:
        self.adb_cmd("logcat", "-c", timeout=15)

    def dump_logcat(self) -> str:
        return self.adb_cmd("logcat", "-d", "-v", "time", timeout=30, check=False)

    def install_clean(self, apk: Path) -> None:
        self.adb_cmd("uninstall", PACKAGE, timeout=60, check=False)
        self.adb_cmd("install", "-r", str(apk), timeout=180)
        ok(f"Installed {apk.name}")

    def launch(self) -> str:
        output = self.shell(f"am start -W -n {SPLASH_ACTIVITY}", timeout=45)
        if "Status: ok" not in output:
            print(output)
            fail("App launch did not report Status: ok")
        ok("Launch reported Status: ok")
        return output

    def screen_size(self) -> tuple[int, int]:
        output = self.shell("wm size", timeout=15)
        match = re.search(r"Physical size:\s*(\d+)x(\d+)", output)
        if not match:
            return (1080, 2400)
        return int(match.group(1)), int(match.group(2))

    def dump_ui(self) -> list[UiNode]:
        self.shell("uiautomator dump /sdcard/pokeclaw-smoke.xml", timeout=30)
        xml = self.shell("cat /sdcard/pokeclaw-smoke.xml", timeout=30)
        try:
            root = ET.fromstring(xml)
        except ET.ParseError as exc:
            fail(f"Could not parse UIAutomator XML: {exc}")

        nodes: list[UiNode] = []
        for elem in root.iter("node"):
            bounds = elem.attrib.get("bounds", "")
            match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if not match:
                continue
            text = elem.attrib.get("text", "")
            desc = elem.attrib.get("content-desc", "")
            nodes.append(
                UiNode(
                    text=text,
                    desc=desc,
                    bounds=tuple(map(int, match.groups())),
                    resource_id=elem.attrib.get("resource-id", ""),
                    class_name=elem.attrib.get("class", ""),
                )
            )
        return nodes

    def visible_text(self) -> str:
        return "\n".join(node.label for node in self.dump_ui() if node.label)

    def find_node(self, label: str, *, contains: bool = False) -> UiNode | None:
        for node in self.dump_ui():
            candidates = [node.text, node.desc]
            for value in candidates:
                if contains and label in value:
                    return node
                if not contains and value == label:
                    return node
        return None

    def tap(self, node: UiNode) -> None:
        self.shell(f"input tap {node.cx} {node.cy}", timeout=15)

    def tap_label(self, label: str, *, contains: bool = False) -> None:
        node = self.find_node(label, contains=contains)
        if not node:
            fail(f"Could not find UI label: {label}")
        self.tap(node)

    def scroll_down(self) -> None:
        width, height = self.screen_size()
        x = width // 2
        self.shell(f"input swipe {x} {int(height * 0.85)} {x} {int(height * 0.42)} 500", timeout=15)
        time.sleep(0.8)

    def assert_no_crash_logs(self) -> None:
        logs = self.dump_logcat()
        forbidden = ["FATAL EXCEPTION", f"ANR in {PACKAGE}"]
        for pattern in forbidden:
            if pattern in logs:
                fail(f"Crash/ANR pattern found in logcat: {pattern}")
        ok("No FATAL/ANR logcat patterns")

    def open_settings(self) -> None:
        self.tap_label("Settings")
        time.sleep(1.0)
        if not self.find_node("Settings"):
            fail("Settings screen did not open")
        ok("Opened Settings")

    def scroll_to_remote_control(self) -> None:
        for _ in range(6):
            text = self.visible_text()
            if "External Automation" in text:
                return
            self.scroll_down()
        fail("External Automation row not found in Settings")

    def verify_settings_defaults(self, distribution: str = "direct") -> None:
        self.scroll_to_remote_control()
        text = self.visible_text()
        if distribution == "play":
            if "Missed Call Follow-up" in text:
                fail("Play build must not show Missed Call Follow-up")
            ok("Play Settings hides missed-call follow-up")
        else:
            if "Missed Call Follow-up" not in text or "Disabled" not in text:
                fail("Missed-call follow-up default disabled state was not visible")
            ok("Direct Settings shows missed-call disabled")
        if "External Automation" not in text:
            fail("External Automation row was not visible")
        ok("Settings defaults visible and External Automation row present")

    def maybe_tap_notification_allow(self) -> bool:
        for label in ["ALLOW", "Allow", "While using the app"]:
            node = self.find_node(label)
            if node:
                self.tap(node)
                time.sleep(1.0)
                ok(f"Accepted notification permission via `{label}`")
                return True
        return False

    def enable_external_automation(self) -> None:
        self.scroll_to_remote_control()
        self.tap_label("External Automation")
        time.sleep(0.8)
        if not self.find_node("Enable External Automation?"):
            fail("External Automation confirmation dialog did not appear")
        self.tap_label("Enable")
        time.sleep(1.0)
        self.maybe_tap_notification_allow()
        ok("External Automation enable flow completed")

    def broadcast_task(self, action: str, task: str) -> None:
        payload = base64.b64encode(task.encode("utf-8")).decode("ascii")
        self.shell(f"am broadcast -a {action} -p {PACKAGE} --es task_b64 {payload}", timeout=30)

    def verify_debug_task_disabled(self) -> None:
        self.clear_logcat()
        self.broadcast_task(f"{PACKAGE}.DEBUG_TASK", "how much battery left")
        time.sleep(3.0)
        text = self.visible_text()
        if "Battery:" in text or "how much battery left" in text:
            fail("DEBUG_TASK affected release UI")
        ok("DEBUG_TASK did not affect release UI")

    def verify_external_task(self) -> None:
        self.clear_logcat()
        self.broadcast_task(f"{PACKAGE}.RUN_TASK", "how much battery left")
        time.sleep(7.0)
        text = self.visible_text()
        if "Battery:" not in text:
            print(text)
            fail("External Automation task did not return battery result")
        ok("External Automation RUN_TASK returned battery result")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--adb", default=default_adb())
    parser.add_argument("--serial", help="ADB serial. Defaults to adb's selected device")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--distribution", choices=["direct", "play"], default="direct")
    args = parser.parse_args()

    smoke = Smoke(args.adb, args.serial)
    apk = (args.apk or find_latest_release_apk(args.distribution)).resolve()
    if not apk.exists():
        fail(f"APK not found: {apk}")

    if not args.skip_install:
        smoke.install_clean(apk)
    smoke.clear_logcat()
    launch_output = smoke.launch()
    print(launch_output.strip())
    time.sleep(8.0)

    text = smoke.visible_text()
    if "Update Available" in text:
        fail("Release startup showed stale update prompt")
    ok("No startup update prompt")
    smoke.assert_no_crash_logs()

    smoke.verify_debug_task_disabled()
    smoke.open_settings()
    smoke.verify_settings_defaults(args.distribution)
    smoke.enable_external_automation()
    smoke.verify_external_task()
    smoke.assert_no_crash_logs()
    ok("Release device smoke passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
