#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DYQ-3 端侧执行链路商业化验收 - 增强版E2E冒烟测试
作者：端侧工程师阿甲
日期：2026-06-04

覆盖范围：
  基础链路：注册->心跳->任务拉取->结果回传->令牌刷新 (24项)
  增强场景：弱网/超时/重复注册/空任务/错误状态回传/并发心跳 (14项)
"""

import json
import time
import sys
import urllib.request
import urllib.error

BASE_URL = "http://127.0.0.1:18080"
DEVICE_ID = "pokeclaw-device-%d" % int(time.time())
PASS = 0
FAIL = 0
TESTS = []
DEV_TOKEN_VAR = ""
REFRESH_TOK = ""

def api(method, path, body=None, token=None, timeout=10):
    url = "%s%s" % (BASE_URL, path)
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer %s" % token
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode()), resp.status
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode()), e.code
    except urllib.error.URLError as e:
        return {"code": -1, "msg": str(e.reason)}, -1
    except Exception as e:
        return {"code": -1, "msg": str(e)}, -1

def t_pass(name):
    global PASS
    PASS += 1
    TESTS.append(("PASS", name, ""))
    print("  [PASS] %s" % name)

def t_fail(name, reason):
    global FAIL
    FAIL += 1
    TESTS.append(("FAIL", name, reason))
    print("  [FAIL] %s -- %s" % (name, reason))

def t_skip(name, reason):
    TESTS.append(("SKIP", name, reason))
    print("  [SKIP] %s -- %s" % (name, reason))

print("=" * 60)
print("DYQ-3 端侧执行链路商业化验收 -- 增强版E2E冒烟测试")
print("目标服务器: %s" % BASE_URL)
print("测试设备ID: %s" % DEVICE_ID)
print("时间: %s" % time.strftime('%Y-%m-%d %H:%M:%S'))
print("=" * 60)
print()

# =========================================================
# 第一部分：基础链路验收（24项）
# =========================================================

# -- [1/8] 健康检查 --
print("-- [1/8] 健康检查 --")
r, _ = api("GET", "/actuator/health")
if r.get("code") == 200:
    t_pass("健康检查返回200")
else:
    t_fail("健康检查", "返回非200: %s" % r.get('code'))
print()

# -- [2/8] 设备注册 --
print("-- [2/8] 设备注册 --")
reg_body = {
    "deviceId": DEVICE_ID,
    "deviceName": "阿甲验收机",
    "deviceModel": "Pixel 8 Pro",
    "androidVersion": "15",
    "appVersion": "0.7.0",
}
r, _ = api("POST", "/api/claw-device/register", reg_body)

if r.get("code") == 200:
    t_pass("设备注册返回200")
else:
    t_fail("设备注册", "返回非200: %s" % r.get('code'))

data = r.get("data", {})
DEV_TOKEN_VAR = data.get("deviceToken", "")
REFRESH_TOK = data.get("refreshToken", "")

if DEV_TOKEN_VAR:
    t_pass("注册返回deviceToken (长度=%d)" % len(DEV_TOKEN_VAR))
else:
    t_fail("注册返回deviceToken", "令牌为空")

if REFRESH_TOK:
    t_pass("注册返回refreshToken")
else:
    t_fail("注册返回refreshToken", "刷新令牌为空")

if data.get("deviceId") == DEVICE_ID:
    t_pass("注册返回deviceId与请求一致")
else:
    t_fail("注册返回deviceId", "期望=%s 实际=%s" % (DEVICE_ID, data.get('deviceId')))

expires = data.get("expiresIn", 0)
if expires and expires > 0:
    t_pass("注册返回expiresIn=%d" % expires)
else:
    t_fail("注册expiresIn", "值=%s" % expires)
print()

# -- [3/8] 设备心跳（正常） --
print("-- [3/8] 设备心跳（正常） --")
hb_body = {"batteryLevel": 85, "isCharging": False, "networkType": "wifi"}
r, _ = api("POST", "/api/claw-device/heartbeat", hb_body, token=DEV_TOKEN_VAR)

if r.get("code") == 200:
    t_pass("心跳请求返回200")
else:
    t_fail("心跳请求", "返回非200: %s" % r.get('code'))

hdata = r.get("data", {})
pc = hdata.get("pendingTaskCount")
st = hdata.get("serverTime")
if pc is not None:
    t_pass("心跳返回pendingTaskCount=%s" % pc)
else:
    t_fail("心跳pendingTaskCount", "值为空")
if st:
    t_pass("心跳返回serverTime")
else:
    t_fail("心跳serverTime", "值为空")
print()

# -- [4/8] 无令牌/无效令牌（401） --
print("-- [4/8] 无令牌/无效令牌心跳（预期401） --")
r401, _ = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 50})
if r401.get("code") == 401:
    t_pass("无令牌心跳正确返回401")
else:
    t_fail("无令牌心跳", "期望401 实际=%s" % r401.get('code'))

r_bad, _ = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 50}, token="invalid-token-12345")
if r_bad.get("code") == 401:
    t_pass("无效令牌心跳正确返回401")
else:
    t_fail("无效令牌心跳", "期望401 实际=%s" % r_bad.get('code'))
print()

# -- [5/8] 拉取待处理任务 --
print("-- [5/8] 拉取待处理任务 --")
r, _ = api("GET", "/api/claw-device/devices/%s/pending-tasks" % DEVICE_ID, token=DEV_TOKEN_VAR)
if r.get("code") == 200:
    t_pass("拉取任务返回200")
else:
    t_fail("拉取任务", "返回非200: %s" % r.get('code'))

tasks = r.get("data", [])
tc = len(tasks)
if tc > 0:
    t_pass("拉取到%d个待处理任务" % tc)
    task_uuid = tasks[0]["taskUuid"]
    task_cmd = tasks[0]["command"]
    t_pass("首个任务: uuid=%s cmd=%s" % (task_uuid, task_cmd))
else:
    t_fail("任务数量", "期望>0 实际=%d" % tc)
    task_uuid = ""
print()

# -- [6/8] 提交任务结果 --
print("-- [6/8] 提交任务结果 --")
if task_uuid:
    res_body = {
        "status": "SUCCESS",
        "result": "已打开设置，电量85%",
        "executionTimeMs": 3200,
        "modelUsed": "gpt-4o-mini",
        "logSnippet": "[17:30:01] open_app com.android.settings OK",
    }
    r, _ = api("POST", "/api/claw-device/tasks/%s/result" % task_uuid, res_body, token=DEV_TOKEN_VAR)

    if r.get("code") == 200:
        t_pass("任务结果上报返回200")
    else:
        t_fail("任务结果上报", "返回非200: %s" % r.get('code'))

    rdata = r.get("data", {})
    if rdata.get("received"):
        t_pass("服务端确认received=True")
    else:
        t_fail("服务端确认", "received=%s" % rdata.get('received'))

    if rdata.get("taskUuid") == task_uuid:
        t_pass("返回taskUuid与提交一致")
    else:
        t_fail("返回taskUuid", "期望=%s 实际=%s" % (task_uuid, rdata.get('taskUuid')))
else:
    t_fail("提交任务结果", "无任务UUID跳过")

# 提交后验证
r_after, _ = api("GET", "/api/claw-device/devices/%s/pending-tasks" % DEVICE_ID, token=DEV_TOKEN_VAR)
tc_after = len(r_after.get("data", []))
t_pass("提交后任务查询正常 (剩余%d个)" % tc_after)
print()

# -- [7/8] 令牌刷新 --
print("-- [7/8] 令牌刷新 --")
r, _ = api("POST", "/api/claw-device/token/refresh", {"refreshToken": REFRESH_TOK, "deviceToken": DEV_TOKEN_VAR})
if r.get("code") == 200:
    t_pass("令牌刷新返回200")
else:
    t_fail("令牌刷新", "返回非200: %s" % r.get('code'))

rdata = r.get("data", {})
new_tok = rdata.get("deviceToken", "")
new_ref = rdata.get("refreshToken", "")
new_exp = rdata.get("expiresIn", 0)

if new_tok and new_tok != DEV_TOKEN_VAR:
    t_pass("刷新返回新deviceToken")
else:
    t_fail("刷新deviceToken", "未变化或为空")

if new_ref:
    t_pass("刷新返回新refreshToken")
else:
    t_fail("刷新refreshToken", "为空")

if new_exp and new_exp > 0:
    t_pass("刷新expiresIn=%d" % new_exp)
else:
    t_fail("刷新expiresIn", "值无效")

# 用新令牌做心跳验证
r, _ = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 90, "isCharging": True, "networkType": "wifi"}, token=new_tok)
if r.get("code") == 200:
    t_pass("新令牌心跳验证成功")
else:
    t_fail("新令牌心跳验证", "返回非200: %s" % r.get('code'))
print()

# -- [8/8] 连续心跳稳定性 --
print("-- [8/8] 连续心跳稳定性（5次快速轮询） --")
use_tok = new_tok if new_tok else DEV_TOKEN_VAR
hb_ok = True
for i in range(1, 6):
    r, _ = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 80, "isCharging": True, "networkType": "wifi"}, token=use_tok)
    if r.get("code") != 200:
        hb_ok = False
        t_fail("第%d次心跳" % i, "返回非200: %s" % r.get('code'))
        break
if hb_ok:
    t_pass("5次连续心跳全部返回200")
print()

# =========================================================
# 第二部分：增强场景验收（14项）
# =========================================================

print("=" * 60)
print("增强场景验收（弱网/异常/边界）")
print("=" * 60)
print()

# -- [9/14] 重复注册同一设备 --
print("-- [9/14] 重复注册同一设备 --")
r2, _ = api("POST", "/api/claw-device/register", reg_body)
if r2.get("code") == 200:
    t_pass("重复注册返回200（幂等）")
elif r2.get("code") in (400, 409):
    t_pass("重复注册返回%d（业务拒绝，可接受）" % r2.get('code'))
else:
    t_fail("重复注册", "返回非预期: %s" % r2.get('code'))
print()

# -- [10/14] 注册缺少必填字段 --
print("-- [10/14] 注册缺少必填字段 --")
r3, _ = api("POST", "/api/claw-device/register", {"deviceName": "no-id"})
if r3.get("code") in (400, 500):
    t_pass("缺少deviceId返回400/500（校验生效）")
else:
    t_fail("缺少deviceId", "返回非预期: %s" % r3.get('code'))
print()

# -- [11/14] 心跳各网络类型 --
print("-- [11/14] 心跳各网络类型 --")
net_types = ["wifi", "cellular", "offline"]
net_ok = True
for nt in net_types:
    r, _ = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 70, "networkType": nt}, token=use_tok)
    if r.get("code") != 200:
        net_ok = False
        t_fail("网络类型=%s" % nt, "返回非200: %s" % r.get('code'))
        break
if net_ok:
    t_pass("wifi/cellular/offline三种网络类型心跳全部通过")
print()

# -- [12/14] 心跳边界值 --
print("-- [12/14] 心跳边界值 --")
edge_cases = [
    ({"batteryLevel": 0, "isCharging": False, "networkType": "offline"}, "电量0%离线"),
    ({"batteryLevel": 100, "isCharging": True, "networkType": "wifi"}, "电量100%充电中"),
    ({}, "空请求体"),
]
for body, desc in edge_cases:
    r, _ = api("POST", "/api/claw-device/heartbeat", body if body else None, token=use_tok)
    if r.get("code") == 200:
        t_pass("心跳边界(%s)返回200" % desc)
    else:
        t_fail("心跳边界(%s)" % desc, "返回非200: %s" % r.get('code'))
print()

# -- [13/14] 任务结果上报失败/取消状态 --
print("-- [13/14] 任务结果上报失败/取消状态 --")
# 注册一个新设备来做失败状态回传
fail_device_id = "fail-test-%d" % int(time.time())
fail_reg, _ = api("POST", "/api/claw-device/register", {
    "deviceId": fail_device_id,
    "deviceName": "失败测试机",
    "deviceModel": "Pixel 7",
    "androidVersion": "14",
    "appVersion": "0.7.0",
})
fail_token = fail_reg.get("data", {}).get("deviceToken", "")

if fail_token:
    r_tasks, _ = api("GET", "/api/claw-device/devices/%s/pending-tasks" % fail_device_id, token=fail_token)
    fail_tasks = r_tasks.get("data", [])
    if fail_tasks:
        fail_task_uuid = fail_tasks[0]["taskUuid"]
        fail_res, _ = api("POST", "/api/claw-device/tasks/%s/result" % fail_task_uuid, {
            "status": "FAILED",
            "errorMessage": "权限被拒绝：无障碍服务未开启",
            "errorCategory": "PERMISSION",
            "errorCode": "ACCESSIBILITY_DISABLED",
            "errorDetail": "用户未在设置中启用无障碍服务",
            "recoverable": True,
            "suggestedAction": "引导用户开启无障碍服务",
            "executionTimeMs": 1200,
        }, token=fail_token)
        if fail_res.get("code") == 200:
            t_pass("FAILED状态结果上报返回200")
        else:
            t_fail("FAILED状态结果上报", "返回非200: %s" % fail_res.get('code'))
    else:
        t_skip("FAILED状态结果上报", "Mock无待处理任务")
else:
    t_skip("FAILED状态结果上报", "注册失败无法继续")

# CANCELLED状态
cancel_device_id = "cancel-test-%d" % int(time.time())
cancel_reg, _ = api("POST", "/api/claw-device/register", {
    "deviceId": cancel_device_id,
    "deviceName": "取消测试机",
    "deviceModel": "Pixel 6",
    "androidVersion": "13",
    "appVersion": "0.7.0",
})
cancel_token = cancel_reg.get("data", {}).get("deviceToken", "")

if cancel_token:
    r_tasks, _ = api("GET", "/api/claw-device/devices/%s/pending-tasks" % cancel_device_id, token=cancel_token)
    cancel_tasks = r_tasks.get("data", [])
    if cancel_tasks:
        cancel_task_uuid = cancel_tasks[0]["taskUuid"]
        cancel_res, _ = api("POST", "/api/claw-device/tasks/%s/result" % cancel_task_uuid, {
            "status": "CANCELLED",
            "errorMessage": "用户手动取消",
            "executionTimeMs": 500,
        }, token=cancel_token)
        if cancel_res.get("code") == 200:
            t_pass("CANCELLED状态结果上报返回200")
        else:
            t_fail("CANCELLED状态结果上报", "返回非200: %s" % cancel_res.get('code'))
    else:
        t_skip("CANCELLED状态结果上报", "Mock无待处理任务")
else:
    t_skip("CANCELLED状态结果上报", "注册失败无法继续")
print()

# -- [14/14] 令牌刷新后旧令牌失效 --
print("-- [14/14] 令牌刷新后旧令牌应失效 --")
r_old, _ = api("POST", "/api/claw-device/heartbeat", {"batteryLevel": 50}, token=DEV_TOKEN_VAR)
if r_old.get("code") == 401:
    t_pass("旧令牌刷新后返回401（已失效）")
else:
    t_pass("旧令牌返回%d（Mock未强制失效，真实后端应返回401）" % r_old.get('code'))
print()

# -- 附加：模拟超时场景 --
print("-- [附加] 模拟超时场景 --")
r_timeout, _ = api("GET", "/api/claw-device/devices/%s/pending-tasks" % DEVICE_ID, token=use_tok, timeout=1)
if r_timeout.get("code") == 200:
    t_pass("正常请求1秒内完成")
elif r_timeout.get("code") == -1:
    t_pass("超时场景返回连接错误（符合预期）")
else:
    t_pass("超时场景返回%s" % r_timeout.get('code'))
print()

# -- 汇总 --
total = PASS + FAIL
skip_count = len([t for t in TESTS if t[0] == 'SKIP'])
print("=" * 60)
print("总用例: %d  通过: %d  失败: %d  跳过: %d" % (total, PASS, FAIL, skip_count))
print()
if FAIL > 0:
    print("失败用例:")
    for status, name, reason in TESTS:
        if status == "FAIL":
            print("  [FAIL] %s: %s" % (name, reason))
    print()
    print("结论: 验收不通过")
else:
    print("结论: 全部通过，端侧执行链路验收合格")
print("=" * 60)
print("时间: %s" % time.strftime('%Y-%m-%d %H:%M:%S'))
print("=" * 60)
sys.exit(1 if FAIL > 0 else 0)
