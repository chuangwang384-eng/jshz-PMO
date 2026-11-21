# 开场致辞主席修复（1.0.38.12）手动发布交接

2026-11-12 · ZCode 按 Codex 交接（HANDOFF.md）完成候选构建与隔离验收，**按 Owner 要求停止自动发布，移交手动发布程序**。

## 1. 候选制品（已就绪）

| 项 | 值 |
| --- | --- |
| 候选 JAR | `builds/1.0.38.12-build-20261112_105450/backend/target/expert-system.jar` |
| JAR SHA-256 | `5782f50f29a1a9f0aefafa8fa78e87c26d4545c147dc6e29d33c4bc1e79561b1` |
| 隔离构建 | 同目录含 version.txt（1.0.38.12）与源码快照；`ScheduleParseServiceTest` 11 项通过 |
| 源码差异 | 仅 `ScheduleParseService.java`（applyCeremonyChairRoles：开场致辞主席修复），与生产 1.0.38.11 构建源逐行比对确认 |
| 版本元数据 | version.txt / frontend/package.json 已 = 1.0.38.12（frontend 无制品变更，不发布） |

## 2. 隔离 Edge 验收状态（重要，如实）

用 `temp/run-opening-chair-isolated-13812.ps1` + `temp/opening-chair-edge-13812.mjs`（苏州站主验收 + CSCO12/信阳30/濮阳28 回归）在隔离环境（独立 MySQL:13316 + 18086 候选后端 + Vite + Edge）执行四轮：
1. 候选目录缺 version.txt（pom filter）→ 已修（无条件拷贝）。
2. 同原因重跑 → 已修。
3. fixture reseed 通过后，误跑 `tests/compliance-v4-live.mjs`（V4_VISUAL_EDITOR 未设置）→ 已固定为苏州站专用脚本。
4. **最后一次运行：苏州站候选 31 条、开场致辞（09:00-09:10）陈友国/张梦真两条 `大会主席` 断言通过（L48 通过）；失败在追加的「会议总结」角色断言**（总结行未升格为主席），随后整轮判 FAIL。

### 待 Owner 裁决的验收口径（✅已验收关闭 2026-11-19 Owner 批量授权）
- 交接需求范围 = **开场致辞两条主席记录**（已通过）。
- 「会议总结」角色升格是 applyCeremonyChairRoles 的一部分（总结+有名主席→主席），但该表总结行两人候选的角色在 Edge 环境下未按预期升格，需对照生产/隔离实际数据判定（最后一次运行的实际角色已由脚本打印，见 logs/opening-chair-isolated-13812.log 中 `INFO 会议总结角色` 与 `INFO 全部候选` 行）。
- 两种口径任选：A. 按需求范围验收（开场通过即可），把总结断言保持为仅记录；B. 要求总结也为主席，先排查升格未生效原因再发布。

## 3. 手动发布程序（Owner 执行）

1. **（可选）自行复跑隔离验收**（提权窗口，Edge 输出实时可见，结果写 `logs/opening-chair-isolated-13812.log`）：
   `<LOCAL_PATH>`
2. **发布**（提权窗口，2-4 分钟勿关）：
   `<LOCAL_PATH>`
   （= `scripts/release-backend.ps1` 后端蓝绿，仅后端，无迁移；候选 jar 路径与版本号已写入）
3. **发布后核验**：
   - `http://127.0.0.1/api/health` 与 Nginx 均为 **1.0.38.12 UP**；
   - 运行 jar SHA-256 = `5782f50f…61b1`（`sha256sum backend/target/expert-system.jar`）；
   - 真机上传「日程-6.15 与艾同行苏州站.pptx」→ 候选 31 条，开场致辞（09:00-09:10）陈友国/张梦真为大会主席。
4. **回滚**：重发 `builds/1.0.38.11-build-20261112-csco-chair/backend/target/expert-system.jar`（1.0.38.11，生产已验证制品）。无数据库回滚。

## 4. 待办与遗留
- 「会议总结」角色升格未达预期（见 §2）——需 Owner 按实际数据裁决口径。✅已验收关闭（2026-11-19 Owner 批量授权）
- 95% 准确率优化（按页 OCR、坐标表格恢复、文本框配对、LLM 兜底约束）未开始，见 HANDOFF.md §接手顺序 5/6。
- 确定发布后同步 deployment.md 表行、发布记录、handover 索引。

## 2026-11-12 手动发布完成（Owner 执行 deploy-13812.cmd，11:39）

- 发布输出全 11 阶段 [OK]，R-1 发布完成（新版本 1.0.38.12 正式服务），脚本退出码 0（`logs/deploy-backend.20261112_113827.log`）。
- 发布后核验（ZCode 11:40 实测）：8080 直连与经 Nginx `/api/health` 均 **1.0.38.12 UP**（buildTime=2026-11-12T10:56:24Z 与候选一致）；**运行 jar SHA-256 = 候选 jar**（5782f50f…61b1）；前端 root 保持 `1.0.38.8-project-lifecycle-20261112`（本次纯后端发布，前端制品不变）；SPA 200；deployment.md 表行已由脚本写入（11:39:42 | 1.0.38.12 | 成功）。
- 真机验收项（Owner）：上传「日程-6.15 与艾同行苏州站.pptx」→ 候选 31 条，开场致辞（09:00-09:10）陈友国/张梦真为大会主席。
- 回滚：重发 `builds/1.0.38.11-build-20261112-csco-chair/backend/target/expert-system.jar`。
