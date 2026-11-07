# 2026-11-07 新增专家·简历解析发布（1.0.37，后端+前端）

状态：**COMPLETE**（后端蓝绿 + 前端 graceful，接口实测通过，无数据库变更）。

## 变更内容
按 [需求 v1.1](../requirements/2026-11-07-新增专家简历解析-需求文档.md)（Owner 确认：复用本机 Tesseract OCR、头像=候选+确认、范围=姓名/单位/头像+顺带命中科室/职称/联系方式）：

**后端**：
- 新增 `ResumeParseService`：格式分发（pdf/doc/docx/ppt/pptx/jpg/png）→ 文本（PDF 文本层不足时栅格化前 3 页回退 Tesseract OCR）→ 多策略字段解析（标签行 + 机构/科室/职称词表 + 首行人名启发式 + 手机/邮箱正则）→ 头像候选（内嵌图片启发式过滤，缩至 ≤600px JPEG base64，最多 3 张）。
- `POST /api/experts/resume/parse`：登录即可；格式白名单 + ≤20MB；非法 400；审计记动作/文件名/命中数（不含简历内容）；简历原文不落盘。
- `KnowledgeDocTextExtractor.ocrPage` private→public（签名不变）；单测 `ResumeParseServiceTest` 7 用例通过。

**前端**（新增专家需审批弹窗）：
- 顶部「简历解析」区：选择简历 → 开始解析 → 空字段自动填充（不覆盖已输入）+ 字段「来自简历」绿色标记 + 头像候选缩略图「使用此照片」（base64→File 走既有临时照片链路）；失败/超时红字提示不阻塞手动填写。

## 发布
- build-all 隔离构建 → deploy-1.0.37-wrapper.ps1（UAC）→ 蓝绿全阶段 OK，/health=1.0.37 UP；前端 graceful，ReleaseId `1.0.37-resume-parse-20261107`，PID 39504 不变。

## 接口实测（测试账号）
- **docx**（构造样例：姓名/单位/科室/职称/电话/邮箱）：5 字段全部命中（张晓丽/山东省立第三医院/教育培训部…/主任药师/13812345678）。
- **png 图片简历**（JDK 绘制中文字样例）：OCR 链路跑通——命中联系方式 13812345678，头像候选返回整图 base64；姓名/单位因 OCR 行内空格未命中启发式（如实记录，属候选+人工确认设计预期）。
- **格式拒绝**：非白名单文件 400「不支持的简历格式（支持 pdf/doc/docx/ppt/pptx/jpg/png）」。
- UI 级上传流程未自动化（IAB 不支持文件选择器），接口+前端构建验证；**弹窗实际操作请人工过一遍**。

## 代码入库说明（披露）
随本发布补齐 1.0.34 已部署未入库的知识解析依赖闭包：KnowledgeController（M）、KnowledgeDocTextExtractor（M）、KnowledgeParseTaskService（新）、KnowledgeParseStatus（新）、迁移 20261106_add_knowledge_parse_status.sql（已上生产，幂等）。否则仓库无法编译本 jar。

## 回滚
后端 deploy-backend 归档 jar（KeepVersions=5）；前端 `release-metadata/1.0.37-resume-parse-20261107.rollback.conf.txt`。
