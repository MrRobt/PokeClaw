#!/usr/bin/env python3
"""Fast self-test for release helper scripts.

This catches argparse/default-value regressions without requiring an Android
device, release signing secrets, or a real Cloud LLM API key.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def run(command: list[str]) -> None:
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    if result.returncode == 0:
        print(f"OK: {' '.join(command)}")
        return
    if result.stdout:
        print(result.stdout)
    if result.stderr:
        print(result.stderr, file=sys.stderr)
    raise SystemExit(f"FAIL: {' '.join(command)}")


def main() -> int:
    python = sys.executable
    scripts = [
        "scripts/release_gate_check.py",
        "scripts/verify_release_artifact.py",
        "scripts/release_device_smoke.py",
        "scripts/release_missed_call_smoke.py",
        "scripts/release_cloud_llm_smoke.py",
        "scripts/release_upgrade_smoke.py",
        "scripts/commercial_readiness_audit.py",
    ]
    run([python, "-m", "py_compile", *scripts])

    help_targets = [
        "scripts/release_gate_check.py",
        "scripts/verify_release_artifact.py",
        "scripts/release_device_smoke.py",
        "scripts/release_missed_call_smoke.py",
        "scripts/release_cloud_llm_smoke.py",
        "scripts/release_upgrade_smoke.py",
        "scripts/commercial_readiness_audit.py",
    ]
    for script in help_targets:
        run([python, script, "--help"])

    run(
        [
            python,
            "scripts/release_cloud_llm_smoke.py",
            "--provider",
            "CUSTOM",
            "--base-url",
            "https://example.invalid/v1",
            "--model",
            "example-model",
            "--api-key",
            "placeholder",
            "--preflight-only",
            "--distribution",
            "play",
        ]
    )
    run(
        [
            python,
            "scripts/release_cloud_llm_smoke.py",
            "--provider",
            "OPENAI",
            "--api-key",
            "placeholder",
            "--preflight-only",
            "--distribution",
            "play",
        ]
    )
    print("OK: release script self-test passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
