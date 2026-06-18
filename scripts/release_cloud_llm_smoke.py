#!/usr/bin/env python3
"""Release-device smoke test for a real Cloud LLM chat path.

The API key is read from CLI args or environment variables and is never printed.
This script intentionally uses the production Settings UI to configure the model
instead of writing app storage directly.
"""

from __future__ import annotations

import argparse
import base64
import os
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

DEFAULT_PROMPT = (
    "Reply with these uppercase tokens joined by hyphens, and no other words: "
    "POKECLAW, CLOUD, SMOKE, OK."
)
DEFAULT_EXPECTED = "POKECLAW-CLOUD-SMOKE-OK"
KNOWN_MODEL_LABELS = {
    "gpt-4o-mini": "GPT-4o Mini",
    "gpt-4o": "GPT-4o",
    "gpt-4.1": "GPT-4.1",
    "gpt-4.1-mini": "GPT-4.1 Mini",
    "gpt-4.1-nano": "GPT-4.1 Nano",
    "claude-sonnet-4-6": "Claude Sonnet 4.6",
    "claude-haiku-4-5": "Claude Haiku 4.5",
    "gemini-2.5-flash": "Gemini 2.5 Flash",
    "gemini-2.5-pro": "Gemini 2.5 Pro",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"OK: {message}")


def env_or_empty(name: str) -> str:
    return os.environ.get(name, "").strip()


def shell_quote(value: str) -> str:
    if "\n" in value or "\r" in value:
        fail("ADB text input does not support newline characters")
    return "'" + value.replace("'", "'\\''").replace(" ", "%s") + "'"


def required(value: str | None, name: str) -> str:
    normalized = (value or "").strip()
    if not normalized:
        fail(f"Missing required {name}")
    return normalized


def find_resource(
    smoke: release_device_smoke.Smoke,
    resource_name: str,
    *,
    max_scrolls: int = 0,
) -> release_device_smoke.UiNode:
    expected = f"{PACKAGE}:id/{resource_name}"
    for _ in range(max_scrolls + 1):
        for node in smoke.dump_ui():
            if node.resource_id == expected:
                return node
        if max_scrolls:
            smoke.scroll_down()
    fail(f"Could not find UI resource: {resource_name}")


def scroll_to_text(smoke: release_device_smoke.Smoke, label: str, *, max_scrolls: int = 8) -> None:
    for _ in range(max_scrolls + 1):
        if smoke.find_node(label) or smoke.find_node(label, contains=True):
            return
        smoke.scroll_down()
    fail(f"Could not find UI text: {label}")


def scroll_up(smoke: release_device_smoke.Smoke) -> None:
    width, height = smoke.screen_size()
    x = width // 2
    smoke.shell(f"input swipe {x} {int(height * 0.35)} {x} {int(height * 0.88)} 500", timeout=15)
    time.sleep(0.6)


def reset_scroll_top(smoke: release_device_smoke.Smoke) -> None:
    for _ in range(8):
        scroll_up(smoke)


def clear_focused_field(smoke: release_device_smoke.Smoke) -> None:
    smoke.shell("input keyevent KEYCODE_MOVE_END", timeout=10, check=False)
    smoke.shell("for i in $(seq 1 256); do input keyevent KEYCODE_DEL; done", timeout=30, check=False)


def set_text_resource(smoke: release_device_smoke.Smoke, resource_name: str, value: str) -> None:
    node = find_resource(smoke, resource_name, max_scrolls=8)
    smoke.tap(node)
    time.sleep(0.4)
    clear_focused_field(smoke)
    output = smoke.shell(f"input text {shell_quote(value)}", timeout=30, check=False)
    if "Error" in output or "Exception" in output:
        fail(f"Failed to input text for {resource_name}")


def open_llm_config(smoke: release_device_smoke.Smoke) -> None:
    text = smoke.visible_text()
    if "LLM Config" not in text and "Permissions" not in text:
        smoke.open_settings()
    reset_scroll_top(smoke)
    scroll_to_text(smoke, "LLM Config", max_scrolls=8)
    smoke.tap_label("LLM Config")
    time.sleep(1.0)
    if not smoke.find_node("Cloud LLM", contains=True):
        scroll_to_text(smoke, "Cloud LLM", max_scrolls=10)
    ok("Opened LLM Config")


def configure_cloud_model(
    smoke: release_device_smoke.Smoke,
    provider: str,
    api_key: str,
    model: str,
    base_url: str,
    model_label: str,
) -> None:
    provider = provider.upper()
    provider_label = provider.capitalize() if provider != "OPENAI" else "OpenAI"
    if provider == "CUSTOM":
        provider_label = "Custom"

    scroll_to_text(smoke, "Cloud LLM", max_scrolls=10)
    scroll_to_text(smoke, provider_label, max_scrolls=2)
    smoke.tap_label(provider_label)
    time.sleep(0.8)

    set_text_resource(smoke, "etApiKey", api_key)

    if provider == "CUSTOM":
        if not base_url:
            fail("POKECLAW_LLM_BASE_URL or --base-url is required for CUSTOM provider")
        if not model:
            fail("POKECLAW_LLM_MODEL or --model is required for CUSTOM provider")
        set_text_resource(smoke, "etBaseUrl", base_url)
        set_text_resource(smoke, "etModelName", model)
    else:
        scroll_to_text(smoke, model_label, max_scrolls=4)
        smoke.tap_label(model_label, contains=True)
        time.sleep(0.5)

    scroll_to_text(smoke, "Save & Activate", max_scrolls=4)
    smoke.tap_label("Save & Activate")
    time.sleep(3.0)
    ok(f"Saved Cloud LLM config for {provider_label} / {model or model_label}")


def ensure_external_automation(smoke: release_device_smoke.Smoke) -> None:
    text = smoke.visible_text()
    if "Settings" not in text:
        smoke.open_settings()
    smoke.scroll_to_remote_control()
    text = smoke.visible_text()
    if "External Automation" in text and "Enabled" in text:
        ok("External Automation already enabled")
        return
    smoke.enable_external_automation()


def broadcast_chat(smoke: release_device_smoke.Smoke, prompt: str) -> None:
    payload = base64.b64encode(prompt.encode("utf-8")).decode("ascii")
    smoke.shell(f"am broadcast -a {PACKAGE}.RUN_CHAT -p {PACKAGE} --es chat_b64 {payload}", timeout=30)


def wait_for_response(smoke: release_device_smoke.Smoke, expected: str, timeout_seconds: int) -> None:
    before = smoke.visible_text()
    if expected in before:
        fail("Expected response marker is already visible before sending the Cloud LLM prompt")

    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        text = smoke.visible_text()
        if expected in text:
            ok("Cloud LLM chat response matched expected marker")
            return
        if "No model selected" in text or "No API key configured" in text:
            print(text)
            fail("Cloud LLM chat did not use a configured cloud model")
        time.sleep(3.0)
    print(smoke.visible_text())
    fail(f"Timed out waiting for Cloud LLM response marker: {expected}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--adb", default=release_device_smoke.default_adb())
    parser.add_argument("--serial", help="ADB serial. Defaults to adb's selected device")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--distribution", choices=["direct", "play"], default="direct")
    parser.add_argument("--provider", default=env_or_empty("POKECLAW_LLM_PROVIDER") or "CUSTOM")
    parser.add_argument("--base-url", default=env_or_empty("POKECLAW_LLM_BASE_URL"))
    parser.add_argument("--model", default=env_or_empty("POKECLAW_LLM_MODEL"))
    parser.add_argument("--model-label", default=env_or_empty("POKECLAW_LLM_MODEL_LABEL"))
    parser.add_argument("--api-key", default=env_or_empty("POKECLAW_LLM_API_KEY"))
    parser.add_argument("--prompt", default=env_or_empty("POKECLAW_LLM_PROMPT") or DEFAULT_PROMPT)
    parser.add_argument("--expected", default=env_or_empty("POKECLAW_LLM_EXPECTED") or DEFAULT_EXPECTED)
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--preflight-only", action="store_true", help="Validate Cloud LLM smoke inputs only")
    args = parser.parse_args()

    provider = args.provider.strip().upper()
    if provider not in {"OPENAI", "ANTHROPIC", "GOOGLE", "CUSTOM"}:
        fail("Provider must be one of OPENAI, ANTHROPIC, GOOGLE, CUSTOM")

    api_key = required(args.api_key, "POKECLAW_LLM_API_KEY or --api-key")
    model = args.model.strip()
    base_url = args.base_url.strip()
    if provider == "CUSTOM":
        base_url = required(base_url, "POKECLAW_LLM_BASE_URL or --base-url for CUSTOM provider")
        model = required(model, "POKECLAW_LLM_MODEL or --model for CUSTOM provider")
    if provider != "CUSTOM" and not model:
        model = {
            "OPENAI": "gpt-4o-mini",
            "ANTHROPIC": "claude-haiku-4-5",
            "GOOGLE": "gemini-2.5-flash",
        }[provider]
    model_label = args.model_label.strip() or KNOWN_MODEL_LABELS.get(model, model)
    if args.preflight_only:
        ok(f"Cloud LLM smoke inputs validated for {provider} / {model or model_label}")
        return 0

    smoke = release_device_smoke.Smoke(args.adb, args.serial)
    apk = (args.apk or release_device_smoke.find_latest_release_apk(args.distribution)).resolve()
    if not apk.exists():
        fail(f"APK not found: {apk}")

    if not args.skip_install:
        smoke.install_clean(apk)
    smoke.clear_logcat()
    launch_output = smoke.launch()
    print(launch_output.strip())
    time.sleep(8.0)
    if "Update Available" in smoke.visible_text():
        fail("Release startup showed stale update prompt")

    open_llm_config(smoke)
    configure_cloud_model(
        smoke,
        provider=provider,
        api_key=api_key,
        model=model,
        base_url=base_url,
        model_label=model_label,
    )
    ensure_external_automation(smoke)
    smoke.clear_logcat()
    broadcast_chat(smoke, args.prompt)
    wait_for_response(smoke, args.expected, args.timeout_seconds)
    smoke.assert_no_crash_logs()
    ok("Release Cloud LLM smoke passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
