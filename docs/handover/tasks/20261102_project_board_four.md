# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261102-project-board-four
- 任务名称：项目板块四项需求（REQ/DES-20261102）
- 主责 Agent：ZCode（GLM，AI 生成+人工确认）
- 交接时间：2026-11-02 14:40
- 当前状态：已完成待验收
- 优先级：P1
- 版本：v1.0.21（后端发布号 1.012，蓝绿）；前端原子发布

## 2. 目标与范围
- 需求一：详情页「项目进度」块置顶（纯模板顺序）
- 需求二：立项阶段文本保存报错根治——ParamUtils.parseLong + 全量加固（核实实为 8 处裸解析，ParamUtils.java:23 为 toLong 自身受保护实现不算）+ 前端 editRich 补 templateId
- 需求三：阶段回退增强——跨级回退（原因必填）+ canRollback 独立字段 + project_phase_history 历史表；推进逻辑不变
- 范围纪律：仅 project 板块文件，其他板块未动 ✓

## 3. 基线
- 分支 feature/project-optimize，提交 a17693e
- 后端 1.012 蓝绿（wrapper: logs/deploy-1p012-wrapper.log，jar: .build-1.0.21-20261102_141814，health=1.0.21 UP）
- 迁移 20261102_project_phase_history.sql：演练库 2 遍幂等 + 生产执行（表已建）

## 4/5. 变更
- 后端：ProjectService（changePhase 加 reason+跨级+历史写入事务；detail 加 canRollback/phaseHistory 最近20条）、ProjectController（6 处加固+reason 传参）、NotificationController/ApplicationService（各 1 处加固）、ParamUtils（+parseLong）、新增实体/Mapper
- 前端：ProjectDetail.vue（卡片顺序、editRich 补 templateId、回退弹窗含跨级提示+原因必填、按钮改 canRollback）、api/project.js（changePhase 带 reason）
- 数据库：新表 project_phase_history；回滚 DROP TABLE 即可（无既有数据依赖）

## 6. 验收结果（测试账号 测试/111111，API+UI）
| 项 | 结果 |
| --- | --- |
| 进度块第一屏置顶（进度→基本信息→预算） | 通过（DOM 卡片顺序验证） |
| saveRichText 缺 templateId（原 500 场景） | 通过：业务异常而非 NumberFormatException/500 |
| templateId 非数字/"null"/空串 | 通过：业务异常 |
| 带 templateId 正常保存+持久化 | 通过 |
| addExpert 缺 expertId | 通过：业务异常 |
| canRollback/phaseHistory 返回 | 通过 |
| 相邻推进材料校验不变 | 通过（缺材料拦截） |
| 相邻回退+原因、跨级回退+原因 | 通过（history: (2,1,ROLLBACK)/(4,1,ROLLBACK) 落库含原因与操作人） |
| 回退缺原因 | 通过：「回退原因不能为空」 |
| 他人项目 changePhase 拒绝 | 通过 |
| UI 回退弹窗（候选含跨级标记/原因置灰逻辑/提示） | 通过（4→立项 跨级回退全链路） |
| CANCELED 恢复 | 未执行（代码保留原逻辑，未单独构造场景） |

## 7-8. 风险与回滚
- 行为变更点：回退不再限制相邻（原「仅相邻推进或回退」改为「推进相邻、回退任意更早阶段」）——已按需求实现
- 回滚：后端 jar 归档 backend/releases/1.012-*/previous；前端回滚脚本；表 DROP 即可
- 验收项目 5 已软删留痕

## 9. 文档同步
- [x] 本交接 + README 摘要；迁移脚本自含
- [ ] docs/deployment.md R-1 记录（部署脚本自动追加，待入库提交）
- [ ] architecture.md 需补 project_phase_history（下一位）

## 10. 下一位
1. 需求人页面复核三项；2. deployment.md 记录入库；3. 禁止：未经确认删历史表
