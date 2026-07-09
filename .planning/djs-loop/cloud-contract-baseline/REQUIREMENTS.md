# 需求

## 业务需求

PokeClaw 端侧（Android）需要在没有真实后端、也没有真机 ADB 的情况下，能向团队其他成员/下游 worker 证明"端侧已具备接入 DYQ 云端契约的最低条件"。这份"端云契约基线"是阻塞链上唯一可由端侧 worker 独立推进的环节。

## 功能需求

### R1. 契约清单结构化提取
- 解析 `api-contracts/device.openapi.yaml`
- 提取 5 个端点：`/api/claw-device/register`、`/api/claw-device/heartbeat`、`/api/claw-device/devices/{deviceId}/pending-tasks`、`/api/claw-device/tasks/{taskUuid}/result`、`/api/claw-device/token/refresh`
- 提取 7 个 schema：`CommonResponse`、`DeviceRegisterRequest/Data/ResponseWrapper`、`DeviceHeartbeatRequest/Data/ResponseWrapper`、`PendingTaskItem`、`PendingTasksResponseWrapper`、`TaskResultRequest`、`SubmitTaskResultData/ResponseWrapper`、`TokenRefreshRequest`、`RefreshDeviceTokenData/ResponseWrapper`
- 提取签名头：`X-Claw-Timestamp`、`X-Claw-Nonce`、`X-Claw-Signature`，含 HMAC-SHA256 算法描述
- 提取 `status` 枚举：`SUCCESS/FAILED/RUNNING/CANCELLED`
- 提取 `networkType` 枚举：`wifi/cellular/offline`
- 提取 `code` 业务码白名单：`0, 200` 为成功，`401` 为鉴权失败

### R2. 端侧实现覆盖表
- 对照 `app/src/main/java/io/agents/pokeclaw/cloud/api/DeviceApi.kt`：
  - 5 个 `@POST/@GET` 端点是否与契约路径完全一致
- 对照 `app/src/main/java/io/agents/pokeclaw/cloud/model/CloudModels.kt`：
  - 每个 data class 的字段名、SerializedName、nullable 与 YAML schema 是否一致
- 对照 `app/src/main/java/io/agents/pokeclaw/cloud/auth/ClawSignatureGenerator.kt`：
  - 签名算法 `HmacSHA256`、签名字符串拼装、密钥为 deviceToken 是否与契约 security 段一致
- 状态：✓对齐 / △扩展 / ✗缺失

### R3. 可重复执行脚本
- `scripts/cloud-contract-baseline-check.sh`
- 无副作用：仅读 yaml、读 kt 源、输出到 `artifacts/...`
- 退出码：0=PASS，1=FAIL，2=BLOCKED
- 自带 Mock 启动可选（默认 off，避免端口占用）
- 输出：`baseline.json`、`kotlin-coverage.md`、`EVIDENCE.md`

### R4. 编译验证
- 跑 `./gradlew :app:compileDebugKotlin --console=plain`
- 期望 BUILD SUCCESSFUL；若失败，必须把失败原因写进 RUN_LOG，不允许静默

### R5. 提交
- 一个 commit：`feat(端云契约): 端云契约基线闭环与最小证据清单`
- 提交信息中文 + 列出 3 类改动（脚本/证据/契约清单）
- 落在 dev 分支本地，**不强推**
- 提交号回填到 EVIDENCE.md

## 非功能需求

- 性能：脚本总耗时 <5s（不含 gradle 编译）
- 兼容性：WSL/Linux/MacOS 都能跑；不需要 Python 之外依赖（用 Python 3 + 标准库 yaml fallback 即可，但应避免安装 pyyaml；改用正则解析或 Python 内置）
- 安全：所有 token 日志脱敏；不向外部服务发任何请求（除可选本机 Mock）
- 可观测：RUN_LOG 记录每步耗时；EVIDENCE.md 包含产物相对路径

## 验收标准

| 编号 | 标准 | 通过条件 |
|------|------|----------|
| AC1 | 契约清单 JSON schema 完整 | 5 endpoints + 7 schemas + signing + enums + codes 全部 key 存在 |
| AC2 | 端侧实现覆盖表 3 状态覆盖率 | 端侧每个核心端点都有 ✓/△/✗ 标记，不允许"未评" |
| AC3 | 脚本自洽 | 脚本不修改 git 状态（git status --short 仍 clean 或仅新文件） |
| AC4 | 编译通过 | `:app:compileDebugKotlin` BUILD SUCCESSFUL |
| AC5 | 提交回填 | EVIDENCE.md 含完整 commit hash + 7 字符短码 |
| AC6 | 评论回填 | kanban 评论含 4 字段：改动文件 / 验证命令 / 证据路径 / 提交号 |
| AC7 | 红线守住 | 远端 0 push；无 token 明文；架构文件 0 修改 |

## 暂不做

- 不在本轮加新的 Kotlin 测试（已存在的 `ClawSignatureGeneratorTest.kt` 等不受影响即可）
- 不写 Python 自动 YAML 解析器依赖（用 stdlib + 简单正则足够；如需可走渐进增强）
- 不引入 CI 配置文件改动（`.github/workflows/` 由另路管理）
