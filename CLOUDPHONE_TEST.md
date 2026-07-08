# PokeClaw 云手机功能测试 — 环境与凭证

> ⚠️ **安全警示**：本文件含**实时测试凭证**（云手机 ADB 连接密码）。它会进 git。
> - 测试结束后请**轮换/回收**这些云手机与密码。
> - **此仓库有 squash→公开 fork 的历史**（`192.168.250.3` 私有 IP 曾这样泄进公开版）。向任何公开快照发布前，**务必先删除本文件**。
> - 计费类密钥（Cloud LLM key）**不放这里**，见 `TEST_CREDENTIALS.local.md`（已 gitignore）。

## 测试云手机（无印/pmock，美区真机，ADB over TCP）

| # | phoneId | 机型 | 地区 | adb connect | 登录密码 |
|---|---|---|---|---|---|
| 1 | 2074424820185325568 | Galaxy S25 Ultra（SM-S938B / Android 15 / arm64-v8a / 1080x1920 / en-US） | 纽约州 | `124.236.70.143:20512` | `xMyzhIze` |
| 2 | 2064892642380410880 | Reno 10 Pro+ | 加州 | `124.236.70.143:24651` | `TTdXLCgX` |
| 3 | 2062060620226166784 | Redmi 23113RKC6C | 加州 | `124.236.70.143:21699` | `ujuIudmw` |

### ADB 上机（每条连接一次性登录）
```bash
export PATH=$HOME/android-sdk/platform-tools:$PATH
D=124.236.70.143:20512
adb connect "$D"
adb -s "$D" shell "xMyzhIze"          # ← 跑一次密码即登录 → "OK! Login successful."；之后该连接 shell 全通
adb -s "$D" shell getprop ro.product.model   # 验证 → SM-S938B
```
- 认证**按连接持久**；连接掉了要重登。PokeClaw 自身用无障碍操作手机，不受此 shell 密码影响。
- 大文件（APK ~122MB）传输到云手机较慢，`adb install` 请用后台/长超时，勿被前台超时打断。
- #1 已装 **Chrome / Gmail / Play 商店**；WhatsApp/Telegram/YouTube 未装（可经 Play 安装）。

## 无印云手机 OpenAPI 工具（本仓库内）
- 位置：`tools/wuyin-cloudphone/`（`SKILL.md` + `scripts/wuyin_cloudphone.py`，Python3 标准库无依赖）。
- 凭证走环境变量 `WUYIN_ACCESS_KEY` / `WUYIN_SECRET_KEY`（**勿硬编码**）。
- 常用：`python3 tools/wuyin-cloudphone/scripts/wuyin_cloudphone.py list | adb <phoneId> | connect <phoneId> | apps <phoneId> --installed | find --country "United States"`。
- 上面 3 台已直接给了 ADB，无需 API 即可上机。

## Cloud LLM（用于 agent loop）
- **DeepSeek**（OpenAI 兼容）：baseUrl `https://api.deepseek.com/v1`，model `deepseek-chat`（V3，支持 function calling；勿用 `deepseek-reasoner`）。
- **API key 不在此文件**（计费敏感）→ 见 `TEST_CREDENTIALS.local.md`（gitignore）或经环境变量注入。
- 在 PokeClaw：LLM Config → OpenAI-compatible / Custom，填 baseUrl + key + model=`deepseek-chat`。

## 构建 → 安装 → 测试 流程
1. 服务器构建（用户要求）：`ssh pokeclaw-test`，SDK 在 `/root/android-sdk`，`./gradlew :app:assembleDirectDebug`。
2. APK scp 回沙箱 → 沙箱 `adb -s <phone> install -r -g <apk>`（后台/长超时）。
3. 配 DeepSeek → 授权无障碍/通知 → 跑核心 agent loop（Chrome 搜索/Play 装应用/Settings 等真 app 任务）+ reliability E2。证据按 `QA_CHECKLIST.md` 的 E0–E3 分级（emulator/云手机 = E2/E3）。
