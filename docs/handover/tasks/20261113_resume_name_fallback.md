# 简历姓名兜底修复

## 1.0.38.25 上线后简介补充修正

发布候选 1.0.38.26 基于线上 1.0.38.25 源码快照，覆盖本次解析服务及测试。隔离执行 `mvn -Drevision=1.0.38.26 -DgitShort=1fa4cf2 package`，125 项通过、10 项跳过，原件回归通过。蓝绿预演通过，正式执行在管理员检查处退出，线上健康仍为 1.0.38.25。管理员执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`，成功后验收原件。候选与回滚 JAR 路径及哈希见脚本。日志 `logs/resume-profile-release-build.log` 和 `logs/resume-profile-release-dryrun.log`。

生产 Ollama 日志中 qwen2.5:3b 两次冷请求分别为 25.17、40.69 秒，模型加载开始到 runner 就绪约 22.4、30.6 秒，其中出现 GPU 探测超时。模型权重 2.105 GB。假设 D 盘实际持续读取 0.5–2 GB/s，纯读取约 1–4 秒。模型目录迁移后若其余开销相近，首次请求可先按 10–25 秒规划，实际需做冷缓存对照。未卸载生产模型或变更配置。

经本机 `/api/health` 确认生产已运行 1.0.38.25。姓名校验原先在简介收集之后执行，导致误作姓名的学历行已被排除。现在先剔除无效姓名，再收集简介，只按整行排除已提取的值。原件加模拟模型误判回归确认普通模式姓名与学历简介均正确，正文专用模式仍无姓名。使用 `mvn -q -DgitShort=1fa4cf2 -Dtest=ResumeParseServiceTest test` 并提供本机原件路径，14 项全通过。简介修正待发布，未改接口、数据库或运行配置。发布可沿用蓝绿流程，回滚基线为线上 1.0.38.25。

Ollama 服务目录位于 E 盘 SATA HDD，D 盘为 NVMe SSD，核对来自 NSSM、`Get-Partition` 和 `Get-PhysicalDisk`。模型目录约 5.25 GB，当前 qwen2.5:3b 驻留约 2.16 GB，配置保留 60 分钟。三次合成短请求的热加载 9–14 ms，生成约 578–584 ms，未测冷启动。迁盘预期主要改善冷加载，未执行迁移。

- 任务编号：20261113-resume-name-fallback。主责：Codex。日期：2026-11-13。
- 状态：代码已完成，生产切换已阻塞。工作区基线 `codex/personal-dashboard-v11` / `1fa4cf2`，开工时干净。生产后端 1.0.38.24 / `96f35d1`，候选以该线上构建源码为基线。

原件幻灯片文本有学历描述，没有目标姓名。原规则只剔除少量关键词，且无法拆掉文件名词段的「副本」「简介」。`ResumeParseService` 校验姓名后再决定是否回退，支持带间隔号的姓名。项目正文专用模式不回退。更新服务测试及架构说明，接口、数据库、权限和运行配置无变更。

隔离目录运行 `mvn -Drevision=1.0.38.25 -DgitShort=96f35d1 package`，启用本机原件回归，常规模式识别为预期姓名，正文模式无姓名。首轮修正了测试位置及非 Spring 实例上传上限为零的问题，最终 135 项中 125 通过、10 跳过、0 失败。后端 11 阶段发布预演通过，日志在 `logs/resume-name-release-retest.log`、`logs/resume-name-release-dryrun.log`。

候选 `builds/1.0.38.25-build-20261113_resume-name/expert-system.jar`。正式执行在管理员令牌检查处退出。由管理员 PowerShell 执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Apply`。见到 `PRODUCTION_VERIFIED` 后，在专家库重新上传并解析原件，核对姓名候选。线上真实解析尚未验收。回滚基线为已核对与当前运行制品哈希一致的 `builds/1.0.38.24-build-20261113_142151/expert-system.jar`。
