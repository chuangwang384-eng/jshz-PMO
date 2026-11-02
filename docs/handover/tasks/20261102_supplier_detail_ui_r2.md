# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261102-supplier-detail-ui-r2
- 任务名称：供应商详情页 UI 重构 R2（左栏收窄 + 员工图片/价格权限放开 + 北京市测试数据）
- 主责 Agent：ZCode（GLM，AI 生成+人工确认）
- 交接时间：2026-11-02 12:40
- 当前状态：已完成待验收
- 优先级：P1
- 版本：**版本号不变（前端仍 1.0.20，后端 jar 未动仅重启）**，按需求人要求

## 2. 目标与验收条件
1. 左栏收窄到信息全部展示（fit-content，上限 400px，值列 230px）；实测 346px、无横向滚动
2. 普通员工可：图片上传、图片改名、历史价格新增/编辑；不可：设主图/删图/编辑删除供应商/评价提交（维持 supplier:manage 仅 ADMIN）
3. 北京市新增全部 6 类型测试供应商（含网络图片）

## 3/5. 基线与变更
- 分支 feature/project-optimize；前端 1.0.20 重构建两次发布（dist_old.20261102_123218 为回滚点）
- 新增迁移：database/migration/20261102_supplier_employee_price_image_open.sql
  - 规则改造（同 20261101 先例，防 id 顺序先命中）：POST */images、PUT */images/*/name → **supplier:image**；PUT */prices → **supplier:price**
  - 两码绑定全部非超管角色；**执行后已重启 ExpertSystemBackend**（规则有进程内缓存）
- 前端：SupplierDetail.vue 左栏 fit-content、上传按钮/改名按钮 → supplier:image、价格三处按钮 → supplier:price、图片卡片标题不换行
- 后端零代码改动（DbPermissionFilter 数据驱动）；数据库仅 permission/role_permission 数据变更

## 6. 测试与验证
| 项 | 结果 | 证据 |
| --- | --- | --- |
| 左栏宽度 | 通过 | evaluate leftW=346px、overflow=false |
| 员工上传图片 | 通过 | API 200（临时 EMPLOYEE 账号） |
| 员工改名 | 通过 | API 200（中文载荷 UTF-8 文件） |
| 员工保存价格 | 通过 | API 200 |
| 员工设主图/删图 | 通过（应403） | API 403 |
| 员工编辑/删除供应商按钮隐藏 | 通过 | DOM 无"编辑"按钮 |
| 员工图片操作层仅"改名" | 通过 | DOM hoverBtns=["改名"] |
| 同城列表 | 通过 | 酒店14：共 8 家全类型，绑定对端置顶带「已绑定」 |
| 测试数据 | 完成 | supplier id 14-19 六类型各 1 家（北京 cityId=1），图片 15 张 |
| ADMIN 回归 | 通过 | admin 清理价格 200（20261101 规则未受影响） |

## 7-8. 测试数据与风险
- **保留的测试数据（需求人要求注入）**：北京市 6 家供应商（北京王府井宴会酒店/北京朝阳快印工场/北京海淀视界传媒/北京丰台光影舞美设备/北京首旅商务车队/北京国旅票务中心，id 14-19）+ 15 张 picsum 占位图 + 酒店↔打印店绑定 1 条；测试账号/临时评价已清理
- 图片说明：Wikimedia/loremflickr 在本机网络不可达，改用 picsum.photos 固定 seed 占位图（**非严格场景匹配**）；如需场景图可后续手动替换
- 风险：supplier:price 为全量替换接口，放开后普通员工可覆盖全部价格数据——已按需求人明确要求执行
- 回滚：迁移 SQL 尾部回滚说明 + `scripts/rollback-frontend.ps1`（归档 1.0.20-20261102_123218 之前版本）

## 9. 文档同步
- [x] 本交接记录 + README 摘要；迁移 SQL 自含说明
- [ ] docs/deployment.md 无需更新（后端 jar 未发布，仅服务重启；如需记录可在 R-1 下补一行）

## 10. 下一位 Agent 指令
1. 检查：`git log --oneline -3`；`SELECT code FROM permission WHERE path='/api/supplier/*/prices'` 应为 supplier:price
2. 需求人复核三项改动；测试数据（14-19）处置由需求人决定（保留演示/删除）
3. 未经确认禁止：回滚迁移、删除测试供应商

## R2.1 追加（2026-11-02 12:50，需求人截图反馈）
- 左栏由 fit-content 改固定 **272px**（信息全部展示宽度），余量匀给中栏（flex:1 实测 405px+）
- 验收：DOM 实测 leftW=272/midW=405、无横向溢出、员工视角截图确认；临时验证账号已清理
- 提交 1d1759b；前端重新发布（版本不变 1.0.20）
