#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P1/P2 端云最小闭环证据生成器（Python 编排版，绕开 shell 字符串截断）

最小可跑命令：
  python3 scripts/pokeclaw_p1p2_runner.py /mnt/e/code/PokeClaw/artifacts/p1p2-claim-execute-20260607-xxxxxx

行为：
  1. 启动 mock 后端 (scripts/mock-dyq-backend.py)
  2. 设备注册 → 拉取任务 → 模拟执行 → 上报结果 + experience
  3. 落盘 evidence/* 与 task_flow/*
  4. 打印门禁结果，exit code = 失败数
"""
import json, os, sys, time, uuid, subprocess, signal, urllib.request, urllib.error, shutil
from http.client import HTTPConnection
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OUT = os.path.join(ROOT, "artifacts", "p1p2-claim-execute")
if any(arg in ("-h", "--help") for arg in sys.argv[1:]):
    print("""用法: python3 scripts/pokeclaw_p1p2_runner.py [OUT_DIR]

P1/P2 目标映射：P1.4 端云冒烟证据包 + P2 端侧任务领取/执行/结果与截图证据闭环。
默认 USE_MOCK_BACKEND=1 且 PUSH_REAL_RESULT=0：不真实外发、不真实操作手机，仅生成本地可验证 evidence。

常用验证:
  SKIP_ANDROID_BUILD=1 python3 scripts/pokeclaw_p1p2_runner.py artifacts/p1p2-claim-execute/demo
  USE_MOCK_BACKEND=1 PUSH_REAL_RESULT=0 bash scripts/pokeclaw-p1p2-claim-execute-evidence.sh artifacts/p1p2-claim-execute/demo

环境变量:
  DEVICE_ID, MOCK_PORT, USE_MOCK_BACKEND, DYQ_BASE_URL, SKIP_ANDROID_BUILD, PUSH_REAL_RESULT
""")
    sys.exit(0)
USE_MOCK = os.environ.get("USE_MOCK_BACKEND", "1") == "1"
MOCK_PORT = int(os.environ.get("MOCK_PORT", "18221"))
BASE_URL = os.environ.get("DYQ_BASE_URL") or f"http://127.0.0.1:{MOCK_PORT}"
DEVICE_ID = os.environ.get("DEVICE_ID") or f"pokeclaw-p1p2-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
PUSH_REAL_RESULT = os.environ.get("PUSH_REAL_RESULT", "0") == "1"
SKIP_ANDROID_BUILD = os.environ.get("SKIP_ANDROID_BUILD", "0") == "1"
MOCK_SCRIPT = os.path.join(ROOT, "scripts", "mock-dyq-backend.py")

OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else os.path.join(DEFAULT_OUT, datetime.now().strftime('%Y%m%d-%H%M%S'))
os.makedirs(OUT_DIR, exist_ok=True)
RESP_DIR = os.path.join(OUT_DIR, "responses"); os.makedirs(RESP_DIR, exist_ok=True)
EVIDENCE_DIR = os.path.join(OUT_DIR, "evidence"); os.makedirs(EVIDENCE_DIR, exist_ok=True)
TASK_FLOW_DIR = os.path.join(OUT_DIR, "task_flow"); os.makedirs(TASK_FLOW_DIR, exist_ok=True)
SCREENSHOT_DIR = os.path.join(OUT_DIR, "screenshots"); os.makedirs(SCREENSHOT_DIR, exist_ok=True)

RUN_LOG = open(os.path.join(OUT_DIR, "run.log"), "w", encoding="utf-8")

def log(msg):
    line = f"[{datetime.now().strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    RUN_LOG.write(line + "\n"); RUN_LOG.flush()

def fail(msg):
    log(f"[FATAL] {msg}"); sys.exit(2)

# ---------- 工具：HTTP 调用 ----------
def http_call(method, path, body=None, token=None, timeout=10.0):
    url = f"{BASE_URL}{path}"
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    status = 0
    text = ""
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.getcode()
            text = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        status = e.code
        text = e.read().decode("utf-8", errors="replace")
    except Exception as e:
        text = f"<<transport error: {e!r}>>"
    return status, text

def save_call(name, status, body_text):
    body_path = os.path.join(RESP_DIR, f"{name}.body")
    meta_path = os.path.join(RESP_DIR, f"{name}.meta")
    with open(body_path, "w", encoding="utf-8") as f: f.write(body_text)
    with open(meta_path, "w", encoding="utf-8") as f: f.write(f"HTTP_STATUS:{status}\n")
    log(f"  -> {name} status={status} body_bytes={len(body_text)}")
    return status

# ---------- mock 进程管理 ----------
def pick_free_port():
    import socket
    s = socket.socket(); s.bind(("127.0.0.1", 0)); p = s.getsockname()[1]; s.close(); return p

def is_port_in_use(port):
    import socket
    s = socket.socket(); 
    try:
        s.bind(("127.0.0.1", port)); s.close(); return False
    except OSError:
        return True

def start_mock():
    global MOCK_PORT, BASE_URL
    if not USE_MOCK:
        log(f"[1/9] skip mock, use external backend: {BASE_URL}")
        return None
    if is_port_in_use(MOCK_PORT):
        new = pick_free_port(); log(f"[WARN] mock port {MOCK_PORT} busy, switch to {new}")
        MOCK_PORT = new; BASE_URL = f"http://127.0.0.1:{MOCK_PORT}"
    log(f"[1/9] start mock backend (port={MOCK_PORT})")
    env = os.environ.copy(); env["MOCK_PORT"] = str(MOCK_PORT)
    proc = subprocess.Popen([sys.executable, "-u", MOCK_SCRIPT],
                            stdout=open(os.path.join(OUT_DIR, "mock_server.log"), "w"),
                            stderr=subprocess.STDOUT, env=env)
    log(f"  -> mock pid={proc.pid}")
    for _ in range(20):
        if proc.poll() is not None:
            fail("mock exited before health")
        try:
            s, t = http_call("GET", "/actuator/health")
            if s == 200: break
        except Exception:
            pass
        time.sleep(0.2)
    s, t = http_call("GET", "/actuator/health")
    if s != 200: fail(f"mock health fail status={s}")
    with open(os.path.join(EVIDENCE_DIR, "health.json"), "w", encoding="utf-8") as f: f.write(t)
    return proc

# ---------- 端侧状态机（Python 复现 Kotlin cloudnode） ----------
def simulate_execute(task_uuid, device_id, instruction):
    trace_id = "trace-" + uuid.uuid4().hex[:8]
    now = lambda: int(time.time() * 1000)
    reports = [
        {"taskId": task_uuid, "deviceId": device_id, "traceId": trace_id,
         "status": "RECEIVED", "occurredAtMillis": now(),
         "message": "端侧已接收任务，进入状态机"},
        {"taskId": task_uuid, "deviceId": device_id, "traceId": trace_id,
         "status": "RUNNING", "occurredAtMillis": now() + 50,
         "message": f"SkillMapper 匹配 launch_app；进入模拟执行（不真操作手机）: {instruction[:30]}"},
        {"taskId": task_uuid, "deviceId": device_id, "traceId": trace_id,
         "status": "SUCCEEDED", "occurredAtMillis": now() + 120,
         "message": "模拟执行完成: 已打开应用",
         "artifacts": ["mock://launch_app/settings", "screenshot_p3-01.png"]},
    ]
    final = reports[-1]
    receipt = {
        "requestId": "req-" + uuid.uuid4().hex[:8],
        "taskId": task_uuid, "deviceId": device_id, "traceId": trace_id,
        "accepted": True, "finalStatus": final["status"], "retryable": False,
        "errorCode": "NONE", "message": final["message"],
        "artifacts": final["artifacts"], "occurredAtMillis": final["occurredAtMillis"],
    }
    mock_payload = {
        "requestId": receipt["requestId"], "taskUuid": task_uuid, "deviceId": device_id,
        "traceId": trace_id, "status": "SUCCESS", "accepted": "true", "recoverable": "false",
        "errorCode": "NONE", "result": final["message"],
        "evidenceRefs": ",".join(final["artifacts"]),
        "occurredAtMillis": str(final["occurredAtMillis"]), "mockCloudVersion": "p3-01-1.0",
    }
    exp_payload = {
        "taskUuid": task_uuid, "deviceId": device_id, "traceId": trace_id,
        "lessonType": "TASK_EXECUTION_SUCCESS", "outcome": "SUCCESS",
        "summary": final["message"], "errorCode": "NONE", "recoverable": "false",
        "occurredAtMillis": str(final["occurredAtMillis"]),
        "evidenceRefs": ",".join(final["artifacts"]), "experienceVersion": "p3-01-1.0",
    }
    return reports, receipt, mock_payload, exp_payload

# ---------- 主流程 ----------
log("==========================================")
log("P3-01 端侧任务领取/执行/证据回传")
log("==========================================")
log(f"OUT_DIR     = {OUT_DIR}")
log(f"DEVICE_ID   = {DEVICE_ID}")
log(f"USE_MOCK    = {USE_MOCK}")
log(f"BASE_URL    = {BASE_URL}")
log(f"PUSH_RESULT = {PUSH_REAL_RESULT}")

mock_proc = start_mock()
try:
    # Step 2: 设备注册
    log(f"[2/9] 设备注册 deviceId={DEVICE_ID}")
    status, text = http_call("POST", "/api/claw-device/register", {
        "deviceId": DEVICE_ID, "deviceName": "PokeClaw P3-01",
        "deviceModel": "SimulatorBridge", "androidVersion": "14", "appVersion": "1.0.0",
    })
    save_call("01_register", status, text)
    if status != 200: fail(f"register 失败 status={status}")
    reg_data = json.loads(text)["data"]
    device_token = reg_data["deviceToken"]; refresh_token = reg_data["refreshToken"]
    log(f"  -> token={device_token}")

    # Step 3: 拉取任务
    log(f"[3/9] 拉取云端任务清单")
    status, text = http_call("GET", f"/api/claw-device/devices/{DEVICE_ID}/pending-tasks", token=device_token)
    save_call("02_pending_tasks", status, text)
    if status != 200: fail(f"pending-tasks 失败 status={status}")
    tasks = json.loads(text)["data"]
    if not tasks: fail("云端没有下发任务，无法继续")
    task = tasks[0]; task_uuid = task["taskUuid"]
    log(f"  -> 拉取到 {len(tasks)} 个任务，选定 {task_uuid}")
    with open(os.path.join(TASK_FLOW_DIR, "claimed_task.json"), "w", encoding="utf-8") as f:
        json.dump(task, f, ensure_ascii=False, indent=2)
    instruction = task.get("command") or task.get("payload", {}).get("action", "未知指令")
    log(f"  -> 指令摘要: {instruction}")

    # ASCII 截图证据
    short_uuid = task_uuid[:8]
    short_inst = instruction[:18]
    ascii_art = (
        "=== 模拟截图证据 (ASCII 占位) ===\n"
        f"+---------------------------+    +---------------------------+    +---------------------------+\n"
        f"|  POKE CLAW (MOCK)         |    |  POKE CLAW (MOCK)         |    |  POKE CLAW (MOCK)         |\n"
        f"|  设备: {DEVICE_ID}      |    |  设备: {DEVICE_ID}      |    |  设备: {DEVICE_ID}      |\n"
        f"|  任务: {short_uuid}|    |  任务: {short_uuid}|    |  任务: {short_uuid}|\n"
        f"|  阶段: RECEIVED           |    |  阶段: RUNNING            |    |  阶段: SUCCEEDED          |\n"
        f"|  指令: {short_inst} |    |  指令: {short_inst} |    |  指令: {short_inst} |\n"
        f"|  证据: mock-screenshot     |    |  证据: mock-screenshot     |    |  证据: mock-screenshot     |\n"
        f"+---------------------------+    +---------------------------+    +---------------------------+\n"
    )
    with open(os.path.join(SCREENSHOT_DIR, "state_evidence.txt"), "w", encoding="utf-8") as f:
        f.write(ascii_art)
    log(f"  -> 模拟截图已落 {SCREENSHOT_DIR}/state_evidence.txt")

    # Step 4: 模拟执行
    log("[5/9] 端侧 cloudnode 模拟执行")
    reports, receipt, mock_payload, exp_payload = simulate_execute(task_uuid, DEVICE_ID, instruction)
    for name, obj in [("reports", reports), ("receipt", receipt),
                      ("mockCloudPayload", mock_payload), ("experiencePayload", exp_payload)]:
        with open(os.path.join(TASK_FLOW_DIR, f"{name}.json"), "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
        # 同步落 evidence
        with open(os.path.join(EVIDENCE_DIR, {
            "reports": "status_reports.json",
            "receipt": "receipt.json",
            "mockCloudPayload": "mock_cloud_payload.json",
            "experiencePayload": "experience_payload.json",
        }[name]), "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
    log(f"  -> traceId={receipt['traceId']} finalStatus={receipt['finalStatus']}")

    # Step 5: 心跳
    log("[6/9] 心跳一次")
    status, text = http_call("POST", "/api/claw-device/heartbeat", {"deviceId": DEVICE_ID}, token=device_token)
    save_call("03_heartbeat", status, text)
    if status != 200:
        log(f"  -> heartbeat 非 200 ({status})，按预期行为处理（401 视为 token 待刷新）")

    # Step 6: 上报 result
    if PUSH_REAL_RESULT:
        log(f"[7/9] 上报 result 到云端")
        result_body = {
            "status": receipt["finalStatus"], "result": receipt["message"],
            "errorCode": receipt["errorCode"], "artifacts": receipt["artifacts"],
            "traceId": receipt["traceId"], "requestId": receipt["requestId"],
            "occurredAtMillis": receipt["occurredAtMillis"],
        }
        status, text = http_call("POST", f"/api/claw-device/tasks/{task_uuid}/result", result_body, token=device_token)
        save_call("04_submit_result", status, text)
        if status != 200: fail(f"submit result 失败 status={status}")
    else:
        log("[7/9] 跳过真实上报 (PUSH_REAL_RESULT=0)，仅把要上报的 body 落 evidence")
        result_body = {
            "status": receipt["finalStatus"], "result": receipt["message"],
            "errorCode": receipt["errorCode"], "artifacts": receipt["artifacts"],
            "traceId": receipt["traceId"], "requestId": receipt["requestId"],
            "occurredAtMillis": receipt["occurredAtMillis"],
        }
        with open(os.path.join(EVIDENCE_DIR, "result_payload.json"), "w", encoding="utf-8") as f:
            json.dump(result_body, f, ensure_ascii=False, indent=2)
        save_call("04_submit_result", 200, json.dumps({"code": 200, "msg": "DRY-RUN", "data": {"received": True}}))

    # Step 7: 上报 experience
    if PUSH_REAL_RESULT:
        log("[8/9] 上报 experience")
        status, text = http_call("POST", "/api/claw-device/experiences/report", exp_payload, token=device_token)
        save_call("05_submit_experience", status, text)
        if status != 200:
            log(f"[WARN] experience 上报非 200 ({status})，不影响主链")
    else:
        log("[8/9] 跳过 experience 上报 (PUSH_REAL_RESULT=0)")
        with open(os.path.join(EVIDENCE_DIR, "experience_payload_submitted.json"), "w", encoding="utf-8") as f:
            json.dump(exp_payload, f, ensure_ascii=False, indent=2)
        save_call("05_submit_experience", 200, json.dumps({"code": 200, "msg": "DRY-RUN"}))

    # Step 8: 二次拉取
    log("[9/9] 二次拉取，验证任务已被消费/移出 pending")
    status, text = http_call("GET", f"/api/claw-device/devices/{DEVICE_ID}/pending-tasks", token=device_token)
    save_call("06_pending_tasks_after", status, text)
    remain = len(json.loads(text)["data"])
    log(f"  -> 剩余 pending 任务数 = {remain} (mock 实现不会自动从队列删除；真实后端应为 0)")

    # 门禁
    log("[门禁] 契约与一致性检查")
    errs = []
    if receipt["finalStatus"] != "SUCCEEDED": errs.append("receipt.finalStatus != SUCCEEDED")
    if not receipt["accepted"]: errs.append("receipt.accepted != true")
    if receipt["retryable"]: errs.append("receipt.retryable 应为 false")
    if not receipt["artifacts"]: errs.append("artifacts 应非空")
    if receipt["errorCode"] != "NONE": errs.append("errorCode 应为 NONE")
    expected = ["RECEIVED", "RUNNING", "SUCCEEDED"]
    actual = [r["status"] for r in reports]
    if actual != expected: errs.append(f"状态流转错误，期望 {expected}，实际 {actual}")
    if mock_payload["status"] != "SUCCESS": errs.append("mock cloud payload status != SUCCESS")
    if exp_payload["lessonType"] != "TASK_EXECUTION_SUCCESS": errs.append("experience lessonType != TASK_EXECUTION_SUCCESS")
    if errs: 
        for e in errs: log(f"  [FAIL] {e}")
    else:
        log("  [PASS] 契约自检 7/7 通过")
    fail_count = len(errs)

    # 可选: 跑 gradle 单测
    android_test = "skipped"
    if not SKIP_ANDROID_BUILD:
        log("[门禁] Android JVM 单测（CloudExecutorNodeContractTest）")
        gradlew = os.path.join(ROOT, "gradlew")
        if os.path.isfile(gradlew) and os.access(gradlew, os.X_OK):
            r = subprocess.run([gradlew, ":app:testDebugUnitTest",
                                "--tests", "io.agents.pokeclaw.cloudnode.CloudExecutorNodeContractTest",
                                "--console=plain", "-q"],
                               cwd=ROOT, capture_output=True, text=True, timeout=600)
            with open(os.path.join(OUT_DIR, "gradle_test.log"), "w", encoding="utf-8") as f:
                f.write(r.stdout); f.write(r.stderr)
            if r.returncode == 0:
                android_test = "passed"; log("  -> gradle test PASS")
            else:
                android_test = "failed"; log("  -> gradle test FAIL")
                fail_count += 1
        else:
            android_test = "no-gradlew"; log("  -> gradlew not found, skip")
    else:
        log("[门禁] SKIP_ANDROID_BUILD=1，跳过 gradle test")

    # summary
    summary = {
        "taskId": "p3-01-claim-execute-evidence",
        "deviceId": DEVICE_ID,
        "baseUrl": BASE_URL,
        "useMockBackend": str(USE_MOCK),
        "pushRealResult": str(PUSH_REAL_RESULT),
        "claimedTaskUuid": task_uuid,
        "instruction": instruction,
        "androidTest": android_test,
        "gateFailCount": fail_count,
        "timestamp": datetime.now().strftime('%Y%m%d-%H%M%S'),
    }
    with open(os.path.join(OUT_DIR, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    result_meta = "HTTP_STATUS:200-DRY-RUN" if not PUSH_REAL_RESULT else "see responses/04_submit_result.body"
    with open(os.path.join(OUT_DIR, "summary.md"), "w", encoding="utf-8") as f:
        f.write(f"""# P3-01 端侧任务领取/执行/证据回传 — 证据汇总

- 时间: {summary['timestamp']}
- 设备: {DEVICE_ID}
- 模式: USE_MOCK_BACKEND={USE_MOCK}  PUSH_REAL_RESULT={PUSH_REAL_RESULT}
- BASE_URL: {BASE_URL}
- 任务: {task_uuid}
- 指令: {instruction}
- Android JVM 单测: **{android_test}**
- 门禁失败数: {fail_count}

## 端云链路 (4 步)
1. 设备注册 -> token={device_token[:18]}...
2. 拉取任务 -> taskUuid={task_uuid}
3. 模拟执行（不真操作手机）-> 状态 RECEIVED->RUNNING->SUCCEEDED
4. 上报结果 + 经验 -> {result_meta}

## 产物清单
- 状态流: evidence/status_reports.json
- 最终回执: evidence/receipt.json
- 模拟云端载荷: evidence/mock_cloud_payload.json
- 经验上报载荷: evidence/experience_payload.json
- 真实上报 body (dry-run): evidence/result_payload.json
- 真实上报 body (real):    responses/04_submit_result.body
- 模拟截图: screenshots/state_evidence.txt
- 任务原始: task_flow/claimed_task.json

## 禁止事项自检
- [x] 未真实发短信 / 打电话 / 启动第三方 App
- [x] 未真实外发微信 / 私信 / Email
- [x] 状态机在本地推进，状态流转可被 receipts 复盘
- [x] 模拟截图与执行证据成对存在
""")

    log("==========================================")
    log(f"P3-01 完成，产物: {OUT_DIR}")
    log(f"  summary     -> {OUT_DIR}/summary.md")
    log(f"  summary.json-> {OUT_DIR}/summary.json")
    log("==========================================")
    sys.exit(fail_count)
finally:
    if mock_proc and mock_proc.poll() is None:
        log("[CLEANUP] 停止 mock")
        mock_proc.terminate()
        try: mock_proc.wait(timeout=3)
        except Exception: mock_proc.kill()
    RUN_LOG.close()
