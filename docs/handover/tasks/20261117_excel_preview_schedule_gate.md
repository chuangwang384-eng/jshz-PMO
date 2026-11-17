# 报价Excel表格预览复用与日程阶段专家全匹配推进校验

## 1. 基本信息
- 任务编号：20261117-excel-preview-schedule-gate
- 任务名称：项目文件 Excel 表格预览复用知识星球方案 + 日程上传阶段专家全匹配推进校验
- 主责 Agent：ZCode
- 交接时间：2026-11-17
- 当前状态：已发布待业务复核（1.0.49，2026-11-17 11:04 上线）
- 优先级：P1
- 工作分支：main（负责人指示后续修改均在 main 直接进行）

## 2. 目标与验收条件
- 业务目标：
  1. 项目文件（含对客报价）预览 xls/xlsx 时不再经 LibreOffice 转 PDF，完全复用知识星球的 Excel 表格预览方案，不写重复逻辑；
  2. 「日程上传」阶段推进条件收紧：须已上传日程且当前日程全部参会专家（project_speaker）匹配到专家库（expert_id 非空），全部匹配后才能推进。
- 本次范围：ProjectController 新增 workbook 端点；KnowledgeWorkbookPreview 增加 fetcher 注入；ProjectDetail 预览分支与待办映射；ProjectService.missingRequiredTemplates 日程阶段校验；配套单测。
- 不在本次范围：PDF/PPT/图片预览链路（不变）；日程解析与专家匹配算法本身；知识星球页面（仅组件加可选 prop，行为不变）。
- 验收条件：
  - [x] 报价 xlsx 预览抽屉显示原生表格（工作表切换/行列分页），非 PDF 画布
  - [x] 匿名访问 workbook 端点 401；非 Excel 文件业务错误
  - [x] 日程阶段无日程时待办=「上传日程」，推进被拒
  - [x] 存在库外专家时待办=「专家匹配入库（N 位库外专家）」，推进被拒（单测覆盖）
  - [x] 全部匹配后推进放行（单测覆盖）

## 3. 项目基线
- 分支：main；基线提交 `01ee970`（1.0.48 发布文档），本次改动叠加其上
- 运行环境：本机生产（8080 已上线 1.0.48），验收用 8081 影子 + vite 5199
- 以上信息核实时间：2026-11-17

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据 |
| --- | --- | --- | --- |
| 项目侧 workbook 端点 | ProjectController.java | 完成 | 影子实测 sheets=['会议报价'] 6行5列 |
| ExcelPreviewService 复用 | 无新增解析代码 | 完成 | 零复制（复用知识库同一 Service） |
| KnowledgeWorkbookPreview fetcher prop | components/KnowledgeWorkbookPreview.vue | 完成 | 知识星球默认行为不变 |
| ProjectDetail Excel 预览分支 | ProjectDetail.vue | 完成 | 浏览器视觉核验=原生表格 |
| 日程阶段推进校验 | ProjectService.missingRequiredTemplates | 完成 | 影子实测 missing=['上传日程'] |
| 待办跳转锚点 | ProjectDetail.vue（#schedule-upload/#schedule-speakers） | 完成 | 实测 rect.top=360 可见 |

## 5. 实际变更
- 修改文件：
  - `backend/.../controller/ProjectController.java`：注入 ExcelPreviewService；新增 `GET /api/project/files/{fileName}/workbook`（权限与文件流端点同口径）
  - `backend/.../service/ProjectService.java`：missingRequiredTemplates 增加日程上传阶段校验（无日程→「上传日程」；expert_id NULL 计数→「专家匹配入库（N 位库外专家）」）
  - `backend/.../service/ProjectAccessRegressionTest.java`：新增 3 用例（无日程拒/未匹配拒/全匹配放行）
  - `backend/.../service/ProjectResumeAvatarTest.java`：ProjectController 构造参数 +1 同步补 null（@RequiredArgsConstructor 连锁，既有测试坑）
  - `frontend/src/api/project.js`：fetchProjectWorkbook
  - `frontend/src/components/KnowledgeWorkbookPreview.vue`：可选 fetcher prop（默认知识库 API，无行为变化）
  - `frontend/src/views/project/ProjectDetail.vue`：预览抽屉 excel 模式；openFilePreview xls/xlsx 分支；resolveLabel/resolveMissing 新增两待办映射；#schedule-upload/#schedule-speakers 锚点；.preview-excel 样式
- 数据库/迁移：无
- API/接口契约：新增 GET /api/project/files/{fileName}/workbook（sheet/rowPage/columnPage，返回结构与知识库 workbook 完全一致）；missingRequiredTemplates 新增两个业务项文案
- 页面/路由/权限：无路由变化；workbook 端点报价文件走 requireQuoteFileReadable
- 状态字典成对修改（17.7）：待办文案后端生成、前端 resolveLabel/resolveMissing 成对同步（ProjectDetail.vue ↔ ProjectService.java）

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 单元测试 | mvn test -Dtest='ProjectAccessRegressionTest,ProjectResumeAvatarTest'（注入 JWT_SECRET） | 通过（14/14） |  |
| 前端构建 | npm run build + is-not-exported 计数 | 通过（告警 0） |  |
| workbook 端点 | 影子 8081 curl 真实报价文件 | 通过（code 0，解析正常） | 匿名 401、非 Excel 业务错误 |
| 日程待办 | 影子 8081：54 推进到日程阶段后 detail | 通过（missing=['上传日程']） |  |
| 浏览器验收 | vite 5199 + IAB：待办卡片/跳转/Excel 预览截图 | 通过（视觉核验原生表格） | 影子后端 CORS 需含预览域名 |
| 生产发布 | 三件套（蓝绿+graceful，无迁移） | 通过（1.0.49 / 9343361，生产核验全过） | 发布记录 ../releases/20261117-excel-sched-gate-1.0.49.md |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 生产发布 | 负责人未指示 | 功能未上线 | 版本 bump 1.0.49 + 三件套 | 负责人决策 |
| 「专家匹配入库」含 N 的浏览器人工走查 | 需已解析日程且存在库外专家的真实数据 | 低（渲染走通用待办卡+同款跳转），单测已覆盖后端逻辑 | 发布后用真实项目复核 | 业务方 |

## 8. 风险与回滚
- 已知问题：日程上传为当前最末阶段（sort=99，无下一阶段），推进校验在 UI 上暂无触发入口；校验逻辑已就位，新增后续阶段后自动生效
- 兼容性：KnowledgeWorkbookPreview 默认行为不变（知识星球无感知）；PDF/图片预览链路未动
- 回滚：revert 本次提交即可，无数据库变更
- 测试数据残留：项目 54 处于日程上传阶段（验收推进），阶段变更动态含验收记录；项目 61（1.0.48 验收遗留）仍待管理员删除

## 9. 文档同步
- [x] 架构文档（architecture.md 增两段）
- [x] 最新交接摘要
- [x] 任务交接记录（本文件）
- [ ] 部署文档/发布说明（发布时补）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git log --oneline -3` 应含本次提交；`curl 127.0.0.1:8080/health` 应为 1.0.48（本次改动未发布）
2. 然后执行：如需发布，bump version.txt+package.json 至 1.0.49 → build-all.ps1 → 三件套（无迁移）
3. 验收标准：报价 xlsx 预览为表格；日程阶段待办与推进联动
4. 未经确认禁止：修改知识星球 workbook 默认行为；删除 project_speaker 数据
5. 需要负责人决策：日程阶段之后是否新增阶段（影响推进入口可见性）
