# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261121-show-detail-pages
- 任务名称：专家详情页合并（出场页取消）/ 过场页多人头像 / 页内简介字号 / 重复识别修复 / 页面增删（1.0.110 + 1.0.111 已发布）
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-21
- 当前状态：已发布上线，待业务验收
- 优先级：P1

## 2. 目标与验收条件
- 业务目标（用户五项反馈）：①取消单独头像出场页；②专家信息页=头像左（含大会角色/姓名/单位）+简介右（单列优先、>8 条两列）；③页内批量调整简介字号；④多人章节过场页含标题+专家头像；⑤修复重复识别 + 向导页面增删入口。
- 验收条件：
  - [x] matchSpeakers 环节内去重（重复 chip 修复）
  - [x] 简历页重构 rp2（左头像/角色/姓名/单位，右条目单列/两列自适应）
  - [x] 过场页多人头像（≤6，占位圆兜底）
  - [x] Slide.fontSize + 页内字号工具条 + 向导写回（1.0.111 修复 resolvePhotos 丢弃 fontSize 的 bug）
  - [x] Step4 页面清单删除按钮 + 文字页添加入口
  - [ ] 用户真机完整实测

## 3. 项目基线
- main；version.txt=1.0.111；/health=1.0.111；root=1.0.111-fs-hotfix；数据库无变更；核实 2026-11-21 13:38

## 4/5. 实际变更
- 后端 ProjectShowService.java：Slide 加 fontSize；resumeHtml 重构（rp2 布局，persons 取主人物）；sectionHtml 全员头像；resolvePhotos 修复 fontSize 透传；编辑脚本 fs-bar；CSS rp2/sec-people/fs-bar（旧 rp-cols/rp-head 删除）
- 前端 showDeck.js：matchSpeakers 环节内去重；sectionSlideV2 persons 数组化
- 前端 ShowWizard.vue：builtSlides 删 bio 生成/过场页全员头像；Step4 大纲删除按钮+添加文字页下拉；saveEdits 读 data-fs-changed 写回 fontSize
- 后端测试：断言更新（rp2-*/sec-people）+ fontSize 传递断言
- 无 API 端点/依赖/数据库变化

## 6. 测试与验证
| 验证项 | 结果 | 备注 |
| --- | --- | --- |
| mvn 全量 | 228 run / 0 fail / 16 skipped | 1.0.111 后 |
| npm build | 通过，告警 0 | |
| preview 冒烟 | --rfs:1.2/rp2-avatar/sec-people/fs-bar/data-edit 全命中 | 项目 62 |
| 真机 | 待用户实测 | — |

## 7. 未完成事项
| 事项 | 下一步 | 责任方 |
| --- | --- | --- |
| 业务验收 | 用户完整走四步实测 | 业务 |
| 冒烟项目 62 删除 | 管理员 | 管理员 |
| 页内排版级扩展 | 后续迭代 | 负责人排期 |

## 8. 风险与回滚
- 回滚：1.0.110/1.0.111 各自 nginx-before + previous jar；数据库无迁移。
- 兼容性：旧 bio 草稿仍走旧渲染路径（k-bio legacy 分支保留）；fontSize 为可选字段旧稿安全。
- 事件：1.0.110 曾带 resolvePhotos 丢 fontSize 的 bug 上线，1.0.111 已修复（冒烟断言补齐）。

## 9. 文档同步
- [x] release 记录（110/111 合并）、handover README、本记录

## 10. 下一位 Agent 接手指令
1. 检查 /health=1.0.111。
2. 改 Slide 契约时注意 fontSize 已是第 8 个组件（构造点测试 10 处均已带）。
3. 未经确认禁止：恢复出场页生成；删除 bio legacy 渲染路径。
