# 任务：PokeClaw 端云契约摸底与最小证据清单

## 主人目标原文
> 小目标：使用 djs-loop（Ralph Loop）先完成一个不依赖父任务的准备闭环，不等待上游。
> 要求：
> 1. 先读项目规则与现有实现；
> 2. 在 .planning/djs-loop/<任务名>/ 写 GOAL/REQUIREMENTS/DESIGN/IMPLEMENTATION_PLAN/RUN_LOG/EVIDENCE；
> 3. 只推进一个可验收小目标，必须有业务可感知产出或明确可执行验收清单；
> 4. 运行最小验证命令；
> 5. 评论回填改动文件、验证命令、证据路径、提交号或未提交原因。
> 红线：不强推、不删远端修改、不改架构、不泄露密钥。

## 工程化目标（self-engineered）

本轮交付一个**端云契约基线闭环**，不依赖真实 DYQ 后端（48080）环境、不依赖 ADB 真机、不依赖父任务（DYQ-3 / 端云执行链路）解阻塞即可独立完成。**业务可感知产出**是：

1. **机器可读契约清单** `cloud-contract-baseline.json`：把 `api-contracts/device.openapi.yaml` 的 5 个核心端点 / 7 个 schema / 头部签名 / 状态码约束，结构化提取出来，给后续冒烟脚本和后端联调提供单一事实源。
2. **端侧 Kotlin 实现对照表** `kotlin-implementation-coverage.md`：把端侧 `DeviceApi.kt` + `CloudModels.kt` + `ClawSignatureGenerator.kt` 与契约做字段级对齐，标记缺失/偏离/扩展字段。
3. **可重复执行的小闭环脚本** `scripts/cloud-contract-baseline-check.sh`：任何人/CI 在 5 秒内可重跑本闭环，产物落到 `artifacts/cloud-contract-baseline/<ts>/`，并产生 `EVIDENCE.md` 摘要。
4. **端侧 Kotlin 编译验证**：`./gradlew :app:compileDebugKotlin` 通过（基线完整性的强证据）。
5. **git 提交**：`feat(端云契约): 端云契约基线闭环与最小证据清单`，落在 dev 分支本地，不强推。

完成上述 5 项后，本任务可被任何下游 worker（CMP-138 后端集成 / DYQ-3 真实联调）作为"端侧就绪证明"消费，不再依赖本端侧 worker 重复摸底。

## 暂不做（明确范围）

- 不修复 48080 后端阻塞（owner: dyq 后端团队，本 worker 不可控）
- 不解决 ADB 0 台设备（owner: 测试机/真机管理）
- 不做后端 OpenAPI 客户端自动生成（已有 `openapi-generator-config.json` 但本轮不跑）
- 不调整 `ARCHITECTURE_RECONSTRUCTION.md` 列出的架构热点（架构债由另路修复）
- 不更新 `BACKLOG.md`（端云契约基线属于"基础设施就绪"，不是 backlog 增量）
- 不在 evidence 中粘贴完整 deviceToken（已有脱敏规范）

## 红线

- 不强推
- 不删远端修改
- 不改架构
- 不泄露密钥（所有 curl/token 走脱敏）
- 任何"健康检查真实后端"的网络探针，先用本机 Mock（18080），再考虑用 localhost/host.docker.internal；禁止把内网 IP 直接拼到 curl 命令里走安全扫描。

## 验收口径

- `cloud-contract-baseline.json` schema 自检通过（5 endpoints, 7 schemas, signing_headers, status_codes）
- `kotlin-implementation-coverage.md` 标记 3 状态：`✓对齐` / `△扩展` / `✗缺失`
- 冒烟脚本在 `<5s` 退出码 0，产出 evidence 目录
- `:app:compileDebugKotlin` BUILD SUCCESSFUL
- git commit hash 记录在 EVIDENCE.md
- 评论回填完整字段
