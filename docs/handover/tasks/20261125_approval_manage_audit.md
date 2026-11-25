# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261125_approval_manage_audit
- 任务名称：「审批管理」板块只读代码审查（bug / 未启用 / 死代码 / 硬编码 / 边界 + 用户场景 + 新需求建议）
- 主责 Agent：ZCode
- 交接时间：2026-11-25
- 当前状态：已完成（纯只读审查，未改任何代码/数据/配置）
- 优先级：P1（发现一处上线起即不可达的完整功能链路）

## 2. 审查范围
- 前端：`views/ApprovalManage.vue`、`components/ApprovalTable.vue`、`components/ApprovalDiffDialog.vue`（共享组件，从管理页视角）、`api/approval.js` 相关封装、路由与权限指令。
- 后端：`ApplicationController` 的管理侧端点（list/approve/reject/return/transfer/add-approver/assignable-users）、`ApplicationService.listRequests` 与审批动作链、permission 表相关行、`application` 表 DDL。
- 数据佐证：生产库只读查库（status 分布、退回单计数、审批人快照）。

## 3. 审查发现（带用户场景，详见对话汇报）

### P1（1 项）
| # | 问题 | 位置 | 用户场景与感知 |
| --- | --- | --- | --- |
| 1 | **「退回修改」功能自上线起前端无入口（整链路不可达）**。后端 returnRequest 端点+permission 行(220)+状态机 0→4+DiffDialog mode='return'+emits/父组件 handler 全部就绪，但 ApprovalTable 待审操作列从未渲染过退回按钮（全 git 历史 grep 证实从未存在）。生产库 status=4 单据 0 条与之互证 | ApprovalTable.vue:36-56（操作列）、:77（emits 声明 return 死代码）、ApprovalManage.vue:27（handler 空挂） | 审批人想让申请人「改一下再交」（尤其保留临时照片、配合 1.0.138 退回后重传照片需求），界面上只有「驳回」可用——驳回即清临时照片，申请人须从头重填重传；后端精心实现的退回流程从未被触发过 |

### P2（2 项）
| # | 问题 | 位置 | 用户场景与感知 |
| --- | --- | --- | --- |
| 2 | 快速审批并发 409 不自动推进：另一审批人先处理某条后，本队列中该行变成坏行，点击只弹「已被其他审批人处理」，需手动「下一条」逐条跳过 | ApprovalDiffDialog.vue:249-253（catch 静默）、ApprovalManage.vue:256-268（仅成功才移除） | 两审批人（王创/jshz 均有 approval:audit）同时清积压时，后处理者连续报错、体验割裂 |
| 3a | 待审/已处理列表无关键词与状态筛选（后端 listRequests 的 keyword 参数前端从未暴露） | ApprovalManage.vue:177-206 | 待审 20 条时申请人催单「刚交的许某某」，审批人只能肉眼逐页翻 |
| 3b | 页面不跟随全局轮询刷新（store.lastPollAt 已有现成机制，本页未接） | ApprovalManage.vue 全文无 watch | 员工说「我刚提交了」→ 审批人页面不刷新看不到，误以为没提交，来回沟通 |

### P3（8 项）
4 列表加载失败无错误态/重试（fetchPending/fetchDone 无 catch，网络抖动→静默空白）；5 分页越界无回退（清尾后空白页，Q7 同款）；6 深链 applicationId 参数残留（刷新重开弹窗，同已修模式）；7 转办/加签下拉可选中自己（后端拒绝，前端不拦）；8 assignableUsers 拉取失败静默清空（下拉空白无提示）；9 快速审批阈值提示歧义（分给自己 ≤3 条但总数>3 时提示「当前没有可处理的待审批」）；10 快速审批 size=100 字面量未收口（与 MAX_PAGE_SIZE 重复定义）；11 已处理 Tab 与 3a 合并考虑。

### 已核实无问题
- 权限链完整：页面根 v-permission('approval:audit')；approve/reject/return/transfer/add-approver/assignable-users 均有 permission 行；转办/加签按钮的 ownedByMe 与后端 checkAssignee 口径一致。
- 硬编码：无盘符/IP/密钥（size=100 字面量除外，列 P3-10）。
- 敏感字段明文展示（changeSummary/对比弹窗）：属 2026-11-24 拍板的「审批需核对原文」口径 + 2026-11-23 专家库信任模型决策，登记不列为问题。
- 审批意见 maxlength=200 < DB varchar(500)，无截断风险；后端 approve/reject/return 有 H2 并发抢占（claimPending），重复审批不可能。
- done Tab 展示全部历史单（含他人审批）符合「全部申请单」定位。

## 4. 新需求候选
A. 审批筛选增强（关键词+类型筛选+跟随轮询）——后端 keyword 已支持，type 需后端加参数；纯本板块；
B. 启用「退回修改」入口（即 P1 修复，或产品决策下线该流程删代码）；
C. 批量通过（勾选+批量 approve，驳回仍逐条）——积压清理，边界：仅 status=0、失败不中断、依赖现有留痕；
D. 工作台审批统计卡（待审数/平均时长/超时数，复用 pendingHours）；
E. 滞留催办：pendingHours ≥24/72 自动催办通知（参照合规提醒模式）。

## 5. 测试与验证
| 项 | 步骤 | 结果 |
| --- | --- | --- |
| 退回入口不可达 | 全库 grep returnRequest/mode='return' 调用点 + git 全历史 -S「退回修改」+ 库中 status=4 计数 | 通过（结论见 P1） |
| 权限行核对 | grep init.sql api/approval | 通过（管理侧端点全覆盖） |
| 硬编码扫描 | 通读页面/组件 | 无违规（size=100 除外） |
| 编译/构建 | 未执行（未改代码） | 不适用 |

## 6. 下一位 Agent 的接手指令
1. P1 需负责人先拍板：启用退回入口（推荐，1-2 行改动+回归）还是下线退回流程（删后端端点+dialog mode+emits，涉及 17.7 状态字典）。
2. P2/P3 修复均无 DB 变更；3b 复用 store.lastPollAt 一行 watch。
3. 未经确认禁止：直接删除 return 后端链路（涉及状态机与 17.7）；给 done Tab 加他人审批数据过滤（改变「全部申请单」定位）。
