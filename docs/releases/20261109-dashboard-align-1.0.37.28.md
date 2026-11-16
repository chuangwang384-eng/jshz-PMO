# 工作台与实际使用功能对齐 — 发布记录 1.0.37.28

日期 2026-11-09 · **状态：1.0.37.28 已发布并完成线上核验；补丁 1.0.37.28.1 已构建待发布** · 无数据库迁移
关联：[差异审查与需求](../requirements/2026-11-09-工作台与实际功能对齐-需求文档.md) / [技术设计](../design/2026-11-09-工作台与实际功能对齐-设计文档.md)

## 1. 发布内容

| 类别 | 内容 |
| --- | --- |
| 后端 | `DashboardReadService.projects()` 重写（阶段取 `project_phase`、成员=负责人/创建人/协助人+历史执行人、风险按 `end_date`、输出新字段）；`search()` 移除配置快照解码；`GlobalSearchModules` project 分组行级过滤与 `extra` 对齐；`DashboardLayoutService` 白名单 +3、布局卡片上限 20→24 |
| 前端 | `DashboardView.vue`：布局编辑固定启用（删 `VITE_DASHBOARD_LAYOUT_V2` 与 legacy 回退）、卡片目录补 3 个实际入口、项目行改新字段、移除失效横幅与 `mine=1` 深链、快捷访问空草稿整单中止；`QuickAccessBar.vue`：补回新增候选、图标 +3；`utils/dashboard.js`：深链白名单加 `/profile/overview`、去掉 `mine` |
| 测试 | `database/testing/dashboard_baseline.sql` 补 project 新列与 `project_assistant`；`DashboardReadIntegrationTest` 断言按新口径同步；`frontend/tests/dashboard.test.mjs` 新增深链用例 |
| 数据库 | **无迁移、无 DDL、无数据变更** |
| 权限 | **无权限码/角色/行级授权变更**；工作台仍为只读聚合 |

**不动**：项目/知识星球/审批/公告/通知/供应商等业务模块，Controller 与 API 契约，App.vue 页头与侧边栏。

## 2. 构建产物

| 项 | 路径 | 结果 |
| --- | --- | --- |
| 前端 | `builds/1.0.37.28-build-20261109/dist` | 48.15s / 0 错误，**故意不带任何环境变量开关** |
| 后端 | `builds/1.0.37.28-build-20261109_170530/expert-system.jar` | build-info 校验 version=1.0.37.28、gitShort=ffba3c1；Boot 包/logback/UTF-8 校验通过 |
| 版本 | `version.txt` / `frontend/package.json` | 1.0.37.28 |

## 3. 验证结果（本次实际执行）

| # | 验证项 | 命令/方式 | 结果 |
| --- | --- | --- | --- |
| V1 | 后端编译（含测试代码） | `mvn -q test-compile -DskipTests -Drevision=1.0.37.28 -DgitShort=a7d6f6e` | 通过 |
| V2 | 后端全量单测 | `JWT_SECRET=<测试值> mvn -q test -Drevision=1.0.37.28 -DgitShort=a7d6f6e` | **93 通过 / 0 失败 / 0 错误 / 9 跳过**（surefire 23 个报告汇总）。首次未注入 `JWT_SECRET` 时 `contextLoads` 因 `${JWT_SECRET:}` 为空触发 `WeakKeyException`，属既有环境前置条件，注入后全绿 |
| V3 | 前端单测 | `node --test tests/dashboard.test.mjs tests/dashboard-search.test.mjs tests/dashboard-layout.test.mjs` | **12/12 通过**（含新增「新入口路径可跳转 + `mine` 被丢弃」用例） |
| V4 | 前端产物 | 产物 grep 校验 | 主包含 `1.0.37.28`；DashboardView chunk 含「自定义布局」「协会合规要求」「企业合规要求」「我的主页」「项目列表」，**不含**「配置异常」「项目状态待核实」「VITE_DASHBOARD」「mine」「最近」；`dashboardGrid` 编辑器 chunk 仍打包 |
| V5 | 新 SQL 口径（生产库只读） | 直接执行新查询 | 项目 54：`team=DEPT3`（项目三部）、`phaseName=日程上传`、`cityName=北京市`、`category=ONLINE`、`meetingStartDate=2026-11-10`、`ownerName=测试`；搜索 `extra=日程上传`。项目 54 当前状态 `CANCELED`，工作台按规则隐藏（正确），全局搜索按既有规则仍可见 |
| V6 | 发布脚本 | ASCII/CRLF + PowerShell Parse | `deploy-13728.cmd` ASCII OK / CRLF；`run-deploy-frontend-13728.ps1` PARSE OK；jar 与 dist 均存在 |

**未执行 / 未验证（如实标注）**：

- 隔离库集成测试 `DashboardReadIntegrationTest`（需 13319 独立 MySQL 实例与 `DASHBOARD_ACCEPTANCE_DB` 环境变量）：断言与基线已同步，但本次**未运行**。
- 线上 `/api/dashboard/overview`、`/api/search/global` 新返回、浏览器真机交互、布局/快捷访问保存闭环：**待发布后验证**。
- 无关模块回归（项目/知识/审批等）：本次改动文件边界已核对，真机回归待 Owner。

## 4. 发布链路

双击 `<LOCAL_PATH>` → UAC 点「是」：

1. 后端蓝绿：`scripts\release-backend.ps1`（`deploy-backend.ps1` 11 阶段，含 8081 影子、切流、NSSM 换 jar、切回 8080、日志清理）
2. 前端 graceful：`temp\run-deploy-frontend-13728.ps1` → `scripts\deploy-frontend-graceful.ps1 -Apply`，ReleaseId `1.0.37.28-dashboard-align-20261109`，保留旧 root 与旧 hashed 资产

无迁移步骤。成功标志：`[OK] 1.0.37.28 ALL DONE`。

## 5. 发布后核验结果（2026-11-09 17:23–17:35 实测，Agent 执行）

发布日志 `logs/deploy-backend.20261109_172025.log` 结尾 `R-1 发布完成（新版本 1.0.37.28 正式服务）`、`发布脚本退出码 = 0`；后端归档 `backend/releases/1.0.37.28-20261109_172104`。

| # | 核验项 | 实测结果 | 判定 |
| --- | --- | --- | --- |
| P1 | `/health` | 直连 8080 与经 Nginx `/api/health` 均 `1.0.37.28 UP`，buildTime `2026-11-09 17:05:37`，gitShort `ffba3c1` | ✅ |
| P2 | Nginx root | `<LOCAL_PATH>`；master PID **7696 未变**（graceful reload）；`1.0.37.27` 旧 root 完整保留 | ✅ |
| P3 | 前端哈希 | 服务端 `index.html` = release（`93abe68d…`）；当前主包 `index-Bfeqg6op.js` 含 `1.0.37.28`；其引用的 `DashboardView-C6EP8bqF.js` 线上哈希 = 磁盘（`e6371bd2…`），含「自定义布局」「协会合规要求」「企业合规要求」「我的主页」，**不含**「配置异常」「项目状态待核实」「VITE_DASHBOARD」「mine=1」 | ✅ |
| P4 | 旧资产兜底 | 1.0.37.27 主包 `index-BdAQuzaw.js` 与 `DashboardView-BNHZFAsR.js` 均 200；SPA 8 条路由（含 `/profile/overview`、两条合规要求）全 200 | ✅ |
| P5 | 全局搜索 | `GET /api/search/global?keyword=测试` project 分组 `extra=日程上传`（原「配置异常」已消除）；六类分组 knowledge/expert/project/customer/supplier/announcement 均正常返回 | ✅ |
| P6 | 「我的项目」端到端 | 库内唯一项目 54 状态 `CANCELED`，按既有规则被工作台隐藏（旧口径同样排除），**非本次退化**。为验证新字段临时置 `ACTIVE` 后实测：`phaseName=日程上传`、`completionState=INCOMPLETE`、`warning=NONE`、`teamLabel=项目三部`、`cityName=北京市`、`category=ONLINE`、`meetingStartDate/EndDate=2026-11-10`、`ownerName=测试`、`myRelations=["负责人","创建人"]`；`unknownCompletionCount=0`、逾期/今日/将到期均 0。**验证后已恢复 `CANCELED` 并复核** | ✅ |
| P7 | 布局白名单 | `PUT /api/dashboard/layout/v2`：非法卡 `page.bogus` → `403 存在不可用的卡片`；3 个新入口 `page.complianceAssociation`/`page.complianceEnterprise`/`page.profileHome` → `code=0` 全部保存成功；随后**恢复原 8 项布局并复核一致** | ✅ |
| P8 | 运行期日志 | 发布后 `expert-app.log` 无新增业务异常；`nginx error.log` 仅 `signal process started` notice；8081 无 LISTENING（仅出站 TIME_WAIT 残留） | ✅ |

**探测过程中的两处自身失误（非生产问题）**：① 首次按 `ls assets/DashboardView-*.js | head -1` 取样命中 release 目录里从旧 root 拷来的历史 chunk，改用主包引用名后结果正常；② 误用 `/api/quick-access`、`/api/dashboard/layout`（真实端点为 `/api/dashboard/quick-access`、`/api/dashboard/layout/v2`），其中错误路径在 17:26:47 留下一条 `NoResourceFoundException` ERROR 日志。

**未验证（需 Owner 真机）**：浏览器「自定义布局」点击/拖拽/保存闭环、Ctrl K、六类搜索面板、60s 刷新、审批/申请/收藏/公告/通知面板交互回归；隔离库集成测试（需 13319 实例）仍未运行。

**发布后发现并已修复的回归见 §8**：快捷访问候选扩到 13 个但后端上限仍为 10，导致「保存布局」400 且整单中止，已出前端补丁 1.0.37.28.1（待发布）。

## 6. 回滚

- 后端：蓝绿回滚到 1.0.37.27 jar；前端：Nginx root 切回 `1.0.37.27-agenda-stage-ui-20261109`（发布脚本保留旧 root）。
- 无数据库变更，回滚不需要数据补偿；已保存的布局/快捷访问配置新旧两端均兼容（卡片 id 未删改）。
- 兼容窗口：旧前端忽略新增字段；新前端对旧数据用 `||` 兜底，可独立前后端回滚。

## 7. 已知限制

- `ProjectService.appendRowLevelCondition` 仍为 `owner + executor`，与 `isProjectMember` 成员口径不一致；工作台按业务口径展示，项目列表页仍按旧口径过滤。属项目模块遗留，本次不改（见需求文档 §3）。
- 项目流程目前仅「日程上传」阶段激活；后续阶段上线后，工作台阶段名会自动跟随 `project_phase` 字典，无需改动。
- 管理员设置各页未纳入工作台入口（权限组合复杂，列为 P3 建议）。
- 隔离库集成测试断言已同步但未运行，需后续专项验证。

## 8. 补丁 1.0.37.28.1（发布后核验发现，待发布）

**问题**：本次把快捷访问候选从 10 扩到 13（新增 3 个菜单入口），并在 `QuickAccessBar.enabled` 中「补回候选集新增项」，但后端 `QuickAccessService.MAX_ITEMS` 仍为 **10**。实测 `PUT /api/dashboard/quick-access` 带 12 项返回 `400 快捷访问最多 10 个`。影响面：

- 未配置账号：`defaultEnabled` 取全部 12–13 个候选 → 进编辑态 → 保存即 400；
- 已配置账号（如测试账号 9 项）：补回新入口后同样超限 → 400；
- 又因本次新增的「快捷访问失败则整单中止」逻辑，**布局本身也一并保存失败**，属 P0 回归。

**修复（纯前端，不动后端/数据库/接口契约）**：`frontend/src/components/QuickAccessBar.vue` 增加 `MAX_ITEMS = 10`（与后端对齐）：

- `defaultEnabled` / `enabled` 均按 10 截断，新入口只补到剩余空位；
- `addable` 在草稿满 10 时返回空，并在编辑区提示「最多 10 个，请先移除再添加」；
- `saveDraft()` 增加超限防御，提示并返回 `false`（保留整单中止语义但不发无效请求）。

卡片目录仍为 23 项、布局上限 24，不受影响；快捷访问只是快捷位，未启用的新入口仍可在布局编辑或侧边栏访问。

| 项 | 结果 |
| --- | --- |
| 静态推演 | 未配置 / 旧 9 项 / 满 10 项 / 含已撤权项 四种场景 `enabled` 恒为 10 |
| 前端单测 | `node --test` 三个测试文件 **12/12 通过** |
| 前端构建 | `builds/1.0.37.28.1-build-20261109/dist`，33.27s / 0 错误，**不带任何环境变量开关**；主包 `index-Dc9RM28N.js` 含 `1.0.37.28.1`，`DashboardView-CVW6buTl.js` 含「请先移除再添加」且仍含「自定义布局」，无「配置异常」 |
| 发布脚本 | `temp\deploy-13728-1.cmd` ASCII/CRLF OK；`temp\run-deploy-frontend-13728-1.ps1` PARSE OK |

**发布**：双击 `<LOCAL_PATH>` → UAC 点「是」。**仅前端 graceful，无迁移、后端不动、无需重启服务**。ReleaseId `1.0.37.28.1-quickaccess-fix-20261109`，保留 1.0.37.28 旧 root。成功标志 `[OK] 1.0.37.28.1 ALL DONE`。发布后建议真机点验：进入「自定义布局」→ 直接点「保存布局」应成功；编辑区满 10 项时候选区显示上限提示。

> 版本说明：后端 `/health` 保持 `1.0.37.28`（代码未变），前端 `version.txt`/`package.json` 为 `1.0.37.28.1`。
