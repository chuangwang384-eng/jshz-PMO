# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261121-show-structured-pages
- 任务名称：串场结构化页面模型——封面/过场/出场/简历/讨论墙 + 人脸预抠图（1.0.106 已发布）
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-21
- 当前状态：已发布上线，待业务验收
- 优先级：P1

## 2. 目标与验收条件
- 业务目标（对应七张参考图）：①模板确定后阅览全部专家头像，系统自动识别人脸预抠图、用户调整保存；②出场页=头像+大会角色+流光姓名+单位（全部模板）；③封面页文字可编辑（摆脱旧编辑器）；④日程每章节自动过场页；⑤讨论环节嘉宾墙+每位嘉宾介绍页；⑥简历按语义分段两列菱形列表。
- 不在本次范围：页内所见即所得拖拽编辑（下一迭代）；后端 rembg 抠图（前端圆形裁剪即可）。
- 验收条件：
  - [x] 五类新页面渲染核验（preview 冒烟 13/13 特征 + 五页截图与参考图一致）
  - [x] 人脸自动识别真机核验（圆框精准套脸、可手动微调、失败回退）
  - [x] 旧扁平草稿兼容（单测断言 + 启发式回退）
  - [x] 章节过场/讨论墙/每位嘉宾介绍页自动生成
  - [ ] 用户真机完整走一遍向导产出真实会议串场稿

## 3. 项目基线
- 分支 main；version.txt=1.0.106；/health version=1.0.106；root=1.0.106-show-structured
- 数据库无变更；核实时间 2026-11-21 11:31

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据 |
| --- | --- | --- | --- |
| Slide 契约扩展 + Person + 五类页面渲染 | ProjectShowService.java（重写） | 完成 | 单测 6/6 + 全量 227 绿 |
| 人脸自动识别 | CropAvatar.vue（@vladmandic/face-api tiny，模型 public/models 196KB） | 完成 | 真机截图圆框套脸 |
| 简历语义分段 | utils/showDeck.js splitResumeItems | 完成 | chip 预览核验 |
| 向导重构 | ShowWizard.vue（封面定制/章节分组/讨论墙/嘉宾页） | 完成 | 真机核验 |
| 依赖 | frontend/package.json + public/models（face-api） | 完成 | 构建通过 |

## 5. 实际变更
- 新增：public/models/{tiny_face_detector_model.bin,manifest}、本记录、release 记录
- 修改：ProjectShowService.java、ProjectStageDeliverableTest.java、showDeck.js、CropAvatar.vue、ShowWizard.vue、frontend/package.json（+@vladmandic/face-api）
- API：无新增端点；Slide JSON 契约扩展（向后兼容，未知字段对旧包安全忽略）
- 依赖变更：@vladmandic/face-api（唯一新增第三方包，理由：前端人脸检测，无可复用既有实现）

## 6. 测试与验证
| 验证项 | 结果 | 备注 |
| --- | --- | --- |
| mvn 全量 | 227 run / 0 fail / 16 skipped | 含新用例 |
| npm build | 通过，is-not-exported=0 | 两次构建 |
| preview 冒烟 | 13/13 特征命中（k-cover/cover-title/ci-k/k-section/sec-num/sec-wm/photo-card/role-badge/rp-cols/k-panel/p-card/p-avatar/k-ending） | 项目 62 |
| 五页截图 | 封面/过场/出场/简历/讨论墙与参考图一致 | temp/show-deck-sample/v2-106.html |
| 真机向导 | 封面定制自动预填 + 人脸识别 + 语义分段 | 项目 57 |

## 7. 未完成事项与阻塞
| 事项 | 下一步 | 责任方 |
| --- | --- | --- |
| 页内所见即所得编辑 | 下一迭代（复用参考稿 hppt-editing 钩子） | 负责人决策排期 |
| 业务验收 | 向导产出真实会议稿 | 业务 |
| 冒烟项目 62 删除 | 管理员 | 管理员 |

## 8. 风险与回滚
- 回滚：前端 nginx-before（1.0.106）+ 后端 previous jar；数据库无迁移。
- 兼容性：旧草稿/旧归档 HTML 不受影响；新 JSON 对旧包安全忽略（Spring Boot 默认不 fail unknown）。
- 性能：face-api chunk 懒加载、模型 196KB 内网加载；识别失败静默回退，不阻塞向导。

## 9. 文档同步
- [x] release/handover README/architecture（待补一行路由说明——已在 1.0.103 节基础上维护）
- 未完成：无

## 10. 下一位 Agent 接手指令
1. 检查 git log 与 /health=1.0.106。
2. 改动页面结构必须同时：BASE_CSS（共享层）+ showDeck.js（构建）+ ProjectStageDeliverableTest（断言），禁止只改一处。
3. 未经确认禁止：删除旧扁平渲染路径（legacyContentHtml/kindOf——旧草稿兼容依赖）；把 rembg 并入向导。
