# 1.0.138 发布记录：「我的申请」审查修复 + 退回修改后可重传照片

## 内容

- 修复：修改申请提交后审批人无待办通知；撤回/重新提交本人判定（applicant 为空 NPE、用户改名后失配）；oldData 非管理员视图联系方式未脱敏；通知与 biz_data 异常静默吞掉。
- 需求：退回修改（status=4）后重新提交可更换照片/身份证正反面（前后端）。
- 确认项修正：对比弹窗对合同申请误弹「历史数据」提示；「我的申请」列表刷新改为跟随全局通知轮询（去掉页面自建定时器）。
- 下线：旧兼容端点 `GET /api/approval/my-requests` 及 3 个无调用重载。
- 详见 [任务交接](../handover/tasks/20261124_my_applications_audit.md)。

## 数据库

- 无迁移、无结构/权限/配置变更；新库基线 `database/init.sql` 与增量目录均未改动，schema parity 不受本次影响。

## 构建与测试

- 后端单测（隔离目录，JDK 17 / Maven 3.10）：`Application*Test` + `DashboardResubmit*Test` 共 13 项，10 项通过，3 项跳过（集成测试需验收库环境变量，本机未启用）。
- 前端：`npm run build` 通过。
- 正式制品：`builds/1.0.138-build-20261124_162613/expert-system.jar`（build-info version=1.0.138，gitShort=9c26a07；commit 9c26a07）；前端产物同目录 `dist/`。构建脚本 `-DskipTests`，测试范围以本节单测为准。

## 发布过程

- 16:20 Preflight 通过（版本真源 1.0.138 一致、迁移状态一致、schema parity 通过、ReleaseId 可用；预检时线上后端为 1.0.137.2）。
- 负责人确认后由 UAC 提权执行 `release-full.ps1 -Mode Publish -Version 1.0.138 -Slug my-applications -Yes`（16:25:36–16:31:57，`logs/release-state.json` status=SUCCESS）。
- 发布 1/6 全量备份：`backup/backup_20261124_162548`。
- 发布 2/6 隔离目录构建：`builds/1.0.138-build-20261124_162613`。
- 发布 3/6 制品校验通过。
- 发布 4/6 后端蓝绿（GracefulSwitch）：影子端口 8081 启动并验证 → Nginx 切 8081 → 停 NSSM、旧包归档到 `backend/releases/1.0.138-20261124_162903/previous-expert-system.jar`（1.0.137.2）→ 新包启动、8080 健康 → 切回 8080 → 清理。全程经 Nginx 不中断。
- 发布 5/6 前端快照切换：Nginx root → `frontend/releases/1.0.138-my-applications`。
- 发布 6/6 发布后验证全绿；按保留策略自动清理 2 个旧前端快照（`1.0.135-customer-info`、`1.0.137.2-org-leaders-ux.rollback`，共约 1527 MB）。
- **附带上线**：已提交但未部署的 1.0.137.3（总经理代理部门负责人归属修复，commit 940f505）随本次后端包一并上线；负责人确认属遗漏而非有意保留。
- 发布日志：`logs/release-full-20261124_162536.log`。

## 技术冒烟

脚本自检（16:31）：

- 后端 `/health`：version=1.0.138、gitShort=9c26a07、buildTime=2026-11-24 16:27:07。
- 经 Nginx `/api/health`=200；`/api/expert/list`=401（鉴权拦截，服务在线）。
- Nginx root = `frontend/releases/1.0.138-my-applications`；首页 200×3 且 hash 与新快照一致；主 bundle 含 v1.0.138。

独立复核（16:34）：

- 直连 8080 与经 Nginx `/health`：均 `status=UP version=1.0.138 gitShort=9c26a07`。
- 首页 200，主 bundle `assets/index-CzCMQRAZ.js`；Nginx root 指向 `1.0.138-my-applications`。
- 未登录探测 `/api/approval/my-applications`、`/api/approval/requests`、`/api/approval/my-requests` 均返回 401（鉴权过滤器先于路由匹配，**无法在未登录状态下确认 my-requests 已 404**，需登录后复核）。
- 应用日志 16:27 起无新增 ERROR/WARN；`expert-backend.err.log` 130 字节（启动期内容，mtime 16:30）。
- **登录后业务验收未执行**（无获准测试账号），见下方清单。

## 回滚

- 前端：`scripts/release-full.ps1 -Mode Rollback -Yes`（依据 `frontend/release-metadata/1.0.138-my-applications.nginx-before.txt`）。
- 后端：`backend/releases/1.0.138-20261124_162903/previous-expert-system.jar`（蓝绿自带；回滚即同时退回 1.0.137.3 修复）。
- 数据库不回滚：本次无数据库变更。

## 业务验收（待完成）

- [ ] 提交专家修改申请后，审批人消息铃铛出现「新的专家信息修改申请待审批」。
- [ ] 审批人退回一张含照片的申请；申请人在「我的申请」点「重新编辑提交」，可重传照片/身份证正反面并提交；审批通过后专家照片为新图。
- [ ] 合同申请在「我的申请」/「审批管理」点「查看对比」，不再出现「该申请单为历史数据」黄色提示，身份证号/银行卡号两行正常显示。
- [ ] 「我的申请」页面停留期间，审批处理后列表随全局通知轮询刷新（约 30 秒内）。
- [ ] 登录后直接访问 `GET /api/approval/my-requests` 返回 404（确认旧端点已下线且无业务依赖）。
- [ ] 普通账号查看他人申请详情（直接调用 `/api/dashboard/focus/application/{id}`）仍被拒绝。
