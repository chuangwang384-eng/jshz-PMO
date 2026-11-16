# 2026-11-16 会议预算制作阶段 发布记录（后端 1.0.39 / 前端 1.0.39）

## 发布内容
- 项目流程新增「会议预算制作」阶段（project_phase id=10, sort=98，位于日程上传之前）
- 新表：project_budget / project_budget_item / project_budget_fee_standard（种子 13 条）
- 后端 1.0.39（gitShort 9513801），蓝绿 GracefulSwitch 发布，11/11 阶段全绿
- 前端 1.0.39-budget-stage-20261116，graceful root 切换，master PID 8036 未变，旧 root 保留可回退
- 功能明细见 [交接记录](../handover/tasks/20261116_budget_stage.md)

## 迁移执行记录
- `scripts/apply-migration-20261116-budget-stage.ps1` 首轮执行曾因 PS5.1 管道缺 `$OutputEncoding=UTF8` 导致中文变 `?`（phase id=9、fee 种子名乱码）；
  已确认新表无业务数据后清除乱码行，脚本补 UTF-8 修复（同 20261109 脚本写法）后重跑成功（phase id=10 正常）。
- 校验：活跃阶段=会议预算制作(98)/日程上传(99)；3 表就绪；种子 13 条中文正常。

## 验证结果（2026-11-16 11:30-11:50）
| 项 | 结果 |
| --- | --- |
| /health（8080 直连 + Nginx） | UP，version=1.0.39，gitShort=9513801 |
| 前端新 bundle | index-5432wUA5.js 经 Nginx 200 |
| 登录（测试/111111） | code=0 |
| GET /config/budget-fee-standards | 13 条正常 |
| ONLINE 项目(54) 预算骨架/保存并确认 | 1 行 ONLINE_QUOTE；确认后 total=8000.00，状态 CONFIRMED |
| 已确认单再存草稿 | 400「报价单已确认…」拦截 ✓ |
| OFFLINE 项目(56 新建"测试-线下预算") 骨架 | 24 行七节，收费标准预填区间取低值（日程5-10→5、台卡10-20→10、讲台花300-400→300、礼品200-300→200）✓ |
| OFFLINE 草稿保存合计 | 20000+10×500+4×500=27000.00 服务端计算 ✓ |
| 浏览器人工验收 | 未执行（待负责人，验收条目见交接记录 §2） |

## 回滚
- 后端：backend/releases/ 内 1.0.38.31 归档 + deploy-backend.ps1 -RollbackJarPath（迁移向后兼容：1.0.38 代码不读新表，可不回滚库）
- 前端：Nginx root 切回 1.0.38.31-project-sidebar-sticky-20261116（rollback.conf 备份于 frontend/release-metadata/1.0.39-budget-stage-20261116.rollback.conf.txt）
- 数据库（如需彻底回退）：DROP 三表 + DELETE phase id=10（SQL 头回滚预案；注意 54/56 两个测试项目已产生 budget 数据，回退前备份）

## 追加：1.0.39.1（仅前端，2026-11-16 12:00）
Owner 口径调整：会议预算制作阶段只展示 生命周期+阶段资料（预算面板）；参会专家卡片仅日程上传阶段展示；右侧「当前待办/项目文件/合规要求注意事项」在预算阶段隐藏。ReleaseId 1.0.39.1-budget-stage-ui-20261116，graceful 切换验证通过（index-C9emcdCy.js 经 Nginx 200）。浏览器人工复核待 Owner。

## 追加：1.0.39.2（仅前端，2026-11-16 12:15）
Owner 口径再调整：右侧悬浮卡片（项目成员/当前待办/项目文件/合规要求注意事项）恢复为**所有阶段都展示**（撤销 .1 中预算阶段隐藏待办/文件/合规的做法；参会专家仍仅日程上传阶段展示）；预算明细"数量"列改为整数输入（step=1、precision=0、step-strictly）。ReleaseId 1.0.39.2-budget-stage-ui-20261116，Nginx 已返回 index-UqZtX2EI.js。浏览器复核待 Owner。

## 追加：1.0.40（后端，2026-11-16 12:17）
缺陷修复：新建项目起始阶段原硬编码「日程上传」（2026-11-09 流程重塑遗留），预算阶段插入后导致新项目跳过预算。改为取激活阶段字典 sort 最小者（现=会议预算制作）。项目57已手工拨回第一阶段；验证：新建项目 59 起始=会议预算制作（验证后已删）。mvn test 149 通过；蓝绿 11 阶段无 FAIL；/health version=1.0.40 gitShort=9315732。
