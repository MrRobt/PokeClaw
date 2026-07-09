# 设计

## 总体思路

不引入新工具链，不写 Kotlin/Java/Python 解析器依赖；用 **stdlib + 正则/手写解析**完成 YAML 和 Kotlin 源文件的事实提取，让本闭环的"自检脚本"也是项目自包含的一部分。

## 数据流

```
api-contracts/device.openapi.yaml
        │
        ▼ parse_yaml.py (stdlib only: 注释行 + 简单缩进/列表识别)
baseline.json
        │
        ▼ human-check (生成时打印摘要到 RUN_LOG)
scripts/cloud-contract-baseline-check.sh
        │
        ├─→ 启动 Mock (可选)
        ├─→ 提取 Kotlin 源 @POST/@GET 注解
        ├─→ 提取 CloudModels.kt 的 @SerializedName 字段
        ├─→ 提取 ClawSignatureGenerator.kt 的 algorithm/密钥
        └─→ 交叉对照 + 输出 kotlin-coverage.md
        │
        ▼
artifacts/cloud-contract-baseline/<ts>/
   ├── baseline.json
   ├── kotlin-coverage.md
   ├── EVIDENCE.md
   ├── smoke_run.log  (来自 dyq3-endcloud-smoke.sh 二选一)
   └── git_status.txt
```

## 关键决策

### 决策 1：自包含 stdlib YAML 解析器
- **选项 A**：依赖 `pip install pyyaml` —— 简单但要装包
- **选项 B**：用 stdlib `tomllib`/正则手写解析 —— 不装包
- **决定 B**。本任务只在团队工作机/WSL 上跑，YAML 结构稳定（355 行、3 缩进层级），用 30 行 Python 解析即可。可读性 > 严谨性。
- **回退条件**：如果未来 YAML schema 嵌套超过 4 层，改为 A。

### 决策 2：不引入 conftest/test runner
- 端侧 Kotlin 编译用现成 `:app:compileDebugKotlin`，不在本轮加新测试
- 原因：现有 `ClawSignatureGeneratorTest.kt` 已覆盖 HMAC 签名契约；本轮是"事实摘录"，不是"行为验证"

### 决策 3：覆盖表 3 状态机
- ✓对齐：字段名、类型、nullable、序列化名、路径与契约完全一致
- △扩展：端侧加了契约之外的字段（如 `errorCategory`/`errorCode`/`recoverable`）—— 视为"扩展"而非"偏离"
- ✗缺失：契约要求而端侧未实现
- **不做**"端侧有但契约没有"的反向 ✗（YAML 是白名单，端侧字段扩展是良性扩展）

### 决策 4：EVIDENCE.md 自动生成 + 人工备注两段
- 自动段：JSON 摘要、覆盖率数字、文件清单、git hash
- 人工段：本轮 owner 留下 1-3 行"残余阻塞/下一步建议"
- 原因：机器产物可复读，但下游对接人更看 human-readable "那接下来我该做什么"

### 决策 5：commit 信息严格按主人格式
- `feat(端云契约): 端云契约基线闭环与最小证据清单`
- 一行标题 + 1-2 行 body
- 不强推

## 接口契约（自产自用）

`baseline.json` schema（v1）：

```json
{
  "version": "1.0.0",
  "openapi_version": "3.0.3",
  "extracted_at": "2026-06-07T04:55:00Z",
  "endpoints": [
    {
      "path": "/api/claw-device/register",
      "method": "POST",
      "operation_id": "registerDevice",
      "request_schema": "DeviceRegisterRequest",
      "response_schema": "DeviceRegisterResponseWrapper",
      "auth_required": false
    }
  ],
  "schemas": ["CommonResponse", "DeviceRegisterRequest", ...],
  "signing": {
    "headers": ["X-Claw-Timestamp", "X-Claw-Nonce", "X-Claw-Signature"],
    "algorithm": "HMAC-SHA256",
    "key": "deviceToken",
    "signing_string": "timestamp + '\\n' + nonce + '\\n' + path + '\\n' + sha256_hex(body)"
  },
  "enums": {
    "task_status": ["SUCCESS", "FAILED", "RUNNING", "CANCELLED"],
    "network_type": ["wifi", "cellular", "offline"]
  },
  "code_whitelist": {
    "success": [0, 200],
    "auth_failure": [401]
  }
}
```

`kotlin-coverage.md` schema：

```markdown
# 端侧 Kotlin 实现与契约对照

| 契约端点/字段 | 端侧位置 | 状态 | 备注 |
|--------------|----------|------|------|
| POST /api/claw-device/register | DeviceApi.kt:9-12 | ✓对齐 | ... |
| TaskResultRequest.errorCategory | CloudModels.kt:145-147 | △扩展 | 契约未要求，端侧扩展 |
```

## 风险与回退

| 风险 | 缓解 |
|------|------|
| Python stdlib YAML 解析漏字段 | 解析后打印缺失字段列表到 RUN_LOG，CI 直接 fail |
| :app:compileDebugKotlin 因环境失败 | 先用 `--offline` 跑一次，若仍失败记录在 RUN_LOG，不假装通过 |
| git commit 失败（pre-commit hook 等） | 不重试，把失败原因写到 EVIDENCE.md，本轮以"草稿"形式交付 |
| 端口占用 | 默认不启 Mock；用 18080 显式 env 时才启 |

## 与父任务的关系

- 本任务是 `t_d2f8d4b7`（"小目标-PokeClaw端云契约摸底与最小证据清单"），**没有父任务**
- 后续若父任务 DYQ-3 解阻塞（后端 48080 健康检查修复 + ADB 真机上线），本任务的 `baseline.json` 可被直接消费作为联调依据
- 本任务不做"父任务前置依赖"声明（因为本任务本身就是父任务——标题是"小目标-..."，没有 parents 字段）
