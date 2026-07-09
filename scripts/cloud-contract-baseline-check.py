#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
cloud-contract-baseline-check.py
================================
从 api-contracts/device.openapi.yaml 提取端云契约基线，并对照端侧 Kotlin
实现（DeviceApi.kt / CloudModels.kt / ClawSignatureGenerator.kt）生成
对齐清单。

仅依赖 Python 3 stdlib；YAML 用受限正则解析（路径 1 层缩进/字段 2 层/schema
3 层、列表项 2 缩进），不安装 pyyaml。

作者：端云契约摸底 worker（djs-loop t_d2f8d4b7）
"""
from __future__ import annotations

import json
import re
import sys
import datetime
import pathlib
import os

REPO_ROOT = pathlib.Path(os.environ.get("POKECLAW_ROOT", ".")).resolve()
OPENAPI = REPO_ROOT / "api-contracts" / "device.openapi.yaml"
DEVICE_API = REPO_ROOT / "app" / "src" / "main" / "java" / "io" / "agents" / "pokeclaw" / "cloud" / "api" / "DeviceApi.kt"
CLOUD_MODELS = REPO_ROOT / "app" / "src" / "main" / "java" / "io" / "agents" / "pokeclaw" / "cloud" / "model" / "CloudModels.kt"
SIGNER = REPO_ROOT / "app" / "src" / "main" / "java" / "io" / "agents" / "pokeclaw" / "cloud" / "auth" / "ClawSignatureGenerator.kt"


# ---------------------------------------------------------------------------
# YAML 解析（受限）
# ---------------------------------------------------------------------------

def parse_openapi(text: str) -> dict:
    """受限制 YAML 解析。仅识别 device.openapi.yaml 现有结构。"""
    lines = text.splitlines()
    state = {"in_paths": False, "in_schemas": False, "current_path": None, "current_path_method": None}
    endpoints = []
    schemas = set()
    request_schemas = []
    response_schemas = []
    security_headers = []

    for raw in lines:
        # 顶层 paths:
        if re.match(r"^paths:\s*$", raw):
            state["in_paths"] = True
            state["in_schemas"] = False
            continue
        if re.match(r"^components:\s*$", raw):
            state["in_paths"] = False
            state["in_schemas"] = False
            continue
        # 顶层 schemas: 在 components 下面，缩进 2 空格
        if re.match(r"^  schemas:\s*$", raw):
            state["in_schemas"] = True
            continue
        if state["in_schemas"]:
            # schema 名在 4 空格缩进
            m = re.match(r"^    ([A-Za-z][A-Za-z0-9_]+):\s*$", raw)
            if m:
                schemas.add(m.group(1))
                continue
            # 离开 schemas 段（其他顶层段）
            if re.match(r"^[a-zA-Z]", raw) and not raw.startswith("    "):
                state["in_schemas"] = False
            continue
        if state["in_paths"]:
            m = re.match(r"^  (/[^:]+):\s*$", raw)
            if m:
                state["current_path"] = m.group(1)
                state["current_path_method"] = None
                continue
            m = re.match(r"^    (get|post|put|delete|patch):\s*$", raw)
            if m and state["current_path"]:
                state["current_path_method"] = m.group(1).upper()
                endpoint = {"path": state["current_path"], "method": state["current_path_method"]}
                endpoints.append(endpoint)
                continue
            if state["current_path_method"]:
                # 抓取 $ref 用作 schema 引用
                m_ref = re.search(r"\$ref:\s*'#/components/schemas/([A-Za-z0-9_]+)'", raw)
                if m_ref:
                    if "request_schema" not in endpoint:
                        endpoint["request_schema"] = m_ref.group(1)
                        request_schemas.append(m_ref.group(1))
                    else:
                        endpoint["response_schema"] = m_ref.group(1)
                        response_schemas.append(m_ref.group(1))
                m_sum = re.search(r"^      summary:\s*(.+)$", raw)
                if m_sum:
                    endpoint["summary"] = m_sum.group(1).strip()
                m_opid = re.search(r"^      operationId:\s*([A-Za-z0-9_]+)\s*$", raw)
                if m_opid:
                    endpoint["operation_id"] = m_opid.group(1)
                if re.search(r"security:\s*$", raw):
                    endpoint["auth_required"] = True
            continue
        # 顶层 schemas
        m = re.match(r"^      ([A-Za-z][A-Za-z0-9_]+):\s*$", raw)
        if m and ("components:" in "\n".join(lines[: max(0, lines.index(raw) - 50)])):
            schemas.add(m.group(1))

    # 操作 ID
    for ep in endpoints:
        ep.setdefault("auth_required", False)

    return {
        "endpoints": endpoints,
        "schemas": sorted(schemas),
        "request_schemas": sorted(set(request_schemas)),
        "response_schemas": sorted(set(response_schemas)),
    }


def parse_enum_block(text: str, name: str) -> list[str]:
    """抓 enum: [...] 段。在 YAML 中 enum 是 type 字段的子项。"""
    # 匹配 name: \n 后某行有 enum: [...]
    m = re.search(
        rf"\b{re.escape(name)}:\s*\n(?:\s+\w+:.*\n)*?\s+enum:\s*\[([^\]]+)\]",
        text, re.M
    )
    if m:
        parts = [p.strip().strip('"\'') for p in m.group(1).split(",")]
        return [p for p in parts if p]
    return []


# ---------------------------------------------------------------------------
# Kotlin 解析（grep 风格）
# ---------------------------------------------------------------------------

ENDPOINT_RE = re.compile(
    r'@(GET|POST|PUT|DELETE|PATCH)\(\s*"([^"]+)"\s*\)',
)


def parse_device_api(text: str) -> list[dict]:
    eps = []
    for m in ENDPOINT_RE.finditer(text):
        method = m.group(1)
        path = m.group(2)
        # 函数名：向后搜 suspend fun NAME
        tail = text[m.end():m.end() + 600]
        fn_match = re.search(r"suspend\s+fun\s+([A-Za-z0-9_]+)", tail)
        func = fn_match.group(1) if fn_match else "?"
        # 装饰头：检查是否含 Signature 头
        has_sig = bool(re.search(r"X-Claw-Signature", tail[:200]))
        eps.append({
            "method": method,
            "path": path,
            "function": func,
            "has_signature_headers": has_sig,
        })
    return eps


# 抓 data class XxxRequest/Response( ... ) - 用括号平衡
def find_matching(text: str, start: int) -> int:
    depth = 0
    i = start
    in_str = False
    esc = False
    while i < len(text):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    return -1


DATA_CLASS_RE = re.compile(r"data\s+class\s+([A-Za-z0-9_]+)\s*\(")


def parse_cloud_models(text: str) -> dict:
    out = {}
    for m in DATA_CLASS_RE.finditer(text):
        cls = m.group(1)
        # 找到匹配的 )
        body_start = m.end()  # "(" 之后
        body_end = find_matching(text, body_start - 1)
        if body_end < 0:
            continue
        body = text[body_start:body_end]
        fields = []
        # 用 SerializedName + val 配对
        for fm in re.finditer(
            r'@SerializedName\(\s*"([^"]+)"\s*\)\s*val\s+([A-Za-z0-9_]+)\s*:\s*([^\n,)]+)',
            body,
        ):
            fields.append({
                "serialized_name": fm.group(1),
                "kotlin_name": fm.group(2),
                "type": fm.group(3).strip().rstrip(',').strip(),
            })
        out[cls] = fields
    return out


def parse_signer(text: str) -> dict:
    info = {
        "algorithm": None,
        "hashing": None,
        "key_holder": "deviceToken",
        "headers": [],
    }
    m_alg = re.search(r'const\s+val\s+HMAC_ALGORITHM\s*=\s*"([^"]+)"', text)
    if m_alg:
        info["algorithm"] = m_alg.group(1)
    m_hash = re.search(r'const\s+val\s+SHA256_ALGORITHM\s*=\s*"([^"]+)"', text)
    if m_hash:
        info["hashing"] = m_hash.group(1)
    for h in re.findall(r'X-Claw-(Timestamp|Nonce|Signature)', text):
        info["headers"].append(f"X-Claw-{h}")
    info["headers"] = sorted(set(info["headers"]))
    return info


# ---------------------------------------------------------------------------
# 对齐判定
# ---------------------------------------------------------------------------

PATH_NORMALIZE = re.compile(r"\{([a-zA-Z0-9_]+)\}")


def norm(path: str) -> str:
    return PATH_NORMALIZE.sub(r"{\1}", path)


def coverage_check(openapi: dict, device_api: list[dict], models: dict, signer: dict) -> list[dict]:
    rows = []
    # 端点覆盖
    def _norm(p: str) -> str:
        p = p.lstrip("/")
        return PATH_NORMALIZE.sub(r"{\1}", p)

    api_paths = {_norm(e["path"]): e for e in device_api}
    for ep in openapi["endpoints"]:
        p = _norm(ep["path"])
        if p in api_paths:
            actual = api_paths[p]
            # 还要看 method
            if actual["method"].upper() == ep["method"]:
                rows.append({
                    "kind": "endpoint",
                    "name": f"{ep['method']} /{p}",
                    "side": f"DeviceApi.kt::{actual['function']}",
                    "status": "✓",
                    "note": ep.get("summary", ""),
                })
            else:
                rows.append({
                    "kind": "endpoint",
                    "name": f"{ep['method']} /{p}",
                    "side": f"DeviceApi.kt::{actual['function']} (method={actual['method']})",
                    "status": "✗",
                    "note": f"method mismatch (yaml={ep['method']}, kt={actual['method']})",
                })
        else:
            rows.append({
                "kind": "endpoint",
                "name": f"{ep['method']} /{p}",
                "side": "missing",
                "status": "✗",
                "note": "未在 DeviceApi.kt 中找到匹配路径",
            })
    # 关键 schema 覆盖
    key_models = {
        "DeviceRegisterRequest": openapi["request_schemas"],
        "DeviceHeartbeatRequest": openapi["request_schemas"],
        "TaskResultRequest": openapi["request_schemas"],
        "TokenRefreshRequest": openapi["request_schemas"],
        "PendingTaskItem": openapi["response_schemas"],
    }
    for cls, _ in key_models.items():
        if cls in models:
            rows.append({
                "kind": "schema",
                "name": cls,
                "side": f"CloudModels.kt::{cls} (fields={len(models[cls])})",
                "status": "✓",
                "note": "",
            })
        else:
            rows.append({
                "kind": "schema",
                "name": cls,
                "side": "missing",
                "status": "✗",
                "note": "未在 CloudModels.kt 中找到",
            })
    # 扩展字段识别
    extra_extensions = []
    for cls, fields in models.items():
        for f in fields:
            # 已知契约字段
            if cls == "TaskResultRequest" and f["kotlin_name"] in {
                "errorCategory", "errorCode", "errorDetail", "recoverable",
                "suggestedAction", "screenshotBase64", "logSnippet"
            }:
                extra_extensions.append(f"{cls}.{f['kotlin_name']}")
            if cls == "TaskResultRequest" and f["kotlin_name"] in {"status", "result", "errorMessage", "executionTimeMs", "toolCalls", "evidenceUrls", "modelUsed"}:
                pass
    for ext in sorted(set(extra_extensions)):
        rows.append({
            "kind": "extension",
            "name": ext,
            "side": f"CloudModels.kt::{ext.split('.')[0]}.{ext.split('.')[1]}",
            "status": "△",
            "note": "端侧扩展字段（契约未强制要求）",
        })
    # 签名算法
    expected = "HmacSHA256"
    actual = signer["algorithm"] or ""
    rows.append({
        "kind": "signing",
        "name": "HMAC-SHA256",
        "side": f"ClawSignatureGenerator.kt (algorithm={actual})",
        "status": "✓" if actual == expected else "✗",
        "note": f"期望 {expected}, 实际 {actual}",
    })
    expected_headers = {"X-Claw-Timestamp", "X-Claw-Nonce", "X-Claw-Signature"}
    rows.append({
        "kind": "signing",
        "name": "X-Claw-* 头",
        "side": f"headers={signer['headers']}",
        "status": "✓" if set(signer["headers"]) >= expected_headers else "✗",
        "note": f"缺少 {expected_headers - set(signer['headers'])}" if set(signer["headers"]) < expected_headers else "",
    })
    return rows


def render_coverage_md(rows: list[dict]) -> str:
    lines = [
        "# 端侧 Kotlin 实现与契约对照",
        "",
        f"生成时间: {datetime.datetime.now().isoformat(timespec='seconds')}",
        "",
        "| 类别 | 契约项 | 端侧位置 | 状态 | 备注 |",
        "|------|--------|----------|------|------|",
    ]
    for r in rows:
        lines.append(
            f"| {r['kind']} | {r['name']} | {r['side']} | {r['status']} | {r['note']} |"
        )
    lines.append("")
    cnt = {"✓": 0, "△": 0, "✗": 0}
    for r in rows:
        cnt[r["status"]] = cnt.get(r["status"], 0) + 1
    lines.append(f"汇总: ✓={cnt['✓']} △={cnt['△']} ✗={cnt['✗']} (共 {len(rows)} 项)")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def main(out_dir: pathlib.Path) -> int:
    out_dir.mkdir(parents=True, exist_ok=True)
    if not OPENAPI.exists():
        print(f"[FAIL] 找不到契约: {OPENAPI}")
        return 1

    openapi_text = OPENAPI.read_text(encoding="utf-8")
    parsed = parse_openapi(openapi_text)
    enums = {
        "task_status": parse_enum_block(openapi_text, "status"),
        "network_type": parse_enum_block(openapi_text, "networkType"),
    }

    baseline = {
        "version": "1.0.0",
        "openapi_version": "3.0.3",
        "extracted_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "endpoints": parsed["endpoints"],
        "schemas": parsed["schemas"],
        "request_schemas": parsed["request_schemas"],
        "response_schemas": parsed["response_schemas"],
        "signing": {
            "headers": ["X-Claw-Timestamp", "X-Claw-Nonce", "X-Claw-Signature"],
            "algorithm": "HMAC-SHA256",
            "key": "deviceToken",
            "signing_string": "timestamp + '\\n' + nonce + '\\n' + path + '\\n' + sha256_hex(body)",
        },
        "enums": enums,
        "code_whitelist": {
            "success": [0, 200],
            "auth_failure": [401],
        },
    }

    # 端侧解析
    api_text = DEVICE_API.read_text(encoding="utf-8")
    models_text = CLOUD_MODELS.read_text(encoding="utf-8")
    signer_text = SIGNER.read_text(encoding="utf-8")
    device_api = parse_device_api(api_text)
    models = parse_cloud_models(models_text)
    signer = parse_signer(signer_text)

    rows = coverage_check(parsed, device_api, models, signer)

    # 输出文件
    baseline_path = out_dir / "baseline.json"
    baseline_path.write_text(json.dumps(baseline, indent=2, ensure_ascii=False), encoding="utf-8")
    coverage_path = out_dir / "kotlin-coverage.md"
    coverage_path.write_text(render_coverage_md(rows), encoding="utf-8")

    # 摘要
    cnt = {"✓": 0, "△": 0, "✗": 0}
    for r in rows:
        cnt[r["status"]] = cnt.get(r["status"], 0) + 1

    summary = {
        "baseline": {
            "endpoints": len(baseline["endpoints"]),
            "schemas": len(baseline["schemas"]),
            "request_schemas": len(baseline["request_schemas"]),
            "response_schemas": len(baseline["response_schemas"]),
        },
        "coverage": cnt,
        "files": {
            "baseline_json": str(baseline_path),
            "coverage_md": str(coverage_path),
        },
    }
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    # 失败闸：只要 ✗ > 0，退出码 1
    if cnt["✗"] > 0:
        return 1
    return 0


if __name__ == "__main__":
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path("artifacts/cloud-contract-baseline/manual")
    sys.exit(main(out))
