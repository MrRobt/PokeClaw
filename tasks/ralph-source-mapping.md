# ralph Story 同步索引

> 整理日期：2026-06-13
> 来源：`D:\work\code\dyqbackupdd\ralph\prd.json`（599 条 user stories）
> 同步目标：当前 PokeClaw 项目
> 详细 PRD：`tasks/prd-claw-endcloud.md`

## 1. 同步统计

| 维度 | 数量 |
|---|---|
| ralph 全部 stories | 599 |
| project 标签为 `claw-*` 的 stories | 55（21+17+10+7） |
| **保留到 PokeClaw PRD** | **14**（端侧实现 + 需要云端 API） |
| 跳过（联调验收类） | 2（US-CLAW3T-007 / US-CLAW-CMP-3-4-1） |
| 跳过（属于 WeFlow 端） | 9 |
| 跳过（属于 dyq Claw 云端后端） | 8 |
| 标记为远期规划（场景类） | 4 |
| 不属于 PokeClaw（dyq 号源市场） | 17 |
| **合计去向** | **55**（无丢失） |

## 2. 完整 55 条 story 去向

### claw-3terminal (7)

| Story ID | 标题 | 去向 |
|---|---|---|
| US-CLAW3T-001 | 云端创建并编排商业任务 | ⏭️ 纯云端（dyq 后端） |
| US-CLAW3T-002 | 设备注册与心跳 | ✅ `prd-claw-endcloud.md` US-CLAW3T-002 |
| US-CLAW3T-003 | PokeClaw 领取任务并回传证据 | ✅ `prd-claw-endcloud.md` US-CLAW3T-003 |
| US-CLAW3T-004 | WeFlow 上报微信消息 | ⏭️ WeFlow 端 |
| US-CLAW3T-005 | WeFlow 安全回复 | ⏭️ WeFlow 端 |
| US-CLAW3T-006 | 经验包沉淀 | ✅ `prd-claw-endcloud.md` US-CLAW3T-006 |
| **US-CLAW3T-007** | **总体验收** | ⏭️ **联调验收（按用户要求跳过）** |

### claw-detail (21)

| Story ID | 标题 | 去向 |
|---|---|---|
| US-CLAW-OW-001 | 主人后台→Claw→PokeClaw 执行 | ✅ `prd-claw-endcloud.md` US-CLAW-OW-001 |
| US-CLAW-OW-002 | 主人微信→weflow | ⏭️ WeFlow 端 |
| US-CLAW-OW-003 | 执行失败反馈与人工接管 | ✅ `prd-claw-endcloud.md` US-CLAW-OW-003 |
| US-CLAW-OW-004 | 边界：撤销授权 | ✅ `prd-claw-endcloud.md` US-CLAW-OW-004 |
| US-CLAW-OW-005 | 边界：日志脱敏 | ✅ `prd-claw-endcloud.md` US-CLAW-OW-005 |
| US-CLAW-CMP-1-1 | 小龙虾角色管理 | ⏭️ 纯云端 |
| US-CLAW-CMP-1-2 | 统一执行节点模型 | ⏭️ 纯云端 |
| US-CLAW-CMP-1-3 | 任务编排核心 | ⏭️ 纯云端 |
| US-CLAW-CMP-1-4 | 经验汇总与查询 | ⏭️ 纯云端 |
| US-CLAW-CMP-1-5 | 指挥调度接口 | ⏭️ 纯云端 |
| US-CLAW-CMP-2-1 | 设备注册为执行节点 | ✅ `prd-claw-endcloud.md` US-CLAW-CMP-2-1 |
| US-CLAW-CMP-2-2 | 心跳保活机制 | ✅ `prd-claw-endcloud.md` US-CLAW-CMP-2-2 |
| US-CLAW-CMP-2-3 | 接收云端指令 | ✅ `prd-claw-endcloud.md` US-CLAW-CMP-2-3 |
| US-CLAW-CMP-2-4 | 执行简单手机控制任务 | ✅ `prd-claw-endcloud.md` US-CLAW-CMP-2-4 |
| US-CLAW-CMP-2-5 | 结果与错误上报 | ✅ `prd-claw-endcloud.md` US-CLAW-CMP-2-5 |
| US-CLAW-CMP-2-6 | 端侧安全与降级 | ✅ `prd-claw-endcloud.md` US-CLAW-CMP-2-6 |
| US-CLAW-CMP-3-1 | 微信消息接收 | ⏭️ WeFlow 端 |
| US-CLAW-CMP-3-2 | 微信消息发送 | ⏭️ WeFlow 端 |
| US-CLAW-CMP-3-3 | 状态查询能力 | ⏭️ WeFlow 端 |
| US-CLAW-CMP-3-4 | 设备注册接口（预留） | ⏭️ WeFlow 端 |
| **US-CLAW-CMP-3-4-1** | **MVP 用例覆盖** | ⏭️ **联调验收（按用户要求跳过）** |

### claw-prd (10)

| Story ID | 标题 | 去向 |
|---|---|---|
| US-CLAW-LANE-C1 | Claw Controller 核心 API | ⏭️ 纯云端 |
| US-CLAW-LANE-C2 | 设备管理与心跳（WebSocket） | ✅ `prd-claw-endcloud.md` US-CLAW-LANE-C2 |
| US-CLAW-LANE-P1 | PokeClaw 端侧执行链路 | ✅ `prd-claw-endcloud.md` US-CLAW-LANE-P1 |
| US-CLAW-LANE-P2 | 端侧稳定性与自愈 | ✅ `prd-claw-endcloud.md` US-CLAW-LANE-P2 |
| US-CLAW-LANE-W1 | WeFlow 微信事件采集 | ⏭️ WeFlow 端 |
| US-CLAW-LANE-W2 | 安全回复闭环 | ⏭️ WeFlow 端 |
| US-CLAW-LANE-S1 | 自动化运营场景 | 🕒 场景类，远期规划（BACKLOG ideas） |
| US-CLAW-LANE-S2 | 截流获客场景 | 🕒 场景类，远期规划（BACKLOG ideas） |
| US-CLAW-LANE-S3 | 商城交易场景 | 🕒 场景类，远期规划（BACKLOG ideas） |
| US-CLAW-LANE-S4 | 养号孵化场景 | 🕒 场景类，远期规划（BACKLOG ideas） |

### claw-workpackage (17)

全部为 dyq 号源市场 P0/P1/P2 工作包，**不属于 PokeClaw**：

| Story ID | 标题 |
|---|---|
| US-AM-PKG-P0-BASELINE-VERIFY | P0 底座·每条收敛声明都有 grep+Read 实证 |
| US-AM-PKG-P0-TX-SAFETY-GATE | P0 底座·R1/R2/R3 自动拦截 |
| US-AM-PKG-P0-PERM-MENU-BASE | P0 底座·90 码菜单/角色一次落 dev |
| US-AM-PKG-P0-CREDENTIAL-MASKED | P0 底座·买家看到打码后的凭证 |
| US-AM-PKG-P0-G5-PAYMENT-AUDIT | P0 底座·每笔打款审计 |
| US-AM-PKG-P1-SUPPLY-PERM-GAP | P1 商业化·套餐/号商/定价启停 |
| US-AM-PKG-P1-SUPPLY-CATALOG-API | P1 商业化·号源后台筛选 |
| US-AM-PKG-P1-CREDENTIAL-DELIVERY-FORM | P1 商业化·受控揭示凭证 |
| US-AM-PKG-P1-DISPUTE-MAINLOOP | P1 商业化·纠纷处理 |
| US-AM-PKG-P1-SETTLEMENT-RECONCILIATION | P1 商业化·每日对账 |
| US-AM-PKG-P1-INVENTORY-ALERT-ACTIVATE | P1 商业化·库存告警 |
| US-AM-PKG-P2-SELLER-SELFSERVICE | P2 模式·商家自助 |
| US-AM-PKG-P2-DISTRIBUTION | P2 模式·分销渠道 |
| US-AM-PKG-P2-QUALITY-AUTOGRADE-ENGINE | P2 模式·质量自动评级 |
| US-AM-PKG-P2-AGENT-INTEGRATION | P2 模式·R13 系统智能体接入 |
| US-AM-PKG-P2-INVOICE-RECEIPT | P2 模式·发票/电子凭证 |
| US-AM-PKG-P2-EVALUATION-REVIEW | P2 模式·买卖评价体系 |

## 3. 过滤规则

- ✅ **保留**：PokeClaw 端可直接实现（含需要云端 API 配合的端侧部分）
- ⏭️ **跳过 - 联调验收**：US-CLAW3T-007 总体验收、US-CLAW-CMP-3-4-1 MVP 用例覆盖
- ⏭️ **跳过 - WeFlow 端**：所有微信/微信 GUI 自动化相关 stories
- ⏭️ **跳过 - 纯云端**：所有 dyq Claw 后端实现（角色、节点模型、编排、调度）
- 🕒 **远期规划**：场景类 S1~S4（写入 BACKLOG ideas，不在当前 PRD）
- ⏭️ **不属于本项目**：claw-workpackage 全部 17 条（dyq 号源市场）

## 4. 后续同步建议

1. **优先实现端侧单端可开发 story**（不依赖云端）：
   - OW-005 日志脱敏
   - CMP-2-4 简单手机控制任务
   - CMP-2-6 端侧安全与降级
   - OW-004 撤销授权
   - LANE-P2 自愈部分

2. **依赖云端的 story 需先确认 API 存在性**：
   - CMP-2-1/2/3/5（设备注册/心跳/指令/上报）→ API 已存在（参考 `.planning/pokeclaw-cloud-integration/integration-checklist-CMP-1940.md`）
   - 3T-006 经验包上传 → API 待确认
   - LANE-C2 WebSocket → 可选，本期可不实现

3. **QA-First 同步**：每条 story 在 `QA_CHECKLIST.md` 预留条目（**联调阶段才启用**）。
