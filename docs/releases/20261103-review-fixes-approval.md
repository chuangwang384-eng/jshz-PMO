# 审查修复：隔离验收与不停机发布审批单

日期：2026-11-03；主责Codex；状态：隔离验收完成，等待负责人批准生产发布。尚未上线。

## 验收证据
- 独立MySQL26.7.0进程，127.0.0.1:13306/acceptance；仅从生产读取结构，导入57张表，无业务数据复制。生产字段send_status=varchar(16)，唯一键user_id,item_id,reminder_type,scheduled_time已只读核实。
- backend：ACCEPTANCE_DB=acceptance、ACCEPTANCE_PASSWORD由隔离配置注入，mvn -Dtest=IsolatedAcceptanceIT test：8/8通过，0失败/错误/跳过。首次fixture误写project_template.sort失败，按实际元数据修正后通过，没有改业务表结构。
- 8项真实数据库验收：时间线403/成员允许；项目引用供应商拒删/移出后删除；供应商关联与删除并发锁；独立富文本及事务回滚；旧共享内容最后引用删除；提醒默认/日期/取消收藏/关闭/完成/下架/责任身份；真实唯一键并发只写一条；瞬时失败重试和持久失败隔离；编辑责任人只通知新增者（部分场景合并在同一测试中）。
- 打包应用在127.0.0.1:18080启动，/health HTTP200 statusUP；匿名时间线HTTP401。全部文件路径使用临时目录，禁用合规/企微定时任务。
- Edge headless真实浏览器+隔离API fixtures：图形展示、已有类型编辑保留绑定、保存、图表切换，无pageerror。不是浏览器到真实数据库完整E2E。
- 浏览器发现额外页面阻塞并修复：wizardVisible初始化前监听导致空白；VueFlow slot props与node包装不匹配导致节点渲染错误。修复后浏览器通过。
- 前端组件脚本测试通过；最终前端生产构建通过（2388模块，37.06秒，有大包告警）。
- 隔离Nginx18082由当前用户启动，green→blue平滑reload：300请求、0失败、green63/blue237、master PID不变。该结果不证明生产SYSTEM身份可直接重载；尚未测试真实慢上传排空或浏览器回滚。
- 新PowerShell脚本语法解析通过；后端-GracefulSwitch -DryRun完整11阶段通过；前端只读计划通过。DryRun输出中的“发布完成”为模拟，不代表上线。
- 隔离Java/Vite/MySQL/Nginx均已停止，临时凭据已删除；生产三服务仍Running，/api/health HTTP200。

## 已准备的发布物
目录：<LOCAL_PATH>
- expert-system.jar：SHA256 6F62438EB5D2C7B13466F044A0709825F7F0D1B31280EA327D9AAF0FC400E278；后端源码来自0e8f3a4独立归档，后续仅新增验收测试，未改后端业务源码。
- frontend/index.html：SHA256 88E38CEA5478C9EBDDDCDD41C91CAC1412E7B1BCFD4D1EFA2A8C554275E4DF22；包含本轮额外页面修复。
- 旧生产jar已只读备份至本机临时验收目录/pre-release-backup，SHA256 B6F5E5D6D9CB3FD132423212E5EE7503247365D2689223D8979A374209EC4B7A；nginx配置同目录备份。正式发布仍按脚本再备份到持久归档。

## 审批范围与明确操作
1. 允许管理员提升及创建/执行/清理SYSTEM一次性计划任务，仅用于给现有LocalSystem Nginx发送平滑reload。先在配置不变时验证该服务身份信号、新worker和旧worker排空；失败立即终止，不回退到重启Nginx。
2. 从NSSM注册表仅在内存注入原MYSQL_PASSWORD/JWT_SECRET等配置，不打印/轮换凭据。
3. 执行 scripts/deploy-backend.ps1 -GracefulSwitch -JarPath <LOCAL_PATH> -Version 1.0.22-review-fixes。先起8081（回环地址，关闭影子定时任务），健康探测后切流；等待旧worker排空后才停8080更换jar；8080健康后回切，等旧worker与8081连接排空再清理蓝进程。全程不重启Nginx。
4. 执行 scripts/deploy-frontend-graceful.ps1 -StagedDist <LOCAL_PATH> -ReleaseId 1.0.22-review-fixes-20261103 -Apply。新建不可变前端release，用新旧hash资源并集；备好旧入口+资源并集的.rollback目录；平滑切换root，不重命名在线dist。验首页SHA256。
5. 持续探测80端口首页、/api/health及认证拒绝，记录切流期间HTTP失败数、主进程PID、worker交替、服务PID/jar hash、页面与关键只读接口。出现失败中止并按回滚保护处理。

## 风险及回滚
- 无DDL、无生产业务数据批处理；运行态SENDING由现有VARCHAR承载。仍需维护人业务验收和生产上线后只读验证。
- 当前会话非管理员，SYSTEM重载尚未实测，因此发布前服务身份预检为硬门槛。未获批准不提升、不执行生产reload。
- 新模式覆盖历史文档“普通身份reload被拒必须重启”的操作建议；不是关闭权限或修改Nginx服务账号。
- 旧worker最多排空300秒，超时保留后端并中止；连接查询失败中止，不假定已排空。
- 后端蓝验证失败：平滑回切旧8080；稳态化失败：保持8081服务，恢复旧jar再验证。不要在蓝仍承接流量时终止蓝进程。
- 前端失败：切到预备.rollback root（旧index+新旧assets），防止已加载新页面的用户懒加载404。元数据在frontend/release-metadata，不在webroot。
- 若整批回滚，不要盲目恢复最早nginx全文导致root丢失资源并集；保持前端.rollback root，仅调整API upstream并平滑重载。
- 回滚代码会重引入旧缺陷；不得删除新富文本文件或清空提醒日志。生产发布需另行明确批准，本单不是已上线记录。

## 下一位第一步
检查分支/git status及产物SHA256，确认审批涵盖上述管理员提升、平滑重载、8080后台服务更换和前端root切换；然后先做SYSTEM身份重载预检。禁止直接运行旧版默认重启切流模式。

## 审批与执行结果
用户已明确批准本单全部生产操作。2026-11-03 15:37:10执行完成；实际异常、监测窗口、产物hash及回滚位置以 [生产发布记录](20261103-review-fixes-production.md) 为准，原待审批文字为历史准备状态。
