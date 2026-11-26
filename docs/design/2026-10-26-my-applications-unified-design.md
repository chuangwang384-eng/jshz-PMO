# 「我的申请」通用化改造 设计文档

- 日期：2026-10-26
- 状态：已实现（feature/my-applications 分支，1.005）；迁移已执行并验证；**生产发布阻塞于 UAC 提权确认（两次被取消）**
- 关联：`database/migration/20261026_add_application_table.sql`、交接记录 `docs/handover/tasks/2026-10-26-我的申请通用化改造与消息中心-交接记录.md`

## 1. 目标

员工可查看本人提交过的所有申请及当前状态（类型/状态/提交时间/审批人/审批时间/审批意见），不再反复追问进度；架构上建立**可扩展的通用申请聚合**，未来接入请假、报销等业务线。

## 2. 架构决策（对话已确认）

1. 通用申请主表 `application` + JSON 业务详情 `biz_data`（不建每业务明细表）；
2. 存量 `expert_change_request` 一次性迁移（保留原 id；旧表保留作历史备份，新写入停用）；
3. 类型 = 代码枚举 `ApplicationType`（一期 EXPERT_MODIFY / EXPERT_CREATE）+ 统一接口 + 类型分发，一期不做配置管理界面；
4. 状态沿用四态 0/1/2/3；
5. 实时更新 = 通知驱动（approval_result 铃铛 + 跳转 /profile/my-approvals?status=1）+ 页面 30s 轮询兜底（不重置分页/筛选，进行中跳过），不引入 WebSocket/SSE；
6. 「我的审批」原地升级为「我的申请」（路由 /profile/my-approvals 保留）；「审批管理」从管理员设置迁移至个人中心（approval:audit 可见），/admin 父路由权限并集 6 码→5 码；「审批流配置」「审批模板管理」不动；
7. 提交入口不变（专家列表按钮），底层换表；
8. 管理员不能提交专家申请（维持现状）；管理员「我的申请」为空属正常。

**超出原规格的必要补充**：application 表纳入 B2 的 current_assignee / extra_approver 两列（B2 转办/加签当天上午刚上线，规格表未列；不带则功能回退），影子实测已回归通过。

## 3. 数据模型

`application`：id / applicant_id（可空，存量按用户名回查）/ applicant / application_type / status / biz_data（JSON）/ approve_by / approve_comment / approve_time / current_assignee / extra_approver / create_time / update_time；索引 idx_applicant、idx_applicant_id、idx_status、idx_application_type。

迁移要点：expert_change_request → application **保留原 id**（INSERT IGNORE 主键幂等；发布窗口期旧代码写入可在部署后重跑补齐），request_type 0/1 → EXPERT_MODIFY/EXPERT_CREATE，change_data 嵌入 biz_data.changes（JSON_VALID 兜底）；approval_record 加 application_id 并按同 id 回填（恒等映射）；存量通知 jump_path 批量修正。**坑**：sys_user 为 utf8mb4_general_ci、业务表为 utf8mb4_0900_ai_ci，关联比较须显式 COLLATE（迁移脚本已处理）。

## 4. 后端

- 新增：`Application` 实体（含 biz_data 解析展示字段）、`ApplicationMapper`、`ApplicationType` 枚举、`ApplicationService`（移植 ApprovalService 全部逻辑：配置/提交/审批/驳回/撤回/转办/加签/通知，读写 application）、`ApplicationController`（URL 沿用 /api/approval/*，permission 路径规则零变更；新增 GET /my-applications；保留 GET /my-requests 兼容）。
- 删除：ApprovalController / ApprovalService / ExpertChangeRequest / ExpertChangeRequestMapper；模板模块常量引用改指 ApplicationService。
- ApprovalRecord 实体加 applicationId；新记录两列同值写入（request_id 非空约束兼容）。
- 关键实现：getPending/withdraw 返回前 fillBiz() 解析 biz_data（expertId 不再是列，漏掉会导致"专家不存在"——影子预探发现并修复）；管理端 keyword 检索专家名用 JSON_EXTRACT(biz_data,'$.expertName')。

## 5. 前端

- MyApproval.vue：标题「我的申请」；改调 getMyApplications；新增审批人/审批时间列（空显 -）；30s 轮询（onBeforeUnmount 清理）；保留状态筛选/变更摘要/审批意见/查看进度/撤回。
- 路由：/profile/my-approvals title→我的申请；新增 /profile/approval/manage（approval:audit）；删除 /admin/approval/manage；/admin 父 permissions 去掉 approval:audit。
- 文案：「我的审批」全仓清零（ExpertList 提示语同步）。
- ApprovalManage.vue 无需改动（接口与响应字段形状兼容，后端换表透明）。

## 6. 验证记录（2026-10-26）

| 项 | 结果 |
| --- | --- |
| mvn package / npm run build | 通过（1.005） |
| 迁移执行 + 核对 | 31/31 行、applicant_id 回填 100%、record 65/65、中文 HEX 校验、77 号 B2 字段随迁 |
| 影子 8081 预探 | 王创 my-applications=29 条存量可见；旧路径 my-requests 200；新提交 id=81（applicantId=152）；审批 81 通过（SUBMIT+APPROVE，expert_info 简介生效）；82 撤回通过；83 转办→jshz 越权被拒→王创审批通过（SUBMIT/TRANSFER/APPROVE 全链） |
| 浏览器双账号实测 + 合并 main | **未执行**（阻塞于发布 UAC 授权） |

## 7. 遗留

1. 生产发布（后端 1.005 蓝绿 + 前端原子）待 UAC 授权；发布后重跑迁移补窗口数据、探针 my-applications；
2. 发布后浏览器实测（王创/jshz/张三菜单可见性、轮询、驳回意见展示）与数据层核查；
3. 实测全过后合并 main。
