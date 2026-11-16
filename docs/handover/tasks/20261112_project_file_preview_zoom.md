# 2026-11-12 项目附件预览缩放

主责 Codex。HEAD 91a67f3，工作区已有暂存和未暂存变更。当前生产前端 root 为 1.0.38.14-projectfile-preview-impl-20261112。状态为已完成待发布。

## 修复与范围

ProjectDetail.vue 的预览 canvas 使用 max-width: 100%，内容超过容器后实际显示宽度被压回。高 DPI 时画布像素尺寸还被用作布局尺寸，造成比例与视觉大小不一致。改为独立设置 CSS 尺寸和设备像素画布，预览区允许横纵滚动。轮上放大，轮下缩小，按钮沿用同一缩放逻辑，范围为 30% 至 300%。PDF.js 取消过时渲染，图片初始适配可视区后可缩放。无后端、数据库、权限、路由或配置变更。

## 验证与回滚

- 在独立输出目录执行 npm --prefix frontend run build，构建通过。git diff --check 通过。
- node temp/project-preview-zoom-edge.mjs 在真实 Edge 和 DPR 2 下通过。隔离附件 API 使用真实 PDF 字节模拟 PPT 转换返回，同时验证图片预览、按钮、连续滚轮、关闭后重开。浏览器测试未调用生产 Office 转换接口。
- 发布尚未执行。接手先核对线上前端根、隔离构建目录和资产差异，然后按前端发布流程切换。旧前端快照可用于回切。验证日志为 logs/project-preview-zoom-edge.log，构建产物路径记录在 temp/project-preview-zoom-build.txt。

## 发布预检

预演通过，线上后端为 1.0.38.14 UP，前端仍指向 1.0.38.14-projectfile-preview-impl-20261112。候选快照拟命名 1.0.38.14-projectfile-zoom-20261112，当前入口引用的项目详情资产为 ProjectDetail-DwTRVnj-.js 和 ProjectDetail-CLpkJE_v.css。当前会话非管理员，工具执行策略拦截了提权启动，生产未切换。管理员 PowerShell 中执行 <LOCAL_PATH> 验收、切换后的在线资源哈希和健康状态，成功时输出 PRODUCTION_VERIFIED。

## 左键拖动预览

后续改动已通过隔离浏览器验收，尚未发布。复核 Nginx root 已指向 1.0.38.14-projectfile-zoom-20261112，该生产快照包含滚轮缩放。ProjectDetail.vue 在 PDF 或图片超出视口时，从文件表面按住左键可横纵拖动。抬起、取消或关闭抽屉时结束拖动，滚动条及原有轮缩继续可用。仅前端改动，回滚时可回切当前线上缩放快照。

独立前端构建通过，日志为 logs/project-preview-pan-build.log。真实 Edge 验证 PDF 横纵拖动、图片纵向拖动、缩放按钮、连续滚轮及关闭后重开，日志为 logs/project-preview-pan-edge.log。附件 API 使用隔离响应。候选构建目录见 temp/project-preview-pan-build.txt。

## 拖动功能生产预检

生产基线为 1.0.38.14-projectfile-zoom-20261112，后端 1.0.38.14 UP。候选资产比较仅发现项目详情 JS 和 CSS 改动，前端切换预演通过。当前终端无管理员权限，上一轮相同的提权启动被工具执行策略拒绝。已生成管理员发布入口 temp/apply-project-preview-pan-20261112.ps1，Windows PowerShell 验收日志检查返回 True。生产切换待管理员执行，成功标记为 PRODUCTION_VERIFIED。
