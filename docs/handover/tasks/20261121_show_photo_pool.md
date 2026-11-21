# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261121-show-photo-pool
- 任务名称：串场照片池化去重 + 专家资料全局化 + 裁剪圆框调大（1.0.108 已发布）
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-21
- 当前状态：已发布上线，待业务验收
- 优先级：P1

## 2. 目标与验收条件
- 业务目标（用户四反馈）：①解决「串场文件过大」（照片重复嵌入）；②专家信息全局一份不再重复编辑；③裁剪圆框调大露出肩膀；④说明图片过大的影响。
- 验收条件：
  - [x] 照片池化（ph: 引用）后端解析 + 单测（两页共享池图 + 非法池图拒绝）
  - [x] 专家资料池全局联动（同 expertId/姓名共享对象）
  - [x] 裁剪默认框 62% + 识别框 1.9×脸框（含肩）+ WebP 输出
  - [ ] 用户真机重测：重复专家只编辑一次、大稿件不再超限

## 3. 项目基线
- main；version.txt=1.0.108；/health=1.0.108；root=1.0.108-photo-pool；数据库无变更；核实 2026-11-21 12:22

## 4/5. 实际变更
- 后端 ProjectShowService.java：Deck 加 photos 池（≤300 张、逐张 image() 校验）；resolvePhotos/photoOf 解析 ph: 引用；旧结构兼容
- 前端 showDeck.js：dedupePhotos（dataURL 唯一化入池、字段改 ph: 引用）
- 前端 ShowWizard.vue：peoplePool 专家资料池（profile 共享、roleBySegment 按环节独立）；deck() 输出池化
- 前端 CropAvatar.vue：默认 62%、识别框 0.95×脸框+圆心下移、WebP 输出回退 PNG
- 无 API 端点变化；无新增依赖

## 6. 测试与验证
| 验证项 | 结果 | 备注 |
| --- | --- | --- |
| mvn 全量 | 228 run / 0 fail / 16 skipped | 含 photoPool 用例 |
| npm build | 通过，告警 0 | |
| preview 冒烟 | ph: 解析正确（出场+简历共享池图）、无残留 | 项目 62 |
| 用户实测 | 待做：重复专家联动 + 大稿保存 | 用户 |

## 7. 未完成事项
| 事项 | 下一步 | 责任方 |
| --- | --- | --- |
| 旧草稿照片未池化 | 重新生成/保存即池化，无需迁移 | — |
| 业务验收 | 用户真机重测 | 业务 |
| 冒烟项目 62 删除 | 管理员 | 管理员 |

## 8. 风险与回滚
- 回滚：前端 nginx-before（1.0.108）+ 后端 previous jar；数据库无迁移。
- 兼容性：photos/ph: 为可选结构，旧草稿/旧编辑器产物（dataURL 直存）均兼容；两版本混用安全。
- 注意：编辑器（ProjectShowEditor）生成的 deck 无 photos——其照片仍为每页内嵌（旧路径，量小不致超限）。

## 9. 文档同步
- [x] release 记录、handover README、本记录
- [ ] architecture.md 串场段落（Deck.photos 契约）随下批统一补

## 10. 下一位 Agent 接手指令
1. 检查 /health=1.0.108。
2. 改动照片链路（CropAvatar/showDeck/resolvePhotos）必须保持三处一致：前端 ph: 生成、后端解析、IMAGE 白名单校验。
3. 未经确认禁止：删除旧 dataURL 直存兼容路径（旧草稿依赖）。
