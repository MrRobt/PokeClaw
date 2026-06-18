#!/usr/bin/env python3
"""Verify a PokeClaw release APK artifact before publishing it."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass, field
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELEASE_APK_DIR = ROOT / "app" / "build" / "outputs" / "apk"
RELEASE_BUNDLE_DIR = ROOT / "app" / "build" / "outputs" / "bundle"
EXPECTED_PACKAGE = "io.agents.pokeclaw"
PLAY_SAFE_PERMISSIONS = [
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_CALL_LOG",
    "android.permission.SEND_SMS",
]
BUNDLETOOL_MAIN_CLASS = "com.android.tools.build.bundletool.BundleToolMain"
CACHED_BUNDLETOOL_DEPS: list[tuple[str, str, list[str]]] = [
    ("com.android.tools.build", "bundletool", ["1.18.3"]),
    ("com.android.tools.build", "aapt2-proto", ["7.3.0-alpha07-8248216", "9.1.0-14792394"]),
    ("com.google.auto.value", "auto-value-annotations", ["1.6.2"]),
    ("com.google.errorprone", "error_prone_annotations", ["2.3.1", "2.41.0", "2.30.0"]),
    ("com.google.guava", "guava", ["32.0.1-jre"]),
    ("com.google.guava", "failureaccess", ["1.0.1", "1.0.2"]),
    ("com.google.guava", "listenablefuture", ["9999.0-empty-to-avoid-conflict-with-guava"]),
    ("com.google.code.findbugs", "jsr305", ["3.0.2"]),
    ("org.checkerframework", "checker-qual", ["3.33.0", "3.48.3", "3.43.0"]),
    ("com.google.j2objc", "j2objc-annotations", ["2.8", "2.7"]),
    ("com.google.protobuf", "protobuf-java", ["3.22.3", "3.25.5", "4.28.3"]),
    ("com.google.protobuf", "protobuf-java-util", ["3.22.3", "3.25.5", "4.28.3"]),
    ("com.google.code.gson", "gson", ["2.8.9", "2.10.1", "2.11.0"]),
    ("com.google.dagger", "dagger", ["2.28.3"]),
    ("javax.inject", "javax.inject", ["1"]),
    ("org.bitbucket.b_c", "jose4j", ["0.9.5"]),
    ("org.slf4j", "slf4j-api", ["1.7.30"]),
]


@dataclass(frozen=True)
class BundletoolRunner:
    description: str
    command_prefix: list[str]


@dataclass
class ManifestNode:
    tag: str
    attrs: dict[str, str] = field(default_factory=dict)
    children: list["ManifestNode"] = field(default_factory=list)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"OK: {message}")


def candidate_tool_names(base: str) -> list[str]:
    return [f"{base}.bat", f"{base}.exe", base]


def find_android_tool(base: str) -> str:
    for name in candidate_tool_names(base):
        path = shutil.which(name)
        if path:
            return path

    roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        "D:\\android-sdk",
        str(Path.home() / "AppData" / "Local" / "Android" / "Sdk"),
    ]
    for root in roots:
        if not root:
            continue
        build_tools = Path(root) / "build-tools"
        if not build_tools.exists():
            continue
        for version_dir in sorted(build_tools.iterdir(), reverse=True):
            if not version_dir.is_dir():
                continue
            for name in candidate_tool_names(base):
                tool = version_dir / name
                if tool.exists():
                    return str(tool)

    fail(f"Could not find Android build-tools executable: {base}")


def gradle_cache_roots() -> list[Path]:
    roots: list[Path] = []
    gradle_user_home = os.environ.get("GRADLE_USER_HOME")
    if gradle_user_home:
        roots.append(Path(gradle_user_home) / "caches" / "modules-2" / "files-2.1")
    roots.append(Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1")
    unique: list[Path] = []
    for root in roots:
        if root not in unique:
            unique.append(root)
    return unique


def find_cached_jar(cache_root: Path, group: str, artifact: str, versions: list[str]) -> Path | None:
    artifact_root = cache_root / group / artifact
    if not artifact_root.exists():
        return None
    for version in versions:
        jars = sorted((artifact_root / version).glob(f"*/{artifact}-{version}.jar"))
        if jars:
            return jars[0]
    return None


def make_cached_bundletool_runner(cache_root: Path) -> BundletoolRunner | None:
    jars: list[str] = []
    for group, artifact, versions in CACHED_BUNDLETOOL_DEPS:
        jar = find_cached_jar(cache_root, group, artifact, versions)
        if not jar:
            return None
        jars.append(str(jar))

    runner = BundletoolRunner(
        f"Gradle cached bundletool from {cache_root}",
        ["java", "-cp", os.pathsep.join(jars), BUNDLETOOL_MAIN_CLASS],
    )
    result = subprocess.run(
        [*runner.command_prefix, "version"],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        return None
    version = (result.stdout or "").strip() or "unknown"
    ok(f"Using {runner.description} ({version})")
    return runner


def find_cached_bundletool_runner() -> BundletoolRunner | None:
    for cache_root in gradle_cache_roots():
        if not cache_root.exists():
            continue
        runner = make_cached_bundletool_runner(cache_root)
        if runner:
            return runner
    return None


def explicit_bundletool_runner(bundletool: Path) -> BundletoolRunner:
    return BundletoolRunner(f"bundletool jar {bundletool}", ["java", "-jar", str(bundletool)])


def run(command: list[str]) -> str:
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    if result.returncode != 0:
        if result.stdout:
            print(result.stdout)
        if result.stderr:
            print(result.stderr, file=sys.stderr)
        fail(f"Command failed: {' '.join(command)}")
    return result.stdout


def run_bundletool(runner: BundletoolRunner, args: list[str]) -> str:
    return run([*runner.command_prefix, *args])


def run_jarsigner(command: list[str]) -> str:
    result = subprocess.run(
        command,
        cwd=ROOT,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
    )
    output = (result.stdout or "") + (result.stderr or "")
    if result.returncode != 0:
        print(output)
        fail(f"Command failed: {' '.join(command)}")
    return output


def find_release_apk(distribution: str) -> Path:
    search_dir = RELEASE_APK_DIR / distribution / "release"
    apks = sorted(search_dir.glob("*.apk"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not apks:
        fail(f"No {distribution} release APK found under {search_dir.relative_to(ROOT)}")
    if len(apks) > 1:
        names = ", ".join(path.name for path in apks)
        fail(f"Expected exactly one {distribution} release APK, found: {names}")
    return apks[0]


def find_release_aab(distribution: str) -> Path:
    search_dir = RELEASE_BUNDLE_DIR / f"{distribution}Release"
    aabs = sorted(search_dir.glob("*.aab"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not aabs:
        fail(f"No {distribution} release AAB found under {search_dir.relative_to(ROOT)}")
    if len(aabs) > 1:
        names = ", ".join(path.name for path in aabs)
        fail(f"Expected exactly one {distribution} release AAB, found: {names}")
    return aabs[0]


def parse_badging(text: str) -> tuple[str, int, str]:
    first_line = text.splitlines()[0] if text.splitlines() else ""
    match = re.search(
        r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",
        first_line,
    )
    if not match:
        fail("Could not parse package/version from aapt badging output")
    package_name, version_code, version_name = match.groups()
    return package_name, int(version_code), version_name


def normalize_sha256(value: str) -> str:
    normalized = value.strip().lower().replace(":", "")
    if not re.fullmatch(r"[0-9a-f]{64}", normalized):
        fail(f"Invalid SHA-256 digest: {value}")
    return normalized


def parse_signer_cert_sha256(text: str) -> str:
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", text)
    if not match:
        fail("Could not parse signer certificate SHA-256 digest from apksigner output")
    return normalize_sha256(match.group(1))


def parse_jarsigner_cert_identity(text: str) -> str:
    match = re.search(r"X\.509,\s*(CN=[^\r\n]+)", text)
    if not match:
        fail("Could not parse signer certificate identity from jarsigner output")
    return match.group(1).strip()


def parse_keytool_cert_sha256(text: str) -> str:
    match = re.search(r"\bSHA256:\s*([0-9a-fA-F:]+)", text)
    if not match:
        fail("Could not parse signer certificate SHA-256 digest from keytool output")
    return normalize_sha256(match.group(1))


def parse_aapt_value(value: str) -> str:
    value = value.strip()
    raw_match = re.search(r'\(Raw: "([^"]*)"\)', value)
    if raw_match:
        return raw_match.group(1)
    quoted_match = re.match(r'"([^"]*)"', value)
    if quoted_match:
        return quoted_match.group(1)
    bool_match = re.match(r"\(type 0x12\)(0x[0-9a-fA-F]+)", value)
    if bool_match:
        return "true" if int(bool_match.group(1), 16) != 0 else "false"
    return value


def parse_aapt_xmltree(text: str) -> ManifestNode:
    root = ManifestNode("__root__")
    stack: list[tuple[int, ManifestNode]] = [(-1, root)]
    for line in text.splitlines():
        stripped = line.lstrip()
        indent = len(line) - len(stripped)
        if stripped.startswith("E: "):
            match = re.match(r"E: ([^\s]+)", stripped)
            if not match:
                fail(f"Could not parse manifest element line: {line}")
            node = ManifestNode(match.group(1))
            while stack and indent <= stack[-1][0]:
                stack.pop()
            stack[-1][1].children.append(node)
            stack.append((indent, node))
        elif stripped.startswith("A: "):
            match = re.match(r"A: ([^=(]+)(?:\([^)]+\))?=(.*)", stripped)
            if not match:
                fail(f"Could not parse manifest attribute line: {line}")
            stack[-1][1].attrs[match.group(1).strip()] = parse_aapt_value(match.group(2))
    return root


def descendants(node: ManifestNode, tag: str) -> list[ManifestNode]:
    matches = [node] if node.tag == tag else []
    for child in node.children:
        matches.extend(descendants(child, tag))
    return matches


def manifest_name_matches(actual: str | None, expected_short_name: str, package_name: str) -> bool:
    if actual == expected_short_name:
        return True
    if expected_short_name.startswith("."):
        return actual == f"{package_name}{expected_short_name}"
    return False


def require_manifest_permission(manifest: ManifestNode, permission: str) -> None:
    for node in descendants(manifest, "uses-permission"):
        if node.attrs.get("android:name") == permission:
            ok(f"APK manifest declares {permission}")
            return
    fail(f"APK manifest must declare {permission}")


def forbid_manifest_permission(manifest: ManifestNode, permission: str) -> None:
    for node in descendants(manifest, "uses-permission"):
        if node.attrs.get("android:name") == permission:
            fail(f"APK manifest must not declare {permission}")
    ok(f"APK manifest does not declare {permission}")


def require_manifest_feature(manifest: ManifestNode, feature: str, required_value: str) -> None:
    for node in descendants(manifest, "uses-feature"):
        if node.attrs.get("android:name") == feature:
            actual = node.attrs.get("android:required")
            if actual != required_value:
                fail(f"APK manifest feature {feature} must set android:required=\"{required_value}\"")
            ok(f"APK manifest feature {feature} has android:required=\"{required_value}\"")
            return
    fail(f"APK manifest must declare feature {feature}")


def find_manifest_component(
    manifest: ManifestNode,
    tag: str,
    short_name: str,
    package_name: str,
) -> ManifestNode:
    for node in descendants(manifest, tag):
        if manifest_name_matches(node.attrs.get("android:name"), short_name, package_name):
            return node
    fail(f"APK manifest must declare {tag} {short_name}")


def forbid_manifest_component(
    manifest: ManifestNode,
    tag: str,
    short_name: str,
    package_name: str,
) -> None:
    for node in descendants(manifest, tag):
        if manifest_name_matches(node.attrs.get("android:name"), short_name, package_name):
            fail(f"APK manifest must not declare {tag} {short_name}")
    ok(f"APK manifest does not declare {tag} {short_name}")


def require_manifest_attr(node: ManifestNode, attr: str, expected: str, context: str) -> None:
    actual = node.attrs.get(attr)
    if actual != expected:
        fail(f"{context} in APK manifest must set {attr}=\"{expected}\"")
    ok(f"{context} in APK manifest has {attr}=\"{expected}\"")


def require_manifest_actions(node: ManifestNode, actions: list[str], context: str) -> None:
    declared = {
        action.attrs.get("android:name")
        for filter_node in node.children
        if filter_node.tag == "intent-filter"
        for action in filter_node.children
        if action.tag == "action" and action.attrs.get("android:name")
    }
    missing = [action for action in actions if action not in declared]
    if missing:
        fail(f"{context} in APK manifest must declare action(s): {', '.join(missing)}")
    ok(f"{context} in APK manifest declares required action(s)")


def check_release_manifest(text: str, package_name: str, distribution: str) -> None:
    parsed = parse_aapt_xmltree(text)
    if distribution == "play":
        forbid_manifest_permission(parsed, "android.permission.READ_PHONE_STATE")
        forbid_manifest_permission(parsed, "android.permission.READ_CALL_LOG")
        forbid_manifest_permission(parsed, "android.permission.SEND_SMS")
    else:
        require_manifest_permission(parsed, "android.permission.READ_PHONE_STATE")
        require_manifest_permission(parsed, "android.permission.READ_CALL_LOG")
        require_manifest_permission(parsed, "android.permission.SEND_SMS")
    require_manifest_feature(parsed, "android.hardware.telephony", "false")

    if distribution == "play":
        forbid_manifest_component(parsed, "receiver", ".receiver.MissedCallReceiver", package_name)
    else:
        missed_call = find_manifest_component(parsed, "receiver", ".receiver.MissedCallReceiver", package_name)
        require_manifest_attr(missed_call, "android:enabled", "true", "MissedCallReceiver")
        require_manifest_attr(missed_call, "android:exported", "true", "MissedCallReceiver")
        require_manifest_actions(missed_call, ["android.intent.action.PHONE_STATE"], "MissedCallReceiver")

    automation_activity = find_manifest_component(parsed, "activity", ".automation.ExternalAutomationActivity", package_name)
    require_manifest_attr(automation_activity, "android:exported", "true", "ExternalAutomationActivity")
    require_manifest_actions(
        automation_activity,
        ["io.agents.pokeclaw.RUN_TASK", "io.agents.pokeclaw.RUN_CHAT"],
        "ExternalAutomationActivity",
    )

    automation_receiver = find_manifest_component(parsed, "receiver", ".automation.ExternalAutomationReceiver", package_name)
    require_manifest_attr(automation_receiver, "android:exported", "true", "ExternalAutomationReceiver")
    require_manifest_actions(
        automation_receiver,
        ["io.agents.pokeclaw.RUN_TASK", "io.agents.pokeclaw.RUN_CHAT"],
        "ExternalAutomationReceiver",
    )

    debug_task = find_manifest_component(parsed, "receiver", ".debug.DebugTaskReceiver", package_name)
    require_manifest_attr(debug_task, "android:enabled", "false", "DebugTaskReceiver")
    require_manifest_attr(debug_task, "android:exported", "false", "DebugTaskReceiver")

    task_trigger = find_manifest_component(parsed, "receiver", ".debug.TaskTriggerReceiver", package_name)
    require_manifest_attr(task_trigger, "android:enabled", "false", "TaskTriggerReceiver")
    require_manifest_attr(task_trigger, "android:exported", "false", "TaskTriggerReceiver")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_aab_entries(aab: Path) -> None:
    required = [
        "BundleConfig.pb",
        "base/manifest/AndroidManifest.xml",
        "base/dex/classes.dex",
        "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties",
        "META-INF/MANIFEST.MF",
    ]
    with zipfile.ZipFile(aab) as bundle:
        names = set(bundle.namelist())
    missing = [entry for entry in required if entry not in names]
    if missing:
        fail(f"AAB is missing required entries: {', '.join(missing)}")
    ok("AAB contains required bundle/base/signature entries")


def check_play_safe_source_gate(distribution: str) -> None:
    if distribution != "play":
        return
    play_manifest = ROOT / "app" / "src" / "play" / "AndroidManifest.xml"
    build_gradle = ROOT / "app" / "build.gradle.kts"
    if not play_manifest.exists():
        fail("Play AAB requires app/src/play/AndroidManifest.xml")
    manifest_text = play_manifest.read_text(encoding="utf-8")
    for permission in PLAY_SAFE_PERMISSIONS:
        if permission not in manifest_text or 'tools:node="remove"' not in manifest_text:
            fail(f"Play manifest must remove {permission}")
    if ".receiver.MissedCallReceiver" not in manifest_text or 'tools:node="remove"' not in manifest_text:
        fail("Play manifest must remove .receiver.MissedCallReceiver")
    gradle_text = build_gradle.read_text(encoding="utf-8")
    if 'create("play")' not in gradle_text:
        fail('Gradle must declare create("play") product flavor')
    if 'buildConfigField("Boolean", "MISSED_CALL_FOLLOWUP_ENABLED", "false")' not in gradle_text:
        fail("Play flavor must disable MISSED_CALL_FOLLOWUP_ENABLED")
    ok("Play-safe source gate removes restricted missed-call feature")


def verify_aab_with_bundletool(
    aab: Path,
    runner: BundletoolRunner,
    distribution: str,
    expected_version: str,
    expected_package: str,
    aapt: str,
    aapt2: str,
) -> None:
    with tempfile.TemporaryDirectory(prefix="pokeclaw-aab-verify-") as temp_dir:
        temp = Path(temp_dir)
        apks = temp / "bundle.apks"
        run_bundletool(
            runner,
            [
                "build-apks",
                f"--bundle={aab}",
                f"--output={apks}",
                "--mode=universal",
                "--overwrite",
                f"--aapt2={aapt2}",
            ]
        )
        with zipfile.ZipFile(apks) as bundle_apks:
            bundle_apks.extract("universal.apk", temp)
        universal_apk = temp / "universal.apk"
        package_name, version_code, version_name = parse_badging(
            run([aapt, "dump", "badging", str(universal_apk)])
        )
        if package_name != expected_package:
            fail(f"Unexpected package name {package_name}; expected {expected_package}")
        if version_name != expected_version:
            fail(f"Unexpected versionName {version_name}; expected {expected_version}")
        if version_code <= 0:
            fail(f"versionCode must be positive, got {version_code}")
        ok(f"Bundletool universal APK identity verified: {package_name} {version_name} ({version_code})")
        check_release_manifest(
            run([aapt, "dump", "xmltree", str(universal_apk), "AndroidManifest.xml"]),
            package_name,
            distribution,
        )


def verify_aab(
    aab: Path,
    distribution: str,
    expected_version: str,
    expected_package: str,
    jarsigner: str,
    keytool: str,
    aapt: str,
    aapt2: str | None,
    bundletool: Path | None,
    expected_signer_cert_sha256: str | None,
    write_sha256: Path | None,
) -> None:
    if aab.suffix.lower() != ".aab":
        fail(f"Expected an .aab file, got: {aab}")
    if distribution != "play":
        fail("AAB verification is currently reserved for the Play distribution")
    require_aab_entries(aab)
    check_play_safe_source_gate(distribution)

    signer_output = run_jarsigner([jarsigner, "-verify", "-certs", "-verbose", str(aab)])
    signer_identity = parse_jarsigner_cert_identity(signer_output)
    ok(f"AAB JAR signature verified; signer {signer_identity}")
    keytool_output = run_jarsigner([keytool, "-printcert", "-jarfile", str(aab)])
    signer_cert_sha256 = parse_keytool_cert_sha256(keytool_output)
    ok(f"AAB signer cert SHA-256 {signer_cert_sha256.upper()}")
    if expected_signer_cert_sha256:
        expected_signer = normalize_sha256(expected_signer_cert_sha256)
        if signer_cert_sha256 != expected_signer:
            fail(
                "AAB signer certificate SHA-256 does not match expected stable release signer: "
                f"{signer_cert_sha256.upper()} != {expected_signer.upper()}"
            )
        ok("AAB signer matches expected certificate")

    runner: BundletoolRunner | None = None
    if bundletool:
        if not bundletool.exists():
            fail(f"bundletool does not exist: {bundletool}")
        runner = explicit_bundletool_runner(bundletool)
    else:
        runner = find_cached_bundletool_runner()

    if runner:
        resolved_aapt2 = aapt2 or find_android_tool("aapt2")
        verify_aab_with_bundletool(
            aab,
            runner,
            distribution,
            expected_version,
            expected_package,
            aapt,
            resolved_aapt2,
        )
    else:
        ok("Skipped bundletool universal APK manifest check; no --bundletool provided and no usable Gradle cache found")

    digest = sha256_file(aab)
    ok(f"AAB SHA-256 {digest.upper()}")
    if write_sha256:
        output = write_sha256.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(f"{digest}  {aab.name}\n", encoding="utf-8")
        ok(f"Wrote checksum file: {output}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, help="Release APK path. Defaults to the only APK under app/build/outputs/apk")
    parser.add_argument("--aab", type=Path, help="Release AAB path. Defaults to the only AAB for --distribution")
    parser.add_argument("--expected-version", required=True, help="Expected versionName, usually the tag without leading v")
    parser.add_argument("--expected-package", default=EXPECTED_PACKAGE)
    parser.add_argument(
        "--distribution",
        choices=["direct", "play"],
        default="direct",
        help="Distribution manifest expectations to enforce",
    )
    parser.add_argument("--aapt", help="Path to aapt")
    parser.add_argument("--aapt2", help="Path to aapt2, used for bundletool AAB universal APK checks")
    parser.add_argument("--apksigner", help="Path to apksigner")
    parser.add_argument("--jarsigner", default="jarsigner", help="Path to jarsigner")
    parser.add_argument("--keytool", default="keytool", help="Path to keytool")
    parser.add_argument("--bundletool", type=Path, help="Optional standalone bundletool jar for deep AAB manifest verification")
    parser.add_argument(
        "--expected-signer-cert-sha256",
        help="Require Signer #1 certificate SHA-256 digest to match this value",
    )
    parser.add_argument(
        "--reference-apk",
        type=Path,
        help="Require this APK to be signed by the same Signer #1 certificate",
    )
    parser.add_argument("--write-sha256", type=Path, help="Write SHA256SUMS.txt to this path")
    args = parser.parse_args()

    if args.apk and args.aab:
        fail("Pass only one artifact: --apk or --aab")

    if args.aab:
        aab = args.aab.resolve()
        if not aab.exists():
            fail(f"AAB does not exist: {aab}")
        aapt = args.aapt or find_android_tool("aapt")
        verify_aab(
            aab,
            args.distribution,
            args.expected_version,
            args.expected_package,
            args.jarsigner,
            args.keytool,
            aapt,
            args.aapt2,
            args.bundletool.resolve() if args.bundletool else None,
            args.expected_signer_cert_sha256,
            args.write_sha256,
        )
        return 0

    apk = (args.apk or find_release_apk(args.distribution)).resolve()
    if not apk.exists():
        fail(f"APK does not exist: {apk}")

    aapt = args.aapt or find_android_tool("aapt")
    apksigner = args.apksigner or find_android_tool("apksigner")

    package_name, version_code, version_name = parse_badging(
        run([aapt, "dump", "badging", str(apk)])
    )
    if package_name != args.expected_package:
        fail(f"Unexpected package name {package_name}; expected {args.expected_package}")
    if version_name != args.expected_version:
        fail(f"Unexpected versionName {version_name}; expected {args.expected_version}")
    if version_code <= 0:
        fail(f"versionCode must be positive, got {version_code}")
    ok(f"APK identity verified: {package_name} {version_name} ({version_code})")

    check_release_manifest(
        run([aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml"]),
        package_name,
        args.distribution,
    )

    signer_output = run([apksigner, "verify", "--verbose", "--print-certs", str(apk)])
    if "Verifies" not in signer_output:
        fail("apksigner output did not report verification success")
    if "Number of signers: 1" not in signer_output:
        fail("Expected exactly one APK signer")
    signer_cert_sha256 = parse_signer_cert_sha256(signer_output)
    ok(f"APK signature verified; signer cert SHA-256 {signer_cert_sha256.upper()}")

    if args.expected_signer_cert_sha256:
        expected_signer = normalize_sha256(args.expected_signer_cert_sha256)
        if signer_cert_sha256 != expected_signer:
            fail(
                "Signer certificate SHA-256 does not match expected stable release signer: "
                f"{signer_cert_sha256.upper()} != {expected_signer.upper()}"
            )
        ok("APK signer matches expected certificate")

    if args.reference_apk:
        reference = args.reference_apk.resolve()
        if not reference.exists():
            fail(f"Reference APK does not exist: {reference}")
        reference_output = run([apksigner, "verify", "--verbose", "--print-certs", str(reference)])
        if "Verifies" not in reference_output:
            fail("Reference APK did not verify")
        if "Number of signers: 1" not in reference_output:
            fail("Expected exactly one reference APK signer")
        reference_signer = parse_signer_cert_sha256(reference_output)
        if signer_cert_sha256 != reference_signer:
            fail(
                "APK signer does not match reference APK signer: "
                f"{signer_cert_sha256.upper()} != {reference_signer.upper()}"
            )
        ok("APK signer matches reference APK signer")

    digest = sha256_file(apk)
    ok(f"APK SHA-256 {digest.upper()}")
    if args.write_sha256:
        output = args.write_sha256.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(f"{digest}  {apk.name}\n", encoding="utf-8")
        ok(f"Wrote checksum file: {output}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
