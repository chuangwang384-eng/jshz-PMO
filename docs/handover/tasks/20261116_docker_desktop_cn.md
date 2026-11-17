# Docker Desktop 升级与汉化

任务 `20261116-docker-desktop-cn`，主责 Codex。2026-11-16 从 Docker Desktop `4.96.1.244132` 原位升级到 `4.97.0.244584`，安装 DockerDesktop-CN 同版本 Windows x86 汉化包。PMO 应用、数据库和权限无变更。

- 升级前 Docker 的 WSL 2 后端正常，AnythingLLM 为健康状态。退出 Desktop 后备份应用数据和 Docker 设置到 `<LOCAL_PATH>`。
- 官方安装包通过 Docker 公布的 SHA-256 和 Docker Inc 签名校验。汉化 ZIP 通过 GitHub Release 摘要校验。汉化可执行文件已被上游修改，原厂签名检查为 `HashMismatch`。
- 安装程序报告成功，`docker desktop version` 返回 `4.97.0`，已安装的 `app.asar` 和前端 EXE 哈希与汉化包一致。安装日志在 `<LOCAL_PATH>`，英文版 4.97.0 前端原文件备份在 `%LOCALAPPDATA%\DockerDesktop-CN\backup\20261116-172137`。
- 自动执行策略拒绝启动汉化后的 Desktop，随后检测到应用已打开，主窗口标题为「仪表盘 - Docker Desktop」。Linux 引擎返回 `29.9.2`，`docker --context desktop-linux run --rm --pull never hello-world` 输出 `Hello from Docker!`。AnythingLLM 自动恢复为 `healthy`，`/api/ping` 返回 `{"online":true}`。PMO `/health` 已复核为 `UP`。
- 如需恢复英文版，在 Docker Desktop 退出后使用该版本汉化包的恢复脚本，选择上述原文件备份并重启 Desktop。该备份对应 4.97.0 前端。
