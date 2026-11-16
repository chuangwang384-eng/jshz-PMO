# 项目建档复用专家库默认简历解析

- 任务编号 `20261116-project-resume-parity`，主责 Codex，2026-11-16，已完成待验收。
- 开工基线为 `codex/personal-dashboard-v11` 的 `1fa4cf2`，工作区已有简历解析和专家库界面的未提交改动。线上健康接口报告后端 UP、版本 `1.0.38.29`，Nginx 前端指向 `1.0.38.24-batch-resume-ui-20261113`。

项目单人和批量建档都从 `BatchResumeDialog.vue` 调用专家库的默认解析入口。正文姓名无效或缺失时可用文件名提取结果。解析姓名须匹配所选参会专家才可提交。项目提交字段、建档回绑、数据库、接口契约、权限及运行配置沿用原有实现。后端可选参数继续兼容旧客户端。

| 验证 | 实际结果 |
| --- | --- |
| `npm run build -- --outDir ../builds/project-resume-parity-build-20261116`（frontend） | 通过，Vite 提示部分 chunk 超过体积阈值 |
| `node frontend/tests/project-resume-reuse-browser.mjs builds/project-resume-parity-build-20261116` | Edge 模拟接口通过。旧构建在文件名兜底的单人建档用例失败，修复后通过。缺名和姓名不符仍拦截，覆盖换文件、提交回绑与列表刷新 |
| `node frontend/tests/expert-batch-resume-browser.mjs builds/project-resume-parity-build-20261116` | Edge 模拟接口通过 |
| `mvn -q -DgitShort=1fa4cf2 -Dtest=ResumeParseServiceTest test`（backend，设置 `RESUME_NAME_TEST_PPTX` 指向截图原件） | 16 项通过。原件测试读取真实 PPTX，使用模拟模型输出，验证默认模式提取「李宏」并保留简介原文 |

已同步架构说明与交接索引。2026-11-16 09:07 前端已上线并通过静态检查，见 [生产发布记录](../../releases/20261116-project-resume-parity.md)。真实 UI 上传和落库验收待执行。下一步用相同 PPTX 分别验证专家库和项目「去建档」，核对解析姓名、项目字段及回绑。
