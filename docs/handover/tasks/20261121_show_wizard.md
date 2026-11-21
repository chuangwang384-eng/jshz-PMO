# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261121-show-wizard
- 任务名称：串场预览 undefined 修复（1.0.102）+「PPT 制作」三步向导（1.0.103）
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-21
- 当前状态：已发布上线（1.0.102 hotfix + 1.0.103 全量），待业务验收
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：①修复「新网页演示」打开显示 undefined；②「PPT 制作」直进三步向导网页：选模板 → 日程提取（专家证件照圆形裁剪、简介提取）→ 预览演示。
- 本次范围：见 releases/20261121-show-wizard-1.0.103.md「内容」。
- 不在本次范围：后端 rembg 抠图端点（决策：前端圆形裁剪即可）；向导完全取代编辑器（决策：编辑器保留）。
- 验收条件：
  - [x] undefined 修复（request 拦截器已解包，前端不再二次解构）
  - [x] 向导三步流程真机核验（模板卡/17 环节提取/专家照片+简介自动载入/圆形裁剪输出/22 页大纲）
  - [x] 模板 bio 页圆形头像渲染核验
  - [ ] 用户在真机 Chrome 实测：PPT 制作新标签打开向导、预览演示 blob 新标签
  - [ ] 业务验收完整走一遍向导 → 编辑器 → 归档

## 3. 项目基线
- 分支：main；提交 c339965（发布时工作区已含本轮改动，发布后本轮代码已提交）
- 版本：version.txt=1.0.103；/health version=1.0.103 gitShort=c339965；前端 root=1.0.103-show-wizard
- 数据库：无变更
- 核实时间：2026-11-21 10:28

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据 |
| --- | --- | --- | --- |
| undefined 修复 | ProjectShowEditor.vue presentNewTab | 完成 | 1.0.102 已上线 |
| 三步向导 | views/project/ShowWizard.vue（新，约 340 行） | 完成 | 真机截图核验 |
| 圆形裁剪组件 | components/CropAvatar.vue（新） | 完成 | 圆框默认套脸、预览产出 |
| 议程解析单一真源 | utils/showDeck.js（新）；ProjectShowEditor.generate 改用 | 完成 | 行为不变，消除 2 处复制 |
| 入口改造 | ProjectDetail.vue「PPT 制作」→ window.open 向导；router +show-wizard | 完成 | 路由核验 |
| 模板圆形头像 | ProjectShowService.java BASE_CSS .photo | 完成 | 截图核验 |

## 5. 实际变更
- 新增：ShowWizard.vue、CropAvatar.vue、utils/showDeck.js、本记录、releases 记录
- 修改：ProjectShowEditor.vue、ProjectDetail.vue、router/index.js、ProjectShowService.java
- API：无新增（复用 preview/saveStageForm/agenda/overview/expert）
- 数据库：无；环境变量：无；依赖：无

## 6. 测试与验证
| 验证项 | 结果 | 备注 |
| --- | --- | --- |
| mvn 全量 | 226 run / 0 fail / 16 skipped，BUILD SUCCESS | 1.0.103 前 |
| npm build | 通过，is-not-exported=0 | 两次构建均查 |
| 真机向导 | Step1/2/3 截图核验通过（项目 57：17 环节、董桂兰专家完整链路） | 未写库 |
| preview 冒烟 | 项目 62 code=0，圆形卡/bio/图片注入命中；渲染截图核验 | temp/show-deck-sample/circle-103.html |
| blob 新标签 | IAB 环境拦截 window.open，**未核验**，待用户真机 Chrome 实测 | 已知未核验项 |

## 7. 未完成事项与阻塞
| 事项 | 下一步 | 责任方 |
| --- | --- | --- |
| blob 新标签真机实测 | 用户刷新页面走「PPT 制作 → 预览演示」 | 用户 |
| 业务验收 | 向导 → 编辑器 → 归档全流程 | 业务 |
| 冒烟项目 62 删除 | 管理员 | 管理员 |
| Step1 双「下一步」冗余 | 随下次改动优化 | 后续任务 |

## 8. 风险与回滚
- 回滚：前端 nginx-before（1.0.102/1.0.103 两份）+ 后端 previous jar（1.0.103 归档含 1.0.101）；数据库无迁移。
- 兼容性：编辑器与向导共用 stage-form 草稿结构（slides/background/template/fontScale），互相兼容；向导产出 background 为空（主K功能仍在编辑器）。

## 9. 文档同步
- [x] releases/20261121-show-wizard-1.0.103.md、handover README、architecture.md（向导路由）
- [ ] deployment.md 无需更新（无环境/命令变更）

## 10. 下一位 Agent 接手指令
1. 检查 `git log --oneline -3` 与 /health version=1.0.103。
2. 用户反馈 blob 打开异常时先查 `presentNewTab`（ProjectShowEditor/ShowWizard 同逻辑）与 previewShow 拦截器解包约定。
3. 未经确认禁止：改动 utils/showDeck.js 契约（两消费方）；把 rembg 抠图并入向导（负责人未选该方案）。
