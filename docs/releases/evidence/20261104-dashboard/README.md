# 工作台生产发布证据

- 日期2026-11-04；发布版本1.0.24；源代码1980a66。
- [批准制品hash](approved-hashes.json)、[只读业务核验](production-verification.json)。
- [切流原始探针](switch-continuity.json)：1次新连接ECONNRESET，原门禁失败，不得抹去。
- [单独稳态复验](steady-continuity.json)：3034次无重试请求零失败，与切流窗口分开。
- [最终运行状态](final-state.json)：PUBLISHED_WITH_WARNING，372资源检查无失败、master不变；非全程零故障或业务验收通过。
- 原始私有日志与凭据不提交。完整事实、回滚限制和后续事项见[生产发布记录](../../20261104-dashboard-production.md)。
