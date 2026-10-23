---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_281799eaceb611f19bf2525400cd780f
    ReservedCode1: +VJGbialPwOgQPRBvh6aSUcwFe5Yh6RM7ZdyYokHoxbWjhHqB1L7CB7PFXjBLAv0WC+Yer5NtFXH27jug4yBUG0TL5PDe1+t8Xn9CqUIju8oAodwD/+VVpYebWBzNqViF7y9bwvkKWxFplRil6RfreZl2T1At9/Xmj9kfKz0C2aT1Vd7aDaVJYtRBII=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_281799eaceb611f19bf2525400cd780f
    ReservedCode2: +VJGbialPwOgQPRBvh6aSUcwFe5Yh6RM7ZdyYokHoxbWjhHqB1L7CB7PFXjBLAv0WC+Yer5NtFXH27jug4yBUG0TL5PDe1+t8Xn9CqUIju8oAodwD/+VVpYebWBzNqViF7y9bwvkKWxFplRil6RfreZl2T1At9/Xmj9kfKz0C2aT1Vd7aDaVJYtRBII=
---

# 发布说明 v1.0.3（jshz-PMO 专家库系统 · CSV 转 Excel）

- 发布日期：2026-10-23
- 标签：`v1.0.3`
- 基线：v1.0.2（RBAC 升级 + 审批流）+ 知识星球内容管理（12 表 / 11 Controller）
- 系统：景帅会展内部管理系统 · 专家库子系统（内网部署）

---

## 一、新增功能（New Features）

1. **导出/模板/导入全面改为 Excel（.xlsx）**：专家库所有 CSV 相关功能迁移至 Excel 格式，后续以 Excel 为准。
   - 全量导出：`GET /api/experts/export` 返回 `experts_all.xlsx`（18 列表头，含完整敏感字段，写审计 `full_xlsx`）。
   - 选中导出：`GET /api/experts/selected/export` 返回 `experts_selected_<时间戳>.xlsx`（写审计 `selected_xlsx`）。
   - 模板下载：`GET /api/experts/template` 返回 `import_template.xlsx`（含表头，写审计 `template_xlsx`）。
   - 批量导入：`POST /api/experts/import` 接受 `.xlsx` 文件，字段/校验逻辑与历史 CSV 导入完全一致（姓名重复拒绝、setUpdateBy 保留、数字/日期列按文本宽松读取）。
   - ZIP 打包：`/api/export`、`/api/export/full`、`/api/experts/selected/zip` 内数据文件由 `experts.csv` 改为 `experts.xlsx`。
2. **Excel 处理库引入**：`backend/pom.xml` 新增 Apache POI `poi-ooxml:5.4.1`（选定原因：本地 m2 仓库已缓存 POI 5.4.1 及全部传递依赖；EasyExcel 3.3.x 底层为 POI 3.17，与 JDK 17 / Spring Boot 3.2.5 存在兼容性风险）。
3. **物理 CSV 备份转 xlsx**：`backup/sys_user_backup_20261021.csv` 已生成同目录 `sys_user_backup_20261021.xlsx` 副本（150 行），原 CSV 保留不删。

## 二、改进（Improvements）

1. `download_log.download_type`（VARCHAR(30)，非 ENUM）：新增 `selected_xlsx / full_xlsx / template_xlsx` 取值，历史 `selected_csv / full_csv / full_zip / selected_zip / md / md_full / employee_zip` 保持不变，无需 ALTER。
2. `DownloadAuditService` / `DownloadLog` 注释同步 xlsx 类型；前端 `DownloadAudit.vue` 类型映射新增 xlsx 文案并保留历史 CSV 兼容展示。
3. `ExportCompatController`（`/api/export`、`/api/export/full`、`/api/export/md`）ZIP 内数据文件同步为 `experts.xlsx`。
4. 前端 `ExpertList.vue` / `ExpertManage.vue`：按钮文案、`accept` 属性、API 函数（`exportAllExcel` / `exportSelectedExcel` / `downloadTemplate` / `importExperts`）全部改为 Excel；`downloadFile` 依赖响应头 Content-Disposition 文件名解析，自动适配 `.xlsx`，无需改动下载逻辑。
5. 文档同步：`docs/`（正式版设计文档、内网数据查询接口使用说明、SQLite 升级文档、UI 精简文档、正式版部署需求）与 `demo/README-demo.md` 中 CSV 导入/导出/模板描述全部更新为 Excel（.xlsx）。

## 三、兼容性说明（Compatibility）

1. 历史下载审计数据（`selected_csv` / `full_csv` 等）仍可正常展示，不做数据迁移。
2. 历史 CSV 导入模板不再作为正式模板下发；如存量用户仍持 CSV 数据，需按 `import_template.xlsx` 表头整理后导入。
3. `backup/sys_user_backup_20261021.csv` 为历史备份保留，后续备份以 Excel 为准。

## 四、构建与部署（Build & Deploy）

- 后端：`mvn package`（或 `mvn clean package -DskipTests`），产物 `backend/target/*.jar`。
- 前端：`npm run build`，产物 `frontend/dist/`。
- 服务重启：NSSM 服务 `ExpertSystemBackend`（`nssm restart ExpertSystemBackend`）。

## 五、回滚方案（Rollback）

- 代码回滚：`git checkout v1.0.2`（或 `git reset --hard <v1.0.2 commit>`），重新 `mvn package` + `npm run build`，重启服务。
- 数据库无需回滚（download_type 为字符串列，新值为增量写入）。
- 物理副本 `backup/sys_user_backup_20261021.xlsx` 可手动删除（不影响系统运行）。

---

## 六、补充调整（2026-10-23 下午）：知识星球上传上移与分级展示

> 设计文档：`docs/20261023-knowledge-planet-upload-design.md`（已确认存档）。本调整在 v1.0.3 基础上增量实施，不改变表结构与 Controller 数量。

### 变更内容

1. **前端新增父页 `KnowledgePlanetHome.vue`**：作为 `/knowledge-planet` 父路由实际页面（原 redirect 已移除），包含
   - 上传中心：分类下拉（4 选 1，必选）+ 标题输入 + 文件上传（pdf/doc/docx/jpg/jpeg/png，≤50MB），提交 `POST /api/knowledge/docs` 时 `category` 取表单所选值（修复原各子页上传分类写死为当前页面的问题）；
   - 全分类文档总览：`GET /api/knowledge/docs` 不传 `category`，列：标题 / 分类（中文名）/ 版本 / 上传人 / 更新时间 / 状态；操作：下载/预览、更新、版本历史、下架（管理员，`v-permission` 对应权限码 `knowledge:manage`）。
2. **子页移除上传入口**：`KnowledgeDocList.vue` 新增 `allowUpload` prop（默认 true）；四个子页 `MeetingExecution / AssociationCompliance / EnterpriseCompliance / CustomerPreferences` 传入 `allow-upload="false"` 隐藏"上传文档"按钮，保留检索 / 下载预览 / 更新 / 版本历史 / 下架。
3. **后端 `GET /api/knowledge/docs` 放宽与分级**：
   - `category` 参数由必填改为可选（不传 = 全部分类，传 = 按分类过滤）；
   - 按权限过滤 `status`：`authUser.hasPermission("knowledge:manage")` 为 true 返回上架+下架；普通员工仅返回 `status=1` 上架文档；
   - 其余接口（上传/更新/下架/版本历史/文件流）不变。

### 构建与部署

- 前端：`npm run build`（已通过，生成 `KnowledgePlanetHome-*.js` 新 chunk）。
- 后端：`mvn package`（已通过 `mvn compile`；完整打包因 `ExpertSystemBackend` 服务运行中锁定 target jar，需先停服后执行 `mvn clean package -DskipTests`）。
- 服务重启：`nssm restart ExpertSystemBackend`（后端）+ Nginx 部署 `frontend/dist`（前端），由主 Agent 安排。

### 回滚

- 后端：还原 `KnowledgeDocumentService.pageList` 查询（category 必填、无 status 过滤）与 `KnowledgeController.list` 参数声明，重新打包部署。
- 前端：路由还原 `redirect: '/knowledge-planet/meeting-execution'`、子页恢复上传按钮、删除 `KnowledgePlanetHome.vue`，重新构建部署。

*（内容由AI生成，仅供参考）*
