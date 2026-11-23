# 串场固定头像框与只读预览

## 任务和基线

- 任务 20261123-show-preview-overview，主责 Codex，2026-11-23。
- 开发分支 `codex/show-preview-overview`，基线 `main` 的 `3b31134`。开始时生产后端和 Nginx 前端均为 1.0.126，历史 Nginx 回滚快照在工作区。
- 验收覆盖固定圆框内平移及缩放、自动人脸加肩部留量、第四步只读缩略图导航、草稿与 HTML 归档、文件名和主题扩展说明。

## 改动

- `CropAvatar.vue` 使用固定尺寸圆框，手动拖动、方向键或缩放改变原图的位置与比例。Canvas 使用相同取景区域输出 400×400 透明头像。自动裁剪仍使用内网 Tiny Face Detector 模型，脸框长边约 2.2 倍覆盖肩部，圆心略下移。手动操作会使尚未返回的识别结果失效。
- `ShowWizard.vue` 移除第四步编辑工具和可修改页面清单。第三步仍可调整顺序、补页和编辑单页。
- `ProjectShowService.java` 的共享播放器支持只读总览模式。缩略图复用已渲染的页面，靠近可视区时加载。使用原生按钮和当前页状态，点击直接跳页。新归档的 HTML 文件名取项目名和服务器归档日。
- 沿用现有 API、权限与存储机制，无数据库迁移和配置变更。模板程序所在目录、主题注册、CSS、离线约束与共享设计目录见 [架构说明](../../architecture.md#扩展串场主题与动效)。

## 验证与发布

- `npm run build -- --outDir <LOCAL_PATH>` 通过。
- JDK 17 执行 `mvn -q -DgitShort=<基线短提交> -Dtest=ProjectStageDeliverableTest,ProjectShowTemplateServiceTest test`，13 项通过，包括新归档文件名和既有模板逻辑。
- 隔离 Edge 脚本 `node temp/show-overview-browser.cjs <LOCAL_PATH>` 通过。使用后端实际渲染的 68 页 HTML 和模拟项目请求，验证固定框、图片缩放和平移、缩略图按需加载、末页跳转、高亮、编辑键禁用和草稿归档请求。
- `scripts/release-full.cmd -Mode Preflight -Version 1.0.127 -Slug show-preview-overview` 通过。日志 `logs/show-127-preflight.log`。
- 补测窄屏下总览与主画面的宽度，以及新标签只读演示和原独立播放器模式。Edge 隔离验证通过。
- 功能提交 `b5dafc4` 已快进合入本地 `main`。1.0.127 正式发布 SUCCESS，主实例、Nginx API 和线上前端主包均已验证。
- 登录生产真实项目后的编辑、保存和归档待业务验收。从第二步测试拖动缩放，再切到第四步点击末页，保存草稿，归档并下载核对文件名。见 [发布和回滚记录](../../releases/20261123-show-preview-overview-1.0.127.md)。
