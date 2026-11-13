# 项目库外专家批量建档复用

- 任务编号：20261113-project-resume-reuse
- 主责：Codex
- 日期：2026-11-13
- 基线：`codex/personal-dashboard-v11`，`56c31ed`，共享工作区有既有改动。
- 状态：已完成待验收，未发布。

项目详情的批量及单人建档入口复用 `BatchResumeDialog.vue`。批量逐卡选项目待建档人员，单人入口预填。正文姓名缺失或不一致时禁止提交该卡，可换文件重试。保留手动建档入口及原有项目字段范围。用户已确认暂不处理库外另行建档却尚未回绑的特殊情况。

移除项目页面旧上传与结果列表及 API 封装。解析接口新增可选参数 `contentNameOnly`，默认行为兼容原客户端。现有项目单条建档接口增加可选字段 `requirePendingSpeaker`，在事务中复核并锁定仍待回绑的正式参会记录。旧批量接口暂留给旧客户端。数据库结构、运行配置和权限码无改动。

`mvn -q -DgitShort=56c31ed -Dtest=ResumeParseServiceTest,ScheduleParseServiceTest test` 通过 23 项，`npm run build -- --outDir ../builds/project-resume-reuse-build-20261113` 和 `git diff --check` 通过。设置 `PLAYWRIGHT_MODULE` 后用 Edge 对该产物执行 `frontend/tests/project-resume-reuse-browser.mjs` 和 `frontend/tests/expert-batch-resume-browser.mjs`，两项均通过。浏览器使用模拟 API，覆盖逐卡选人、正文缺名和姓名不符、换文件、单条提交、待办刷新、单人预填与专家库原流程回归。

发布须先上后端，再上前端。回滚先恢复前端，再按需要回滚后端，已创建档案及回绑沿用原有业务流程处理。下一步使用隔离项目及脱敏样本验收真实解析、落库和并发回绑。

生产发布已授权，候选位于 `builds/1.0.38.19-project-resume-build-20261113`。Maven 全量回归首次因未注入 JWT 测试密钥失败，使用临时密钥及隔离连接配置重跑后 133 项中 123 通过、10 跳过、0 失败，构建通过。前端构建及项目简历、专家库批量简历、快速审批三组 Edge 回归通过。后端 11 阶段及前端切换预演通过。正式执行在管理员令牌检查处退出，线上后端仍为 UP 1.0.38.18，前端仍指向快速审批快照。

由管理员 PowerShell 运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`。脚本先校验制品和旧版 JAR，再执行后端平滑发布、前端切换和线上探测，以 `PRODUCTION_VERIFIED` 为成功标志。回滚前端配置为 `frontend/release-metadata/1.0.38.19-project-resume-reuse-20261113.rollback.conf.txt`，后端回滚制品为 `builds/1.0.38.18-build-20261112_161236/expert-system.jar`。
## 单人建档入口修正

生产发布已授权。基于线上 1.0.38.19 前端构建的候选位于 `builds/1.0.38.19-project-single-archive-build-20261113/dist`，项目建档和专家库批量简历 Edge 回归及发布预演通过。正式切换因终端没有管理员令牌在写入前退出。线上仍指向 `1.0.38.19-project-resume-reuse-20261113`。

管理员运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`，成功标志为 `PRODUCTION_VERIFIED`。回滚使用切换时生成的 `frontend/release-metadata/1.0.38.19-project-single-archive-20261113.rollback.conf.txt` 并平滑重载。此修正仅发布前端。

候选行「去建档」和正式参会行「库外专家·去建档」共用单人界面，标题为「专家建档」，预填并锁定行姓名，只接收一份简历。候选建档通过现有项目建档接口回绑，刷新时保留候选表中未保存的编辑。侧栏仍进入批量界面。无接口、数据库、权限码和配置变更。

前端构建和 `git diff --check` 通过。对 `builds/project-single-archive-build-20261113` 执行 `frontend/tests/project-resume-reuse-browser.mjs` 的 Edge 模拟接口回归通过，覆盖两个单人入口、候选提交及批量姓名校验。线上核对为上一版 `1.0.38.19-project-resume-reuse-20261113`，本次修正未发布。

## 候选参会专家同名匹配

生产发布已授权。以线上单人建档快照为基线，隔离候选 `builds/1.0.38.19-project-exact-match-build-20261113/dist` 通过构建、Edge 回归和发布预演。正式执行因当前终端缺少管理员令牌，在写入前退出。线上仍指向单人建档快照，后端为 1.0.38.19。由管理员执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`，成功标志为 `PRODUCTION_VERIFIED`。回滚配置由脚本写入 `frontend/release-metadata/1.0.38.19-project-exact-match-20261113.rollback.conf.txt`。

在项目详情打开候选行的「专家库匹配」时，按当前行姓名查询，筛选全名一致的档案，并显示单位以区分同名专家。改名时清除旧关联。继续使用现有专家列表接口，无数据库、配置或权限变更。

前端构建、`git diff --check` 及 `project-resume-reuse-browser.mjs` 的 Edge 模拟接口回归通过，覆盖同名不同单位同时显示、单位命中但姓名不同的档案被排除，以及原建档流程。待业务验收，未发布。下一步可在隔离项目核对下拉结果，再按前端快照方式发布。回滚恢复上一前端快照。

