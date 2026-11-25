# 1.0.141 发布记录：申请删除放宽到已通过 + 去掉变更摘要列

## 内容

- 负责人反馈「删除功能无法使用」：排查确认根因是删除口径原为「仅已驳回/已撤回」，负责人名下单据多为已通过，界面无删除入口（Nginx access log 显示浏览器从未发出 DELETE 请求），属口径不符预期而非功能缺陷。
- 按负责人决策放宽：**已通过/已驳回/已撤回均可删除**（单条 + 批量同口径）。专家档案不受影响，变更留痕由 operation_log（DELETE_APPLICATION）保留；待审批须先撤回、退回修改须先重新提交的限制不变。
- 「我的申请」列表去掉「变更摘要」列（移动端卡片同步去掉），变更内容在详情抽屉查看。
- 详见 [任务交接](../handover/tasks/20261125_my_applications_p2p3_detail_drawer.md) 文末「追加修订」。

## 数据库

- 无迁移、无结构/权限/配置变更。

## 构建与测试

- 审批相关单测（隔离目录 `temp/p141/backend`，`LOG_DIR` 已隔离到临时目录，生产日志无污染）：35 例全绿（Guard 10：新增 `deleteAllowsApprovedForOwner`；Hardening 12：批量删除混合批次期望 2→3；3 例集成测试需验收库跳过）。
- 前端：临时目录构建通过，`is-not-exported` 类告警 0 条。
- 正式制品：`builds/1.0.141-build-20261125_092714/expert-system.jar`（version=1.0.141，gitShort=1c529ba；commit 1c529ba）。

## 发布过程

- 09:25 Preflight 通过（线上当时为 1.0.140 / 5f87871）。
- UAC 提权执行 `temp/publish-1.0.141.ps1`，09:26:32–09:33:52，`logs/release-state.json` status=SUCCESS step=verified，退出码 0，0 个 FAIL。
- 全量备份 `backup/backup_20261125_0926xx`；后端蓝绿（旧包归档 `backend/releases/1.0.141-20261125_093049/previous-expert-system.jar`）；前端 root → `frontend/releases/1.0.141-my-applications`。
- 发布日志：`logs/release-full-20261125_092632.log`。

## 技术冒烟与登录后验证

- 直连与经 Nginx `/health`：均 version=1.0.141 gitShort=1c529ba。
- **线上实测（测试账号 测试，浏览器经 <PRIVATE_IP>）**：
  - 页面为 v1.0.141；列头无「变更摘要」；「已通过」行出现「删除」按钮且勾选框可用。
  - 删除一张「已通过」单（必填测试专家，2026-11-07 新增申请，测试数据）：确认弹窗文案正确 → 提示「申请单已删除」→ 行消失，列表 5→4。
  - 操作日志留痕核验（只读查库）：`DELETE_APPLICATION 删除本人申请单：专家「必填测试专家」新增专家申请（状态 已通过）` 09:35:49。
- 发布前已在本地验证同一交互链路（新前端连旧后端）：请求发出后被旧口径拒绝并正确提示，证明前后端接线正确。

## 回滚

- 前端 `release-full.ps1 -Mode Rollback -Yes`；后端用 `previous-expert-system.jar`（1.0.140）。已删除的申请单物理删除不可恢复（删除前有确认弹窗与操作日志）。

## 业务验收

- [x] 已通过单据可删（线上实测）。
- [x] 变更摘要列已去掉（线上实测）。
- [x] 删除留痕 operation_log（查库核验）。
- [ ] 批量删除多选执行（口径与单条一致，单测覆盖；未在浏览器实测，负责人可直接试用）。
