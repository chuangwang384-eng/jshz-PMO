# 发布记录：日程上传阶段附件删除 + 解析泛用性强化（1.0.37.29）

- 日期：2026-11-10
- 分支：codex/personal-dashboard-v11
- 状态：**已发布生产（2026-11-10 09:00，后端蓝绿 + 前端 graceful，无迁移）**，发布后核验通过；页面真机验收待 Owner
- 类型：功能迭代（日程上传阶段），**无数据库结构变更（无迁移脚本）**

## 1. 背景（Owner 2026-11-10 反馈）

1. 项目详情页日程上传阶段附件不可删除，同一文件重复上传堆积（截图实证 5 份同名附件）。
2. 解析操作逻辑不清晰：分不清哪份附件在参与解析。
3. 新上传【会议日程】三门峡(2)(2).pptx 解析准确率低，要求泛用性更强。

**Owner 已确认口径**：删生效日程附件 → 联动清除日程记录与候选；历史日程版本 → 不保留，直接删除（不做切换）。

## 2. 三门峡 pptx 结构取证（改造前根因）

解包 `ppt/slides/slide1.xml`（单页表格）取证：

- 表头为 **「时间/内容/嘉宾」**——旧 ruleParse 表头识别只认 `讲者/主持/主题`，表头定位失败 → **整张表被跳过**，全靠 Ollama(qwen2.5) 兜底 → 准确率低。这是根因。
- 讨论段姓名在**内容列**（“讨论：A 教授、B 教授…”），嘉宾列为空 → 即使表头命中也会漏抓 13 人。
- 纵向合并单元格导致环节行时间留空。
- 人名与“教授”跨单元格拆分（POI getText 可正确合并，非问题）；页脚为「会议主席」而旧正则只认「大会主席」。

## 3. 实际变更

### 后端
- `ScheduleParseService.java`
  - **上传改替换语义**：硬删旧生效份日程行与未确认候选（confirmed=1 人工成果保留）、软删旧日程附件，旧物理文件全库无引用时物理删——附件列表不再堆积历史版本。
  - **新增 `deleteScheduleDoc()`**：软删附件；若删的是生效日程文件，联动硬删该项目全部日程记录+未确认候选，文件无引用（含软删行）才物理删；操作留痕。
  - **ruleParse 泛用性强化**：
    - 表头识别改为「≥2 个列头关键词命中」（时间/主题/题目/议题/内容/讲者/嘉宾/主持/专家/讲），兼容「时间/内容/嘉宾」等列头；
    - 讨论段姓名落在内容列时从主题单元格提取，段名取标签词（“讨论”）；
    - 纵向合并单元格时间沿上一行；
    - 行级角色词判定：主席致辞→CHAIR、主持/第X环节→HOST、讨论→DISCUSSANT、多人同段→DISCUSSANT；
    - 无表头兜底：时间段锚定行（`\d{1,2}:\d{2}-\d{1,2}:\d{2}`）启发式提取；
    - 页脚兼容「大会主席/会议主席」；
    - `extractNames` 剥段首标签（讨论：/主持：）；`isPlausibleName` 拒含数字/字母串，黑名单增加 讨论/主持/嘉宾/环节/病例/扫码 等。
- `ProjectController.java`：新增 `DELETE /api/project/{id}/schedule/docs/{docId}`。

### 前端
- `ProjectDetail.vue`：日程阶段附件行加「生效日程」标签、解析状态文案、删除按钮（删除生效日程二次确认且文案明示联动清除候选）；上传按钮文案改“重新上传日程（替换当前份）”；提示语理顺。
- `api/project.js`：新增 `deleteScheduleDoc(id, docId)`。

### 版本
- `version.txt` → `revision=1.0.37.29`，`frontend/package.json` → `1.0.37.29`。

## 4. 验证结果（实测）

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 三门峡真实文件离线解析 | 临时 JUnit 读 E:\ 文件跑 extract+ruleParse（验证后已删除该临时测试） | **候选 23 人全量抓齐**：主席2+环节主持3+讲者4+讨论12+总结2，角色与时间段全对（改造前仅 Ollama 兜底零散结果） |
| 单测 | `mvn test -Dtest=ScheduleParseServiceTest` | 8/8 通过（新增三门峡结构回放、无表头时间锚定 2 用例） |
| 后端全量 | `mvn test`（注入 JWT_SECRET 须≥32字节，否则 contextLoads 因 WeakKeyException 失败，与本改动无关） | 96 通过 0 失败 9 跳过 |
| 前端构建 | `npm run build` | 通过 32.6s；rollup `is not exported` 警告数=0（门禁） |
| 真机验收 | 未执行（未发布） | 未执行 |

## 4.1 发布执行与发布后核验（2026-11-10 08:56–09:01 实测）

- 制品：`builds/1.0.37.29-build-20261110_085609/expert-system.jar`（出树打包，build-info version=1.0.37.29 / gitShort=ffba3c1 / buildTime=2026-11-10 08:56:38）；前端 `builds/1.0.37.29-build-20261110/dist`（主包 index-CxELBlX1.js 含 v1.0.37.29，ProjectDetail chunk 含「生效日程」删除功能）。
- 发布方式：`temp\deploy-13729.cmd`（自提权，复用 1.0.37.28 已验证链路：`release-backend.ps1` 蓝绿 + `deploy-frontend-graceful.ps1`）。前端 ReleaseId=`1.0.37.29-schedule-doc-delete-20261110`，graceful 重载 master PID 7696 未变，DEPLOY_EXIT=0。
- 发布后核验：
  - `/health` 直连 8080 与经 Nginx `/api/health` 均 **1.0.37.29 UP**（buildTime 与制品一致）；
  - Nginx root → `frontend/releases/1.0.37.29-schedule-doc-delete-20261110`；线上 index 主 bundle 与暂存产物哈希一致（index-CxELBlX1.js）；
  - SPA `/` 200；`/api/project/1/schedule` 未登录 **401**（鉴权未放松）；
  - 登录冒烟：测试/111111 code=0（该账号未绑角色 token=null，为既有已知口径；页面级验收 Owner 用绑定角色的账号点验）。

## 5. 风险与回滚

- 物理删文件仅在“全库（含软删行）无 project_doc 行引用同一 filePath”时执行且打日志；误删风险可控。
- 解析改动以新增兜底分支为主，原示例日程（10.23）用例全过，未动已验证路径。
- 回滚：revert 本次提交，无数据库迁移需要回补。

## 6. 遗留

- 姜建华（第一环节）时间继承为 14:00-14:05（表格合并单元格首行值，实际环节 14:05 开始）——候选可人工改，不影响验收。
- 图片/PDF 扫描日程识别率仍受 Tesseract 限制，失败走手动补录（既有口径）。
- 回滚方式：后端切回上一版 jar（backend/releases 归档），前端 Nginx root 指回 `1.0.37.28-dashboard-align-20261109`（备份指针见 `frontend/release-metadata/1.0.37.29-schedule-doc-delete-20261110.nginx-before.txt`）。
