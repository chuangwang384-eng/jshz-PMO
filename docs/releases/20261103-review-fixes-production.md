# 生产蓝绿发布与交接记录

- 任务编号：20261103_production_review_fixes；主责：Codex；日期：2026-11-03（UTC+8）。
- 状态：已上线，技术验证完成，业务验收待负责人复核。
- 授权：用户明确批准按审批单执行生产不停机蓝绿发布、管理员提升、SYSTEM平滑重载及前端目录切换。
- 源码基线：codex/fix-review-and-config-takeover，e3eb25c；后端业务源码0e8f3a4。发布过程追加脚本兼容性修复，未变更业务实现。
- 发布成功时间：2026-11-03 15:37:10；上线后检查至15:40:25。

## 实际结果
- SYSTEM一次性计划任务平滑重载实际生效；Nginx master PID4364始终不变，未Stop/Restart Nginx服务。
- 先启动回环8081 PID29996，禁用影子定时任务；健康/业务探测通过后流量转蓝；旧worker排空后更换8080正式jar；新8080健康后回切；蓝worker/端口排空后停止影子。
- 正式后端8080 PID33864，ExpertSystemBackend和ExpertSystemNginx均Running；8081无监听。
- 前端root现为 <LOCAL_PATH>
- 首页SHA256 88E38CEA5478C9EBDDDCDD41C91CAC1412E7B1BCFD4D1EFA2A8C554275E4DF22，与审批候选一致。
- 运行jar SHA256 6F62438EB5D2C7B13466F044A0709825F7F0D1B31280EA327D9AAF0FC400E278，与审批候选一致；/api/health HTTP200/statusUP，版本标签仍1.0.22，用hash和release ID区分本次构建。

## 连续性与上线验证
- 正式重试/切流及观察窗口15:32:42–15:40:25：连接复用10377请求、独立连接10377请求，均0失败；探测首页200、/api/health 200、匿名项目时间线401，无重试掩盖失败。
- 生产Edge登录页正常、无pageerror/requestfailed；新旧静态资源HEAD抽查16项均200；首页hash匹配。
- 验收证据已提交至 [evidence/20261103-review-fixes](evidence/20261103-review-fixes/)。原始运行日志位于本机 .build-review-fixes-20261103/production-release.log，不等同Git交付。
- 未执行真实账号登录后的业务写操作或破坏性故障注入；此前隔离MySQL8项集成测试、组件测试和浏览器fixture验收结论仍适用。并非负责人业务验收通过。

## 实际异常（不得表述全流程零异常）
1. 首次管理员WinPS默认Restricted未执行本地脚本；改用仅当前进程RemoteSigned，不修改系统永久执行策略。
2. 第二次后端脚本在WinPS5.1中因UTF-8无BOM中文误读解析失败，未启动业务发布；修复为UTF-8 BOM并用实际WinPS5.1解析通过。
3. 预检初始窗口15:29:11–15:31:59，4167请求中出现1次fetch failed（15:30:28）。生产仍健康，未推进后端切换；未能证实该连接异常根因。该窗口证据单独保留，不计入后续零失败窗口。
4. SYSTEM计划任务短暂State/LastTaskResult不同步，267009代表Running，原helper误判失败并中止。改为等待调度运行/排队状态完成，再验证新worker及旧worker排空。重新预检通过后才正式切流。
- 上述兼容性修复涉及scripts/deploy-backend.ps1、deploy-frontend-graceful.ps1、invoke-nginx-graceful.ps1；无生产认证/密钥轮换、无数据库迁移或批量数据修改。

## 备份与回滚
- 旧后端：backend/releases/1.0.22-review-fixes-20261103_153545/previous-expert-system.jar；旧hash B6F5E5D6D9CB3FD132423212E5EE7503247365D2689223D8979A374209EC4B7A。
- 原配置：.build-review-fixes-20261103/production-backup/jshz-pmo.conf；Nginx脚本另有conf.d/backup归档。
- 前端兼容回滚root：frontend/releases/1.0.22-review-fixes-20261103.rollback。
- 前端配置元数据：frontend/release-metadata/1.0.22-review-fixes-20261103.nginx-before.txt 和同前缀.rollback.conf.txt；不在webroot，不入Git。
- 后续回滚属于新的生产操作，需负责人批准；按蓝绿方法恢复旧jar，保持前端.rollback资源并集，不直接Stop Nginx、不盲目恢复最早nginx全文、不删除新富文本或提醒日志。

## 下一位接手
1. 阅读本记录及审批单，核实当前分支/提交/git status；用健康检查、运行jar hash、root配置验证状态。
2. 让业务负责人复核项目配置、权限拒绝、资料和提醒行为；不要在生产重放调度或造测试数据。
3. 下一次发布沿用GracefulSwitch和不可变前端root路径；旧deploy-frontend.ps1默认dist已不是当前root，禁止据旧路径误认已发布。
4. 运行态归档文件和回滚目录需按受控保留策略维护，不能当普通源码或擅自清理。

文档同步：架构、部署、审批单状态、交接摘要、发布证据已同步；无DDL/API新增迁移；运行日志和前端回滚配置元数据排除版本控制。已知限制为未完成真实账号业务验收与初始连接异常根因未证实。
