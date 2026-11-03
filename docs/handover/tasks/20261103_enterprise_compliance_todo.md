# Agent 任务交接记录

## 1. 基本信息
- 任务：企业合规板块「个人关注驱动待办」改造（REQ/DES v3.0）
- 主责：ZCode（GLM，AI 生成+人工确认）；交接时间：2026-11-03 10:10
- 状态：已完成待验收（API+浏览器验收通过）
- 版本：**运行版 1.0.22 不变**（后端 jar 蓝绿多次，frontend 1.0.22 不变）

## 2. 交付
- 文档入库：需求/设计 v3.0（提交 1b20ab9）
- 迁移：database/migration/20261103_add_compliance_tables.sql（5 表幂等，已执行生产）
- 后端（deb387a）：Compliance 5 实体/Mapper、ComplianceParseService（xlsx POI 表头映射+文本规则解析）、ComplianceService（全局 CRUD/草稿确认/跨版本匹配 item_code/我的待办=收藏∪责任人/个人进度三档+阻塞原因/提醒设置/维护人只读进度/历史）、ComplianceItemController（15 接口）、ComplianceDueScheduler（每日 08:30 提醒幂等，配置 expert.compliance.*）、Knowledge 上传/更新联动解析、Notification +5 消息类型
- 前端（deb387a）：MyTodoSection/MaintenanceBoard 组件、EnterpriseCompliance.vue 重构（我的待办区+文档库+维护人看板入口 v-permission 语义）、api/compliance.js、MessageCenter +5 类型、个人中心入口

## 3. 部署过程问题与修复（均已入 jar）
1. application.yml 重复 `expert:` 键 → 启动失败（DuplicateKeyException）→ 合并；插入位置一度破坏 knowledge 子键层级 → 已修正为合规块独立
2. updateVersion 事务内联动异常导致 rollback-only → 联动移至 Controller 层（事务外静默）
3. 跨版本匹配 insert 顺序（migrate 在 insert 前 → item_id null）与 item_code 唯一键冲突 → insert→migrate→删除旧事项顺序修正

## 4. 验收（测试账号 测试/111111 + 临时维护角色，API+浏览器）
- 上传 xlsx → 自动解析 2 条草稿（表头映射+阶段归类）+ item_code 生成 ✓
- 确认发布（published=2、通知收藏用户）✓
- 收藏 → my-todo 2 条（source=收藏）✓；进度/备注/阻塞/提醒设置 ✓
- 责任人事项 → my-todo 出现（source=责任人）；取消收藏后仍保留 ✓；重新收藏恢复（进度继承验证 PROCESSING+阻塞）✓
- 跨版本更新 → E1 自动匹配继承（code EC-2026-0001 释放/继承）、E3 新增草稿、E4 旧事项标删除隐藏 ✓；确认后个人进度继承到新事项 ✓
- 定时提醒（E8-E11 幂等矩阵）：代码+表结构+唯一约束就位；cron 08:30 未到点未实测推送——**标记未实测**
- 浏览器：企业合规页统计卡/表格/来源标签/维护人看板入口/文档库 ✓
- 临时维护角色已从测试账号移除（重启后彻底生效）

## 5. 风险与未完成
- 提醒推送真实触发未实测（cron 每日 08:30；可临时调 cron 验证）
- 工作日提醒、历史待办 UI：按需求下轮
- 解析规则随样本持续维护；PDF/DOCX 规则解析为行级粗规则
- ⚠️ 测试账号权限缓存：移除维护角色后需服务重启才彻底回收（当前会话仍持有，测试号不用于生产）

## 6. 下一步
需求人按需求 §7/§7.1 复核；提醒 cron 实测可选；历史待办 UI 下轮。

## 7. 追加修复：文档预览失败（2026-11-03 10:40，需求人反馈，未改版本号）
- 现象：点「预览」显示「预览失败，可下载查看」（xlsx/PDF 全部受影响）
- 排查结论：后端 GET /docs/files/{name}?preview=true 返回 200 + 合法 PDF（pypdf 可解析），问题在前端渲染
- **三个叠加根因**（均已在 KnowledgeDocList.vue 修复）：
  1. `pdfDoc = ref(null)` → Vue 深响应式 Proxy 包裹 pdfjs 文档对象，pdfjs 内部私有字段（#field）经 Proxy 访问抛 `Cannot read from private field`（**主因**，堆栈定位到 pagesNumber getter）→ 改 `shallowRef`
  2. worker 以独立 `.mjs` 发布，nginx 未配置 .mjs MIME（实测返回 `application/octet-stream`），浏览器拒绝模块加载 → `Setting up fake worker failed` → 改为把 worker 源码以 blob URL 注入（自带 MIME，不依赖服务器配置）
  3. `pdfjs-dist` 6.x 现代构建依赖 `Promise.withResolvers` 等新 API，旧浏览器直接抛错 → 改用官方 `legacy` 兼容构建
- 验证：浏览器实测预览抽屉正常显示 canvas（595×841，页码 1/1），内容可见；构建产物无独立 worker .mjs
- 提交：2bfc6c7；前端原子发布（版本不变 1.0.22）
- 备注：曾尝试改 nginx `mime.types` 加 `js mjs` 并重启 nginx，但 UAC 提权未生效，已回滚该配置（blob worker 方案不依赖它）；如后续需恢复 .mjs 独立文件方式，再执行该项并重启 Nginx
