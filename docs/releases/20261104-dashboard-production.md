# 工作台1.0.24生产发布与交接

## 状态与授权
- 任务：20261104_dashboard_production；主责：Codex；日期：2026-11-04（北京时间）。
- 用户明确批准发布；应用已发布到生产，待业务验收，最终状态PUBLISHED_WITH_WARNING。
- **严格连续性门禁未通过**：切流窗口1次新连接ECONNRESET，发布编排因此返回FAILED；前后端此前已切换且业务核验通过。保留原FAILED门禁与探针记录，不把后续复验当成全程零故障。
- 后续约165秒稳态复核3034请求零失败；基于新版本当前健康、只读业务与资源检查通过，保留健康版本运行，避免无收益的再次切换。不等于负责人业务验收通过。

## 发布标识与实际结果
- 发布ID：1.0.24-dashboard-20261104；应用版本1.0.24；业务源码提交1980a66，分支codex/personal-dashboard-v11。
- 发布开始15:43:47；15:46已完成前后端切换及授权账号只读验证；15:46:06严格连续性门禁结束；15:47:19–15:50:04单独稳态复核。
- 生产前端root：`<LOCAL_PATH>`；旧root `frontend/releases/1.0.23.3`保留。
- 正式后端8080健康，ExpertSystemBackend/ExpertSystemNginx均Running；8081影子已停止。
- Nginx master PID39504始终保留，未Stop/Restart Nginx服务。后端在影子承接流量时更换正式包。
- 候选/当前后端SHA256：7c1e724e72edd597b02e9c1873de2c249a48f45cb0f85b7212258d302fa4d5d2。
- 候选/生产首页SHA256：c8e442437ce02bf6838dadcec191bb4506a1ed68486d42367a4ceb5111424480。
- 242个业务类与隔离验证候选逐字节一致，仅重建1.0.24版本资源。生产旧包没有在候选中缺失的类。

## 前置检查、数据与凭据
- 实查生产expert_system/3306，项目配置相关表存在，8个LEGACY_BASELINE快照，未删除项目缺失快照数0。
- 本轮无迁移、回填、角色/认证/CORS策略或业务原始数据修改。授权测试账号仅执行正常登录和只读业务检查，登录审计照常产生。
- 现有服务环境直接加载到发布进程，MYSQL_PASSWORD/JWT_SECRET一致性检查通过，不记录或轮换密钥。
- 备份：`backup/backup_20261104_154350`，数据库及照片/知识文件备份完成，backup.ps1 Verify通过。
- 已保留原配置、完整可启动旧后端包，并检查候选和回滚包均为Boot可执行制品。

## 验证结果与异常
| 范围 | 真实结果 |
| --- | --- |
| 原隔离技术验证 | 17项后端、8项前端，隔离构建、业务浏览器与小数据压力；详见实施交接 |
| 生产登录与会话 | 既有授权测试账号登录、/api/auth/me通过 |
| 工作台/搜索/旧模块 | overview各卡片无error、六类搜索无error、原项目分页通过；未生产重放审批/资料等写操作 |
| 静态资源 | 首页hash匹配；新旧资源共372次HEAD均200 |
| 生产页面 | IAB实际打开/dashboard进入登录，显示1.0.24；无控制台加载error/warn |
| 首次切流窗口 | 15:44:02–15:46:06，复用连接1102请求0失败；新连接1098请求1失败；不重试 |
| 单独稳态窗口 | 15:47:19–15:50:04，复用1518、新连接1516，合计3034请求0失败；不重试 |
| 最终运行 | 1.0.24健康，前后端hash和root匹配，Nginx master不变，旧8081无监听 |

异常发生于15:44:32.037，GET /api/health的新连接被reset，未获得HTTP业务错误响应。Nginx错误日志同时间附近仅见15:44:31平滑重载signal记录；只能确定时间相关，**不能断言根因就是worker切换**。不能将此发布标记为严格不停机验收通过。业务与稳态复核通过，后续排查见[跟踪记录](../handover/tasks/20261104_nginx_switch_reset.md)。

## 备份与回滚
- 完整旧后端：`backend/releases/1.0.24-dashboard-20261104-20261104_154453/previous-expert-system.jar`；SHA256 8220dfe36b7ef9806120de3a290dadbd5cce007d3c8adc20319c5e4977894cfa。
- 同一旧包另存私有stage previous-expert-system.jar，原配置在同stage nginx-before.conf。
- 前端兼容回滚root：`frontend/releases/1.0.24-dashboard-20261104.rollback`，含原入口与新旧hash资源并集；配置元数据位于frontend/release-metadata同发布ID前缀。
- 后续回滚是新的生产操作，须负责人批准；优先评估恢复前端入口同时保留兼容新旧客户端的新后端。
- **回退旧后端的限制**：旧resubmit不会处理新文本补丁，仍打开新页面的客户端可能提交补丁而被旧服务忽略。若确需回退旧后端，必须先控制该写入口/客户端刷新并验证兼容策略；不能只替换旧jar就宣称数据行为兼容。不删除申请、审计或原始资料。

## 代码、工具与文档交付
- scripts/release-dashboard.ps1为本次受控编排，默认预演，Apply要求管理员、候选hash与完整回滚包；发布尝试后拒绝重复Apply，避免对已生效版本盲目重切。
- 探针及只读核验源码：scripts/dashboard-release-probe.mjs、dashboard-release-verify.mjs；运行时按既有受控DPAPI测试凭据读取，凭据不入Git。
- 源代码、需求/设计、架构/部署/API/测试说明、实施交接及最新摘要同步。版本1.0.24已提交，最终发布记录另有文档提交。
- 原始状态/日志/备份在私有`.build-dashboard-production-20261104`：release-state.json仍保留门禁FAILED，final-state.json记录PUBLISHED_WITH_WARNING；不得覆盖原始失败证据。
- 可提交的无凭据证据：[发布证据目录](evidence/20261104-dashboard/README.md)。

## 下一位Agent第一步
1. 读取本记录、实施交接和连接重置跟踪；检查git status/log、生产health版本、运行jar/hash、Nginx root和master，不根据旧未发布记录猜测状态。
2. 请业务负责人复核首屏、审批/退回编辑、个人项目与UNKNOWN提示；当前只读技术检查不能代替业务验收。
3. 排查连接重置时保留两个独立探针窗口，禁止删除/合并异常或重放生产故障；优先在隔离环境重现。
4. 不重跑本次Apply，不自动迁移、轮换密钥或回滚；此类新生产操作需另行具体批准。
