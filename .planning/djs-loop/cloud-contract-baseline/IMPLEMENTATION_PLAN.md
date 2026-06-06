# 实施计划

STATUS: IN_PROGRESS

## 任务切片（2-5 分钟粒度）

| ID | 任务 | 验证 | 状态 |
|----|------|------|------|
| T1 | 解析 `device.openapi.yaml` → `baseline.json`（stdlib Python） | `python3 -m json.tool baseline.json` 合法 | done |
| T2 | 提取 `DeviceApi.kt` 5 端点路径 | grep + 打印到 RUN_LOG | done |
| T3 | 提取 `CloudModels.kt` 字段（@SerializedName + nullable） | grep + 计数 | done |
| T4 | 提取 `ClawSignatureGenerator.kt` 算法与密钥描述 | grep + diff 到 signing 段 | done |
| T5 | 生成 `kotlin-coverage.md`（3 状态机） | markdown 表格行数 ≥ 5 | done |
| T6 | 写 `scripts/cloud-contract-baseline-check.sh`（自洽入口） | `bash -n` 语法过 + 退出码测试 | done |
| T7 | 跑 baseline-check.sh，产物落到 `artifacts/cloud-contract-baseline/<ts>/` | ls 产物齐全 | done |
| T8 | 跑 `./gradlew :app:compileDebugKotlin --console=plain` | BUILD SUCCESSFUL | done |
| T9 | git add + commit（feat(端云契约): ...） | git log -1 显示新提交 | done |
| T10 | 写 EVIDENCE.md + RUN_LOG.md | 必填字段齐 | done |
| T11 | kanban_comment 回填 4 字段 | 调用返回 success | done |
| T12 | kanban_complete | status=done, summary + metadata | done |

## 顺序约束

- T1 → T2-T4 可并发（但本 worker 是单进程，所以顺序）
- T5 依赖 T2/T3/T4 完成
- T6 依赖 T1/T5 路径已知
- T7 依赖 T6
- T8 可与 T7 并行（独立进程）
- T9 依赖 T7/T8 全部产物
- T10-T12 串行

## 必跑验证命令（按顺序）

```bash
# 1. 写完脚本先语法过
bash -n scripts/cloud-contract-baseline-check.sh

# 2. 跑闭环
bash scripts/cloud-contract-baseline-check.sh artifacts/cloud-contract-baseline/manual

# 3. 端侧编译
./gradlew :app:compileDebugKotlin --console=plain

# 4. git 状态
git status --short
git log -1 --pretty='%H %s'

# 5. 产物清单
ls -la artifacts/cloud-contract-baseline/manual/
```

## 状态机流转

```
DISCOVERY   (读 CLAUDE.md / AI_INDEX / ARCHITECTURE)  done
DESIGN      (写 DESIGN.md)                            done
PLANNING    (写 REQUIREMENTS.md + IMPLEMENTATION_PLAN) done
BUILDING    (写脚本 + 跑闭环)                          in_progress → done
REVIEWING   (本 worker 自审，无独立审查 agent)        pending
INTEGRATING (无多仓，本轮 single-repo)                n/a
VERIFYING   (编译 + git 状态)                         pending
COMPLETE    (kanban_complete)                         pending
```

## 强制门禁

- [ ] baseline.json JSON 合法
- [ ] kotlin-coverage.md 至少 10 行
- [ ] EVIDENCE.md 含 commit hash
- [ ] :app:compileDebugKotlin BUILD SUCCESSFUL
- [ ] git status --short 仅新文件
- [ ] 无任何架构文件被改（`ARCHITECTURE_RECONSTRUCTION.md` / `CLAUDE.md` / `BACKLOG.md`）
- [ ] 无 token 明文落盘
