# 发布记录：项目恢复/成员级编辑 + 日程解析 Excel 支持/Word 提准（1.0.37.32）

- 日期：2026-11-10
- 状态：**已发布生产（12:08，后端蓝绿 R-1 发布完成 + 前端 graceful，无迁移），发布后核验通过；页面真机验收待 Owner**
- 类型：功能迭代，**无数据库迁移**

## 1. 取证（Owner 两份样例文件）

### xlsx《5月13日第一届新长征军旅肿瘤论坛日程5.9版本(1)》
- 表头「时间/讲题/讲者/主持」——现有列映射直接可用；讲者含「王红阳 院士」（院士此前不在头衔表会丢名）；讨论行名单顿号分隔在讲者列；段首合并横幅「上半场：庚续辉煌 大会主席：胡毅 秦叔逵 王宝成 朱波」（8 位主席团在横幅里）；「大会主席」占位行无需出人。

### docx《【整合日程】大医万里行 HER2…巡讲》
- 单张表「时间/内容/讲者/主持」；**合并单元格使讨论名单只存在于内容列**（「真知灼见:冯利国、郝小强…」无头衔、顿号分隔）；改造前基线解析 11 人（讨论段全漏），原因是 topic 提名门槛要求"带头衔词"。

## 2. 实际变更

### 项目恢复（Owner 口径：仅 CANCELED 可恢复，权限与取消对称）
- `ProjectService.restore()`：仅 CANCELED、isOwnerOrCreator；置 ACTIVE，**阶段保持不变**，cancelReason/cancelTime 保留留痕，写操作日志。
- `ProjectController`：`PUT /project/{id}/restore`；detail 返回新增 `canRestore`。
- 前端：ProjectDetail「更多操作」加「恢复项目」（确认弹窗）；ProjectList 行操作加「恢复」（仅 CANCELED 行），取消按钮在已取消行隐藏。

### 项目基础信息成员级编辑
- `ProjectService.update()`：requireManage → `requireOperable`（负责人/创建人/协助人，与日程上传同口径）；守卫：携带 ownerId/assistantIds 时仍须负责人/创建人/admin（防协助人越权改人员结构），否则提示"仅负责人/创建人可修改项目人员"。
- detail 新增 `canEditBasic`；前端「编辑项目」按钮 `canManage || canEditBasic`。ProjectList 编辑按钮维持 project:manage 口径不变。

### 日程解析 Excel 支持（xlsx+xls，WorkbookFactory 统一）
- 白名单/报错文案/前端 accept（日程上传处）加 xlsx/xls；`extractExcel()`：遍历全部 sheet、**纵向合并向下填充**（时间列合并续行），**横向合并不复制**（段首横幅复制会灌进讲者列产生假人名——xlsx 实证「再谱华章」，横幅人名由 plainText 页脚兜底接管）；cell 经 DataFormatter 格式化；空行跳过。
- `TITLE_SUFFIXES` 增加「院士」。

### Word 解析提准
- topic 提名门槛放宽：带头衔词 **或 token 判名 ≥2 人**（顿号名单强信号）→ 大医万里行讨论段从漏抓到全量提取。
- `extractDocx` 递归收集嵌套表格（cell.getTables()）并跳过全空行。
- 新增 `extractDoc()`：.doc 旧格式优先 HWPF TableIterator 提取表格（此前只取纯文本致表格型 .doc 全漏），失败回退纯文本。

## 3. 验证（实测）

| 验证项 | 结果 |
| --- | --- |
| docx 大医万里行（当前实现离线解析） | **候选 11→26 人**：4 段"真知灼见"讨论名单全量提取（冯利国/郝小强/武帅 等，含「武  帅」内嵌空格拆字合并），角色/时间全对 |
| xlsx 新长征（当前实现离线解析） | **候选 76 人**：讲者/主持/讨论全对（王红阳 院士 等头衔剥离正常）、8 位主席团 CHAIR、假名（再谱华章/庚续辉煌）0 |
| 解析单测 | 全量通过（新增 docx 合并讨论名单回放用例） |
| 后端全量 mvn test | 全绿 EXIT=0 |
| 前端构建 | 27.3s，is-not-exported=0 |

## 3.1 发布执行与发布后核验（12:06–12:09 实测）

- `temp\deploy-13732.cmd`：后端 R-1 11 阶段蓝绿（影子 8081 健康 10×6s 探测全过 → 稳态化 → 8080 新版本 UP → **R-1 发布完成**，日志 `logs/deploy-backend.20261110_120649.log`）；前端 graceful（ReleaseId `1.0.37.32-restore-edit-excel-word-20261110`，DEPLOY_EXIT=0）。
- 核验：`/health` 直连+经 Nginx 均 **1.0.37.32 UP**（buildTime=12:02:49、gitShort=a417c51 与制品一致）；root 已切新 release 且线上 index 主 bundle=磁盘哈希（index-BycwhgtW.js）；SPA 200；`PUT /project/1/restore` 未登录 401（新端点已注册且鉴权未放松）。

## 4. 回滚
revert 提交即可；无迁移、无配置变更。

## 5. 遗留
- 「大会主席团」整行多人（xlsx R3）未转 CHAIR 候选（非"大会主席："格式）；如需可下轮加规则。
- 图片/扫描 PDF 日程仍受 Tesseract 限制。
