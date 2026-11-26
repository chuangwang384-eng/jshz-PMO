# Agent 任务交接记录 — 公告板块审查修复 + 置顶排序

## 1. 基本信息
- 任务编号：20261126-announcement-fixes
- 任务名称：公告板块 P2/P3 审查修复（8 项）+ 需求四置顶排序管理
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-26
- 当前状态：已完成待验收（1.0.155 已上线）
- 优先级：P1
- 审查来源：本会话 2026-11-26 公告板块开发审查（P2-1~P2-4、P3-1~P3-5）

## 2. 目标与验收条件
- 修复审查发现的全部 P2/P3 项（P3-1~P3-5 一并含）；交付需求四（置顶组内上移/下移）。
- 验收条件：
  - [ ] 失效时间早于生效时间/（新建时）早于当前时间被拒绝（前后端双校验）
  - [ ] 员工公告列表只出现生效中公告（与抽屉/角标同口径）
  - [ ] 已读统计 读+未读=在职总数，进度不超 100%，停用用户已读不计入
  - [ ] 并发重复标记已读不再 500
  - [ ] 搜索关键词命中 HTML 标签不再误报
  - [ ] 管理列表已读进度无逐行 N+1；生效口径服务内单处实现
  - [ ] 管理模式开关按账号隔离；切页容量回第 1 页
  - [ ] 置顶公告可上移/下移，顺序对列表/抽屉/工作台一致生效

## 3. 项目基线
- 分支 main；起点提交 20cca6b（1.0.154）；目标版本 1.0.155
- 核实时间：2026-11-26

## 4/5. 变更清单
| 项 | 文件 | 说明 |
| --- | --- | --- |
| P2-1 | AnnouncementService.applyForm/create、AnnouncementFormDialog.vue | expire>effective 双端校验；新建时 expire<now 拒绝（编辑存量保留历史过期不受限；未携带 expireTime=清空为永久，既有契约不变） |
| P2-2 | AnnouncementService.pageQuery | 员工视角一律 applyEffective（scope 参数保留兼容不再分支）；AnnouncementList 阅读模式不再出现未生效/已过期公告 |
| P2-3 | AnnouncementService.readStats/fillAux/activeUserIds | 全部按 sys_user.status=1 口径；已读名单过滤停用用户；detail() 复用 readStats |
| P2-4 | AnnouncementService.insertReadQuietly | 撞 uk_announcement_user 视为幂等成功；markRead/markAllEffectiveRead 共用 |
| P3-1 | AnnouncementController/Service | 删除 GET /effective 死端点与 effectiveList/MAX_EFFECTIVE_LIMIT（前端无消费方，grep 证实） |
| P3-2 | AnnouncementService.pageQuery 关键词分支 | title LIKE 预筛 + Jsoup 纯文本过滤 + 内存分页（公告量小可接受；注释注明规模前提） |
| P3-3 | AnnouncementService.applyEffective/fillAux | 生效口径服务内单处实现（3 消费方共用）；已读进度/总数批量查询；工作台 SQL ORDER BY 同步（top_sort） |
| P3-4 | AnnouncementList.vue | 管理模式开关 key 加 authStore.id |
| P3-5 | AnnouncementList.vue | size-change 回第 1 页 |
| 需求四 | 实体 topSort、迁移 20261126_announcement_top_sort.sql（守卫加列+存量回填，回填守卫防覆盖人工排序）、init.sql 同步、POST /{id}/move + 权限迁移（announcement:manage，init.sql id=156）、AnnouncementList ↑/↓ 按钮（边界禁用）、DashboardReadService ORDER BY | 排序值小=靠前；取消置顶保留值（重新置顶恢复原位）；新置顶取 min-1 排最前 |

- 既有契约保持：编辑未携带 expireTime 清空为永久（applyForm 注释）；置顶操作日志复用 TOP/UPDATE_ANNOUNCEMENT，未新增字典项。

## 6. 测试与验证
| 验证项 | 结果 |
| --- | --- |
| 新增 AnnouncementServiceFixTest（13 例：P2-1×3/P2-2×2/P2-4×2/P2-3×1/P3-2×1/需求四×4） | 全部通过 |
| 全量 mvn test | 见发布记录（进行中） |
| npm run build | 通过，is-not-exported=0 |
| parity / 迁移 | 发布阶段执行并验证回填 |
| 浏览器人工验收（置顶排序、员工列表口径、催办文案） | 未执行，待业务验收 |

## 7. 风险与回滚
- 行为变更点：员工列表不再显示未生效/已过期公告（P2-2 决策=统一口径）；搜索改为纯文本匹配（英文标题词仍可命中标题 LIKE）。
- top_sort 迁移回滚：DROP COLUMN 即可，旧代码不感知；回填守卫保证重放不覆盖人工排序。
- 回滚走 release-full Rollback（前端 root 回指 + previous jar）。

## 8. 文档同步
- [x] 交接记录+摘要；发布记录（发布后补链接）
- [ ] architecture.md 公告小节（随发布提交更新）
- 未执行项：浏览器人工验收（标注于发布记录）

## 9. 下一位 Agent 指令
1. 检查 /health 版本与迁移台账（20261126_announcement_*）。
2. 用管理员账号验收：新建公告填过期失效时间被拒；置顶 3 条做上移/下移并核对抽屉顺序；员工账号确认列表无未生效公告。
3. 未经确认禁止：改员工生效口径（P2-2 为审查决策）。
