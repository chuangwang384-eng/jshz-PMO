# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261120-expert-doc-upload-photolimit
- 任务名称：照片限额 5MB→10MB + 专家详情资料上传（医生证等）
- 主责 Agent：ZCode (GLM)
- 交接时间：2026-11-20
- 当前状态：已完成待验收（**未发布**；迁移 20261120_expert_document 随发布执行）
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：① 身份证等照片上传限额 5MB→10MB（Owner 提出，手机拍摄证件照常超 5MB）；② 专家详情弹窗底部新增「资料文件」上传区（医生证等资质资料，图片/pdf）。
- 本次范围：限额调整 + expert_document 表/接口/前端上传区。
- 不在本次范围：存储管理页的专家资料库占用统计（StorageAdminController scopes 未扩展，孤儿扫描不含专家资料库，已在风险中披露）。
- 验收条件：
  - [ ] 身份证照片 >5MB 且 ≤10MB 可上传成功
  - [ ] 专家详情弹窗底部可上传图片/pdf 资料、列表展示、可下载可删除
  - [ ] 非 pdf/图片被拒；>50MB 被拒
  - [ ] 删除专家时资料文件级联删除

## 3. 项目基线
- 分支 main；基线 1.0.85（线上）；check-schema-parity 于改动前实测通过（86 业务表无漂移）
- 核实时间：2026-11-20

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据 |
| --- | --- | --- | --- |
| photo-max-bytes 5MB→10MB（yml 真源 + MetaController/PhotoStorageService 占位兜底 + limits.js 离线兜底） | application.yml:82 等 | 完成 | 影响证件照/身份证/头像全部照片上传，Nginx 60m 与 multipart 50MB 不变 |
| 迁移：expert_document 表 + expert:edit 权限两行（POST/DELETE /api/experts/*/documents…） | database/migration/20261120_expert_document.sql，已并入 init.sql | 完成 | 可重跑（IF NOT EXISTS/IGNORE）；parity 改动前基线校验通过 |
| 实体/Mapper/Service | ExpertDocument.java / ExpertDocumentMapper.java / ExpertDocumentService.java | 完成 | 复用 FileNames/UploadGuard；上限复用 doc-max-bytes 50MB；扩展名 jpg/jpeg/png/gif/webp/bmp/pdf |
| 接口：GET/POST /api/experts/{id}/documents、DELETE …/documents/{docId}；专家删除级联删资料 | ExpertController.java、ExpertService.deleteExpert | 完成 | 操作日志记 ACTION_UPDATE_EXPERT；GET 登录即可（与照片一致），POST/DELETE 由 permission 数据 expert:edit 门控 |
| 静态映射 /api/files-doc/** → 专家资料库 | WebConfig.java、application.yml expert.document.storage-dir | 完成 | 登录即可访问（anyRequest.authenticated 兜底） |
| 前端：api 4 函数 + 详情弹窗「资料文件」区（上传/列表/下载/删除） | api/expert.js、views/ExpertList.vue | 完成 | 前端预检扩展名与 docMaxBytes；与照片/字段保存解耦，上传即生效 |

## 5. 实际变更
- 新增：ExpertDocument.java、ExpertDocumentMapper.java、ExpertDocumentService.java、database/migration/20261120_expert_document.sql
- 修改：application.yml（photo 10MB + document.storage-dir）、MetaController/PhotoStorageService（占位默认 10485760）、limits.js（兜底 10MB）、ExpertController、ExpertService、WebConfig、api/expert.js、ExpertList.vue、docs/architecture.md、docs/deployment.md、database/init.sql
- 数据库/迁移：新表 expert_document（expert_id/file_name/stored_name/file_size/created_by/create_time）+ permission 2 行；**随发布经 db-migrate 执行**，执行后业务表 86→87
- API 契约：新增三端点见上；无既有接口变更
- 配置/环境变量：无新增环境变量（目录走既有 STORAGE_ROOT 出口）；18.4 清单不变
- 依赖变更：无

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 后端编译 | mvn -DgitShort=… compile | 通过 |  |
| check-schema-parity | 改动前 + init.sql 并入后 | 改动前通过；并入后报 1 张幻影表（expert_document 未入生产库，待迁移，符合预期） |  |
| 前端构建 | npm run build | 通过（37.9s，is-not-exported 0） |  |
| 上传/删除/10MB 边界实测 | 未执行 | 需发布（含迁移）后线上验证 | 建议用测试账号 |
| 删除专家级联 | 未执行 | 未执行 | |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 发布（含迁移） | 须负责人批准 | 功能未生效 | 批准后：db-migrate → build-all → 后端蓝绿 → 前端 graceful；bump 1.0.86 | 负责人 |
| 架构/交接文档表数更新 | 迁移未执行，87/102 未实测 | 发布后须实测回写 | 发布核验阶段执行 `information_schema` 实测并更新 architecture.md 三处计数 | 发布 Agent |
| 存储管理页不含专家资料库 | 未扩 StorageAdminController scopes | 占用统计/孤儿扫描暂不覆盖该目录 | 如需覆盖另立小任务 | 待定 |

## 8. 风险与回滚
- 已知问题：/api/files-doc/** 登录即可访问（与照片 /api/files/** 一致），未做专家级隔离——与既有照片口径一致
- 数据风险：低（纯新增表）；删除资料不可恢复
- 回滚：前端按 nginx root 指针回滚；后端蓝绿回退旧包；expert_document 表可保留不回滚（旧包不访问）
- 回滚限制：已上传文件需手工清理

## 9. 文档同步
- [x] 架构文档（表清单加入 expert_document；计数待发布后回写）
- [x] 部署文档（STORAGE_ROOT 目录清单 + 限额变更）
- [x] init.sql / 迁移
- [x] 最新交接摘要
- [ ] 发布记录（随发布产出）

## 10. 下一位 Agent 的接手指令
1. 首先检查：git status 两文件改动仍在；parity 脚本幻影表仍为 expert_document（即迁移未执行）
2. 然后执行：获批后 bump 1.0.86 → build-all 全量 → db-migrate Apply 20261120_expert_document → release-backend → deploy-frontend-graceful → parity 复验 87 表 → architecture.md 计数回写
3. 验收标准：第 2 节四条验收条件（HTTP 200 ≠ 成功，以页面行为为准）
4. 未经确认禁止：未经批准发布；勿在 backend/ 内跑 mvn package
5. 需要负责人决策：存储管理是否覆盖专家资料库（小任务）；资料是否需要下载留痕（当前未接 DownloadAudit）
