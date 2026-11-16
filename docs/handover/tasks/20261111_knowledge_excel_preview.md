# 规范文档库 Excel 直接预览

2026-11-11 · 主责 Codex · 1.0.38.1 · 本地开发完成待验收

- 基线分支 codex/personal-dashboard-v11，工作区存在先前任务改动。版本从 1.0.37.39 更新为 1.0.38.1。
- KnowledgeController 复用文档访问校验并添加工作簿读取接口。ExcelPreviewService 通过现有 Apache POI 读取 xls 和 xlsx，输出有界表格区域、列宽、格式化值与合并跨度。KnowledgeWorkbookPreview 渲染可切换、可滚动的只读工作表。KnowledgeDocList 按文件类型选择预览，PDF、PPT 与现有下载接口沿用当前入口。
- 范围包含单元格数据与基础排版。公式显示保存值，复杂样式、图表及嵌入图片由下载原件查看。无数据库迁移及新依赖。
- Maven 在独立构建目录运行 ExcelPreviewServiceTest 通过，覆盖两种格式、工作表选择、隐藏内容、跨组合并、原件完整性。Vite 构建通过。上线前通过浏览器验证宽表、合并、表切换与 PDF/PPT 预览。
- 回退按应用版本恢复后端及前端制品。业务文件不需迁移。

- 真实君实工作簿读取验证通过，结果包含 1 张可见表、4 列、30 行。已验证内容列及纵向合并。日志 logs/excel-direct-preview-real-sample-20261111.log。

- 发布准备：后端 JAR 与前端制品预演通过。真实 Edge 使用待发布制品，在隔离库验证 xlsx、pdf、pptx 三种预览均通过。日志 logs/excel-direct-preview-edge-retest-20261111.log。生产提权调用等待期间未生成正式发布日志，已结束本次等待。线上仍为 1.0.37.39 UP。管理员入口 temp/apply-excel-preview-1381.ps1，前端回退快照 1.0.37.38-compliance-maintenance-20261110，后端回退目标 1.0.37.39。

- 1.0.38.1 已由管理员执行生产蓝绿及前端平滑切换，脚本输出 PRODUCTION_VERIFIED。独立复核直连和 Nginx 健康、运行 JAR 与线上首页及知识文档组件哈希。无需迁移，真实文件浏览器验收在隔离环境完成。发布日志 logs/deploy-backend.20261111_101828.log，后端回退制品 backend/releases/1.0.38.1-20261111_101906/previous-expert-system.jar。
