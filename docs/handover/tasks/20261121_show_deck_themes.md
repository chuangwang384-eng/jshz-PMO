# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261121-show-deck-themes
- 任务名称：串场制作模板化——四套内置模板 + 只读预览 + 新网页演示
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-21
- 当前状态：已完成待验收（**1.0.101 已发布上线**，后端蓝绿 + 前端 graceful，发布后验证全绿；发布中一次编排参数错位事故已处置并记入 deployment.md §4.1）
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：串场制作阶段的 HTML 串场稿达到参考稿（E:\王创\串场案例\聚例辉煌研讨会-串场-HTML版.html）视觉水准——名字流光、对话逐步切换；编辑器可切换内置模板、字号、主 K，并在新网页即时演示未保存草稿。
- 本次范围：后端 render() 模板引擎重写 + /show/preview 预览端点；前端编辑器模板/字号选择与新网页演示按钮。
- 不在本次范围：生产发布与版本号 bump（待批准）；编辑器内逐页视觉即改即所得（编辑区仍为示意底色）；参考稿的背景视差/山脉线（未纳入，粒子已纳入 gold/tech）。
- 验收条件：
  - [x] 四套主题（gold/tech/paper/bamboo）均可渲染且含流光标题、data-step 步进、16:9 缩放、键盘翻页、HUD 进度条（浏览器截图逐套核验通过）
  - [x] /show/preview 与 /show/html 共用同一 render()，预览产物与归档一致（单一真源）
  - [x] 旧草稿 JSON（无 template/fontScale 字段）可正常加载并回退 gold/标准字号
  - [x] 后端全量测试通过；前端构建通过且 is-not-exported 告警=0
  - [ ] 负责人在真实项目串场制作阶段走一遍「生成→编辑→新网页演示→归档」流程验收

## 3. 项目基线
- 分支：main
- 当前提交号：dc40312（工作区另有大量本任务之前未提交改动，与本任务无关，未触碰）
- 工作区状态：本任务新增/修改文件见第 5 节，未提交
- 前端/后端/数据库版本：version.txt=1.0.101（发布编排自动同步）；无数据库变更
- 运行环境及关键端口：本机即生产（8080/80），后端蓝绿已切 1.0.101（gitShort=dc40312）、前端 root 已切 `1.0.101-show-deck-themes`
- 发布记录：docs/releases/20261121-show-deck-themes-1.0.101.md
- 以上信息核实时间：2026-11-21

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据或说明 |
| --- | --- | --- | --- |
| Deck 增加 template/fontScale 字段（旧字段顺序不变） | ProjectShowService.java:31-35 | 完成 | 旧草稿反序列化为 null 后回退，测试覆盖 |
| render() 重写为「公共引擎 + 主题 CSS 变量层」 | ProjectShowService.java:118-176 | 完成 | 流光 .flow（background-clip:text + shine 6s）、.ppt-step-hidden 步进、fit() 16:9、粒子 canvas（gold/tech） |
| 四套主题 GOLD/TECH/PAPER/BAMBOO 注册 | ProjectShowService.java:39-88 | 完成 | themeOf 未知 id 回退 GOLD |
| 页面分型 cover/agenda/ending/bio/content | ProjectShowService.kindOf | 完成 | 沿用编辑器既有 label 约定（会议串场/会议议程/茶歇/主持与致辞/会议环节/感谢参会），bio=带配图页 |
| 只读预览端点 | ProjectController.java:368-373 + ProjectShowService.preview | 完成 | requireProjectReadable，不落盘不写操作记录 |
| 编辑器模板/字号选择 + 新网页演示 | ProjectShowEditor.vue | 完成 | previewShow→Blob→window.open，60s 后 revokeObjectURL |
| 编辑器示意底色随模板 | ProjectShowEditor.vue frameTints | 完成 | 浅色主题加深色薄纱保证示意文字可读，非 WYSIWYG |
| 单测更新+新增 | ProjectStageDeliverableTest | 完成 | 构造点随 Deck 签名同步；新增 deckRendersThemeVariantsStepsAndClampsFontScale |

## 5. 实际变更
- 新增文件：docs/handover/tasks/20261121_show_deck_themes.md（本记录）
- 修改文件：
  - backend/src/main/java/com/jshz/expert/service/ProjectShowService.java（Deck/Theme 记录、THEMES、render 重写、preview）
  - backend/src/main/java/com/jshz/expert/controller/ProjectController.java（POST /{id}/show/preview）
  - backend/src/test/java/com/jshz/expert/service/ProjectStageDeliverableTest.java（构造点补 null,null；新增主题用例）
  - frontend/src/api/project.js（previewShow）
  - frontend/src/views/project/ProjectShowEditor.vue（模板/字号选择、新网页演示、frameTints、draft 恢复）
  - docs/architecture.md（§项目阶段交付物 349-351 行）
- 删除文件：无（临时样例导出用例 ShowDeckRenderDumpTest.java 核验后已删除，未入库）
- 数据库/迁移：无（template/fontScale 存于既有 project_stage_form.formData JSON）
- API/接口契约：新增 POST /api/project/{id}/show/preview（ApiResponse<String>，需登录+项目阅读权限）；/show/html 请求体新增可选 template/fontScale 字段，向后兼容
- 页面/路由/权限：无新路由；preview 仅读权限、publish 保持操作权限+阶段校验不变
- 配置/环境变量：无新增
- 依赖变更：无
- 前端映射同步（17.7）：templates 列表与后端 THEMES 成对（ProjectShowEditor.vue ↔ ProjectShowService.THEMES），id 以后端为准

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 目标单测 | mvn test -Dtest=ProjectStageDeliverableTest（JWT_SECRET+revision+gitShort 注入） | 通过 | 5/5 绿 |
| 全量单测 | mvn test -Drevision=1.0.100 -DgitShort=dc40312（JWT_SECRET 32 字节） | 通过 | 226 run / 0 fail / 0 error / 16 skipped；首次失败系测试密钥 136bit 过短，换 32 字节后全绿，非代码缺陷 |
| 前端构建 | npm run build | 通过 | 33.66s；is-not-exported 告警数=0（grep -ci 复核） |
| 渲染核验 | 临时导出用例生成四主题样例至 temp/show-deck-sample/，本地 127.0.0.1:18923 + 浏览器逐套截图（封面/议程步进/人物页/内容页/主K覆盖） | 通过 | 样例保留于 temp/show-deck-sample/（gitignored）供人工复查；截图确认流光、步进过渡态、照片卡、胶囊、HUD、k-on 白字覆盖均正确；临时服务器与用例已清理 |
| 权限/安全验证 | 运行时对照：无阅读权限项目 preview 返回 403、不存在项目 400；文本 HtmlUtils 转义、图片白名单、CSP 保持 default-src 'none'（代码审查级） | 通过 | 403/400 对照实测通过 |
| 部署健康检查 | Preflight 6/6 → 蓝绿 → graceful → /health=1.0.101、root 指针、首页 200×3、bundle v1.0.101、API 经 Nginx 200、preview 冒烟 9844B 特征全中（t-gold/shine/data-step×4/k-cover/a-wide） | 通过 | 发布事故与补跑见 docs/releases/20261121-show-deck-themes-1.0.101.md；logs/release-state.json 保留 FAILED 历史事实，终态以发布记录为准 |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 冒烟项目 62「发布冒烟-待删」 | 发布验证按惯例创建（同历史 54/56/57） | 占用一条测试数据 | 管理员删除 | 管理员 |
| 真实项目业务验收 | 待业务方执行 | — | 串场制作阶段走「生成→换模板→新网页演示→归档」全流程 | 负责人/业务 |
| 编辑器示意底色与正式渲染存在差异 | 编辑区为示意图非 WYSIWYG | 低 | 如需 WYSIWYG 另立任务评估 | 负责人决策 |

## 8. 风险与回滚
- 已知问题：编辑器示意底色对浅色主题（paper/bamboo）加深色薄纱，与无主 K 时的正式渲染（深字浅底）不一致；粒子仅 gold/tech 开启（有意为之）。
- 数据风险：无（无数据库结构变更；旧草稿 JSON 直接兼容）。
- 兼容性风险：历史上已归档的旧版串场 HTML 文件仍是旧视觉，仅重新归档后才会变成新模板；/show/html 请求体新增可选字段向后兼容。
- 回滚步骤：`git checkout -- backend/src/main/java/com/jshz/expert/service/ProjectShowService.java backend/src/main/java/com/jshz/expert/controller/ProjectController.java backend/src/test/java/com/jshz/expert/service/ProjectStageDeliverableTest.java frontend/src/api/project.js frontend/src/views/project/ProjectShowEditor.vue docs/architecture.md`；已发布场景则回退 jar 与前端快照。
- 回滚限制：重新归档过新模板的项目，其归档 HTML 回滚后仍是新视觉文件（文件产物不随代码回滚）。

## 9. 文档同步
- [x] 架构文档（docs/architecture.md §项目阶段交付物）
- [x] 交接记录（本文件）+ docs/handover/README.md 摘要
- [ ] 部署文档：无需更新（无环境变量/端口/启动命令变更）
- [ ] 接口文档：接口契约已写入 architecture.md，独立接口文档不存在
- [ ] 发布说明：待发布后按 releases/ 惯例补
- 未完成项及原因：发布说明依赖发布动作本身

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`/`git diff` 确认本记录第 5 节文件清单与工作区一致；`grep -c "THEMES\|themeOf" backend/src/main/java/com/jshz/expert/service/ProjectShowService.java` 确认模板引擎在位。
2. 然后执行：如已获发布批准——按 docs/deployment.md 发布链 bump version.txt/package.json 后先 vite build 再打包 jar（勿在 backend/ 内 mvn package），蓝绿+graceful 发布。
3. 验收标准：发布后健康检查通过；真实项目串场阶段「从日程生成→换模板→新网页演示→归档 HTML」全流程可用。
4. 未经确认禁止：直接发布生产；修改 THEMES 的 id（前端 templates 列表与其成对）；把编辑器示意底色当成正式渲染口径。
5. 需要负责人决策：是否需要 WYSIWYG 编辑区；是否为 paper/bamboo 也开启粒子。
