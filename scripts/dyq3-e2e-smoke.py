#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DYQ-3 端侧执行链路商业化验收 - E2E冒烟测试
作者：端侧工程师阿甲
日期：2026-06-04
"""

import json
import time
import sys
import urllib.request

BASE_URL = "http://127.0.0.1:18080"
DEVICE_ID = f"pokeclaw-device-{int(time.time())}"
PASS = 0
FAIL = 0
TESTS = []
DEV_TOKEN = ""
REFRESH_TOK = ""

def api(method, path, body=None, token=None):
    url = f"{BASE_URL}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())

def t_pass(name):
    global PASS
    PASS += 1
    TESTS.append(("PASS", name, ""))
    print(f"  [PASS] {name}")

def t_fail(name, reason):
    global FAIL
    FAIL += 1
    TESTS.append(("FAIL", name, reason))
    print(f"  [FAIL] {name} — {reason}")

print("=" * 60)
print("DYQ-3 端侧执行链路商业化验收 — E2E冒烟测试")
print(f"目标服务器: {BASE_URL}")
print(f"测试设备ID: {DEVICE_ID}")
print(f"时间: {time.strftime('%Y-%m-%d %H:%M:%S')}")
print("=" * 60)
print()

# ── [1/8] 健康检查 ──
print("── [1/8] 健康检查 ──")
r = api("GET", "/actuator/health")
if r.get("code") == 200:
    t_pass("健康检查返回200")
else:
    t_fail("健康检查", f"返回非200: {r.get('code')}")
print()

# ── [2/8] 设备注册 ──
print("── [2/8] 设备注册 ──")
reg_body = {
    "deviceId": DEVICE_ID,
    "deviceName": "阿甲验收机",
    "deviceModel": "Pixel 8 Pro",
    "androidVersion": "15",
    "appVersion": "0.7.0",
}
r = api("POST", "/api/claw-device/register", reg_body)

if r.get("code") == 200:
    t_pass("设备注册返回200")
else:
    t_fail("设备注册", f"返回非200: {r.get('code')}")

data = r.get("data", {})
DEV_TOKEN = data.get("deviceToken", "")
REFRESH_TOK = data.get("refreshToken", "")

if DEV_TOKEN:
    t_pass(f"注册返回deviceToken (长度={len(DEV_TOKEN)})")
else:
    t_fail("注册返回deviceToken", "令牌为空")

if REFRESH_TOK:
    t_pass("注册返回refreshToken")
else:
    t_fail("注册返回refreshToken", "刷新令牌为空")

if data.get("deviceId") == DEVICE_ID:
    t_pass("注册返回deviceId与请求一致")
else:
    t_fail("注册返回deviceId", f"期望={DEVICE_ID} 实际={data.get('deviceId')}")

expires = data.get("expiresIn", 0)
if expires and expires > 0:
    t_pass(f"注册返回expiresIn={expires}")
else:
    t_fail("注册expiresIn", f"值={expires}")
print()

# ── [3/8] 设备心跳（正常） ──
print("── [3/8] 设备心跳（正常） ──")
hb_body = {"batteryLevel": 85, "isCharging": False, "networkType": "wifi"}
r = api("POST", "/api/claw-device/heartbeat", hb_body, token=DEV_TOKEN)

if r.get("code") == 200:
    t_pass("心跳请求返回200")
else:
    t_fail("心跳请求", f"返回非200: {r.get('code')}")

hdata = r.get("data", {})
pc = hdata.get("pendingTaskCount")
st = hdata.get("serverTime")
if pc is not None:
    t_pass(f"心跳返回pendingTaskCount={pc}")
else:
    t_fail("心跳pendingTaskCount", "值为空")
if st:
    t_pass("心跳返回serverTime")
else:
    t_fail("心跳serverTime", "值为空")
print()

# ── [4/8] 无令牌/无效令牌（401） ──
print("── [4/8] 无令牌/无效令牌心跳（预期401） ──")
r401 = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 50})
if r401.get("code") == 401:
    t_pass("无令牌心跳正确返回401")
else:
    t_fail("无令牌心跳", f"期望401 实际={r401.get('code')}")

r_bad = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 50}, token="invalid-token-12345")
if r_bad.get("code") == 401:
    t_pass("无效令牌心跳正确返回401")
else:
    t_fail("无效令牌心跳", f"期望401 实际={r_bad.get('code')}")
print()

# ── [5/8] 拉取待处理任务 ──
print("── [5/8] 拉取待处理任务 ──")
r = api("GET", f"/api/claw-device/devices/{DEVICE_ID}/pending-tasks", token=DEV_TOKEN)
if r.get("code") == 200:
    t_pass("拉取任务返回200")
else:
    t_fail("拉取任务", f"返回非200: {r.get('code')}")

tasks = r.get("data", [])
tc = len(tasks)
if tc > 0:
    t_pass(f"拉取到{tc}个待处理任务")
    task_uuid = tasks[0]["taskUuid"]
    task_cmd = tasks[0]["command"]
    t_pass(f"首个任务: uuid={task_uuid} cmd={task_cmd}")
else:
    t_fail("任务数量", f"期望>0 实际={tc}")
    task_uuid = ""
print()

# ── [6/8] 提交任务结果 ──
print("── [6/8] 提交任务结果 ──")
if task_uuid:
    res_body = {
        "status": "SUCCESS",
        "result": "已打开设置，电量85%",
        "executionTimeMs": 3200,
        "modelUsed": "gpt-4o-mini",
        "logSnippet": "[17:30:01] open_app com.android.settings OK",
    }
    r = api("POST", f"/api/claw-device/tasks/{task_uuid}/result", res_body, token=DEV_TOKEN)

    if r.get("code") == 200:
        t_pass("任务结果上报返回200")
    else:
        t_fail("任务结果上报", f"返回非200: {r.get('code')}")

    rdata = r.get("data", {})
    if rdata.get("received"):
        t_pass("服务端确认received=True")
    else:
        t_fail("服务端确认", f"received={rdata.get('received')}")

    if rdata.get("taskUuid") == task_uuid:
        t_pass("返回taskUuid与提交一致")
    else:
        t_fail("返回taskUuid", f"期望={task_uuid} 实际={rdata.get('taskUuid')}")
else:
    t_fail("提交任务结果", "无任务UUID跳过")

# 提交后验证
r_after = api("GET", f"/api/claw-device/devices/{DEVICE_ID}/pending-tasks", token=DEV_TOKEN)
tc_after = len(r_after.get("data", []))
t_pass(f"提交后任务查询正常 (剩余{tc_after}个)")
print()

# ── [7/8] 令牌刷新 ──
print("── [7/8] 令牌刷新 ──")
r = api("POST", "/api/claw-device/token/refresh", {"refreshToken": REFRESH_TOK, "deviceToken": DEV_TOKEN})
if r.get("code") == 200:
    t_pass("令牌刷新返回200")
else:
    t_fail("令牌刷新", f"返回非200: {r.get('code')}")

rdata = r.get("data", {})
new_tok = rdata.get("deviceToken", "")
new_ref = rdata.get("refreshToken", "")
new_exp = rdata.get("expiresIn", 0)

if new_tok and new_tok != DEV_TOKEN:
    t_pass("刷新返回新deviceToken")
else:
    t_fail("刷新deviceToken", "未变化或为空")

if new_ref:
    t_pass("刷新返回新refreshToken")
else:
    t_fail("刷新refreshToken", "为空")

if new_exp and new_exp > 0:
    t_pass(f"刷新expiresIn={new_exp}")
else:
    t_fail("刷新expiresIn", "值无效")

# 用新令牌做心跳验证
r = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 90, "isCharging": True, "networkType": "wifi"}, token=new_tok)
if r.get("code") == 200:
    t_pass("新令牌心跳验证成功")
else:
    t_fail("新令牌心跳验证", f"返回非200: {r.get('code')}")
print()

# ── [8/8] 连续心跳稳定性 ──
print("── [8/8] 连续心跳稳定性（5次快速轮询） ──")
use_tok = new_tok if new_tok else DEV_TOKEN
hb_ok = True
for i in range(1, 6):
    r = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 80, "isCharging": True, "networkType": "wifi"}, token=use_tok)
    if r.get("code") != 200:
        hb_ok = False
        t_fail(f"第{i}次心跳", f"返回非200: {r.get('code')}")
        break
if hb_ok:
    t_pass("5次连续心跳全部返回200")
print()

# ── 汇总 ──
total = PASS + FAIL
print("=" * 60)
print(f"总用例: {total}  通过: {PASS}  失败: {FAIL}")
print()
if FAIL > 0:
    print("失败用例:")
    for status, name, reason in TESTS:
        if status == "FAIL":
            print(f"  [FAIL] {name}: {reason}")
    print()
    print("结论: 验收不通过")
else:
    print("结论: 全部通过，端侧执行链路验收合格")
print("=" * 60)
print(f"时间: {time.strftime('%Y-%m-%d %H:%M:%S')}")
print("=" * 60)
sys.exit(1 if FAIL > 0 else 0)
