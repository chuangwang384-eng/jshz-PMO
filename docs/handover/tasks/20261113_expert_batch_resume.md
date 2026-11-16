# 专家库批量上传简历

- 任务编号：20261113-expert-batch-resume
- 主责：Codex
- 交接日期：2026-11-13
- 状态：已完成待验收
- 基线：`codex/personal-dashboard-v11`，`56c31ed`。初查工作区有存储管理及发布等既有改动。

专家库在「新增专家」旁增加批量入口。一次选多份简历，每份生成一张候选卡，沿用现有专家卡风格展示。候选可逐条编辑和勾选。失败或缺姓名、单位的条目可重试或补填，其他条目可独立提交。直接新增和新增申请沿用现有权限及审批路径。

姓名和单位相同则显示两张卡的详情，支持覆盖、都保留、取消当前简历解析。库内覆盖只发送确认后的非空字段和选定照片，直接编辑及修改审批按现有权限处理。同批覆盖先在候选中合并原值和确认字段。对比库内档案沿用已有详情权限与脱敏规则。

- 文件：`ExpertList.vue` 接入按钮和刷新，`BatchResumeDialog.vue` 管理候选与提交，`BatchResumeCard.vue` 展示摘要和对比详情。同步架构文档和交接索引。
- 验证：`npm run build -- --outDir ../builds/expert-batch-resume-build-20261113` 通过。设置 `PLAYWRIGHT_MODULE` 后，运行 `node frontend/tests/expert-batch-resume-browser.mjs builds/expert-batch-resume-build-20261113` 通过。测试用模拟 API 验证 Edge 中的多选上传、同批和库内对比、三个处理动作、部分失败重试、覆盖请求字段和审批分流。
- 影响：前端变更，沿用现有 API、权限码、数据库和配置，无迁移。查重不锁定其他会话写入，也不包含待审批申请。旧档案不保留原始简历，库内对比显示已保存的专家档案。
- 下一步：使用隔离测试账号和脱敏样本检查实际 OCR、照片入库及审批后落库，再准备独立前端发布。生产未发布、未验收。回滚可恢复上一前端快照，已处理的专家数据或审批单走原业务流程处理。

## 生产发布准备

负责人已授权发布。隔离候选以 `56c31ed` 为基线，纳入已在线的 1.0.38.18 存储管理前端和本次专家库变更。候选目录为 `builds/1.0.38.18-expert-batch-resume-build-20261113/dist`。构建、候选 Edge 回归和发布预演通过。执行 `-Apply` 在写入前因当前 PowerShell 无管理员令牌退出，Nginx root 复核仍指向 `1.0.38.18-reclaim-deleted-docs-20261112`，后端发布前核验为 UP 1.0.38.18。

下一步由管理员 PowerShell 执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`。脚本先校验候选哈希和线上基线，再平滑切换 Nginx 并验证专家库、存储管理和后端健康。成功标志为 `PRODUCTION_VERIFIED`。回滚使用切换时生成的 `frontend/release-metadata/1.0.38.18-expert-batch-resume-20261113.rollback.conf.txt` 并平滑重载。无后端发布和数据库迁移。
