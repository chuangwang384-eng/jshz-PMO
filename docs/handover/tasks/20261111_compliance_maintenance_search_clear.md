# 模板维护搜索修复 + 候选事项清空功能

## 1. 基本信息
- 任务编号：20261111-compliance-maintenance-search-clear
- 任务名称：企业合规「模板维护」搜索修复与候选事项清空
- 主责 Agent：CodeBuddy（Claude）
- 交接时间：2026-11-11（2026-11-12 已发布生产 1.0.38.8）
- 当前状态：已完成待验收（生产已发布，待业务验收）
- 优先级：P1

## 1.1 发布结果（2026-11-12 08:25，经 Owner 批准）
- 后端：`deploy-backend.ps1 -GracefulSwitch` 11 阶段全部通过，`/api/health` = UP version=1.0.38.8；发布日志 `logs/deploy-backend-1.0.38.8-20261112.log`。
- 前端：快照 `frontend/releases/1.0.38.8-maintenance-search-20261112` 平滑切换，SPA 路由 200，新功能代码已入快照；上一 root=`1.0.38.7-maintenance-ui-20261111`。
- 版本：version.txt / frontend/package.json → 1.0.38.8。
- 回滚：见 docs/releases/20261112-maintenance-search-clear-1.0.38.8.md。
- 未完成：带真实数据的业务验收（搜索/清空全流程）由 Owner 执行。

## 2. 目标与验收条件
- 业务目标：
  1. 关键词搜索能正确匹配候选/已发布事项（标题或内容子串匹配）；
  2. 特殊字符（`%`、`_`、`\`）按字面字符匹配，不被当作 LIKE 通配符；
  3. 搜索无结果时给出明确提示（区别于"暂无数据"）；
  4. 候选事项区域新增「清空」按钮，一次性删除该文档全部草稿候选；
  5. 清空后界面状态、数据绑定、搜索列表同步更新。
- 本次范围：`ComplianceService`、`ComplianceItemController`、`compliance.js`、`MaintenanceBoard.vue`、单元测试。
- 不在本次范围：解析流程、发布/撤下流程、记事本、权限模型、其他板块。
- 验收条件：
  - [ ] 搜索关键词（如"讲费"、"70%"、"_"、"\"）返回字面匹配结果（后端逻辑已实现并编译通过，真实接口联调待部署后验证）
  - [ ] 搜索无结果时两个区块分别提示"无匹配…"（前端已实现，浏览器验证待执行）
  - [ ] 候选分组内可一键清空，有确认弹窗 + 数量预览 + expectedCount 乐观校验（单元测试覆盖）
  - [ ] 清空后列表刷新、分组计数归零、空态展示、`changed` 事件同步父级（代码路径走既有 perform→load）
  - [ ] 现有流程（解析/保存/编辑/删除/撤下/清空重解析）不受影响（未改动相关逻辑）

## 3. 项目基线
- 分支：master（工作区含大量其他任务的已暂存/未暂存改动，本次仅在其上叠加最小改动，未触碰他人文件内容）
- 当前提交号：a417c51（HEAD）
- 工作区状态：非干净（多任务并行，本次改动文件见第 5 节）
- 前端/后端/数据库版本：version.txt=1.0.38.7；数据库无变更
- 运行环境及关键端口：后端 8080（未重启，改动未生效于运行中服务）
- 以上信息核实时间：2026-11-11（git log / git status / version.txt 现查）

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据或说明 |
| --- | --- | --- | --- |
| 关键词 LIKE 通配符转义 + trim | backend ComplianceService.listItems | 完成 | `\ % _` 转义后传入 MP like，字面匹配；PreparedStatement 绑定无注入 |
| 清空候选预览/执行方法 | backend ComplianceService | 完成 | clearDraftsPreview / clearDrafts(expectedCount 409 校验)，逐条复用 deleteItem（含责任人清理+修改历史） |
| 新增 2 个维护人接口 | backend ComplianceItemController | 完成 | GET `/api/compliance/documents/{id}/drafts-clear-preview`、POST `/api/compliance/documents/{id}/drafts-clear`（均 requireMaintainer） |
| API 封装 | frontend/src/api/compliance.js | 完成 | clearDraftsPreview / clearDrafts |
| 候选区清空按钮 + 空态文案 | frontend MaintenanceBoard.vue | 完成 | 分组内"清空候选事项"按钮；候选/已发布空态区分"搜索无结果"与"暂无数据" |
| 单元测试 | backend ComplianceMaintenanceTest | 完成 | 新增 clearDraftsCountMismatchRejected、clearDraftsDeletesAllDrafts 两用例 |

## 5. 实际变更
- 新增文件：docs/handover/tasks/20261111_compliance_maintenance_search_clear.md（本文件）
- 修改文件：
  - `backend/src/main/java/com/jshz/expert/service/ComplianceService.java`（listItems 关键词转义；新增 clearDraftsPreview/clearDrafts）
  - `backend/src/main/java/com/jshz/expert/controller/ComplianceItemController.java`（新增 2 端点）
  - `backend/src/test/java/com/jshz/expert/service/ComplianceMaintenanceTest.java`（构造注入 assigneeMapper mock；新增 2 测试；source() 文档补 title）
  - `frontend/src/api/compliance.js`（新增 2 封装）
  - `frontend/src/components/compliance/MaintenanceBoard.vue`（清空按钮、clearDraftGroups、空态文案）
  - `docs/handover/README.md`（追加摘要条目）
- 删除文件：无
- 数据库/迁移：无
- API/接口契约：新增上述 2 个接口；`GET /compliance/items` 的 keyword 语义由"LIKE 含通配符"变为"字面子串匹配"（更严格，属行为修正）
- 页面/路由/权限：无新页面；新接口沿用 `knowledge:maintain` 权限
- 配置/环境变量：无
- 依赖变更：无

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 后端单元测试 | `mvn -Drevision=1.0.38.7 -DgitShort=a417c51 -Dtest=ComplianceMaintenanceTest test` | 通过（4 用例，exit 0） | 含新增 2 用例 |
| 后端编译 | 同上（含 test-compile） | 通过 | 未做 `mvn clean package`（生产进程占用 jar，按 AGENTS.md 0.4 禁止） |
| 前端构建 | `npm run build -- --outDir ../temp/frontend-build-maintenance-20261111 --emptyOutDir` | 通过（✓ built in 1m 6s） | 输出至 temp 独立目录，未动 dist/线上指针；仅历史 chunk 体积告警 |
| 真实接口/浏览器验证 | 关键词检索、特殊字符输入、清空候选 E2E | 未执行 | 需先部署后端（当前运行服务为旧代码）；前端可在隔离环境用待发布构建验证 |
| 权限/安全验证 | 非 maintainer 调用新接口应 403 | 未执行（代码路径为既有 requireMaintainer） | — |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 浏览器/接口真机验收 | 后端改动需发布后生效 | 验收条件 1-2 未闭环 | 部署后按第 10 节步骤验证 | Owner/下一位 Agent |
| 生产发布 | 按 AGENTS.md 第 10/14 节需批准 | 功能未上线 | 申请发布窗口：后端蓝绿 + 前端快照切换 | Owner |

## 8. 风险与回滚
- 已知问题：keyword 语义变化为字面匹配，若有用户依赖 `%` 通配符搜索习惯将不再命中（属预期修正）。
- 数据风险：清空候选为物理删除（与单条删除一致），不可恢复；已有确认弹窗 + expectedCount 校验兜底；已发布事项不受影响。
- 兼容性风险：新增接口为增量，无旧调用方。
- 回滚步骤：撤回第 5 节所列文件的本次改动并重新构建前后端；无数据库回滚。
- 回滚限制：清空操作本身不可逆（删除后候选只能重新解析生成）。

## 9. 文档同步
- [ ] 架构文档（接口数清单未动，2 个增量接口待下次发布后统一更新）
- [ ] 部署文档（无部署方式变更，未改）
- [ ] 接口文档（如有独立接口清单，需补 2 个新端点）
- [x] 数据库迁移/数据字典（无变更，不适用）
- [x] 测试说明（ComplianceMaintenanceTest 用例即说明）
- [ ] 发布说明（待发布时创建）
- [x] 最新交接摘要（docs/handover/README.md 已追加）
- 未完成项及原因：发布类文档在发布时补。

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`/`git diff` 确认本文件第 5 节改动仍在且未被他人覆盖；`curl -s http://127.0.0.1:8080/health` 确认后端版本（旧版则新接口未生效）。
2. 然后执行：获批准后构建并发布后端（参考 `scripts/deploy-backend.ps1` 流程，勿在 backend/ 原地 `mvn clean package`），再按既有前端快照流程发布。
3. 验收标准：搜索"讲费"命中候选；搜索"70%"、"_"、"\"返回字面匹配而非全量；候选分组"清空候选事项"带预览与确认；清空后计数归零且已发布不变。
4. 未经确认禁止：生产数据库直接删除 compliance_item 数据；未经 Owner 批准发布。
5. 需要负责人决策：是否同步更新接口清单文档；发布窗口。

## 11. 禁止事项/待确认事项
- 禁止：清空候选不做确认直接删；在 backend/ 目录执行 clean 打包。
- 待确认：keyword 字面匹配语义变化是否需要通知用户。
