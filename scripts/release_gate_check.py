#!/usr/bin/env python3
"""Commercial release preflight for PokeClaw.

Checks the repo defaults that must be correct before cutting a public APK:
- default versionName/versionCode in app/build.gradle.kts
- matching README changelog entry
- debug/release BuildConfig safety switches
- optional GitHub latest release freshness
- optional signing secret presence for real release builds
- optional expected version, normally derived from the Git tag
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "app" / "build.gradle.kts"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
PLAY_MANIFEST = ROOT / "app" / "src" / "play" / "AndroidManifest.xml"
README = ROOT / "README.md"
GITHUB_LATEST_API = "https://api.github.com/repos/agents-io/PokeClaw/releases/latest"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
PACKAGE_NAME = "io.agents.pokeclaw"
SIGNING_KEYS = ["KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"]
RESTRICTED_PLAY_PERMISSIONS = {
    "android.permission.READ_CALL_LOG": "Call Log",
    "android.permission.SEND_SMS": "SMS",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"OK: {message}")


def warn(message: str) -> None:
    print(f"WARN: {message}")


def parse_semver(value: str) -> tuple[int, ...]:
    core = value.strip().lstrip("v").split("-", 1)[0]
    parts: list[int] = []
    for part in core.split("."):
        if not part.isdigit():
            fail(f"Version '{value}' is not a simple numeric semver")
        parts.append(int(part))
    return tuple(parts)


def compare_versions(left: str, right: str) -> int:
    l_parts = list(parse_semver(left))
    r_parts = list(parse_semver(right))
    width = max(len(l_parts), len(r_parts))
    l_parts.extend([0] * (width - len(l_parts)))
    r_parts.extend([0] * (width - len(r_parts)))
    return (l_parts > r_parts) - (l_parts < r_parts)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"Missing required file: {path.relative_to(ROOT)}")


def extract_default_version(build_gradle: str) -> tuple[int, str]:
    code_match = re.search(
        r'versionCode\s*=\s*readLocalOrEnvInt\("POKECLAW_VERSION_CODE",\s*(\d+)\)',
        build_gradle,
    )
    name_match = re.search(
        r'versionName\s*=\s*readLocalOrEnvString\("POKECLAW_VERSION_NAME",\s*"([^"]+)"\)',
        build_gradle,
    )
    if not code_match or not name_match:
        fail("Could not parse default versionCode/versionName from app/build.gradle.kts")
    return int(code_match.group(1)), name_match.group(1)


def extract_build_type_block(build_gradle: str, marker: str) -> str:
    start = build_gradle.find(marker)
    if start < 0:
        fail(f"Could not find build type marker: {marker}")
    brace = build_gradle.find("{", start)
    if brace < 0:
        fail(f"Could not parse build type block after marker: {marker}")

    depth = 0
    for index in range(brace, len(build_gradle)):
        char = build_gradle[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return build_gradle[brace + 1 : index]
    fail(f"Unterminated build type block after marker: {marker}")


def require_contains(haystack: str, needle: str, context: str) -> None:
    if needle not in haystack:
        fail(f"Missing `{needle}` in {context}")
    ok(f"{context} contains `{needle}`")


def android_attr(element: ET.Element, name: str) -> str | None:
    return element.attrib.get(f"{ANDROID_NS}{name}")


def manifest_name_matches(actual: str | None, expected_short_name: str) -> bool:
    if actual == expected_short_name:
        return True
    if expected_short_name.startswith("."):
        return actual == f"{PACKAGE_NAME}{expected_short_name}"
    return False


def parse_manifest_file(path: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except FileNotFoundError:
        fail(f"Missing required file: {path.relative_to(ROOT)}")
    except ET.ParseError as exc:
        fail(f"Could not parse {path.relative_to(ROOT)}: {exc}")


def parse_manifest() -> ET.Element:
    return parse_manifest_file(MANIFEST)


def manifest_declares_permission(manifest: ET.Element, permission: str) -> bool:
    for element in manifest.findall("uses-permission"):
        if android_attr(element, "name") == permission:
            return True
    return False


def require_manifest_permission(manifest: ET.Element, permission: str) -> None:
    if manifest_declares_permission(manifest, permission):
        ok(f"Manifest declares {permission}")
        return
    fail(f"Manifest must declare {permission}")


def require_manifest_feature(manifest: ET.Element, feature: str, required_value: str) -> None:
    for element in manifest.findall("uses-feature"):
        if android_attr(element, "name") == feature:
            actual = android_attr(element, "required")
            if actual != required_value:
                fail(f"Manifest feature {feature} must set android:required=\"{required_value}\"")
            ok(f"Manifest feature {feature} has android:required=\"{required_value}\"")
            return
    fail(f"Manifest must declare feature {feature}")


def find_manifest_component(manifest: ET.Element, tag: str, short_name: str) -> ET.Element:
    application = manifest.find("application")
    if application is None:
        fail("Manifest must include an application element")
    for element in application.findall(tag):
        if manifest_name_matches(android_attr(element, "name"), short_name):
            return element
    fail(f"Manifest must declare {tag} {short_name}")


def manifest_component_declared(manifest: ET.Element, tag: str, short_name: str) -> bool:
    application = manifest.find("application")
    if application is None:
        return False
    return any(
        manifest_name_matches(android_attr(element, "name"), short_name)
        for element in application.findall(tag)
    )


def require_manifest_attr(element: ET.Element, attr: str, expected: str, context: str) -> None:
    actual = android_attr(element, attr)
    if actual != expected:
        fail(f"{context} must set android:{attr}=\"{expected}\"")
    ok(f"{context} has android:{attr}=\"{expected}\"")


def require_manifest_actions(element: ET.Element, actions: list[str], context: str) -> None:
    declared = {
        android_attr(action, "name")
        for action in element.findall("intent-filter/action")
        if android_attr(action, "name")
    }
    missing = [action for action in actions if action not in declared]
    if missing:
        fail(f"{context} must declare action(s): {', '.join(missing)}")
    ok(f"{context} declares required action(s)")


def require_manifest_permission_removed(manifest: ET.Element, permission: str, context: str) -> None:
    for element in manifest.findall("uses-permission"):
        if android_attr(element, "name") == permission:
            node_op = element.attrib.get("{http://schemas.android.com/tools}node")
            if node_op == "remove":
                ok(f"{context} removes {permission}")
                return
            fail(f"{context} must remove {permission} with tools:node=\"remove\"")
    fail(f"{context} must declare a removal for {permission}")


def require_manifest_component_removed(
    manifest: ET.Element,
    tag: str,
    short_name: str,
    context: str,
) -> None:
    application = manifest.find("application")
    if application is None:
        fail(f"{context} must include an application element")
    for element in application.findall(tag):
        if manifest_name_matches(android_attr(element, "name"), short_name):
            node_op = element.attrib.get("{http://schemas.android.com/tools}node")
            if node_op == "remove":
                ok(f"{context} removes {tag} {short_name}")
                return
            fail(f"{context} must remove {tag} {short_name} with tools:node=\"remove\"")
    fail(f"{context} must declare a removal for {tag} {short_name}")


def check_manifest_security(manifest: ET.Element) -> None:
    require_manifest_permission(manifest, "android.permission.READ_PHONE_STATE")
    require_manifest_permission(manifest, "android.permission.READ_CALL_LOG")
    require_manifest_permission(manifest, "android.permission.SEND_SMS")
    require_manifest_feature(manifest, "android.hardware.telephony", "false")

    missed_call = find_manifest_component(manifest, "receiver", ".receiver.MissedCallReceiver")
    require_manifest_attr(missed_call, "enabled", "true", "MissedCallReceiver")
    require_manifest_attr(missed_call, "exported", "true", "MissedCallReceiver")
    require_manifest_actions(missed_call, ["android.intent.action.PHONE_STATE"], "MissedCallReceiver")

    automation_activity = find_manifest_component(manifest, "activity", ".automation.ExternalAutomationActivity")
    require_manifest_attr(automation_activity, "exported", "true", "ExternalAutomationActivity")
    require_manifest_actions(
        automation_activity,
        ["io.agents.pokeclaw.RUN_TASK", "io.agents.pokeclaw.RUN_CHAT"],
        "ExternalAutomationActivity",
    )

    automation_receiver = find_manifest_component(manifest, "receiver", ".automation.ExternalAutomationReceiver")
    require_manifest_attr(automation_receiver, "exported", "true", "ExternalAutomationReceiver")
    require_manifest_actions(
        automation_receiver,
        ["io.agents.pokeclaw.RUN_TASK", "io.agents.pokeclaw.RUN_CHAT"],
        "ExternalAutomationReceiver",
    )


def check_play_store_policy_gate(
    manifest: ET.Element,
    build_gradle: str,
    approval_acknowledged: bool,
) -> None:
    declared = [
        f"{permission} ({group})"
        for permission, group in RESTRICTED_PLAY_PERMISSIONS.items()
        if manifest_declares_permission(manifest, permission)
    ]
    if not declared:
        ok("Play Store restricted SMS/Call Log permission check passed: none declared")
        return

    play_manifest = parse_manifest_file(PLAY_MANIFEST)
    if approval_acknowledged:
        ok(f"Play Store restricted permission approval acknowledged for: {', '.join(declared)}")
        return

    for permission in RESTRICTED_PLAY_PERMISSIONS:
        require_manifest_permission_removed(play_manifest, permission, "Play manifest")
    require_manifest_permission_removed(play_manifest, "android.permission.READ_PHONE_STATE", "Play manifest")
    require_manifest_component_removed(play_manifest, "receiver", ".receiver.MissedCallReceiver", "Play manifest")
    require_contains(build_gradle, 'create("play")', "Play product flavor")
    require_contains(
        build_gradle,
        'buildConfigField("Boolean", "MISSED_CALL_FOLLOWUP_ENABLED", "false")',
        "Play product flavor",
    )
    ok("Play Store restricted permission check passed via Play-safe flavor manifest")


def fetch_latest_version(timeout_seconds: int) -> str:
    request = urllib.request.Request(
        GITHUB_LATEST_API,
        headers={"User-Agent": "PokeClaw-release-gate"},
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        payload = json.loads(response.read().decode("utf-8"))
    tag = str(payload.get("tag_name") or "").strip()
    if not tag:
        fail("GitHub latest release response did not include tag_name")
    return tag.lstrip("v")


def read_local_properties() -> dict[str, str]:
    props: dict[str, str] = {}
    local_properties = ROOT / "local.properties"
    if local_properties.exists():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def resolve_keystore_path(raw_path: str) -> Path:
    path = Path(raw_path).expanduser()
    candidates = [path]
    if not path.is_absolute():
        candidates.extend([ROOT / path, ROOT / "app" / path])
    for candidate in candidates:
        if candidate.exists():
            return candidate.resolve()
    return candidates[0].resolve()


def find_jdk_tool(base: str) -> str | None:
    for name in [base, f"{base}.exe"]:
        path = shutil.which(name)
        if path:
            return path

    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        for name in [base, f"{base}.exe"]:
            path = Path(java_home) / "bin" / name
            if path.exists():
                return str(path)
    return None


def redact(text: str, secrets: list[str]) -> str:
    redacted = text
    for secret in secrets:
        if secret:
            redacted = redacted.replace(secret, "***")
    return redacted


def signing_problem(message: str, require_validation: bool) -> bool:
    if require_validation:
        fail(message)
    warn(message)
    return False


def validate_private_key_with_jarsigner(
    values: dict[str, str],
    keystore: Path,
    require_validation: bool,
) -> bool:
    jarsigner = find_jdk_tool("jarsigner")
    if not jarsigner:
        return signing_problem("Could not find jarsigner to validate the release private key password", require_validation)

    with tempfile.TemporaryDirectory(prefix="pokeclaw-release-signing-") as temp_dir:
        temp = Path(temp_dir)
        unsigned = temp / "probe.jar"
        signed = temp / "probe-signed.jar"
        with zipfile.ZipFile(unsigned, "w") as archive:
            archive.writestr("probe.txt", "pokeclaw signing probe\n")

        command = [
            jarsigner,
            "-keystore",
            str(keystore),
            "-storepass",
            values["KEYSTORE_PASSWORD"],
            "-keypass",
            values["KEY_PASSWORD"],
            "-signedjar",
            str(signed),
            str(unsigned),
            values["KEY_ALIAS"],
        ]
        try:
            result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, timeout=30)
        except subprocess.TimeoutExpired:
            return signing_problem("Timed out while validating release private key with jarsigner", require_validation)

        if result.returncode != 0:
            details = redact((result.stderr or result.stdout or "").strip(), list(values.values()))
            if details:
                warn(details)
            return signing_problem(
                "Release private key is not usable by jarsigner; check KEY_ALIAS and KEY_PASSWORD",
                require_validation,
            )
        if not signed.exists() or signed.stat().st_size <= 0:
            return signing_problem("jarsigner did not produce a signed probe artifact", require_validation)

    ok("Release private key is usable by jarsigner")
    return True


def validate_signing_key(values: dict[str, str], require_validation: bool) -> bool:
    keystore = resolve_keystore_path(values["KEYSTORE_FILE"])
    if not keystore.is_file():
        return signing_problem(f"Release keystore file does not exist: {keystore}", require_validation)
    if keystore.stat().st_size <= 0:
        return signing_problem(f"Release keystore file is empty: {keystore}", require_validation)

    keytool = find_jdk_tool("keytool")
    if not keytool:
        return signing_problem("Could not find keytool to validate release signing credentials", require_validation)

    fd, csr_path = tempfile.mkstemp(prefix="pokeclaw-release-signing-", suffix=".csr")
    os.close(fd)
    csr = Path(csr_path)
    csr.unlink()
    try:
        command = [
            keytool,
            "-certreq",
            "-alias",
            values["KEY_ALIAS"],
            "-keystore",
            str(keystore),
            "-storepass",
            values["KEYSTORE_PASSWORD"],
            "-keypass",
            values["KEY_PASSWORD"],
            "-file",
            str(csr),
        ]
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, timeout=30)
        if result.returncode != 0:
            details = redact((result.stderr or result.stdout or "").strip(), list(values.values()))
            if details:
                warn(details)
            return signing_problem(
                "Release signing key is not readable by keytool; check KEYSTORE_FILE, "
                "KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD",
                require_validation,
            )
        if not csr.exists() or csr.stat().st_size <= 0:
            return signing_problem("keytool did not produce a certificate request for the release key", require_validation)
    except subprocess.TimeoutExpired:
        return signing_problem("Timed out while validating release signing credentials with keytool", require_validation)
    finally:
        try:
            csr.unlink()
        except FileNotFoundError:
            pass

    ok("Release signing key is readable by keytool")
    if not validate_private_key_with_jarsigner(values, keystore, require_validation):
        return False
    ok("Release signing credentials are valid")
    return True


def signing_values_present(require_validation: bool) -> bool:
    props = read_local_properties()

    values: dict[str, str] = {}
    missing = []
    for key in SIGNING_KEYS:
        value = os.environ.get(key) or props.get(key)
        if not value:
            missing.append(key)
        else:
            values[key] = value
    if missing:
        warn(f"Release signing inputs missing: {', '.join(missing)}")
        return False
    ok("Release signing inputs are present")
    return validate_signing_key(values, require_validation)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-network", action="store_true", help="Do not query GitHub latest release")
    parser.add_argument("--latest-version", help="Use this latest public version instead of querying GitHub")
    parser.add_argument("--expected-version", help="Require repo defaults to match this version")
    parser.add_argument("--require-signing", action="store_true", help="Fail if release signing inputs are missing")
    parser.add_argument(
        "--play-store",
        action="store_true",
        help="Fail Play-bound releases that declare restricted SMS/Call Log permissions without approval",
    )
    parser.add_argument(
        "--play-store-policy-approved",
        action="store_true",
        help="Acknowledge documented Play approval/default-handler or exception evidence for restricted SMS/Call Log permissions",
    )
    parser.add_argument("--timeout-seconds", type=int, default=20)
    args = parser.parse_args()

    build_gradle = read_text(BUILD_GRADLE)
    manifest = parse_manifest()
    readme = read_text(README)

    version_code, version_name = extract_default_version(build_gradle)
    ok(f"Default version is {version_name} ({version_code})")

    if version_code <= 0:
        fail("versionCode must be positive")

    if args.expected_version:
        expected = args.expected_version.lstrip("v")
        if version_name != expected:
            fail(f"Default version {version_name} does not match expected release version {expected}")
        ok(f"Default version matches expected release version {expected}")

    changelog_header = f"### v{version_name}"
    require_contains(readme, changelog_header, "README changelog")

    debug_block = extract_build_type_block(build_gradle, 'getByName("debug")')
    release_block = extract_build_type_block(build_gradle, "\n        release")

    require_contains(debug_block, 'buildConfigField("Boolean", "DEBUG_AUTOMATION_ENABLED", "true")', "debug build type")
    require_contains(debug_block, 'buildConfigField("Boolean", "UPDATE_CHECK_ENABLED", "false")', "debug build type")
    require_contains(debug_block, 'manifestPlaceholders["debugAutomationEnabled"] = "true"', "debug build type")
    require_contains(debug_block, 'manifestPlaceholders["debugAutomationExported"] = "true"', "debug build type")
    require_contains(release_block, 'buildConfigField("Boolean", "UPDATE_CHECK_ENABLED", "true")', "release build type")
    require_contains(release_block, 'manifestPlaceholders["debugAutomationEnabled"] = "false"', "release build type")
    require_contains(release_block, 'manifestPlaceholders["debugAutomationExported"] = "false"', "release build type")

    default_config = extract_build_type_block(build_gradle, "defaultConfig")
    require_contains(default_config, 'buildConfigField("Boolean", "DEBUG_AUTOMATION_ENABLED", "false")', "defaultConfig")
    require_contains(default_config, 'buildConfigField("Boolean", "UPDATE_CHECK_ENABLED", "false")', "defaultConfig")
    require_contains(default_config, 'buildConfigField("Boolean", "MISSED_CALL_FOLLOWUP_ENABLED", "true")', "defaultConfig")
    require_contains(default_config, 'manifestPlaceholders["debugAutomationEnabled"] = "false"', "defaultConfig")
    require_contains(default_config, 'manifestPlaceholders["debugAutomationExported"] = "false"', "defaultConfig")

    check_manifest_security(manifest)
    if args.play_store:
        check_play_store_policy_gate(manifest, build_gradle, args.play_store_policy_approved)

    latest_version = args.latest_version
    if not latest_version and not args.skip_network:
        latest_version = fetch_latest_version(args.timeout_seconds)

    if latest_version:
        comparison = compare_versions(version_name, latest_version)
        if comparison <= 0:
            fail(f"Default version {version_name} must be newer than public latest {latest_version}")
        ok(f"Default version {version_name} is newer than public latest {latest_version}")
    else:
        warn("Skipped GitHub latest release freshness check")

    has_signing = signing_values_present(args.require_signing)
    if args.require_signing and not has_signing:
        fail("Release signing inputs are required for this run")

    ok("Release gate preflight passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
