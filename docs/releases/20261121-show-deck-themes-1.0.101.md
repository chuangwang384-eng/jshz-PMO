# 发布与回滚记录：1.0.101 串场制作模板化（show-deck-themes）

- 发布时间：2026-11-21 09:47–09:54
- 发布方式：`scripts/release-full.ps1` 编排（后端蓝绿）+ `deploy-frontend-graceful.ps1` 前端补跑（见下「事件」）
- ReleaseId：`1.0.101-show-deck-themes`
- 构建目录：`builds/1.0.101-build-20261121_094738`
- 版本文件备份：`logs/version-backup-20261121_094612`

## 内容

- 串场离线 HTML 模板引擎升级：四套内置模板（gold 红金盛典 / tech 深蓝科技 / paper 商务简白 / bamboo 青竹学术）+ `Deck.fontScale` 字号（0.8~1.4），含流光标题、对话逐条步进、16:9 自适应、键盘翻页、HUD 进度条；红金/深蓝含金色粒子氛围。
- 新增 `POST /api/project/{id}/show/preview` 只读预览（与 `/show/html` 归档共用同一 `render()`，单一真源）。
- 编辑器：模板/字号选择、「新网页演示」（未保存草稿经 preview 即时渲染到新标签页）、示意底色随模板。
- 数据库：无迁移（template/fontScale 存于既有 stage-form JSON）。

## 时间线与事件

| 时间 | 事件 |
| --- | --- |
| 09:46 | Preflight 6/6 通过（迁移 0 待执行 0 漂移、schema parity 绿、ReleaseId 可用） |
| 09:47 | Publish 启动（管理员提权）；版本文件自动同步 1.0.100→1.0.101；全量备份完成 |
| 09:47–09:50 | 隔离目录构建、制品校验通过（jar build.version=1.0.101；含新增 `ProjectShowService$Theme.class`） |
| 09:50:34 | **后端蓝绿成功**：影子 8081 起新版→验流→graceful 切 8080→清理影子；deployment.md R-1 表自动追加 |
| 09:50:35 | 前端步骤**中止（无副作用）**：`-ExpectJarClass` 数组经 `powershell -File` 跨进程传递被拆散为位置参数，第二元素（类路径）误绑 `$ReleaseId`，被 `deploy-frontend-graceful.ps1` 的 `^[a-zA-Z0-9._-]+$` 参数校验护栏拦截，exit=1；编排记 FAILED。坑已记入 deployment.md §4.1 |
| 09:52:39–09:53:44 | 前端 graceful **单独补跑成功**（简单参数）：staged dist 校验（bundle 含 v1.0.101、无旧标记）→ 快照落 `frontend/releases/1.0.101-show-deck-themes` → Nginx root 切换、graceful reload（master PID 不变） |
| 09:54 | 发布后验证全绿（见下） |

**注意**：`logs/release-state.json` 因编排中止保留为 `"status": "FAILED"`（历史事实，未回改）；实际终态以后端蓝绿成功 + 前端补跑成功 + 下述验证为准。

## 发布后验证（2026-11-21 09:54 实测）

| 验证项 | 结果 |
| --- | --- |
| `GET /health` | UP，version=1.0.101，buildTime=09:48:23，gitShort=dc40312 |
| Nginx root | `<LOCAL_PATH>` |
| 首页连续探针 | 200 × 3 |
| 线上主 bundle | 含 `v1.0.101` 标记 |
| API 经 Nginx 探活 | `/api/health` 200 |
| 串场预览接口冒烟 | 测试账号登录 → `POST /project/62/show/preview` 返回 9844 字节渲染产物，特征全命中：`t-gold`、`shine 6s`（流光）、`data-step`×4（步进）、`k-cover`、`a-wide`×2、`requestFullscreen` |
| 权限对照 | 对无阅读权限的项目 57 调 preview 返回 403（护栏生效）；不存在项目返回 400「项目不存在」 |
| 单测 | mvn 全量 226 run / 0 fail / 16 skipped |

## 回滚

- 前端：`frontend/release-metadata/1.0.101-show-deck-themes.nginx-before.txt`（发布前 root = `1.0.100-stage-deliverables-20261121`）；`scripts/release-full.ps1 -Mode Rollback -Yes` 或手工恢复该行后 `nginx -s reload`。
- 后端：`backend/releases/1.0.101-20261121_095006/previous-expert-system.jar`（即 1.0.100 制品），蓝绿脚本既有回滚路径。
- 数据库：无迁移，不涉及。

## 遗留

- 冒烟项目 **62「发布冒烟-待删」**（发布验证临时创建，惯例同历史 54/56/57）待管理员删除。
- 串场功能业务验收：真实项目走「从日程生成 → 换模板 → 新网页演示 → 归档」流程。
- 已归档的旧版串场 HTML 保持旧视觉，重新归档后才使用新模板。
