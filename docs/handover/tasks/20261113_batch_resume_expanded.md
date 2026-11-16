# 批量简历卡片默认展开

候选证件照已移至卡片顶部的姓名和单位旁。窄卡可换行，头像选择仍位于字段表单上方。与默认展开一同打包。隔离构建、Edge 模拟接口回归（顶部选择、默认展开和单卡收起）及发布预演通过。

线上基线为前端 `1.0.38.24-reuse-consolidation-20261113`、后端 1.0.38.29 UP。候选目录 `builds/1.0.38.24-build-20261113_batch-resume-ui/dist`。管理员运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`。成功标志 `PRODUCTION_VERIFIED`。回滚使用切换时生成的 `frontend/release-metadata/1.0.38.24-batch-resume-ui-20261113.rollback.conf.txt` 并平滑重载。正式切换留给负责人，本次未执行。

2026-11-13，Codex。分支 `codex/personal-dashboard-v11`，开工时有既有后端和文档改动。本次仅调整共享批量简历组件 `BatchResumeDialog.vue` 的解析完成状态：默认展开整张字段表单，可逐张收起。现有字段权限、API、数据库及配置无改动。

前端构建 `npm run build -- --outDir ../builds/batch-resume-expanded-build-20261113` 通过。隔离 Edge 回归使用 `frontend/tests/expert-batch-resume-browser.mjs`，新增解析后表单默认展开与单卡收起断言。生产未发布。下一步可验收多份简历同屏核对，发布后如需回滚，恢复上一个前端快照。
