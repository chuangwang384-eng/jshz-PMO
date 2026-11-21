# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261121-show-wysiwyg-edit
- 任务名称：串场所见即所得编辑 + 结构收敛 + 圆框照片（1.0.107 已发布）
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-21
- 当前状态：已发布上线，待业务验收
- 优先级：P1

## 2. 目标与验收条件
- 业务目标（用户五项需求）：①专家信息页可保存；②删议程页；③专家详情页照片圆框、简历页含圆形照片；④预览演示所见即所得编辑全部信息；⑤删编辑器入口、结构收敛为「封面-章节过场-专家详情-…-结束」。
- 验收条件：
  - [x] data-edit 锚点全文本覆盖（冒烟断言）
  - [x] 编辑模式真机激活（虚线框/提示条/按钮态）
  - [x] 结构收敛（57 项目 17 页：封面+2 章节+11 讨论墙+2 出场+致谢，无议程/文字页）
  - [x] 出场页圆框照片、简历页圆形头像
  - [ ] 用户真机完整实测页内编辑→保存修改闭环

## 3. 项目基线
- main 分支；version.txt=1.0.107；/health=1.0.107；root=1.0.107-wysiwyg-edit；数据库无变更；核实 2026-11-21 11:59

## 4/5. 实际变更
- 后端 ProjectShowService.java：slideHtml 签名改页面序号；全部渲染函数加 data-edit 锚点（ed() helper）；deckTail 增编辑模式脚本（E 键/__toggleEdit/__isEditing/editTip）；bio photo-card 改圆形；resumeHtml 增 rp-head 圆形头像（persons 取首个有图者）
- 前端 showDeck.js：resumeSlideV2 带 persons（头像入简历页）
- 前端 ShowWizard.vue：builtSlides 删议程页/无专家文字页；专家卡「保存」按钮；Step3 改内嵌 iframe（editableDeck 快照 + toggleEdit/saveEdits/writeEditPath 编辑闭环）+ 归档 HTML 按钮；删「进入编辑器」入口
- 后端测试：断言补 rp-avatar/data-edit 路径
- 无新增依赖；无 API 变化（data-edit 是渲染产物）

## 6. 测试与验证
| 验证项 | 结果 | 备注 |
| --- | --- | --- |
| 目标测试 | 6/6 绿 | rp-avatar/data-edit 断言 |
| 前端构建 | 通过，告警 0 | |
| preview 冒烟 | data-edit 路径/编辑脚本/圆形照片全命中 | 项目 62 |
| 真机 | Step3 内嵌预览 + 编辑模式激活核验 | 项目 57，未写用户草稿 |
| 页内编辑→保存闭环全流程 | 静态核验 + 机制审查（writeEditPath 单测未覆盖，标注未执行） | 用户实测 |

## 7. 未完成事项
| 事项 | 下一步 | 责任方 |
| --- | --- | --- |
| 页内编辑排版级能力（字号/位置） | 下一迭代 | 负责人排期 |
| writeEditPath 单测 | 随下批补 | 后续任务 |
| 冒烟项目 62 删除 | 管理员 | 管理员 |
| 旧编辑器页面保留但无入口（/project/:id/show 仍可访问） | 如需彻底删除请负责人确认 | 负责人决策 |

## 8. 风险与回滚
- 回滚：前端 nginx-before（1.0.107）+ 后端 previous jar；数据库无迁移。
- 风险：页内编辑写回依赖 data-edit 路径与 deck 结构对齐——改渲染函数时必须同步（渲染函数与路径一一对应）；editableDeck 快照机制下，第二步修改后须重新进入第三步刷新快照（已知行为）。

## 9. 文档同步
- [x] release 记录、handover README、本记录
- [ ] architecture.md：串场段落已含向导路由说明，页内编辑机制随下批统一补

## 10. 下一位 Agent 接手指令
1. 检查 /health=1.0.107 与 git log。
2. 改任何渲染函数必须保持 data-edit 路径与 Slide 字段对应（writeEditPath 解析依赖）。
3. 未经确认禁止：删除旧扁平渲染路径；向页内编辑引入位置拖拽（未排期）。
