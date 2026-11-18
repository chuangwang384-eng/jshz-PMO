# 专家库 PPT 简历解析与照片大图预览

- 任务编号 20261118-resume-ppt-preview，主责 Codex，交接日期 2026-11-18，已完成待验收。
- 工作区基线为 `main@2450900`，已有证件照补肩及其他任务的未提交改动。生产只读健康检查返回 UP、1.0.54、`b35471d`。

## 原因与变更

- 旧版 PPT 为 OLE2 二进制文件。原服务将两个 PPT 扩展名都交给 `XMLSlideShow`，因而抛出 `OLE2NotOfficeXmlFileException`。现在用 HSLF 读取 PPT，两个格式共用组合形状、表格及图片提取。
- PPTX 原件的 JPEG 条目为 STORED，长度由 ZIP 数据描述符与中央目录提供。旧 POI 流式读取将输入字节数算成 0，触发解压比检查。服务改为系统临时文件上按中央目录只读打开，抽取后在 finally 清理。继续使用 POI 的 ZIP 限制。
- `PhotoImage.vue` 复用 Element Plus 查看器展示现有鉴权图片、本地 Blob 和解析候选。专家新增、编辑及批量对话框能点击照片打开大图。批量候选保留独立选用按钮，已保存证件的现有脱敏分支继续生效。
- 数据库、API 返回结构、依赖与部署配置无变更。架构文档和交接摘要已同步。

## 验证

- 隔离 Java 服务调用 `ResumeParseService.parse`。1.0.54 对两份原件均返回解析失败。修复后根据专家库默认策略正常回填姓名与单位，PPTX 与 PPT 分别返回 1 张和 3 张候选。该测试以空语义模型结果和 JPEG 候选回退隔离格式解析。
- `mvn -q test -Dtest=ResumeParseServiceTest -DgitShort=local -Drevision=1.0.54` 在隔离副本运行，22 项通过，另一个需独立样本的既有可选测试跳过。旧 PPT 合成回归覆盖组内文本、表格与图片。通过环境变量 `RESUME_STORED_IMAGE_TEST_PPTX` 指向实际 PPTX 原件验证 ZIP 数据描述符样本。
- `npm --prefix frontend run build -- --outDir ../temp/resume-upload-check/frontend-dist` 通过。Edge 无头测试在隔离构建中以模拟 API 验证单独上传候选放大、选用后预览、手工上传图片预览、批量候选大图和选用状态相互独立，以及批量已选照片预览。
- `git diff --check` 通过。线上登录、上传与保存业务验收待执行。接手时在隔离实例或经批准发版后，用两份原件分别上传及批量上传，验证照片可放大且选用后仍按原流程提交。
- 回滚时针对本次 PPT 解析及照片预览差异恢复代码，保留前一任务的照片补肩变更。
