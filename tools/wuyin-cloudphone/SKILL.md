---
name: wuyin-cloudphone
description: 调用无印魔盒(Pmock Pass / 武印)云手机 OpenAPI——列出云手机、获取 ADB 连接信息(host:port:password)、做 adb 连接测试。触发词：无印云手机、武印、pmock、pmock pass、云手机 adb、cloudphone adb、getAdbInfo、云手机连接测试、wuin.cc、给我一个云手机连接。
---

# 无印魔盒 (Pmock Pass) 云手机 OpenAPI 技能

无印魔盒/武印云手机**通过 HTTP OpenAPI 接入**（非直连）。本技能封装其鉴权与「列手机 → 取 ADB 连接信息 → adb 连接测试」链路，供快速拿到 `host:port` 做 ADB 联调。

> 契约全部对 dyq 后端**运行代码**核实（`dyq-framework/dyq-spring-boot-starter-pmock-pass` 的 `PmockPassClient` / `PmockPassSignatureUtil` / `PmockPassProperties`），非文档脑补。dyq 适配器里的 `PmockSignatureUtil`(HMAC-SHA256) 是旧 SaaS 死代码，**勿用**。

## 何时用
- 用户要「无印/武印/pmock 云手机的 ADB 连接 / 连接信息 / 连接测试」。
- 需要按 phoneId 拿 `adb connect host:port`。
- 需要列出账户下云手机及其 phoneId。

## 前置：凭证（禁止硬编码）
脚本从环境变量取凭证（也可 `--access-key/--secret-key` 覆盖）：
```bash
export WUYIN_ACCESS_KEY='<你的 accessKey>'
export WUYIN_SECRET_KEY='<你的 secretKey>'
# 可选覆盖 base URL(默认 https://wuin.cc/api/openapi/phone)
# export WUYIN_API_URL='https://wuin.cc/api/openapi/phone'
```
**凭证来源（dyq 内）**：租户级配置表 `cloudphone_tenant_vendor_config`（`vendor_code='pmock_pass'`，credentials JSON 含 `accessKey`/`secretKey`，DB 内加密存储）；或 `dyq.pmock-pass.*`（框架 starter `PmockPassProperties`，prefix 见该类）。**取值交由用户/运维提供，勿从库中导出明文写入任何文件或日志。**

## 用法
脚本：`~/.claude/skills/wuyin-cloudphone/scripts/wuyin_cloudphone.py`（Python3 标准库，无依赖）

```bash
S=~/.claude/skills/wuyin-cloudphone/scripts/wuyin_cloudphone.py

# 1) 列出云手机, 拿 phoneId (GET /capacity/phone/list)
python3 "$S" list                      # 全部
python3 "$S" list --status 1 --size 20 # 按状态/分页
python3 "$S" list --json               # 原始 JSON

# 2) 取某台的 ADB 连接信息 (POST /capacity/v2/getAdbInfo) —— 核心
python3 "$S" adb 123456789             # → host / port / password + 'adb connect host:port'
python3 "$S" adb 123456789 987654321   # 批量

# 3) 取 ADB 信息并直接 adb connect 测试(需本机装 adb)
python3 "$S" connect 123456789

# 4) 查手机已装应用 (GET /capacity/v2/listApp) —— 见下方局限
python3 "$S" apps <phoneId> --installed
python3 "$S" apps <phoneId> --name TikTok --json

# 5) 按 国家/在线/联网 批量筛手机(可选按已装应用), 返回 phoneId
python3 "$S" find --country "United States" --limit 5          # 美区在线机(不查应用)
python3 "$S" find --country "United States" --app TikTok       # 要求已装 TikTok(依赖 listApp 有数据)

# 6) 调试: 复验签名算法(不需网络; 应得官方样例值)
python3 "$S" sign --access-key e58bc5ccecacf0a2 \
  --secret-key 9735c59e8f8e5f297b0544941eab4d27 --timestamp 1701153526603
# sign = 9ef4a1eaa94d9669d65c0eaa232d29d8
```

## ⚠️ 已装应用检测的局限（实测结论 2026-07-08）
- 唯一可用的应用接口是 `GET /capacity/v2/listApp`（dyq `PmockPassAdapter.listInstalledApps` 亦用它）；文档里的 `GET /phone/app/list`(29.1) 在生产 **404 未部署**。
- 对某些账户/空白机，`listApp` 对全部手机返回 `total=0`（厂商未走货架管理），此时**无法用 API 判断某机是否装了 TikTok** 等应用。
- 可靠办法：`adb connect host:port`（用 `getAdbInfo` 取）后 `adb shell pm list packages | grep -i musically`（TikTok 包名 `com.zhiliaoapp.musically`/`com.ss.android.ugc.trill`）。
- 因此 `find --app` 仅在 `listApp` 对该账户有数据时有效；否则请用 `find --country ...` 拿在线机再 ADB 上机自查。

## 已核实的 API 契约
| 项 | 值 |
|---|---|
| Base URL | `https://wuin.cc/api/openapi/phone` |
| 鉴权 | **HTTP header**：`appKey`=accessKey、`timestamp`=当前毫秒、`sign`=`md5(appKey#secretKey#timestamp)` 32位小写hex |
| 列手机 | `GET /capacity/phone/list`（query: status/groupId/nameOrCode/phoneId/pageNum/pageSize…）→ `data.rows[].phoneId` |
| 取 ADB | `POST /capacity/v2/getAdbInfo`  body `{"phoneIds":[<long>...]}` → `data[].{phoneId,host,port,password}` |
| 响应约定 | `{code,msg,data,traceId,status}`；`code=200` 成功，其余为业务错误；列表外层 `status`=`finish`/`pending` |

ADB 连接串 = `host:port`；`password` 为 ADB 连接密码（部分场景需要）。

## 安全红线
- 凭证只走环境变量/参数，**绝不硬编码进脚本或提交进仓库**。
- 不把从 DB 取到的 accessKey/secretKey 明文回显、写文件或落日志。
- ADB 连接目标是真实云手机，连接后的操作按授权范围进行。

## 排障
- `业务错误[code=xxx]`：多为签名/凭证错或 phoneId 不属于该账户；先 `sign` 子命令核对签名、确认 ak/sk 正确。
- `getAdbInfo 无数据`：手机未开机 / 未开 ADB / phoneId 无效。
- `HTTP 4xx/5xx`：base URL 或网络问题；确认能出网到 `wuin.cc`。
- 时间戳用毫秒原始值，服务端有防重放窗口，机器时钟需大致准确。
