# 三端最小闭环联调验收包 — 小蓝QC心跳更新

**日期**: 2026-06-04 (心跳轮次)
**执行人**: 测试员小蓝（ec2afe67）
**关联Issue**: DYQ-5
**父Issue**: DYQ-1

---

## 〇、环境即时探测（2026-06-04 本轮）

| 探测项 | 结果 |
|--------|------|
| dyq-server:48080 | ❌ 无监听，curl exit=7 |
| Docker容器 | 无运行中容器 |
| ADB设备 | 无在线设备 |
| MySQL:3306 | 无监听 |
| PokeClaw仓库 | ✅ /mnt/e/code/PokeClaw 存在 |
| WeFlow仓库 | ✅ /mnt/d/work/code/WeFlow 存在 |
| DYQ后端仓库 | ✅ /mnt/e/code/dyq 存在 |

**结论**: 本轮环境与上轮相同——dyq-server未运行，三端真实联调仍不可执行。

---

## 一、三端证据汇总（对照DYQ-2/3/4评论证据逐条核对）

### 1. 云端中枢（DYQ-2）— blocked

| # | 证据项 | 来源 | 状态 | 核对 |
|---|--------|------|------|------|
| 1 | 沙箱模板CRUD链路（创建→查询→删除） | 阿盾 verification-report-updated.md | ✅ 通过 | 实际请求返回Code=0 |
| 2 | 技能数据查询（page接口） | 阿盾 verification-report-updated.md | ✅ 通过 | Total=2，ACTIVE |
| 3 | 失败链路：参数校验400 | 阿盾 verification-report-updated.md | ✅ 通过 | Code=400，Msg=总CPU核数不能为空 |
| 4 | 失败链路：无Token 401 | 阿盾 verification-report-updated.md | ✅ 通过 | Code=401，Msg=账号未登录 |
| 5 | OpenAPI路径前缀不一致 | 阿盾 verification-report-updated.md | ⚠️ 风险 | spec缺少/admin-api前缀 |
| 6 | TLS证书字段长度不足 | 阿盾 verification-report-updated.md | ⚠️ 风险 | varchar(64)无法存PEM |
| 7 | proxy_session_log幂等DDL | 已提交推送 | ✅ 通过 | V20260604已落地 |
| 8 | dyq-server:48080健康检查 | 本轮即时探测 | ❌ 未通过 | 端口不可达 |

**成功证据**: 沙箱CRUD+技能查询（链路A+B）
**失败/异常证据**: dyq-server未启动+OpenAPI前缀不一致+TLS字段长度不足
**子任务DYQ-11**: done ✅ (OpenAPI装配核验与模块修复已完成)

### 2. PokeClaw端侧（DYQ-3）— in_progress

| # | 证据项 | 来源 | 状态 | 核对 |
|---|--------|------|------|------|
| 1 | Mock 7/7 全PASS（注册/心跳/拉取/回传/无token/坏token/断网） | 阿甲 final-closure-evidence | ✅ 通过 | 多轮复跑均7/7 |
| 2 | 冒烟脚本判定修正（不再误判HTTP 200业务500） | 阿甲 final-closure-evidence | ✅ 通过 | 旧脚本已修复 |
| 3 | 真实后端健康检查 | 阿甲 final-closure-evidence + 本轮 | ❌ 未通过 | 端口不可达 |
| 4 | ADB真机证据 | DYQ-9 done但本机无设备 | ❌ 无设备 | adb devices空 |
| 5 | 验证报告48/48+弱网14/14 | verification-report.md | ✅ 通过 | 全PASS |
| 6 | DYQ-9真机ADB最小证据 | 已标记done | ✅ 已提交 | 注册-心跳-回传 |
| 7 | DYQ-10后端鉴权样本 | 已标记done | ✅ 已提交 | 真实后端联通与鉴权 |

**成功证据**: Mock 7/7全PASS + 验证48/48全PASS + DYQ-9/10均done
**失败/异常证据**: 真实后端端口不可达+ADB无设备

### 3. WeFlow微信控制底座（DYQ-4）— done ✅

| # | 证据项 | 来源 | 状态 | 核对 |
|---|--------|------|------|------|
| 1 | 消息托管→AI决策→GUI安全回复 | 产品经理阿宁验收 | ✅ 通过 | 产品边界确认 |
| 2 | 三类事件归档 receive/sendResult/error | DYQ-8已完成 | ✅ 通过 | |
| 3 | 产品侧门禁（初审→复审→验证） | 老周+小蓝 | ✅ 通过 | 已标记done |
| 4 | 仓库存在 | 本轮探测 | ✅ 通过 | /mnt/d/work/code/WeFlow |

**成功证据**: 产品侧门禁全通过
**无失败证据**（但运行态联调待dyq-server启动后补充）

---

## 二、统一演示脚本 v1（复用上轮，微调）

```bash
#!/bin/bash
# 三端最小闭环联调演示脚本 v1.1
# 前提：dyq-server:48080 运行中，ADB设备在线，WeFlow可启动
set -e

echo "===== [1/3] 云端中枢 ====="
HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:48080/actuator/health 2>/dev/null || echo "000")
if [ "$HTTP" = "200" ]; then
  echo "✅ dyq-server:48080 健康"
  # 沙箱链路
  curl -sf http://127.0.0.1:48080/admin-api/sandbox/skill/page?pageNo=1\&pageSize=5 && echo "✅ 沙箱API正常" || echo "❌ 沙箱API异常"
else
  echo "❌ dyq-server:48080 不可达 (HTTP=$HTTP)"
fi

echo ""
echo "===== [2/3] PokeClaw 端侧 ====="
ADB_COUNT=$(adb devices 2>/dev/null | grep -v "List" | grep -c "device" || echo 0)
if [ "$ADB_COUNT" -gt 0 ]; then
  echo "✅ ADB设备在线: $ADB_COUNT 台"
else
  echo "❌ 无ADB设备在线"
fi
# Mock冒烟
MOCK_HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8765/health 2>/dev/null || echo "000")
if [ "$MOCK_HTTP" = "200" ]; then
  echo "✅ Mock后端运行中"
  cd /mnt/e/code/PokeClaw && bash scripts/dyq3-endcloud-smoke.sh /tmp/dyq5-demo-smoke
else
  echo "❌ Mock后端未运行 (HTTP=$MOCK_HTTP)"
fi

echo ""
echo "===== [3/3] WeFlow 微信控制底座 ====="
if [ -d "/mnt/d/work/code/WeFlow" ]; then
  echo "✅ WeFlow仓库存在"
  echo "启动: cd /mnt/d/work/code/WeFlow && npm run dev"
else
  echo "❌ WeFlow仓库不存在"
fi

echo ""
echo "===== 演示完成 ====="
```

---

## 三、待验证清单

| 编号 | 验证项 | 优先级 | 负责人 | 验证命令 | 状态 |
|------|--------|--------|--------|----------|------|
| V1 | dyq-server:48080启动与健康 | P0 | 阿盾 | `curl http://127.0.0.1:48080/actuator/health` | ❌ 未通过 |
| V2 | 云端设备注册API | P0 | 后端 | `curl -X POST http://127.0.0.1:48080/api/claw/device/register -H 'Content-Type: application/json' -d '{"deviceId":"test-001"}'` | ❌ 阻塞 |
| V3 | 云端任务下发→PokeClaw接收 | P0 | 阿甲 | PokeClaw注册→心跳→拉取任务 | ❌ 阻塞 |
| V4 | PokeClaw结果回传云端 | P0 | 阿甲 | 结果上报→云端消费归档 | ❌ 阻塞 |
| V5 | 沙箱CRUD完整链路（宿主机） | P0 | 阿盾 | DDL修复后创建宿主机+Docker TLS | ⚠️ TLS字段待修复 |
| V6 | WeFlow运行态联调 | P1 | 小蓝 | WeFlow连接dyq-server后发送消息 | ⏳ 阻塞于V1 |
| V7 | proxy_session_log写入验证 | P1 | 阿盾 | 执行proxy session后查表 | ⏳ 阻塞于V1 |
| V8 | PokeClaw git推送证据 | P1 | 阿甲 | `git log origin/dev --oneline -5` | ⚠️ 待补 |
| V9 | 前端管理台设备/任务/消息查看 | P1 | 前端 | 浏览器访问管理后台 | ⏳ |
| V10 | OpenAPI spec路径前缀修复 | P1 | 老周 | 更新sandbox.openapi.yaml添加/admin-api | ⚠️ 需修复 |
| V11 | TLS证书字段DDL修改 | P1 | 阿盾 | ALTER tls_client_key→TEXT | ⚠️ 需修复 |

---

## 四、风险清单

| 级别 | 风险 | 影响 | 缓解措施 |
|------|------|------|----------|
| P0 | dyq-server:48080未启动 | 三端闭环无法端到端演示 | 阿盾需优先启动并验证健康检查 |
| P0 | 云端设备注册/任务下发API未闭环 | PokeClaw无法对接真实后端 | DYQ-2后端需完成Controller注册 |
| P1 | OpenAPI路径前缀不一致 | 前端/端侧按spec生成代码会404 | 老周修复spec |
| P1 | TLS证书字段长度不足 | 宿主机Docker TLS连接会失败 | 阿盾提交DDL修改 |
| P1 | PokeClaw仅Mock验证 | 商业化交付可信度不足 | dyq-server启动后立即跑真实链路 |
| P1 | WeFlow产品验收通过但无运行态证据 | 三端联调时可能暴露集成问题 | 需dyq-server运行后补充 |
| P1 | git推送状态不一致 | 审计链断裂 | 阿甲补齐提交推送 |

---

## 五、验收结论

### 当前可验收项
- **PokeClaw端侧（Mock环境）**: 7/7 + 48/48 + 14/14 全PASS ✅
- **WeFlow微信控制底座**: 产品侧门禁全通过，已done ✅
- **云端沙箱CRUD链路**: 模板+技能+失败场景全通过 ✅
- **云端DDL幂等**: proxy_session_log已落地 ✅
- **子任务DYQ-9/10/11**: 均done ✅

### 阻塞项（必须解除才能标记DYQ-5 done）
1. **dyq-server:48080启动**（P0）— 无此后端，三端闭环无法演示
2. **云端设备注册+任务下发API**（P0）— PokeClaw端侧对接前提
3. **PokeClaw真实后端联调**（P0）— Mock→真实后端切换验证

### 与上轮变化
| 变化项 | 上轮 | 本轮 |
|--------|------|------|
| DYQ-9 | done | 无变化 |
| DYQ-10 | done | 无变化 |
| DYQ-11 | done | 无变化 |
| DYQ-3 | in_progress | 仍in_progress（Mock全PASS，真实后端仍不可达） |
| DYQ-2 | blocked | 仍blocked（沙箱CRUD已有证据，但dyq-server未运行） |
| DYQ-4 | done | 无变化 |
| 新增发现 | - | OpenAPI前缀不一致+TLS字段长度不足 |

### 建议
1. DYQ-5 暂保持 blocked（DYQ-2仍blocked，硬门禁未满足）
2. 关键解锁路径：**启动dyq-server:48080** → 跑真实后端冒烟 → 闭环演示
3. 阿盾优先：修复TLS字段DDL + 启动dyq-server + 验证健康检查
4. 老周优先：修复sandbox.openapi.yaml路径前缀
