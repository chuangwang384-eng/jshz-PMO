# 三份专家简历照片修复

- 任务编号 20261118-three-portrait-fixes，主责 Codex，交接日期 2026-11-18，已完成待验收。基线 `main@f10fb7e`，开工工作区干净，生产健康为 `UP / 1.0.68 / 90fd15a`。
- 谭玲原件扩展名为 PPT，内容为 OOXML。旧 HSLF 读取报 OfficeXmlFileException。按签名分流至 PPTX 后恢复候选，既有透明 PNG 直接用原 alpha 裁剪。
- 吕喜英内嵌的是编辑器截图，截图内存在主体与缩略图两处人像。MODNet 当前处理选定主要区域，移除远处缩略图。张永智的环境照经同一算法去除左侧游离背景。花枝和张永智右臂旁仍有与主体相连的少量残边。
- 修改 `ResumeParseService.java`、`photomatte-worker.py` 和 `ResumeParseServiceTest.java`，架构说明同步。API 契约、权限、模型权重、数据库和部署配置沿用现有实现。
- 临时复测目录为 `temp/three-portraits/`。用原件运行 Java 解析复核候选数量，用本地 MODNet 在 1200px 输入上限下复核输出。透明原图用 alpha 逐像素比对验证；截图输出宽高比回到 1.4 以下，目检无缩略图。
- 生产尚未发布。下一步用相同三份原件登录隔离环境核对首选照片和选照。回滚可恢复 Java 与 worker 变更后部署前一制品，数据库无需补偿。

隔离 Maven 测试 34 项通过、5 项可选测试跳过。最初终端 Maven 使用 JDK 25.0.5，编译出现 Lombok 生成方法缺失。为本次隔离进程指定 JDK 17.0.20.1 后，`clean test -Dtest=ResumeParseServiceTest,PhotoMatteServiceTest` 成功。最终 Python worker 用三张原图和软边合成掩码复测通过。生产配置未执行写入。
