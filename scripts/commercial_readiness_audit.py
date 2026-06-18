#!/usr/bin/env python3
"""Run the local commercial-readiness audit for PokeClaw.

This script intentionally separates local evidence from external release
evidence. Local gates fail the command. Missing public signing, production LLM,
third-party account, or policy evidence is reported as BLOCKED_EXTERNAL because
those cannot be proven from this repo alone.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "app" / "build.gradle.kts"
DIRECT_RELEASE_APK_DIR = ROOT / "app" / "build" / "outputs" / "apk" / "direct" / "release"
PLAY_RELEASE_APK_DIR = ROOT / "app" / "build" / "outputs" / "apk" / "play" / "release"
PLAY_RELEASE_AAB_DIR = ROOT / "app" / "build" / "outputs" / "bundle" / "playRelease"
PUBLIC_SIGNER_SHA256 = "e000d1d6555b8fab20c03a5d9ddeba83944f26eecf0b978ac7affc2eebd43186"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"OK: {message}")


def run_required(command: list[str]) -> None:
    print(f"\n$ {' '.join(command)}", flush=True)
    result = subprocess.run(command, cwd=ROOT, text=True)
    if result.returncode != 0:
        fail(f"Required local gate failed: {' '.join(command)}")


def run_probe(command: list[str]) -> bool:
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    return result.returncode == 0


def read_default_version() -> str:
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    match = re.search(
        r'versionName\s*=\s*readLocalOrEnvString\("POKECLAW_VERSION_NAME",\s*"([^"]+)"\)',
        text,
    )
    if not match:
        fail("Could not parse default versionName from app/build.gradle.kts")
    return match.group(1)


def single_artifact(directory: Path, pattern: str, label: str) -> Path:
    matches = sorted(directory.glob(pattern), key=lambda path: path.stat().st_mtime, reverse=True)
    if not matches:
        fail(f"Missing {label}; build release artifacts first under {directory.relative_to(ROOT)}")
    if len(matches) > 1:
        names = ", ".join(path.name for path in matches)
        fail(f"Expected exactly one {label}, found: {names}")
    return matches[0]


def require_workflow_selftest() -> None:
    for workflow in [".github/workflows/build.yml", ".github/workflows/release.yml"]:
        path = ROOT / workflow
        text = path.read_text(encoding="utf-8")
        if "python scripts/release_scripts_selftest.py" not in text:
            fail(f"{workflow} must run release_scripts_selftest.py")
    ok("GitHub workflows include release script self-test")


def signing_inputs_available() -> bool:
    required = ["KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"]
    if all(os.environ.get(name) for name in required):
        return True
    local_properties = ROOT / "local.properties"
    if not local_properties.exists():
        return False
    text = local_properties.read_text(encoding="utf-8")
    return all(re.search(rf"^{re.escape(name)}\s*=", text, re.MULTILINE) for name in required)


def gradle_command() -> str:
    script = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not script.exists():
        fail(f"Missing Gradle wrapper: {script.relative_to(ROOT)}")
    return str(script)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-version", help="Expected versionName; defaults to app/build.gradle.kts")
    parser.add_argument("--skip-network", action="store_true", help="Skip GitHub latest-release freshness check")
    parser.add_argument("--skip-gradle-tests", action="store_true", help="Skip direct/play debug unit tests")
    parser.add_argument(
        "--expected-public-signer-sha256",
        default=PUBLIC_SIGNER_SHA256,
        help="Expected long-lived public release signer certificate SHA-256",
    )
    parser.add_argument(
        "--fail-on-external-blockers",
        action="store_true",
        help="Exit non-zero when external release evidence is still missing",
    )
    args = parser.parse_args()

    python = sys.executable
    version = args.expected_version or read_default_version()

    require_workflow_selftest()
    run_required([python, "scripts/release_scripts_selftest.py"])

    gate = [python, "scripts/release_gate_check.py", "--play-store", "--expected-version", version]
    if args.skip_network:
        gate.append("--skip-network")
    run_required(gate)

    direct_apk = single_artifact(DIRECT_RELEASE_APK_DIR, "*.apk", "direct release APK")
    play_apk = single_artifact(PLAY_RELEASE_APK_DIR, "*.apk", "Play release APK")
    play_aab = single_artifact(PLAY_RELEASE_AAB_DIR, "*.aab", "Play release AAB")

    run_required(
        [
            python,
            "scripts/verify_release_artifact.py",
            "--apk",
            str(direct_apk),
            "--expected-version",
            version,
            "--distribution",
            "direct",
        ]
    )
    run_required(
        [
            python,
            "scripts/verify_release_artifact.py",
            "--apk",
            str(play_apk),
            "--expected-version",
            version,
            "--distribution",
            "play",
        ]
    )
    run_required(
        [
            python,
            "scripts/verify_release_artifact.py",
            "--aab",
            str(play_aab),
            "--expected-version",
            version,
            "--distribution",
            "play",
        ]
    )

    if not args.skip_gradle_tests:
        run_required([gradle_command(), ":app:testDirectDebugUnitTest", ":app:testPlayDebugUnitTest", "--console=plain"])

    external_blockers: list[str] = []
    signer = args.expected_public_signer_sha256
    signer_probe = [
        python,
        "scripts/verify_release_artifact.py",
        "--apk",
        str(direct_apk),
        "--expected-version",
        version,
        "--distribution",
        "direct",
        "--expected-signer-cert-sha256",
        signer,
    ]
    if not run_probe(signer_probe):
        external_blockers.append(
            "Direct release APK is not signed with the expected long-lived public signer "
            f"{signer.upper()}."
        )

    aab_signer_probe = [
        python,
        "scripts/verify_release_artifact.py",
        "--aab",
        str(play_aab),
        "--expected-version",
        version,
        "--distribution",
        "play",
        "--expected-signer-cert-sha256",
        signer,
    ]
    if not run_probe(aab_signer_probe):
        external_blockers.append(
            "Play AAB is not signed with the expected long-lived public signer "
            f"{signer.upper()}."
        )

    if not signing_inputs_available():
        external_blockers.append("Release signing inputs are not present in env/local.properties for this shell.")

    if not os.environ.get("POKECLAW_LLM_API_KEY"):
        external_blockers.append("Production Cloud LLM smoke still needs a real POKECLAW_LLM_API_KEY and endpoint/model evidence.")

    external_blockers.extend(
        [
            "Writable WhatsApp/Telegram/Gmail account flows still need live account evidence.",
            "Direct-channel missed-call SMS needs documented policy/approval evidence outside the Play-safe flavor.",
        ]
    )

    print("\nOK: local commercial-readiness gates passed")
    if external_blockers:
        print("\nBLOCKED_EXTERNAL:")
        for blocker in external_blockers:
            print(f"- {blocker}")
        if args.fail_on_external_blockers:
            return 2
    else:
        print("\nOK: no external blockers detected by this audit")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
