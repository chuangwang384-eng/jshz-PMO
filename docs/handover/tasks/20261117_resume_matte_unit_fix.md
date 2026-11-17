# 简历抠图"仅人头"、单位混入姓名与职称词边界修复

## 1. 基本信息
- 任务编号：20261117-resume-matte-unit-fix
- 任务名称：修复专家库简历解析抠图"仅识别到人头"、单位字段混入姓名、职称词边界误拒
- 主责 Agent：ZCode
- 交接时间：2026-11-17
- 当前状态：已完成待验收（未发布）
- 优先级：P1
- 工作分支：main（负责人指示后续均在 main 直接开发）

## 2. 目标与验收条件
- 业务目标：
  1. 抠图完整提取人物有效区域，避免仅保留人头/人像发虚；结果清晰、边界准确、可供后续解析；
  2. 单位字段不含姓名（"李向东，汝州市第一人民医院" → "汝州市第一人民医院"）。
- 样本：`1_4_1_李向东简介 -.docx`（蓝底证件照 290×416）、`武素芳简介(2).pptx`（蓝底证件照 413×626）。
- 验收条件：
  - [x] 抠图输出人像区域不透明（≥200 占比 li 0.58 / wu 0.52，原身体为 ~130 半透明）
  - [x] 抠图按前景裁剪、人物从头到脚连续覆盖（逐 1/8 高度带无零断层）
  - [x] 新 worker 脚本按后端协议端到端 READY→OK→OK
  - [x] 单位净化：报告案例与 5 组边界单测通过
  - [x] ResumeParseServiceTest 18/18（1 可选跳过）、后端 test-compile 通过

## 3. 根因分析（按用户要求的五个维度）
| 维度 | 问题 | 修复 |
| --- | --- | --- |
| 图像预处理 | 原图直接转 RGBA 送推理，无增强 | 小图先 LANCZOS 放大到最长边 640 |
| 人物区域检测 | u2net 是通用显著性检测，对人像身体衰减、散点噪声 | 闭运算+填洞+保留最大连通域（人物主体） |
| 裁剪边界判定 | 原输出保留整幅含透明边距，未聚焦人物 | 按前景 bbox+3% 边距裁剪 |
| 背景分割 | u2net 输出软 alpha（身体多 128~254 半透明）→ 发虚 | gamma(0.45) 硬化 + 主体内强制 ≥200 不透明 |
| 分辨率处理 | resizePortraitInput 只缩 >1200，小图（<320）掩码细节不足 | worker 内对 <640 小图放大（u2net 内部 320 输入） |

**为何不用 u2net_human_seg**：人像专用模型权重本机未装，实测 GitHub 下载 ~50kB/s（176MB 需约 1 小时），生产应离线安全，故不引入新模型依赖；用现有 u2net + 精修链即可达标。scipy 缺省时降级为纯 alpha 硬化，不阻断抠图。

**单位混入姓名根因**：设计稿把姓名与单位写同一行（"李向东，汝州市第一人民医院甲状腺乳腺外科副主任医师"）。`extractFields` 单位兜底按机构后缀"医院"截断 `substring(0, idx+len)`，把前置姓名一并带入；LLM 语义路径只要值以机构后缀结尾且原文包含即采纳，同样会带入姓名。

## 4. 实际变更
- 修改文件：
  - `backend/src/main/resources/photomatte-worker.py`：新增 `refine()` 精修链（放大→分割→硬化→主体提取→裁剪）；协议 READY/OK/ERROR 不变，Java 侧零改动
  - `backend/.../service/ResumeParseService.java`：新增 `sanitizeUnit(raw, knownName)`（接入 `extractFields` 单位 put 前与 `buildResult` LLM 语义单位校验）；新增 `extractTitleByLongestMatch`（职称兜底改"尾边界+最长匹配"，替换旧前后非汉字边界）
  - `backend/.../service/ResumeParseServiceTest.java`：新增 `namePrefixedUnitLineIsSeparated`、`sanitizeUnitStripsNameAndKeepsOrg`、`embeddedTitleAfterDepartmentExtracted`、`longestTitleWinsOverSubstring`、`nurseHeadNotMatchedAsNurse`
- 数据库/迁移：无
- API/接口契约：不变（解析返回结构不变，单位值更干净）
- 依赖：worker 用 numpy/scipy（生产 venv 已具备；scipy 可选降级）；无新增第三方库

## 5. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 单元测试 | mvn test -Dtest=ResumeParseServiceTest（注入 JWT_SECRET） | 21/21 通过（1 可选跳过） |
| 解析速度对比 | 临时基准（已删）：同文本旧/新职称算法各 30000 次 | 职称扫描旧 ~1.2µs→新 ~1.5µs（+0.3µs）；完整 extractFields ~50µs 不变；无实质影响 |
| 后端编译 | mvn test-compile | 通过 |
| 抠图复现 | venv rembg(u2net) 原图 vs 精修对比 | 原身体半透明/武素芳下半被删 → 精修后不透明连续 |
| worker 端到端 | 新脚本按 stdin JSON 协议跑两样本 | READY→OK→OK，输出 446×566 / 368×445 |
| 前端构建 | — | 不适用（本次无前端改动） |
| 生产发布 | — | 未执行（待负责人指示） |

## 6. 风险与回滚
- 已知边界：职称"尾边界+最长匹配"对"护士长"仍正确排除（不命中"护士"），对"主任医师资格"这类后接汉字的仍不命中（与旧行为一致）；gamma 硬化对极端艺术照可能偏硬，证件照场景已验证良好。
- 速度影响：职称兜底由"首个命中即停"改为"全词表扫描取最长"，单次职称扫描 +0.3µs（微秒级），完整字段解析与整体解析（IO/OCR/rembg 秒级主导）无实质影响。
- 兼容性：worker 协议不变、Java 零改动；scipy 缺省自动降级；解析返回结构不变。
- 回滚：revert 本次提交即可，无数据库变更。
- 临时产物：样本解压与对比图在 `temp/resume-fix/`（gitignored）；已清理 u2net_human_seg 半成品下载。

## 7. 文档同步
- [x] 架构文档（architecture.md 增修复段）
- [x] 任务交接记录（本文件）
- [ ] 最新交接摘要（提交时同步）
- [ ] 发布说明（发布时补）

## 8. 下一位 Agent 的接手指令
1. 首先检查：`git log --oneline -3` 含本次提交；`curl 127.0.0.1:8080/health` 仍为 1.0.49（本次未发布）
2. 然后执行：如需发布，bump 1.0.50 → build-all.ps1 → 三件套（无迁移）；发布后用真实蓝底证件照简历复核抠图清晰度与单位字段
3. 验收标准：抠图人像完整清晰、单位不含姓名
4. 未经确认禁止：引入需联网下载的新抠图模型；回退职称"最长匹配"为旧前后非汉字边界（会重新误拒同行拼接职称）
5. 需要负责人决策：是否发布上线
