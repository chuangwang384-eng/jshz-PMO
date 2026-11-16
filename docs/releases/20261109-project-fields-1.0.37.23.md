# 项目基本信息改版 + 测试数据清理 — 发布记录 1.0.37.23

- 日期：2026-11-09
- 版本：`1.0.37.23`
- 类型：前后端 + 数据迁移（新列/新表 + 一次性数据清理）
- 需求：[需求文档](../requirements/2026-11-09-项目基本信息改版-需求文档.md) / [设计文档](../design/2026-11-09-项目基本信息改版-设计文档.md)

## 1. 发布内容

- project 表新增 7 列（team/city_id/meeting_start_date/meeting_end_date/client_company/client_name/remark）+ 2 索引；新表 `project_assistant`。
- 后端：列表/导出新筛选与新输出字段；创建/编辑新字段校验（团队枚举必填、起止必填、城市校验、举办时间区间校验）；总负责人缺省=创建人（全员可选）；协助人多选存取。
- 前端：项目列表/表单/详情按新字段重做；查询状态入 URL。
- 数据清理（已执行）：project 9 / executor 15 / expert 1 / supplier 1 / doc 5 / phase_history 20 / 合规挂接 4 / 项目日志 82 行，全部先备份到 `*_bak_20261109`；标记表 `mig_20261109_project_reset_done` 防重跑。

## 2. 发布链路

`temp\deploy-13723.cmd`（管理员）顺序执行，任一步失败即停：

1. `scripts\apply-migration-20261109-project-rebuild.ps1` — 清理已做过（标记表存在，跳过），重放幂等 DDL 并校验
2. `scripts\release-backend.ps1` — 后端蓝绿 1.0.37.23（`builds\1.0.37.23-build-20261109_112651\expert-system.jar`）
3. `temp\run-deploy-frontend-13723.ps1` — 前端 graceful 切 root 到 `1.0.37.23-project-fields-20261109`

顺序不可调：新前端会读新列，迁移与后端必须先行。

## 3. 构建

| 项 | 结果 |
| --- | --- |
| 前端 | `builds/1.0.37.23-build-20261109/dist`，27.30s / 0 错误；ProjectList/ProjectEdit/ProjectDetail chunk 均含新字段文案；`编辑布局` 仍在；客户页「查看关联客户」、供应商页「写第一条评价」未丢 |
| 后端 | `builds/1.0.37.23-build-20261109_112651/expert-system.jar`，version=1.0.37.23；`mvn compile`+`test-compile` 通过（含 ComplianceV4AcceptanceIT 签名同步） |
| 迁移 | DDL 已执行并校验（7 列 + 2 索引 + project_assistant 表）；数据清理已执行，残留 0 行 |

## 4. 发布后核验（2026-11-09 11:40 已执行回填）

| 项 | 方法 | 结果 |
| --- | --- | --- |
| 后端 | `curl /health` | ✅ version=1.0.37.23 UP，buildTime=2026-11-09 11:26:57，gitShort=43ae865 |
| root 指针 | `grep root <LOCAL_PATH>` | ✅ `frontend/releases/1.0.37.23-project-fields-20261109` |
| 入口版本号 | 入口 chunk 内搜 `v1.0.37.23` | ✅ 命中（index-DZVJ6w01.js） |
| 前端哈希 | 本地 vs HTTP sha256 | ✅ index.html=`82f8c3e6…`、index-DZVJ6w01.js=`712f1f51…`、ProjectList-Ca1JcDUG.js=`1825fd19…` 三者线上线下一致 |
| 懒加载 chunk | index.js 引用 Edit/Detail chunk HTTP 状态 | ✅ ProjectEdit-B0qVBKgg.js / ProjectDetail-BcmgH2AL.js 均 200 |
| 接口 | 无 token `GET /api/project` | ✅ 401 |
| 旧资产 | 旧 hashed chunk（如 ProjectList-6bM5U2gu.js，union 兜底副本） | ✅ 200 |
| 数据 | information_schema + 行数 | ✅ project 新列 7/7；`project_assistant` 存在；project 行数 0；清理标记表 `mig_20261109_project_reset_done` 存在；索引含 idx_project_team / idx_project_city（共 7 个二级索引 + PRIMARY） |

> 注：发布目录 assets 下存在大量历史同名前缀 chunk（union 兜底副本），以 index.html→入口 chunk 实际引用链为准判断，勿误判为多版本混布。

## 5. 回滚

- 数据：`INSERT INTO project SELECT * FROM project_bak_20261109;`（及各子表备份表；新列在备份表中为缺省 NULL）
- 后端：`backend/releases/` 上一版 jar 蓝绿回切（旧代码忽略新列，无需回滚 DDL）
- 前端：`scripts/rollback-frontend.ps1`，旧 release 完整保留

## 6. 已知口径（Owner 已确认或默认）

1. 团队为固定三部门枚举（DEPT1/2/3），未做成可配置字典。
2. 表单保留「类型 + 阶段 + 执行人」：类型决定阶段流程（子功能依赖），执行人承担阶段推进授权（负责人+执行人），不属于新字段但必须保留才能不破坏子功能。
3. 会议城市存 `supplier_city.id`；展示为「省份+市名」（直辖市只显示市名）。
4. 「已取消/软删除」机制、行级可见性权限、导出留痕均不变。
