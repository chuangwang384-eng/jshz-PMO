# 发布与回滚记录：简历解析单位优先级重构 1.0.74

- 日期：2026-11-19
- 提交：`5b2b786`（fix: 简历解析单位优先级改为组合行>标签行>文件名>散行扫描 1.0.74）
- 类型：仅后端（无前端快照切换、无数据库迁移）
- 负责人手动发布（elevated PowerShell 执行 `temp\deploy-resume-unit-1074.ps1 -Apply`）

## 变更内容
单位识别优先级：**组合行（医院单位+科室+医师职称，支持相邻行拼接）> 标签行 > 文件名 > 散行机构扫描**（Ollama 兜底不变）。修复李宏案例（单位误识别为"日本上尾中央综合医院"）。详见 [任务交接](../handover/tasks/20261119_resume_unit_priority_combo.md)。

## 验证证据（发布后核验，均实测）
| 项 | 结果 |
| --- | --- |
| 发布脚本退出标记 | `DEPLOY_EXIT=0`，日志 `logs/20261119-resume-unit-1.0.74-deploy.log` 全 PASS |
| /health 与 /api/health | UP，version=1.0.74，gitShort=5b2b786 |
| 运行 jar == 候选包 | sha256 一致（backend/target/expert-system.jar == builds/1.0.74-build-20261119_111533） |
| 匿名鉴权 | /api/experts、/api/projects 均 401 |
| nginx root | 未变（1.0.73 前端快照，后端独发符合预期），/ 与 /experts 均 200 |
| 测试基线 | 发布前 mvn test：208 项 0 失败（JDK17）；真实李宏原件解析=湖北省肿瘤医院/微创介入科/副主任医师 |
| 回滚包 | builds/1.0.73-build-20261119_100418/expert-system.jar（与发布前线上 jar 哈希一致） |

## 回滚方案
`scripts\deploy-backend.ps1 -JarPath '<LOCAL_PATH>' -Version 1.0.73 -GracefulSwitch`（需提权；无迁移无补偿项）。

## 同日环境变更（与本发布无关，记录备查）
本机 Temurin JDK 25 已卸载，机器级 JAVA_HOME/PATH 已切回 JDK 17（`logs/20261119-switch-java17.log`）。生产 NSSM 一直以绝对路径 JDK 17 运行，未受影响。
