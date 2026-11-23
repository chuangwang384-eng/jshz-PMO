# 1.0.132 专家库：身份证有效期 + 资料预览修复 + 批量全选（2026-11-23）

## 范围
专家库模块。合并上一轮已提交未发布的 P1/P2 加固（282851c）。后端蓝绿 + 前端 graceful（`1.0.132-expert-idcard-expiry`）。

## 数据库
- `expert_info` 新增 `id_card_expiry VARCHAR(50) NULL`（迁移 `database/migration/20261123_expert_id_card_expiry.sql`，幂等；已并入 init.sql 基线，check-schema-parity 前后均通过）。

## 新功能
1. **身份证有效期字段**：专家卡片（meta 行）、列表视图列、详情/新增/管理员新增表单、Excel 导出、审批快照均带该字段；审批白名单加入「身份证有效期」。
2. **反面上传自动提取**：上传身份证反面后本机 PP-OCRv5 识别「有效期限」并归一为 `YYYY.MM.DD-YYYY.MM.DD` / `YYYY.MM.DD-长期` 自动填入。触发点：管理员直写新增/修改（multipart）、审批通过照片归档后（新增/修改两路）。提取失败保留原值不阻断；仅本次上传反面才触发。新增 `IdCardExpiryExtractor`（解析与 OCR 解耦，7 个单测）+ `PhotoStorageService.photoPath`（防穿越读取）。
3. **资料缩略图预览**：图片资料用 PhotoImage（新增 doc 模式）显示缩略图并可放大；PDF 显示徽标。
4. **批量简历全选**：BatchResumeDialog 工具栏「全选/取消全选」，仅作用于可提交卡。

## Bug 修复
- **资料点击报错（401/500 Whitelabel 页）**：双重根因——① 3 个历史资料文件误落在 `temp\专家资料库`（上传时进程 STORAGE_ROOT 异常），服务映射目录为空 → 已哈希校验归位到 `专家资料库`；② 裸 `<a target=_blank>` 打开失败直接暴露后端错误页 → 改为鉴权 blob 请求 + 新窗口展示，失败友好提示。
- 合并 282851c：专家删除/换照片事务化与文件提交后删除、前端 blob 泄漏/竞态/分页/静默失败等（详见该交接记录）。

## 验证
- `mvn test`：241 通过 / 0 失败 / 16 跳过（含新增 IdCardExpiryExtractorTest 7 例）。
- **OCR 真实图片端到端**：临时 JUnit 读取艾宁身份证反面原图 → PP-OCRv5 → 正确提取 `2007.11.28-2027.11.28`（跑完即删临时测试）。
- 生产接口：`/api/files-doc/expertdoc_524_*.png` 带登录 cookie 返回 200（478610 字节，修复前 500/401）。
- 浏览器实测（测试账号）：v1.0.132 生效；20 张卡片全部含有效期 meta；专家详情资料缩略图渲染、文件名为可点击按钮；编辑表单含有效期输入框；页面内鉴权 fetch 资料 200 image/png。
- `npm run build` 通过，is-not-exported=0。
- 批量全选按钮：构建+代码核验（IAB 不支持文件上传，未做真实上传流程实测）。
- window.open(blob:) 在 IAB 被拦属已知工具限制，真实浏览器为标准行为（与串场预览同模式）。

## 重要事实更正
初审查 S1「后端写操作零权限校验」结论有误：`DbPermissionFilter` 按 permission 表规则对专家写端点强制 expert:add/edit/delete（测试账号直调 POST 实测 403）。决策记录已补更正章节。

## 回滚
后端回上一归档版本 + 前端 rollback-frontend.ps1 回 1.0.131-dashboard-ux。数据库新增列为可空 additive，回滚代码无需删列。
