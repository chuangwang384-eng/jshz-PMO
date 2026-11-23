# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261123-expert-hardening
- 任务名称：专家库 P1 数据一致性 + P2 前端健壮性修复 + 死代码清理
- 主责 Agent：ZCode
- 交接时间：2026-11-23
- 当前状态：已完成待验收（**未发布**，本轮用户未授权部署）
- 优先级：P1

## 2. 目标与验收条件
- 修复专家库审查的数据一致性类（P1）与前端体验/健壮性类（P2）bug；删除已查出的未启用/死代码；验证现有功能不受影响。
- 不在范围：S1/S2/S3（已由负责人拍板为刻意设计，见 docs/decisions/2026-11-23-专家库权限信任模型-架构决策记录.md）；ExpertManage 旧页下线/规则对齐（待决策）；新需求四项。
- 验收条件：后端测试全绿；前端构建零 is-not-exported；死代码残留 grep 为 0。

## 3. 项目基线
- 分支 main；生产后端 1.0.130 / 前端 1.0.131（本次改动尚未发布）。

## 4-5. 实际变更
**后端 P1（事务与文件时机）**
- `ExpertService`：`deleteExpert`/`updateFromMultipart`/`createFromMultipart` 加 `@Transactional`；新照片统一走 `storePhotoWithRollbackCleanup`（回滚删新文件）；旧照片/删除专家的物理文件统一 `afterCommit` 提交后删除。DB 失败不再丢文件。
- `ExpertDocumentService`：upload/deleteDocument/deleteByExpert 加事务；落盘文件回滚清理；物理删除推迟到提交后；IOException 由静默吞掉改 log.warn；同毫秒文件名碰撞防护（stamp 递增）。

**后端死代码删除**
- 端点：`GET /api/experts/{id}/download-count`、`GET /api/experts/export/md`（前端零调用；ExportCompatController 的 /api/export/md 未动）。
- 方法：`ExpertFilter` 7 参旧构造器、`ResumeParseService.addCandidate`、`sanitizeUnitAgainstName`；ExpertController 未用的 downloadAuditService 字段与 sensitive 参数声明。
- `ResumeParseService.parse` 失败补 log.warn（含堆栈）。
- **保留**：`photoMatteService == null` 判空——隔离测试以 (null, null) 构造，移除致 4 测试失败后恢复（复审误判，已加注释防再犯）。

**前端 P2**
- `PhotoImage.vue`：blob URL 换图/卸载 revoke + 序号防竞态。
- `ExpertList.vue`：fetchList 序号防竞态 + 失败提示（空态文案区分失败）；size 变更重置页码；openDetail 错误兜底 + null 防护 + 预览 revoke + 重置上一专家简历状态；removeDocument 取消/失败兜底；4 处导出吞错改提示；新增/管理员新增弹窗预览 revoke；卸载释放全部 blob；简历解析序号防两弹窗串扰。
- `ExpertManage.vue`：fetchList 序号 + 失败提示；size 变更重置页码；4 处下载吞错改提示。
- sensitive 契约清理：expert.js / ExpertList / ExpertManage / BatchResumeDialog 不再传 sensitive:1，失真注释修正。

**前端死代码删除**：`resumeTarget` 死变量（以解析序号替代其 intended 职责）、`.card-apply-btn` 死 CSS。

## 6. 测试与验证
| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量测试 | `mvn test`（JWT_SECRET≥32B） | 234 通过 / 0 失败 / 16 跳过 |
| 前端构建 | `npm run build` | 通过，is-not-exported=0 |
| 死代码残留 grep | download-count/export-md/sensitive/resumeTarget/card-apply-btn/死方法 | 全部 0（仅 1 条历史注释提及旧方法名） |
| 浏览器端到端回归 | — | **未执行**（需发布后验证：专家列表翻页/筛选、详情开合、照片预览、简历解析、资料上传删除、导出） |

## 7. 未完成事项
| 事项 | 原因 | 下一步 |
| --- | --- | --- |
| 发布上线 | 本轮未获部署授权 | 负责人批准后走后端蓝绿 + 前端 graceful（建议 1.0.132） |
| 浏览器回归 | 依赖发布 | 发布后按第 6 节清单过一遍 |

## 8. 风险与回滚
- 行为变化：物理文件删除时机从「立即」改为「事务提交后」——正常路径结果一致，失败路径更安全；无接口契约变化（删除的两个端点前端零调用）。
- 回滚：代码 revert 即可，无数据库迁移。

## 9. 文档同步
- [x] 本交接记录
- [ ] 发布记录（待发布时补）

## 10. 下一位 Agent 的接手指令
1. `git log -1` 确认本任务提交；`mvn test` 复核。
2. 获授权后发布 1.0.132（后端蓝绿 + 前端 graceful），发布后浏览器回归专家库核心流程。
3. 未经确认禁止：改动 S1/S2/S3 相关权限/文件访问逻辑（已拍板刻意设计）；下线 ExpertManage。

---

## 附：1.0.132 已发布（2026-11-23）
本记录第 7 节「发布上线」已完成：合并 282851c 与身份证有效期/资料预览/批量全选新功能，后端蓝绿 + 前端 graceful `1.0.132-expert-idcard-expiry`（gitShort 861f060）。数据库 additive 迁移已执行并并入 init.sql。OCR 真实图片端到端验证通过（2007.11.28-2027.11.28）。详见 [发布记录](../../releases/20261123-expert-idcard-expiry-1.0.132.md)。
**事实更正**：S1 初审查结论有误——DbPermissionFilter 按 permission 表对专家写端点强制 expert:add/edit/delete（实测 403），并非「真空」；决策记录已补更正。
