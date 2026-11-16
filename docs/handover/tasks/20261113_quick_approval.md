# 审批管理快速审批

- 任务编号：20261113-quick-approval
- 主责：Codex
- 日期：2026-11-13
- 状态：已完成待验收
- 基线：`codex/personal-dashboard-v11`，`56c31ed`，工作区存在其他任务的改动。

待审批总数超过 3 条时显示「快速审批」。进入时按待审批列表顺序加载队列，保留未指定审批人、本人被指定或本人被加签的条目。复用变更对比弹窗，提供上一条和下一条。单条通过或驳回成功后移除该条，前进至下一条，末尾有跳过项时回到首条。队列清空后关闭。切换条目清空意见，驳回仍要求意见，接口失败时保留当前条目。

页面入口与队列在 `frontend/src/views/ApprovalManage.vue`，弹窗在 `frontend/src/components/ApprovalDiffDialog.vue`。复用既有待审批列表、通过、驳回接口和服务端权限。无需数据库迁移、配置调整或后端发布。队列是进入时的快照，其他审批人处理同一条时由现有后端规则拒绝，退出并重新进入可刷新队列。

`npm run build -- --outDir ../builds/quick-approval-build-20261113`、`git diff --check` 通过。设置 `PLAYWRIGHT_MODULE` 后执行 `node frontend/tests/quick-approval-browser.mjs builds/quick-approval-build-20261113` 通过，使用模拟接口验证入口阈值、前后导航、意见清空、驳回必填、自动前进、转办及加签筛选。生产发布和真实审批未执行。

下一步以隔离测试账号验收真实审批。准备发布时只纳入已确认的线上前端基线和本任务改动。若需要回滚，恢复上一前端快照，已提交的审批结果按现有业务流程处理。

## 生产发布

负责人已授权。线上 Nginx 当前指向 `1.0.38.18-expert-batch-resume-20261113`，后端健康为 UP 1.0.38.18。候选为 `builds/quick-approval-build-20261113`。快速审批及批量简历 Edge 回归、发布预演通过。正式执行因当前终端缺少管理员令牌，在写入前退出。

管理员执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`。脚本核对线上基线与候选哈希，平滑切换并检查三个路由、资源哈希和后端健康。成功标志为 `PRODUCTION_VERIFIED`。回滚使用 `frontend/release-metadata/1.0.38.18-quick-approval-20261113.rollback.conf.txt` 并平滑重载。
