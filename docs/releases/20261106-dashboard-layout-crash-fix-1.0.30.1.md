# 2026-11-06 工作台崩溃修复（1.0.30.1，前端专项不停机发布）

状态：**COMPLETE**（已发布并真实浏览器验证）。

## 故障
内网登录后进入 /dashboard **整页空白**，浏览器标签页崩溃（Playwright 捕获 `Target crashed`），无控制台输出。所有页面因登录后跳转工作台而不可用，表现为"无法登录"。

## 根因
上一轮（1.0.29）引入的 `grid-layout-plus` 在**模块求值/首次渲染阶段触发渲染进程崩溃**（V8 级崩溃，非普通 JS 异常，故无 pageerror）。
定位方法：① 本地静态复刻发布目录 + Playwright 复现崩溃；② 二分构建"移除 grid-layout-plus 导入"变体 → 工作台正常渲染，确认库为唯一诱因。

## 修复
移除 `grid-layout-plus` 依赖（含 package.json），改为**零依赖自研布局**：
- 布局模型 `[{i,w}]`：数组顺序即排列顺序，`w` 为 12 列栅格宽度（保留"布局即数据"思路，仍按用户持久化到 `user_dashboard_layout`）
- 编辑模式：HTML5 原生拖拽重排（dragstart/dragover/drop）+ ◀ ▶ 列宽调节 + × 移除 + 备选卡片池添加 + 恢复默认
- 卡片目录、权限过滤（`approval:audit`/capabilities）、页面卡跳转、保存/读取布局均保留

## 验证（本地 + 生产双份）
- 变体与本版均用真实 Edge（Playwright/msedge）实测：登录→/dashboard 正常渲染 8 张卡（按权限过滤）、编辑模式 8 卡 + 9 个备选、列宽 3→4 生效、「保存布局」成功提示、**零 pageerror / 零崩溃**
- 生产 http://<PRIVATE_IP>：登录成功、工作台渲染正常、/project 页面正常、ERR: NONE
- 发布：graceful 前端切换 root→`releases/1.0.30.1`，index 哈希与产物一致；后端无改动（health 仍 1.0.30，版本一致）

## 回滚
`frontend/release-metadata/1.0.30.1.rollback.conf.txt` 可切回上一 root；注意上一 root（1.0.30 / 1.0.29）含崩溃版本工作台，**不建议回退**。

## 教训
引入第三方 UI 依赖必须做真实浏览器冒烟（构建成功 ≠ 运行正常）；本次崩溃构建、单测、curl 全绿，只有真实浏览器能暴露。
