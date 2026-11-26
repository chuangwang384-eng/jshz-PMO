---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_208ad186d10711f1814c525400cd780f
    ReservedCode1: EQYefZSBtujojpCuXhtHPOqFb1KL78htgSIfCjmwXTfqisd/aRVOXNuYZ36+jkyozC9rWXjjpoDhFqSl5eQWV0uc5eJWz9kpGwHv+gpUWgQ59wO3ZNd4QqlB1lKKUV/c5FzY7jy+XOBESF+IomXCn/QC2kImtZmvqczw2k9RhzRdEliqe2biSoaMT/Y=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_208ad186d10711f1814c525400cd780f
    ReservedCode2: EQYefZSBtujojpCuXhtHPOqFb1KL78htgSIfCjmwXTfqisd/aRVOXNuYZ36+jkyozC9rWXjjpoDhFqSl5eQWV0uc5eJWz9kpGwHv+gpUWgQ59wO3ZNd4QqlB1lKKUV/c5FzY7jy+XOBESF+IomXCn/QC2kImtZmvqczw2k9RhzRdEliqe2biSoaMT/Y=
---

# 知识星球「文档格式展示」改造设计

- 日期：2026-10-26
- 项目：jshz-PMO（景帅会展内网专家库系统）
- 状态：已确认（用户评审通过）
- 技术栈：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus + Vue 3.4 + Vite 5 + Element Plus + MySQL 8

## 一、目标与范围

将「知识星球」父页及其 4 个子菜单页（会议执行 / 协会合规要求 / 企业合规要求 / 客户喜好）从纯表格展示升级为**文档格式化展示**：

1. 列表以文件形态呈现：格式徽标一目了然、文档名带后缀
2. 支持按格式筛选（全部 / PDF / DOCX / DOC / XLSX / PPTX / 图片）
3. PDF / DOC / DOCX / XLS / XLSX / PPT / PPTX / 图片全部**页面内嵌预览**，不再跳新窗口
4. 上传白名单扩展至全套 Office 格式（xls / xlsx / ppt / pptx）

**保留不变**：检索、分页、收藏、版本历史、下载计数、上/下架管理、权限控制（`knowledge:manage` 过滤逻辑）。

## 二、现状核查结论（事实依据）

| 项目 | 现状 | 缺口 |
|---|---|---|
| 文件流接口 | 已有 `GET /api/knowledge/docs/files/{fileName}`（inline 预览 / download=1 下载，含下载计数与防路径穿越） | 无 |
| file_type 字段 | `knowledge_document.file_type` 存小写扩展名（pdf/doc/docx/jpg/png 等），上传时 `extensionOf()` 截取 | 前端列表未展示该字段 |
| 文件存储 | `<LOCAL_PATH>`，命名 `doc_<毫秒时间戳>_<原文件名>`，`file_path` 存存储文件名 | 无 |
| 列表接口 | `GET /api/knowledge/docs?category=&keyword=&page=&pageSize=` | 无 `fileType` 过滤参数 |
| 上传白名单 | `KnowledgeDocStorageService.ALLOWED_EXT` = pdf/doc/docx/jpg/jpeg/png，≤50MB | 缺 xls/xlsx/ppt/pptx |
| 前端预览 | `handlePreview(row)` 拉 Blob + `window.open` 新窗口；**无内嵌预览组件** | 需内嵌阅读器 |
| 前端列表 | `KnowledgeDocList.vue` / `KnowledgePlanetHome.vue` 均为 el-table，无格式列 | 需新增格式徽标列 |

## 三、展示层设计（前端）

改造集中在 `frontend/src/components/KnowledgeDocList.vue` 与 `frontend/src/views/KnowledgePlanetHome.vue`（4 个子页均复用 `KnowledgeDocList.vue`，改动一处全页面生效）：

1. **新增「格式」列**：彩色徽标展示 `file_type`（PDF 红 / DOCX 蓝 / XLSX 橙 / PPTX 紫 / 图片绿 / DOC 灰蓝），无需后端新字段
2. **文档名称列**：显示 `title` + 弱化灰显的真实后缀（如 `会议执行操作手册.pdf`）
3. **顶部格式筛选标签栏**：`全部 | PDF | DOCX | DOC | XLSX | PPTX | 图片`，点击后请求带 `fileType` 参数的列表，与分类（子页固定）、关键词检索可组合
4. 其余列（分类 / 版本 / 上传人 / 下载次数 / 更新时间 / 状态 / 操作）与交互保持不变

## 四、内嵌预览设计

点击「预览」后，页面内弹出抽屉式阅读器（替换 `window.open` 新窗口方式）。

| 格式 | 渲染方式 |
|---|---|
| PDF | 前端 pdf.js 渲染（分页、缩放、页码） |
| 图片（jpg/jpeg/png） | 直接 `<img>` 展示 |
| DOCX / XLSX / PPTX | 后端统一转 PDF 后由 pdf.js 渲染 |
| DOC / XLS / PPT（老格式） | 后端转 PDF 后渲染（老格式前端无法可靠解析） |

阅读器内含：下载按钮、翻页/缩放控件、关闭按钮。

**转换缓存策略**：转换结果按「文档 id + 版本号」缓存至 `知识文档库/_preview/` 目录，避免每次打开重复转换；文档更新（版本 +1）或删除时同步清理对应缓存。

**技术选型说明**：曾评估前端直接渲染方案（docx-preview / SheetJS / PPTX 渲染库），需维护多个前端库且 PPTX 前端渲染质量普遍一般；统一后端转 PDF 可保证格式保真与一致性，一套渲染代码。**选定：后端 LibreOffice 统一转换。**

## 五、后端设计

1. **格式筛选**：`KnowledgeController` / `KnowledgeDocumentService.pageList()` 增加 `fileType` 参数，对 `file_type` 字段做等值过滤（与 category、keyword 组合），前端 `api/knowledge.js` 同步增加参数
2. **上传白名单扩展**：`KnowledgeDocStorageService.ALLOWED_EXT` 增加 `xls / xlsx / ppt / pptx`，大小上限 50MB 不变
3. **Office 转 PDF 服务**：新增 `DocPreviewService`：
   - 用 `ProcessBuilder` 调用本机 LibreOffice headless：`soffice --headless --convert-to pdf --outdir <缓存目录> <源文件>`
   - 输出文件名按文档 id + 版本管理，避免并发冲突
   - 首次转换后写缓存，文档更新/删除时清理对应缓存
   - 转换失败/超时抛出明确异常，由控制器转为「暂无法在线预览」提示
4. **预览接口**：复用 `GET /api/knowledge/docs/files/{fileName}` 的 inline 模式；对 Office 文档，先经 `DocPreviewService` 产出 PDF 缓存，再返回 PDF 文件流；PDF/图片直接返回原文件流
5. **部署前置**：后端所在机器安装 **LibreOffice**（免费开源；内网可离线安装）。`application.yml` 增加 `expert.knowledge.preview-dir`（默认 `知识文档库/_preview`）与 `expert.knowledge.libreoffice-path`（默认 `soffice`）

## 六、数据流

```
列表页 → 格式筛选(fileType) + 关键词(keyword) + 分类(category) → GET /api/knowledge/docs
预览点击 → 判断 file_type
   ├─ PDF / 图片 → 直接拉文件流 inline 预览
   └─ Office → 检查缓存 → 无则调用 soffice 转换 → 返回 PDF 流
下载 / 收藏 / 版本 / 下架 / 删除 → 走现有接口，逻辑不变
```

## 七、错误处理

- 文件不存在 → 提示「文档文件已缺失」，引导联系管理员
- 转换失败 / 超时 → 提示「暂无法在线预览，请下载查看」，下载按钮始终可用
- 首次转换等待 → 前端显示加载态；命中缓存后秒开
- 白名单外格式上传 → 沿用现有拦截逻辑

## 八、测试与验收

1. 格式筛选与关键词、分类组合查询结果正确
2. PDF、DOCX、XLSX、PPTX、DOC、图片各上传 1 份，逐一验证内嵌预览正常
3. 老格式（DOC/XLS/PPT）转换预览正确、缓存命中、二次打开秒开
4. 新增格式（xlsx/pptx/ppt/xls）上传成功，白名单外格式被拦截
5. 回归：检索、分页、收藏、版本历史、下载计数、上/下架、权限过滤不回归
6. 前端 `npm run build` 后重启 `ExpertSystemNginx` 生效（注意多次构建覆盖 dist 的踩坑：所有改动完成后做最后一次 build 再重启）
7. 按 `AGENTS.md` 同步更新项目文档

## 九、改造面清单

| 文件 | 改动 |
|---|---|
| `frontend/src/components/KnowledgeDocList.vue` | 新增格式徽标列、文件名带后缀、格式筛选标签、内嵌预览抽屉 |
| `frontend/src/views/KnowledgePlanetHome.vue` | 同步格式筛选标签、内嵌预览接入 |
| `frontend/src/api/knowledge.js` | 列表接口增加 fileType 参数、预览接口调整 |
| `backend/.../KnowledgeController.java` | 列表接口接收 fileType；预览接口对 Office 走转换 |
| `backend/.../KnowledgeDocumentService.java` | pageList 增加 file_type 过滤 |
| `backend/.../KnowledgeDocStorageService.java` | ALLOWED_EXT 扩展 |
| `backend/.../DocPreviewService.java`（新增） | LibreOffice 转换 + 缓存管理 |
| `backend/.../application.yml` | 预览目录、LibreOffice 路径配置 |
| `knowledge_document` 表 | 无需变更（file_type 已存在） |
*（内容由AI生成，仅供参考）*
