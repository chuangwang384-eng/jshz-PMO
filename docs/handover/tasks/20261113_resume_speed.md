# 简历解析耗时优化

## 2026-11-16 重启后慢解析排查

08:24:37 主机重启，后端 1.0.38.29 UP。08:25:49 抠图预热失败，08:39:31 抠图记录 TimeoutException 并回退原图。对应两次 Ollama 请求为 1.98、1.14 秒，均发生在 08:27:32 Ollama 预热完成后。Ollama 日志确认从 D 盘加载。两次生成结束到 Nginx 完成响应分别约 14、12 秒，代理未记录完整请求时长。

`temp/ResumeTiming.java` 以 1.0.38.29 构建类和原李宏 PPTX 在新 JVM 中复测，抠图冷、Ollama 热时总耗时 14.982 秒、抠图阶段 12.592 秒。随后同进程两次 1.888、1.860 秒，均得到透明候选。日志 `logs/resume-reboot-diagnosis-20261116.log`。当前 READY 与单图共用 15 秒等待，启动预热失败后要等下一次请求重试，未记录分阶段细节，不能区分线上超时发生在初始化还是实际抠图。建议后续将启动预热重试与请求等待拆开并分段记录，验收重启后首张与随后热解析。本轮未修改代码或生产配置。

## 文件名补单位

生产发布已授权。17:17 确认 1.0.38.28 UP。1.0.38.29 候选完整重跑 137 项中 127 通过、10 跳过、0 失败。首次构建工作台时限测试失败，未更改测试，完整重跑通过。候选原件双模式通过，JAR 哈希及蓝绿预演通过。正式执行在管理员检查处退出，线上未切换。

管理员执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`，成功标志 `PRODUCTION_VERIFIED`。回滚 JAR 是 `builds/1.0.38.28-build-20261113_resume-portrait/expert-system.jar`。日志 `logs/resume-unit-retest.log`、`logs/resume-unit-release-original.log`、`logs/resume-unit-release-preflight.log`。

定向 Maven 测试 15 通过、1 项可选原件测试跳过。使用 `temp/UnitProbe.java` 与本机 Ollama 对谷培利原件实测，普通及正文姓名专用模式均返回「郑州市人民医院」。下一步发布时基于已核验的线上 1.0.38.28 制品构建并蓝绿切换，回滚使用该基线。

谷培利 PPTX 正文包含姓名和科室，未包含「郑州市人民医院」。解析优先保留正文单位，语义单位补缺须有正文依据且符合机构后缀规则。单位缺失时从文件名的机构词段补填。项目正文专用模式仍限制姓名来源，允许文件名补单位。接口、权限、数据库和配置无变更。本轮未发布。

## 陈珂 PPTX 识别修正

生产发布已获授权。17:05 核对 1.0.38.27 UP，候选和回滚包哈希预检、蓝绿预演通过。正式执行在管理员令牌检查处退出。管理员执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`，待 `PRODUCTION_VERIFIED` 后用原件业务验收。预检日志 `logs/resume-portrait-release-preflight.log`。

1.0.38.28 隔离构建通过，136 项中 126 通过、10 跳过。用候选构建、本机 Ollama 和 D 盘 U²-Net 复测原件三次，均返回陈珂，首个候选为 400×600 透明 PNG。首次 11.965 秒，热解析 3.814、3.684 秒。发布预演通过，生产未切换。本轮为故障修复与验证，正式发布待授权。日志位于 `logs/resume-portrait-build.log`、`logs/resume-portrait-original.log` 和 `logs/resume-portrait-dryrun.log`。

原件正文将姓名写为带空格的「陈  珂」，位于「个人简介」之前，首行「基本情况」曾被当作姓名。优先使用明确标签或个人简介标题前的姓名，语义姓名须有正文依据，普通模式支持「简介新」文件名兜底。原件中的 3840×5760 竖版照片超过旧尺寸上限。修正后竖版优先，先缩放再用现有 U²-Net 抠图。接口、数据库与运行配置无变更。回滚基线为线上 1.0.38.27。

2026-11-13，Codex。生产切换已阻塞。仓库基线 `1fa4cf2`，候选基于生产 1.0.38.26 构建快照并叠加解析服务、抠图服务、工作脚本和抠图测试。

Java 后端预热一个本地 Python 工作进程，通过标准输入逐张发送图片路径，复用同一个 U²-Net 会话。文字抽取与照片处理并行，保持原 U²-Net 权重，抠图不可用时回退原图 JPEG。后端关闭时结束进程。无接口、前端、数据库或配置变更。

隔离执行 `mvn -Drevision=1.0.38.27 -DgitShort=1fa4cf2 package`，125 通过、10 跳过、0 失败。用 `temp/ResumeTiming.java` 测原 PPTX，确认三次均有透明照片。Ollama 热、抠图冷和 JVM 首次解析为 8.015 秒，两个模型热的后续解析为 1.963、1.680 秒。双冷和真实浏览器端到端尚未验收。日志见 `logs/resume-speed-final-build.log`、`logs/resume-speed-final-timing.log`。

1.0.38.27 蓝绿预演通过，正式执行缺管理员令牌，在写入前退出。管理员运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`。成功标志 `PRODUCTION_VERIFIED`，然后验收浏览器真实解析。回滚制品为 `builds/1.0.38.26-build-20261113_resume-profile/expert-system.jar`。
