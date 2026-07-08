#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""无印魔盒 (Pmock Pass) 云手机 OpenAPI 客户端 —— 列手机 / 取 ADB 连接信息 / adb 连接测试.

契约来源: 对 dyq 后端运行代码逐一核实(非文档脑补):
  - 鉴权:  dyq-framework/.../pmockpass/util/PmockPassSignatureUtil.java
  - 客户端: dyq-framework/.../pmockpass/client/PmockPassClient.java (buildHeaders/getAdbInfo/getPhoneList)
  - Base URL 默认: PmockPassProperties.apiUrl = https://wuin.cc/api/openapi/phone

鉴权(header 参数, 非 query/body):
  appKey    = accessKey
  timestamp = 当前毫秒(原始值, 不做窗口对齐)
  sign      = md5( appKey + "#" + secretKey + "#" + timestamp )  # 32 位小写 hex
  官方样例: md5("e58bc5ccecacf0a2#9735c59e8f8e5f297b0544941eab4d27#1701153526603")
           = 9ef4a1eaa94d9669d65c0eaa232d29d8  (本脚本 sign 子命令可复验)

凭证: 环境变量 WUYIN_ACCESS_KEY / WUYIN_SECRET_KEY (或 --access-key/--secret-key). 禁止硬编码.
只用 Python 标准库, 无第三方依赖.
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_API_URL = "https://wuin.cc/api/openapi/phone"


def _sign(ak, sk, ts_ms):
    return hashlib.md5("{}#{}#{}".format(ak, sk, ts_ms).encode("utf-8")).hexdigest()


def _creds(args):
    ak = args.access_key or os.environ.get("WUYIN_ACCESS_KEY") or os.environ.get("PMOCK_ACCESS_KEY")
    sk = args.secret_key or os.environ.get("WUYIN_SECRET_KEY") or os.environ.get("PMOCK_SECRET_KEY")
    if not ak or not sk:
        sys.exit("缺少凭证: 设置 WUYIN_ACCESS_KEY / WUYIN_SECRET_KEY 环境变量, 或传 --access-key/--secret-key")
    return ak.strip(), sk.strip()


def _api_url(args):
    return (args.api_url or os.environ.get("WUYIN_API_URL") or DEFAULT_API_URL).rstrip("/")


def _request(args, method, path, query=None, body=None):
    ak, sk = _creds(args)
    url = _api_url(args) + path
    if query:
        q = {k: v for k, v in query.items() if v is not None and v != ""}
        if q:
            url += "?" + urllib.parse.urlencode(q)
    ts = int(time.time() * 1000)
    headers = {
        "Content-Type": "application/json",
        "appKey": ak,
        "timestamp": str(ts),
        "sign": _sign(ak, sk, ts),
    }
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=args.timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace") if e.fp else ""
        sys.exit("HTTP {} {} {}\n{}".format(e.code, method, url, detail))
    except urllib.error.URLError as e:
        sys.exit("网络错误 {} {}: {}".format(method, url, e.reason))
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        sys.exit("响应非 JSON: {}".format(raw[:800]))
    code = payload.get("code")
    if code is not None and code != 200:
        sys.exit("业务错误[code={}] {}  ({} {})".format(code, payload.get("msg"), method, url))
    return payload


def _rows(payload):
    data = payload.get("data")
    if isinstance(data, dict):
        return data.get("rows") or data.get("list") or data.get("records") or []
    if isinstance(data, list):
        return data
    return []


def cmd_list(args):
    query = {
        "status": args.status,
        "groupId": args.group_id,
        "nameOrCode": args.name,
        "phoneId": args.phone_id,
        "pageNum": args.page,
        "pageSize": args.size,
    }
    payload = _request(args, "GET", "/capacity/phone/list", query=query)
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    rows = _rows(payload)
    if not rows:
        print("(无手机; 外层 status={})".format(payload.get("status")))
        return
    print("{:<14}{:<28}{:<8}".format("phoneId", "name/code", "status"))
    for it in rows:
        pid = it.get("phoneId") or it.get("id") or ""
        name = it.get("nameOrCode") or it.get("phoneName") or it.get("name") or it.get("phoneCode") or ""
        st = it.get("status") if it.get("status") is not None else it.get("phoneStatus", "")
        print("{:<14}{:<28}{:<8}".format(str(pid), str(name), str(st)))
    total = payload.get("data", {}).get("total") if isinstance(payload.get("data"), dict) else len(rows)
    print("\n共 {} 台 (total={})".format(len(rows), total))
    print("提示: 取某台 ADB 连接信息 →  wuyin_cloudphone.py adb <phoneId>")


def cmd_adb(args):
    ids = [int(x) for x in args.phone_ids]
    payload = _request(args, "POST", "/capacity/v2/getAdbInfo", body={"phoneIds": ids})
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    data = payload.get("data") or []
    if not data:
        print("(getAdbInfo 无数据; 手机可能未开机 / 未开启 ADB / phoneId 不属于该账户)")
        return
    for it in data:
        host, port, pwd = it.get("host"), it.get("port"), it.get("password")
        print("phoneId={}  host={}  port={}  password={}".format(it.get("phoneId"), host, port, pwd))
        if host and port:
            print("  adb connect {}:{}".format(host, port))


def cmd_connect(args):
    payload = _request(args, "POST", "/capacity/v2/getAdbInfo", body={"phoneIds": [int(args.phone_id)]})
    data = payload.get("data") or []
    if not data:
        sys.exit("getAdbInfo 无数据; 手机可能未开机 / 未开启 ADB / phoneId 不属于该账户")
    it = data[0]
    host, port, pwd = it.get("host"), it.get("port"), it.get("password")
    if not (host and port):
        sys.exit("未返回 host/port: {}".format(it))
    target = "{}:{}".format(host, port)
    print("phoneId={}  →  {}   password={}".format(it.get("phoneId"), target, pwd))
    print("$ adb connect {}".format(target))
    try:
        out = subprocess.run(["adb", "connect", target], capture_output=True, text=True, timeout=30)
    except FileNotFoundError:
        sys.exit("未找到 adb 命令; 请先安装 android platform-tools (apt install adb / brew install android-platform-tools)")
    except subprocess.TimeoutExpired:
        sys.exit("adb connect {} 超时".format(target))
    print((out.stdout or out.stderr).strip())
    if out.returncode == 0:
        print("\n验证: adb devices | grep {}".format(target))


def cmd_apps(args):
    query = {
        "phoneId": int(args.phone_id),
        "apkName": args.name,
        "onlyShowInstalled": "true" if args.installed else None,
        "pageNum": args.page,
        "pageSize": args.size,
    }
    payload = _request(args, "GET", "/capacity/v2/listApp", query=query)
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    rows = _rows(payload)
    if not rows:
        print("(无应用)")
        return
    print("{:<42}{:<14}{}".format("apkPkg", "installStatus", "apkName"))
    for it in rows:
        print("{:<42}{:<14}{}".format(str(it.get("apkPkg")), str(it.get("installStatus")), it.get("apkName")))
    print("\n共 {} 条".format(len(rows)))


# TikTok 识别: 包名或应用名命中任一
_TIKTOK_PKGS = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"}


def _is_tiktok(item):
    pkg = (item.get("apkPkg") or "").lower()
    name = (item.get("apkName") or "").lower()
    return (pkg in _TIKTOK_PKGS or "zhiliaoapp" in pkg or "musically" in pkg
            or "trill" in pkg or "tiktok" in name)


def _installed(item):
    st = item.get("installStatus")
    # onlyShowInstalled=true 已在服务端过滤; installStatus 兜底(None 视为已装, 0/false 视为未装)
    return st not in (0, False, "0", "false")


def cmd_find(args):
    want_country = args.country.strip().lower()
    app = (args.app or "").lower()
    # 1) 拉手机列表, 按国家/在线/联网过滤
    payload = _request(args, "GET", "/capacity/phone/list",
                       query={"pageNum": 1, "pageSize": args.scan})
    rows = _rows(payload)
    cand = []
    for r in rows:
        if want_country not in (r.get("networkCountryName") or "").lower():
            continue
        if args.status is not None and args.status >= 0 and r.get("phoneStatus") != args.status:
            continue
        if args.require_network and not (r.get("networkHost") or "").strip():
            continue
        cand.append(r)
    if not args.app:
        # 无 --app: 只按 国家/在线/联网 筛, 直接列候选
        shown = cand if args.limit <= 0 else cand[:args.limit]
        for r in shown:
            print("phoneId={}  model={}  host={}  region={}  country={}  status={}".format(
                r.get("phoneId"), r.get("phoneModel"), r.get("networkHost"),
                r.get("networkRegion"), r.get("networkCountryName"), r.get("phoneStatus")))
        if not cand:
            print("未找到(country~='{}' status={})".format(args.country, args.status))
        return
    print("[find] {} 台里 {} 台候选(country~='{}' status={} 联网={}); 逐台查已装应用(依赖 listApp 有数据)...".format(
        len(rows), len(cand), args.country, args.status, args.require_network), file=sys.stderr)
    # 2) 逐台查已装应用, 命中目标 app 即返回
    hits = 0
    for i, r in enumerate(cand, 1):
        pid = r.get("phoneId")
        try:
            ap = _request(args, "GET", "/capacity/v2/listApp",
                          query={"phoneId": int(pid), "onlyShowInstalled": "true",
                                 "apkName": args.app if args.app else None,
                                 "pageNum": 1, "pageSize": 200})
        except SystemExit:
            continue
        apps = [a for a in _rows(ap) if _installed(a)]
        if args.app == "TikTok" or app == "tiktok":
            matched = [a for a in apps if _is_tiktok(a)]
        else:
            matched = [a for a in apps if app in (a.get("apkName") or "").lower()
                       or app in (a.get("apkPkg") or "").lower()]
        if matched:
            hits += 1
            print("✔ phoneId={}  model={}  host={}  region={}  country={}  app={}({})".format(
                pid, r.get("phoneModel"), r.get("networkHost"), r.get("networkRegion"),
                r.get("networkCountryName"), matched[0].get("apkName"), matched[0].get("apkPkg")))
            if hits >= args.limit:
                return
        else:
            print("  [{}/{}] phoneId={} 无 {}".format(i, len(cand), pid, args.app), file=sys.stderr)
    if hits == 0:
        print("未找到满足条件的手机(country~='{}' app='{}')".format(args.country, args.app))


def cmd_sign(args):
    ak, sk = _creds(args)
    ts = args.timestamp or int(time.time() * 1000)
    print("appKey    = {}".format(ak))
    print("timestamp = {}".format(ts))
    print("sign      = {}".format(_sign(ak, sk, ts)))


def main():
    # 公共选项挂到 parent, 让 --access-key/--json 等在子命令前后都可用
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--access-key", help="accessKey(优先于 WUYIN_ACCESS_KEY)")
    common.add_argument("--secret-key", help="secretKey(优先于 WUYIN_SECRET_KEY)")
    common.add_argument("--api-url", help="覆盖 base URL(默认 https://wuin.cc/api/openapi/phone)")
    common.add_argument("--timeout", type=int, default=20)
    common.add_argument("--json", action="store_true", help="输出原始 JSON 响应")

    # 公共选项只挂子命令(放在 <子命令> 之后), 避免"挂 main 又挂 sub"导致前置选项被 sub 默认值覆盖
    p = argparse.ArgumentParser(
        description="无印魔盒(Pmock Pass) 云手机 OpenAPI 客户端: 列手机 / 取ADB连接信息 / adb连接测试\n"
                    "凭证优先用环境变量 WUYIN_ACCESS_KEY/WUYIN_SECRET_KEY; 命令行选项须置于 <子命令> 之后",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    lp = sub.add_parser("list", parents=[common], help="列出云手机 (GET /capacity/phone/list)")
    lp.add_argument("--status", type=int, help="手机状态码过滤")
    lp.add_argument("--group-id", type=int)
    lp.add_argument("--name", help="按名称/编号模糊过滤 nameOrCode")
    lp.add_argument("--phone-id", type=int)
    lp.add_argument("--page", type=int, default=1)
    lp.add_argument("--size", type=int, default=50)
    lp.set_defaults(func=cmd_list)

    ap = sub.add_parser("adb", parents=[common], help="取 ADB 连接信息 (POST /capacity/v2/getAdbInfo)")
    ap.add_argument("phone_ids", nargs="+", help="一个或多个 phoneId")
    ap.set_defaults(func=cmd_adb)

    cp = sub.add_parser("connect", parents=[common], help="取 ADB 信息并执行 adb connect 测试")
    cp.add_argument("phone_id", help="phoneId")
    cp.set_defaults(func=cmd_connect)

    pp = sub.add_parser("apps", parents=[common], help="查手机已装应用 (GET /capacity/v2/listApp)")
    pp.add_argument("phone_id", help="phoneId")
    pp.add_argument("--installed", action="store_true", help="仅显示已安装 (onlyShowInstalled=true)")
    pp.add_argument("--name", help="按应用名过滤 apkName(如 TikTok)")
    pp.add_argument("--page", type=int, default=1)
    pp.add_argument("--size", type=int, default=200)
    pp.set_defaults(func=cmd_apps)

    fp = sub.add_parser("find", parents=[common],
                        help="按国家/在线/已装应用批量筛手机, 返回 phoneId")
    fp.add_argument("--country", required=True, help="networkCountryName 模糊匹配(如 'United States')")
    fp.add_argument("--app", help="要求已装的应用(如 TikTok; 传 TikTok 用内置包名识别)")
    fp.add_argument("--status", type=int, default=1, help="phoneStatus 过滤(默认1=在线; 传 -1 不限)")
    fp.add_argument("--require-network", action="store_true", default=True,
                    help="要求有 networkHost(默认开)")
    fp.add_argument("--limit", type=int, default=1, help="命中几台后停(默认1)")
    fp.add_argument("--scan", type=int, default=1000, help="拉取手机列表页大小上限")
    fp.set_defaults(func=cmd_find)

    sp = sub.add_parser("sign", parents=[common], help="调试: 打印当前(或指定 timestamp)签名")
    sp.add_argument("--timestamp", type=int, help="毫秒时间戳(默认当前)")
    sp.set_defaults(func=cmd_sign)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
