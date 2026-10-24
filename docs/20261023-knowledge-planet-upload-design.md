---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_0bc3d776cebd11f19190525400de85a5
    ReservedCode1: frejqzvNDaI5Brt8ZKrrY6tCAAK4+N8ho2ktw7mtkWrA75Sx0ydtMgUJtYxjcFTAgNzdJtaAhDrJzkuUhNO85ixGtYaZFbqCiXn5PRG4KSGF77XgLaztrcX+mMzRzDrvZvLSqlBgE5qgnVJzX0RVbt8qTjE8aY7REZoP+5DH3Omzlhcm7mBDQVkgWOI=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_0bc3d776cebd11f19190525400de85a5
    ReservedCode2: frejqzvNDaI5Brt8ZKrrY6tCAAK4+N8ho2ktw7mtkWrA75Sx0ydtMgUJtYxjcFTAgNzdJtaAhDrJzkuUhNO85ixGtYaZFbqCiXn5PRG4KSGF77XgLaztrcX+mMzRzDrvZvLSqlBgE5qgnVJzX0RVbt8qTjE8aY7REZoP+5DH3Omzlhcm7mBDQVkgWOI=
---

# 知识星球上传上移与分级展示 — 设计文档

- 日期：2026-10-23
- 范围：jshz-PMO 前端（Vue3）+ 后端（Spring Boot3）小改
- 状态：已获用户确认，待实现

## 背景

知识星球内容管理一期（2026-10-23 上午）已上线：4 个二级菜单（会议执行 / 协会合规要求 / 企业合规要求 / 客户喜好）均可上传/浏览/检索/更新/下架文档。

上线后用户反馈两个问题：
1. 各二级菜单的上传对话框里虽可"选择分类"，但提交时实际写死为当前页面分类（`formData.append('category', props.category)`），分类选择不起效，易产生误传。
2. 期望统一入口：上传放在"知识星球"父级界面，下属二级菜单只做展示、下载、修改。

## 目标

- 父级"知识星球"页 = 上传中心 + 全部分类文档总览（可检索、可管理）。
- 4 个二级菜单 = 本分类列表展示、下载/预览、更新（新版本）、版本历史、下架（管理员），移除上传入口。
- 权限展示分级：管理员（含超级管理员）可见已下架文档；普通员工不可见下架记录。
- 后端仅小改：列表接口 `category` 参数放宽为可选 + 查询按权限过滤 `status`。

## 页面与交互设计

### 父页 `/knowledge-planet`（新增 KnowledgePlanetHome.vue）

- 路由：父路由 `component` 挂载本页，移除原 `redirect`（不再自动跳第一个子菜单）。
- 上传区：分类下拉（4 选 1，必选）+ 标题输入 + 文件上传（pdf/doc/docx/jpg/jpeg/png，≤50MB），提交现有 `POST /api/knowledge/docs`，分类来自表单选择（真实生效）。
- 总览区：跨全分类混排列表（`GET /api/knowledge/docs` 不传 category），关键词检索；列：标题、分类（中文名）、版本、上传人、更新时间、状态；行操作：下载/预览、更新、版本历史、下架（管理员，`v-permission="'knowledge:manage'"`）。
- 管理员视角列表含已下架文档（状态列标"已下架"）；普通员工接口即不返回下架记录。

### 子页（MeetingExecution / AssociationCompliance / EnterpriseCompliance / CustomerPreferences）

- 复用 `KnowledgeDocList.vue`，新增 `allowUpload` prop 传 `false`，隐藏"上传文档"按钮。
- `category` prop 传各自分类（保持现状），列表只展示本分类。
- 保留：检索、下载/预览、更新、版本历史、下架（管理员）。

## 后端设计（小改）

### `GET /api/knowledge/docs`

- `category` 参数改为可选：不传 = 查询全部分类；传 = 按分类过滤（原逻辑）。
- 查询按权限过滤 `status`：
  - `authUser.hasPermission("knowledge:manage")` 为 true（管理员/超级管理员）→ 返回上架 + 下架文档；
  - 普通员工 → 仅返回 `status=1` 上架文档。
- 其余接口（上传/更新/下架/版本历史/文件流）不变。

## 数据流

- 上传：父页表单 → POST /api/knowledge/docs（category 来自下拉）→ 落盘 `<LOCAL_PATH>` + 主表/版本表。
- 浏览：父页不传 category 查全部；子页传 category 查本类；后端按权限过滤 status。
- 更新：PUT /api/knowledge/docs/{id} → version+1 + 版本历史追加。
- 下架：DELETE（管理员）→ status=0 → 普通员工列表立即不可见，管理员仍可见。

## 错误处理

- 上传：分类未选 / 标题为空 / 超 50MB / 扩展名不符 → 前端校验提示 + 后端 BusinessException。
- 下载：文件不存在 → 404 提示。
- 下架无权限 → 403（已有机制）。

## 验证方案

1. 代码抽查：父页组件真实挂载路由；子页"上传文档"按钮已移除；后端 category 可选与 status 权限过滤落盘。
2. 构建：`npm run build` 生成新 chunk；`mvn package` 成功。
3. 服务：重启 ExpertSystemBackend（后端 jar）与 ExpertSystemNginx（前端 dist）。
4. 双账号实测：王创（普通员工）列表无下架文档、无上传按钮；jshz（管理员）可见下架文档、可下架。

## 文档同步

- `docs/architecture.md`、`AGENTS.md`：更新页面/路由/接口描述。
- `docs/release-notes-v1.0.3.md` 或新增补丁说明：记录本次调整。

## 回滚

- 后端：还原 KnowledgeDocumentService 查询与 Controller 参数声明，重新打包部署。
- 前端：路由还原 redirect、子页恢复上传按钮、删除父页组件，重新构建部署。
*（内容由AI生成，仅供参考）*
