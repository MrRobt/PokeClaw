# DYQ-3 弱网/重试/超时场景验证证据

**日期**: 2026-06-04 20:08:20
**执行人**: 端侧工程师阿甲
**结果**: 13/14 通过

| 编号 | 描述 | 结果 | 详情 |
|------|------|------|------|
| A1 | 正常注册基线 | PASS | status=200, code=200 |
| A2 | 正常心跳基线 | PASS | status=200, code=200 |
| B1 | 不可达地址单次请求失败 | PASS | status=-1, attempts=1 |
| B2 | 不可达地址3次重试后仍失败 | PASS | attempts=4, error=<urlopen error [Errno 111] Connection refused> |
| B3 | 网络恢复后重试成功 | PASS | status=200, code=200 |
| C1 | 超时请求失败可追溯 | FAIL | status=200, error= |
| C2 | 超时后正常重试成功 | PASS | status=200 |
| D1 | 旧令牌心跳正常 | PASS | status=200 |
| D2 | 令牌刷新返回新token | PASS | old=mock-device-token-49... new=mock-device-token-a2... |
| D3 | 新令牌心跳验证成功 | PASS | status=200 |
| D4 | 无效令牌返回401（用户可见） | PASS | code=401, msg=令牌无效 |
| D5 | 无令牌返回401（用户可见） | PASS | code=401, msg=缺少有效令牌 |
| E1 | 缺少deviceId返回400 | PASS | code=400, msg=缺少必填字段: deviceId |
| E2 | 空请求体返回400 | PASS | code=400, msg=请求体无效：需要JSON格式 |

## 场景覆盖

- **场景A**: 正常链路基线（注册+心跳）
- **场景B**: 网络不可达+重试机制（单次失败、多次重试、恢复后成功）
- **场景C**: 超时场景（超时可追溯、超时后重试成功）
- **场景D**: 令牌失效→刷新→恢复（旧令牌、刷新、新令牌验证、无效/无令牌401）
- **场景E**: 输入校验（缺少必填字段400、空请求体400）
