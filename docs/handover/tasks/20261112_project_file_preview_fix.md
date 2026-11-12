# 项目详情页项目文件预览/下载失效修复

## 1. 基本信息
- 任务编号：20261112_project_file_preview_fix
- 任务名称：项目详情页「项目文件」预览与下载失效修复
- 主责 Agent：CodeBuddy（Claude）
- 交接时间：2026-11-12（2026-11-12 13:55 已发布生产）
- 当前状态：已发布待业务验收
- 优先级：P1

## 1.1 发布结果（2026-11-12 13:55，经 Owner 批准）
- **重要**：并行会话 13:36 发布的 `1.0.38.14-file-preview-20261112` 并未真正修复——其 ProjectDetail chunk 只有预览抽屉模板（canvas/空态/按钮引用），无任何实现代码（getDocument/GlobalWorker/createObjectURL 均为 0，构建于本修复之前的源码），点击仍无响应。
- 本次发布：从含修复的当前源码重新构建（断言 createObjectURL=4、`下载失败，请重试` 在 bundle 内），发布快照 `frontend/releases/1.0.38.14-projectfile-preview-impl-20261112`，Nginx 平滑切换（提权 UAC 方式，EXIT=0），上一 root=`1.0.38.14-file-preview-20261112` 已保留。
- 发布后核验：`/` 与 `/enterprise-compliance` 均 200；线上 `/assets/ProjectDetail-gQP13lhW.js`（HTTP 200, 62,939 bytes）含实现代码断言 True。
- 日志：`logs/deploy-frontend-projectfile-impl-20261112.log`；回滚：`frontend/release-metadata/1.0.38.14-projectfile-preview-impl-20261112.rollback.conf.txt` 与 `.rollback/` 目录。
- 过期产物已清理（temp 构建与 1.0.38.8 基线过期 staging，防误部署回退）。
- 待办：Owner 在项目详情页实际点击"预览/下载"做业务验收。

## 2. 目标与验收条件
- 业务目标：项目详情页「项目文件」卡片中"预览/下载"恢复可用（Office 转 PDF 预览、图片直显、blob 下载）。
- 验收条件：
  - [x] 根因定位并修复
  - [x] 前端构建通过，修复后 bundle 含预览代码
  - [ ] 生产发布 + 页面实际点击预览/下载验证（待 Owner 批准发布）

## 3. 根因（已核实）
`frontend/src/views/project/ProjectDetail.vue` 中项目文件预览/下载整段代码（`openFilePreview`、`downloadProjectFile`、pdf.js 渲染等，约 110 行）被放在了 `</script>` 标签**之后**、`<style scoped>` 之前——第 1592 行 `</script>` 提前闭合了 `<script setup>`。该段成为 SFC 顶层杂散文本，构建时被编译器丢弃，不报错但模板中 `openFilePreview`/`downloadProjectFile` 未定义，点击预览/下载无响应。

**生产坐实**：线上快照 `frontend/releases/1.0.38.8-maintenance-search-20261112/assets/ProjectDetail-*.js` 全部不含「预览失败，可下载查看」等预览代码；修复后构建含之。

## 4. 修复内容
- 将 `</script>` 从 onMounted 之后移至 `downloadProjectFile` 函数末尾（`<style scoped>` 之前），预览/下载代码回归脚本作用域。
- 无其他逻辑改动；导入（pdfjsLib/pdfWorkerCode/fetchProjectFile/shallowRef/watch）本就齐全，`api/project.js` 的 `fetchProjectFile` 与后端 `/api/project/files/{fileName}` 接口此前已就绪。
- 修复时间：2026-11-12；错误引入时间：2026-11-12 该功能首次开发时（标签错位，构建期静默丢弃，故单元/构建校验未拦截）。

## 5. 验证
| 项目 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 构建 | `npm run build -- --outDir ../temp/frontend-build-projectfile-fix-20261112 --emptyOutDir` | 通过（51.1s） |
| bundle 断言 | 修复构建 ProjectDetail chunk 含预览代码 | True |
| 线上断言 | 生产快照 ProjectDetail chunk 含预览代码 | False（坐实失效根因） |
| 页面点击验证 | 预览抽屉/下载保存 | 未执行（需发布后） |

## 6. 发布与回滚
- 待批准发布：`deploy-frontend-graceful.ps1 -StagedDist temp\frontend-build-projectfile-fix-20261112 -ReleaseId 1.0.38.8-projectfile-preview-20261112 -Apply`（建议先按脚本惯例将 StagedDist 指向正式构建产物目录）。
- 回滚：脚本自动生成 `.rollback/` 与 rollback conf；或指回上一 root `1.0.38.8-maintenance-search-20261112`。
- 无后端/数据库变更。

## 7. 风险
- 纯前端单文件修复，风险低；唯一注意点：pdf.js worker 走 blob URL（沿用知识星球同口径），首次预览加载稍慢属预期。
