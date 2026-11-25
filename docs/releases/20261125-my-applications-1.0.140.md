# 1.0.140 发布记录：「我的申请」P2/P3 修复 + 申请详情抽屉 + 批量删除

## 内容

- 修复：重提时失效照片不再静默剔除（响应回传 `droppedPhotoFields`，列表标「照片失效」）；重提清空非必填字段改为撤回该变更（不再把空串写进专家档案）；移动端弹窗/抽屉自适应；30 秒轮询静默刷新；合同申请通知文案；列表关键词与排序；提交接口入参类型校验；响应去掉 `bizData`/`approverSnapshot`；重提敏感字段校验限定本次改动键；对比弹窗退回单临时照片误标「已清理」。
- 需求 C：「查看进度」「查看对比」合并为申请详情抽屉（对比 + 审批进度时间线 + 行内操作）。
- 批量删除（参照消息中心）：列表多选，新增 `DELETE /api/approval/requests/batch`，口径同单条删除（本人 + 已驳回/已撤回）。
- 未做（待负责人决策）：P1 退回修改单可撤回；合同发起提交的待办通知。
- 详见 [任务交接](../handover/tasks/20261125_my_applications_p2p3_detail_drawer.md)。

## 数据库

- 无迁移、无结构/权限/配置变更；`schema parity` 不受本次影响。

## 构建与测试

- 审批相关单测（隔离目录 `temp/p140/backend`，JDK 17 / Maven 3.10）：`Application*Test` + `DashboardResubmit*Test` 共 34 项，31 项通过，3 项跳过（集成测试需验收库环境变量，本机未启用）；其中新增 12 项。
- 后端全量单测（同目录 `mvn -o test`，注入 `JWT_SECRET`）：320 项，0 失败 / 0 错误 / 16 项跳过（环境门控），BUILD SUCCESS。
- 前端：发布前临时目录构建通过（40.4s，`is-not-exported` 类告警 0 条）。
- 正式制品：`builds/1.0.140-build-20261125_085733/expert-system.jar`（build-info version=1.0.140，gitShort=5f87871；commit 5f87871）；前端产物同目录 `dist/`。构建脚本 `-DskipTests`，测试范围以本节单测为准。

## 发布过程

- 08:55 Preflight 通过（版本真源 1.0.140 一致、迁移状态一致、schema parity 通过、ReleaseId 可用；预检时线上后端为 1.0.139）。
- 负责人指示发布，由 UAC 提权执行 `temp/publish-1.0.140.ps1`（包装 `release-full.ps1 -Mode Publish -Version 1.0.140 -Slug my-applications -Yes`），08:56:59–09:02:31，`logs/release-state.json` status=SUCCESS step=verified，退出码 0。
- 发布 1/6 全量备份：`backup/backup_20261125_085709`。
- 发布 2/6 隔离目录构建：`builds/1.0.140-build-20261125_085733`。
- 发布 3/6 制品校验通过。
- 发布 4/6 后端蓝绿（GracefulSwitch）：影子 8081 启动并验证 → Nginx 切 8081 → 停 NSSM、旧包归档到 `backend/releases/1.0.140-20261125_090004/previous-expert-system.jar`（1.0.139）→ 新包启动、8080 健康 → 切回 8080 → 清理。全程经 Nginx 不中断。
- 发布 5/6 前端快照切换：Nginx root → `frontend/releases/1.0.140-my-applications`。
- 发布 6/6 发布后验证 5/5 通过；按保留策略自动清理 2 个旧前端快照（约 1544 MB）。
- 发布日志：`logs/release-full-20261125_085659.log`；控制台转录：`logs/publish-1.0.140-console.log`。

## 技术冒烟

脚本自检（09:02）：

- 后端 `/health`：version=1.0.140、gitShort=5f87871、buildTime=2026-11-25 08:58:26。
- 经 Nginx `/api/health`=200；`/api/expert/list`=401（鉴权拦截，服务在线）。
- Nginx root = `frontend/releases/1.0.140-my-applications`；首页 200×3 且 hash 与新快照一致；主 bundle `index-CNJsYoOZ.js` 含 v1.0.140。

独立复核（09:04–09:10）：

- 直连 8080 与经 Nginx `/api/health`：均 `status=UP version=1.0.140 gitShort=5f87871`。
- `backend/target/expert-system.jar` 为完整包（BOOT-INF/lib 69 项，65.4 MB）；8081 无残留监听。
- 路由引用的 `MyApproval-DoOdQHep.js`、`ApprovalDiffDialog-Bl58sdQw.js`、`approval-DHU7O0J8.js` 与入口 `index-CNJsYoOZ.js`：线上文件与构建产物 sha256 一致，且均含本次功能代码。
- **登录后接口验证（测试账号 测试，经 Nginx）**：
  - `GET /api/approval/my-applications`：200，5 条；响应不含 `bizData`/`approverSnapshot`，含 `changes` 与进度记录。
  - 非法筛选：`status=9`、`type=NOPE`、`endDate=2026-13-99` 均返回明确的拒绝文案。
  - 关键词 `%25%25`（字面 `%%`）返回 0 条（通配符已转义），关键词「范」返回 1 条。
  - `DELETE /api/approval/requests/batch` 传空数组 / 非数组：均被拒绝（「请选择要删除的申请单」），**未执行任何删除**。
  - `POST /api/approval/requests` 传 `changes` 为字符串：返回「changes须为字段对象…」（此前为 500）。
- 应用日志：09:00 新版本启动后无新增 ERROR。

### 需要说明的两项日志观察

1. `logs/expert-app.log` 中 08:51:04 / 08:53:04 / 08:55:04 三条「服务器内部错误」为 `NoResourceFoundException: No static resource api/health.`——外部程序每 2 分钟直连 8080 探测 `/api/health`（后端无 `/api` 前缀路由，经 Nginx 才会去掉前缀）。与本次改动无关，早于发布。
2. 同一文件 08:54–08:55 约 60 行 `[main]` 线程的 INFO/WARN/ERROR（含「预算保存」「项目硬删物理文件清理」「合规提醒发送失败」等）来自本次在隔离目录运行的**单元测试**，是模拟对象打印的日志，**不是真实业务操作**；单元测试沿用应用 logback 配置，日志落到了生产日志目录。后续在本机跑单测应设置 `LOG_DIR` 指向临时目录。11-24 的全量测试亦为同样行为。

## 回滚

- 前端：`scripts/release-full.ps1 -Mode Rollback -Yes`（依据 `frontend/release-metadata/1.0.140-my-applications.nginx-before.txt`）。
- 后端：本次蓝绿归档的 `previous-expert-system.jar`（1.0.139）。
- 数据库不回滚：本次无数据库变更。

## 业务验收（待完成）

测试账号名下 5 张单据均为「已通过」，以下路径未能在浏览器实测，仅由代码与单测保证：

- [ ] 撤回一张申请 → 列表勾选该行 →「批量删除」→ 提示「已删除 1 张」且行消失；待审批单勾选框为禁用。
- [ ] 普通账号直接调用 `DELETE /api/approval/requests/batch` 传他人单据 id：返回 0，对方数据不变。
- [ ] 退回修改单：重新编辑弹窗清空一个非必填字段并提交 → 审批通过后专家该字段保持原值；清空「姓名」被拒绝。
- [ ] 原照片已失效的退回单：列表显示「照片失效」标签，重提提交后提示「已重新提交；原申请的「…」已失效，本次提交不包含该照片」。
- [ ] 合同申请重新提交后，审批人收到的待办标题为「新的专家合同审批申请待审批」。
- [ ] 窄屏（≤768px）打开详情抽屉与重新编辑弹窗不溢出。
- [ ] 审批管理页的对比弹窗行为与改造前一致（仍为弹窗形态）。

已在浏览器实测通过（发布前，新前端连旧后端，测试账号）：列表与勾选列禁用态、详情抽屉内容与布局、760px 卡片视图、36 秒内轮询刷新多次且加载遮罩出现 0 次。
