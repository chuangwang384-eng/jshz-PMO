# 串场过场、专家简介与出场顺序

## 任务与基线

- 任务 20261123-show-sequence，主责 Codex，2026-11-23。
- 已确认的验收行为：同一过场的头像、姓名和职称按对应专家简介页排序。旧草稿允许按过场补齐专家简介，可保留有意删除的页面。拖动文本框后按从上到下、同行从左到右的顺序播放出场动画。
- 开发分支 `codex/show-sequence`，基线 `ecbe46c`。开始时生产后端健康接口与 Nginx 前端指针均为 1.0.125。工作区已有 1.0.125 的两个版本文件、部署记录和历史回滚快照，继续保留。

## 改动与验收

- `ShowWizard.vue` 将草稿恢复、卡片前后移动和页面清单排序后的专家简介顺序同步到相应过场。已有预览中的头像排序也同步对应简介。根据日程段和专家标识匹配。
- 新稿从已确认的日程生成简介，即使简介正文为空。旧稿恢复优先采用已保存的页面快照。截图中的具体草稿来源待核实。在仅有过场或部分专家缺简介的旧稿中，点击「补齐专家简介」可恢复对应页面，草稿仍通过已有保存入口持久化。
- `ProjectShowService.java` 使用统一的预览和离线 HTML 播放器。页面存在坐标调整时，按实际屏幕坐标排序已有动画步骤。未移动的页面沿用原有顺序。历史归档需重新生成才能包含新版播放器。
- 沿用现行草稿与归档接口、权限和数据结构，无迁移或额外配置。已更新架构说明。

## 验证与发布

- JDK 17 执行 `mvn -q -DgitShort=<基线短提交> -Dtest=ProjectStageDeliverableTest test`，11 通过。首次命令缺少构建元数据参数，补齐后通过。
- `node temp/show-sequence-check.cjs` 通过分组排序、补页幂等、已排版简介保留和动画排序断言。临时脚本保存在本机 `temp/`。
- `node temp/show-sequence-edge.cjs` 通过 Edge 播放断言。使用隔离 HTML 验证移动后的文本框按视觉顺序显示，反向播放按同一顺序撤回。
- `npm run build -- --outDir <LOCAL_PATH>` 通过。`node temp/show-sequence-wizard.cjs <LOCAL_PATH>` 在 Edge 验证卡片前移同步更新过场成员、同一专家在另一个阶段可补齐简介。
- 发布预检 `scripts/release-full.cmd -Mode Preflight -Version 1.0.126 -Slug show-sequence` 通过。首次运行遇到 Windows PowerShell 模块路径污染，使用 Windows PowerShell 模块路径重跑通过。证据见 `logs/show-126-preflight.log`。
- 功能提交 `33cb5a0` 已快进合入本地 `main`。普通终端的发布尝试因缺少管理员令牌退出，随后通过 Windows 管理员进程完成 1.0.126 正式发布。发布状态为 SUCCESS，线上前后端均已验证 1.0.126。
- 生产真实项目写入流程待业务验收。打开「日程与角色」调换简介、补齐旧稿、拖动文本并保存，再重新预览和归档。发布证据、备份与回滚见 [1.0.126 发布记录](../../releases/20261123-show-sequence-1.0.126.md)。
