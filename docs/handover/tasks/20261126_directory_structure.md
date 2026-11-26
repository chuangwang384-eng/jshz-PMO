# Agent 任务交接记录 — 目录结构规范检查与一/二/三批整改

## 基本信息
- 任务编号：20261126-directory-structure ｜ 主责 Agent：ZCode ｜ 交接时间：2026-11-26
- 当前状态：已完成待验收 ｜ 背景：目录结构审查（同日）发现"约定已立未执行"的存量堆积

## 已完成工作
| 批次 | 内容 | 结果 |
| --- | --- | --- |
| 一 | prune-temp -Keep 5 / prune-builds -Keep 5 | temp 删 253 项释放 1.87GB（引用保护跳过 47）；builds 删 134 个目录释放 ~15.2GB（剩 5 构建+archive+README） |
| 一 | 删 frontend/builds（3 个 11-21 旧目录）、照片库_tmp（8KB）、backend/temp/expert-app.log（测试日志散落，LOG_DIR 相对路径所致）、temp/sr-venv（206MB，无引用可重建） | 完成 |
| 一 | get-internal-url.bat → scripts/（无引用依赖路径，git mv） | 完成 |
| 一 | 注册计划任务 jshzPMOPruneMonthly（每月 1 日 03:30 prune-all -Keep 5）+ jshzPMOBackupWeekly（周日 02:00 全量备份），SYSTEM 身份 | Ready（提权直调 schtasks 注册；子脚本内调 schtasks 被拒属嵌套提权环境差异，注册脚本本身未改） |
| 二 | AGENTS 0.4 补丁：备份归属行（全量=backup/、手工=temp/backup/）；构建产物禁止项加 frontend/builds；新增"测试日志"行（LOG_DIR 必须绝对路径）；收纳计划任务说明 | 完成 |
| 二 | prune-temp.ps1 扩展散落物扫描：backend/temp、frontend/temp、frontend/builds 全清理（不受 Keep/引用保护，保留 MinAgeDays 新近保护）；PS 语法校验 + DryRun 通过 | 完成 |
| 三 | scripts/legacy/ 归档 18 个一次性脚本（apply-migration-*×11、install-*×2、python 迁移工具×5）+ README（含旧路径映射说明，历史文档引用按 legacy/ 定位） | 完成 |
| 三 | docs/superpowers 拆解删除：plans→docs/requirements/（1 个）、specs→docs/design/（8 个）；目录已删 | 完成 |
| 三 | repo-map：scripts 行加 legacy 说明；存储 7 目录登记 AGENTS 0.4 豁免（生产引用中不搬迁，新环境用非仓库根 STORAGE_ROOT） | 完成 |

## 实测效果
- builds 16GB→剩余目录数 6；temp 571MB+206MB 大件清空后 2.4GB（含 2 天内新发布产物，受 MinAgeDays 保护，由月度任务自然收敛）；backend/、frontend/ 下不再有 *.log。
- 双计划任务 Ready，防复积机制落位。

## 验证
- prune-temp DryRun=0 删除（清理后已干净）；PSParser 语法 OK；Get-ScheduledTask 提权复核两任务 Ready。
- 未执行：下一次自动运行（2026-12-01 03:30）的实际效果观察。

## 风险与回滚
- 删除均为可再生内容（构建/临时产物/venv）；文档移动 git mv 可逆；计划任务 Unregister 即可撤销。
- 历史文档中 scripts/apply-migration-* 等旧路径引用不改（按 legacy/README 映射定位），避免大面积噪音改动。

## 文档同步
AGENTS 0.4、repo-map（scripts/存储豁免行）、legacy README、本记录+摘要。未完成项：无。
