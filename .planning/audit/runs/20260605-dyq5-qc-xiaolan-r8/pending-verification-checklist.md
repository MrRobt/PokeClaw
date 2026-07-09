# DYQ-5 待验证清单 — 第8轮

更新时间: 2026-06-05T07:33+08:00
验证人: 测试员小蓝 (ec2afe67)

---

## P0 阻塞项（2项，阻塞三端闭环）

| # | 项目 | 验证命令 | 负责人 | 状态 |
|---|---|---|---|---|
| 1 | dyq-server:48080启动并health通过 | `curl http://192.168.250.3:48080/admin-api/actuator/health` | 阿甲(DYQ-2) | 未解 |
| 2 | Nacos:8848启动 | `curl http://192.168.250.3:8848/nacos/` | 阿甲(DYQ-2) | 未解 |

## P1 完整验收项（4项）

| # | 项目 | 验证命令 | 状态 |
|---|---|---|---|
| 3 | WeFlow真实微信发送（非fake模式） | Windows端运行agent bridge或安装pyweixin到vendor | 待修复 |
| 4 | PokeClaw ADB设备连接 | `adb devices` 非空 | 待连接 |
| 5 | 端到端任务下发→执行→回传 | 需云+端+WeFlow同时可用 | 阻塞于P0 |
| 6 | 前端管理后台查看设备/任务/经验 | 需云端可用 | 阻塞于P0 |
| 7 | 异常注入验证 | 需全链路可用 | 阻塞于P0 |

## 已解决项（本轮）

| # | 项目 | 解决方式 |
|---|---|---|
| ~~3~~ | ~~WeFlow pyweixin模块缺失~~ | 切换agent bridge到`--backend fake`模式 |
| ~~4~~ | ~~PokeClaw无ADB设备~~ | 降级为P1（硬件依赖，不影响软件层验证）|

## 变更日志

- [2026-06-05 07:33] 第8轮：WeFlow agent bridge切fake模式，P0从4→2，WeFlow自闭环可通
