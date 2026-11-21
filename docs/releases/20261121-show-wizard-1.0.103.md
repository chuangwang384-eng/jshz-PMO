# 发布与回滚记录：1.0.102/1.0.103 串场预览修复与三步向导（show-wizard）

- 发布时间：2026-11-21（1.0.102 约 10:10，1.0.103 10:23–10:28）
- 方式：1.0.102 前端 graceful 单发（hotfix）；1.0.103 走 `release-full.ps1` 编排全量（后端蓝绿 + 前端 graceful，**一次成功**，state=SUCCESS/verified）
- ReleaseId：`1.0.102-show-preview-fix`、`1.0.103-show-wizard`

## 内容

**1.0.102（hotfix）**：「新网页演示」打开显示 `undefined` —— 前端 `presentNewTab` 对已解包的响应做了二次解构（`request.js` 拦截器 code=0 时返回 `data` 本身，HTML 字符串无 `.data` 属性），`new Blob([undefined])` 产出字符串 "undefined"。修复为直接取字符串并加 `<` 前缀校验。纯前端。

**1.0.103**：「PPT 制作」入口与流程重构：
- `ProjectDetail` 的「PPT 制作」改为**新标签页打开三步向导**（新路由 `/project/:id/show-wizard`，`ShowWizard.vue`）：
  1. **选择模板**：四套模板卡片（色块预览+说明）；
  2. **日程与专家**：复用 `getShowAgenda`+`getScheduleOverview` 提取环节（实测项目 57 提取 17 环节）；每位专家自动从专家库载入证件照与简介，新组件 `CropAvatar.vue` 圆形裁剪（拖动圆框+缩放滑杆，canvas 输出 400×400 圆形透明 PNG），姓名/单位/职称/简介可编辑；
  3. **预览演示**：保存草稿 / 预览演示（`/show/preview`，与归档同模板）/ 进入编辑器微调归档（自动携带草稿）。
- 议程解析逻辑抽 `frontend/src/utils/showDeck.js` 单一真源（编辑器「从日程生成」同步改用，消除 2 处同形复制）。
- 后端模板 bio 页照片卡改**圆形头像**（`border-radius:50%; aspect-ratio:1/1`）。
- 编辑器保留全部既有能力（手动改页/主K/草稿/归档），作为向导产物的微调与归档入口。

## 验证

- 后端全量 226 测试绿；前端构建 is-not-exported 告警 0。
- 真机核验（<PRIVATE_IP>，1.0.103）：向导 Step1 模板卡、Step2 环节列表（17 项）+ 专家自动载入（董桂兰：照片/单位/职称/简介全带入、圆框默认套脸、圆形预览产出）、Step3 22 页大纲含头像标记。
- 1.0.103 preview 接口冒烟（项目 62）：code=0，圆形照片卡 CSS、bio 分型、图片注入命中；圆形卡渲染截图核验通过（`temp/show-deck-sample/circle-103.html`）。
- 已知未核验项：IAB 内 `window.open(blob)` 被环境拦截，真机 Chrome 的 blob 新标签打开请用户实测确认。

## 回滚

- 前端：`frontend/release-metadata/1.0.102-show-preview-fix.nginx-before.txt` / `1.0.103-show-wizard.nginx-before.txt`（各自指向发布前 root）。
- 后端：`backend/releases/1.0.103-*/previous-expert-system.jar`（1.0.101 制品；1.0.102 无后端变更）。
- 数据库：无迁移。

## 遗留

- 向导 Step1 底部存在「下一步」与「下一步：提取日程」双入口（视觉冗余，功能无害，随下次改动优化）。
- 冒烟项目 62「发布冒烟-待删」仍待管理员删除。

## 追加：1.0.104 新版本提醒（2026-11-21 10:49）

- 背景：1.0.103 发布后用户反馈「线上没有向导」——服务端 root/bundle/chunk 三项核验均正常，根因为 **SPA 长驻页面不整页刷新就不会加载新代码**。
- 治本：App.vue 新增版本轮询（每 2 分钟匿名探测 `/health`，与构建期注入的 `VITE_APP_VERSION` 不一致时 ElNotification 提示 Ctrl+F5 刷新，同一版本只提示一次，探测失败静默）。
- 发布：纯前端 graceful（ReleaseId `1.0.104-version-notify`，staged dist 归档 `builds/1.0.104-build-20261121_104124`），回滚依据 `frontend/release-metadata/1.0.104-version-notify.nginx-before.txt`（指向 1.0.103-show-wizard）。
- 用户操作：本次仍需手动 Ctrl+F5 一次（1.0.104 生效后，今后发布都会收到提醒）。
