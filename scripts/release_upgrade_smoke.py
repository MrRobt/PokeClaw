#!/usr/bin/env python3
"""Verify that a candidate release APK upgrades over an installed reference APK."""

from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path

import release_device_smoke
import verify_release_artifact


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = release_device_smoke.PACKAGE
SPLASH_ACTIVITY = release_device_smoke.SPLASH_ACTIVITY


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"OK: {message}")


def resolve_apk(path: Path, label: str) -> Path:
    apk = path.resolve()
    if not apk.exists():
        fail(f"{label} APK does not exist: {apk}")
    if not apk.is_file():
        fail(f"{label} APK is not a file: {apk}")
    return apk


def apk_identity(aapt: str, apk: Path) -> tuple[str, int, str]:
    return verify_release_artifact.parse_badging(
        verify_release_artifact.run([aapt, "dump", "badging", str(apk)])
    )


def apk_signer(apksigner: str, apk: Path) -> str:
    output = verify_release_artifact.run([apksigner, "verify", "--verbose", "--print-certs", str(apk)])
    if "Verifies" not in output:
        fail(f"{apk.name} did not verify")
    if "Number of signers: 1" not in output:
        fail(f"{apk.name} must have exactly one signer")
    return verify_release_artifact.parse_signer_cert_sha256(output)


def preflight_upgrade_pair(
    aapt: str,
    apksigner: str,
    reference_apk: Path,
    candidate_apk: Path,
    expected_candidate_version: str | None,
) -> tuple[int, int, str]:
    ref_package, ref_code, ref_name = apk_identity(aapt, reference_apk)
    cand_package, cand_code, cand_name = apk_identity(aapt, candidate_apk)
    if ref_package != PACKAGE:
        fail(f"Reference package {ref_package} does not match {PACKAGE}")
    if cand_package != PACKAGE:
        fail(f"Candidate package {cand_package} does not match {PACKAGE}")
    if expected_candidate_version and cand_name != expected_candidate_version:
        fail(f"Candidate versionName {cand_name} does not match expected {expected_candidate_version}")
    if cand_code <= ref_code:
        fail(f"Candidate versionCode {cand_code} must be greater than reference {ref_code}")
    ok(f"Upgrade version path verified: {ref_name} ({ref_code}) -> {cand_name} ({cand_code})")

    ref_signer = apk_signer(apksigner, reference_apk)
    cand_signer = apk_signer(apksigner, candidate_apk)
    if cand_signer != ref_signer:
        fail(
            "Candidate signer does not match reference signer: "
            f"{cand_signer.upper()} != {ref_signer.upper()}"
        )
    ok(f"Upgrade signer compatibility verified: {cand_signer.upper()}")
    return ref_code, cand_code, cand_name


def installed_version_code(smoke: release_device_smoke.Smoke) -> int:
    output = smoke.shell(f"dumpsys package {PACKAGE}", timeout=30)
    match = re.search(r"versionCode=(\d+)", output)
    if not match:
        fail("Could not read installed versionCode from dumpsys package")
    return int(match.group(1))


def launch_and_check(smoke: release_device_smoke.Smoke, phase: str) -> None:
    output = smoke.launch()
    print(output.strip())
    time.sleep(6.0)
    text = smoke.visible_text()
    if "Update Available" in text:
        fail(f"{phase} launch showed stale update prompt")
    smoke.assert_no_crash_logs()
    ok(f"{phase} launch smoke passed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference-apk", type=Path, required=True, help="Currently public APK to install first")
    parser.add_argument("--candidate-apk", type=Path, required=True, help="Candidate release APK to install with -r")
    parser.add_argument("--expected-candidate-version", help="Expected candidate versionName")
    parser.add_argument("--adb", default=release_device_smoke.default_adb())
    parser.add_argument("--serial", help="ADB serial. Defaults to adb's selected device")
    parser.add_argument("--aapt", help="Path to aapt")
    parser.add_argument("--apksigner", help="Path to apksigner")
    parser.add_argument(
        "--preflight-only",
        action="store_true",
        help="Only verify version/signing compatibility; do not touch a device",
    )
    args = parser.parse_args()

    reference_apk = resolve_apk(args.reference_apk, "Reference")
    candidate_apk = resolve_apk(args.candidate_apk, "Candidate")
    aapt = args.aapt or verify_release_artifact.find_android_tool("aapt")
    apksigner = args.apksigner or verify_release_artifact.find_android_tool("apksigner")

    _, candidate_code, candidate_name = preflight_upgrade_pair(
        aapt,
        apksigner,
        reference_apk,
        candidate_apk,
        args.expected_candidate_version,
    )
    if args.preflight_only:
        ok("Release upgrade preflight passed")
        return 0

    smoke = release_device_smoke.Smoke(args.adb, args.serial)
    smoke.adb_cmd("uninstall", PACKAGE, timeout=60, check=False)
    smoke.adb_cmd("install", str(reference_apk), timeout=180)
    ok(f"Installed reference APK {reference_apk.name}")
    smoke.clear_logcat()
    launch_and_check(smoke, "Reference")

    smoke.adb_cmd("install", "-r", str(candidate_apk), timeout=180)
    ok(f"Upgraded to candidate APK {candidate_apk.name}")
    installed_code = installed_version_code(smoke)
    if installed_code != candidate_code:
        fail(f"Installed versionCode {installed_code} does not match candidate {candidate_code}")
    smoke.clear_logcat()
    launch_and_check(smoke, f"Candidate {candidate_name}")
    ok("Release upgrade smoke passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
