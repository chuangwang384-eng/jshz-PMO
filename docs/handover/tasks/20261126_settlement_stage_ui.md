# 项目详情 · 结算资料上传前端改造

2026-11-26，主责 Codex。基线 `main@94723b1`，生产后端 `1.0.151`，前端 root 为 `1.0.151-directory-fallback`。

状态为已完成待验收。功能提交 `eb6535d` 已快进合入本地 `main`，生产前端于 08:45 完成平滑切换。

## 范围与实现

- `ProjectDetail.vue` 在当前结算阶段使用紧凑生命周期和底部推进条，合并重复的待办呈现。项目文件、合规汇总和其他阶段保留既有入口。
- `ProjectSettlementMaterials.vue` 以接口返回资料为准，提供必填进度、缺项定位、待办/已完成/全部筛选、分组折叠、当前文件表格、预览、下载和确认删除。继续展示现有细项说明、时限文字和建议核对角色。现有资料接口不返回文件体积，文件表格采用 API 有值的列。
- 批量选择或拖入文件后，按唯一匹配的可上传细项建议归类。用户确认后逐细项提交，有文件的细项追加，成功项移除，失败项保留。成本单沿用在线表单。
- 酒店发票按当前批次单一明确标注的人民币金额汇总。接受 `¥3000`、`8656元` 等格式，忽略无金额标记或包含多个金额的文件名。仅用于页面核对。
- 数据库、接口、权限、依赖和运行配置均无代码变更。当天补件、文件大小限制、阶段推进、项目类型适用性及项目文件归档沿用服务端规则。

## 验证与发布

- 构建命令为 `npm --prefix frontend run build -- --outDir <隔离目录>/dist-stage --emptyOutDir`。构建通过，Vite 给出大分块提示。
- `node frontend/tests/settlement-stage-browser.mjs dist-stage` 通过，`PLAYWRIGHT_MODULE` 指向已有 Playwright。隔离 Edge 覆盖 1280px 阶段条、600px 阶段选择器、首屏底栏、折叠、定位、进度、归类、追加、部分失败重试和删除确认。生产登录业务验收需有权限的账号与项目。
- 补跑既有 `project-resume-reuse-browser.mjs` 时，新制品和发布前快照均在等待「批量建档」按钮的同一断言超时。这项旁路检查未通过，未观察到新旧制品差异。
- 发布仅切换前端。候选发布 ID 为 `1.0.151-settlement-stage-ui`，前端构建版本继续与生产后端健康接口对齐。发布预演已验证目标路径，生产切换使用管理员 PowerShell 运行 `scripts/deploy-frontend-graceful.ps1`，并核对实服首页哈希、入口资源、Nginx 根目录和后端健康。回滚使用本次生成的 `frontend/release-metadata/1.0.151-settlement-stage-ui.rollback.conf.txt`，按部署文档校验配置及平滑重载。
- 生产确认 Nginx master PID 8200 未变，旧 worker 已退出，root 指向 `frontend/releases/1.0.151-settlement-stage-ui`。首页 SHA-256 为 `BD95F8A18CCFEBD2074CB013D5065697F5EEBDA4105CF6D2BA78AAB6EDA0DC55`，入口包和项目详情包均返回 200 且与制品哈希匹配。后端直连和经 Nginx 均为 1.0.151 UP，匿名结算接口返回 401。证据见 `logs/settlement-stage-ui-publish.log`。
- 业务验收从有权限的结算阶段项目开始，核对待办定位、批量归类确认和当前批次金额。使用获准测试资料验证上传与删除。
