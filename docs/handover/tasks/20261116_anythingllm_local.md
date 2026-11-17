# AnythingLLM 本机 Docker 部署

任务 `20261116-anythingllm-local`，主责 Codex。2026-11-16 已部署并通过基础验证，中文知识库问答待试点。PMO 基线为 `4c3a60a`，操作前已有 RAG 可行性评估文档改动。

- 服务在 `http://127.0.0.1:3001`，仅从本机访问。Compose 文件为 `<LOCAL_PATH>`，容器名为 `anythingllm`，随 Docker 引擎启动，手动停止时保持停止。
- 应用配置、SQLite 和知识库持久化目录为 `<LOCAL_PATH>`。Docker 镜像仍在 Docker Desktop 的现有 C 盘 WSL 数据盘。镜像来自 `mintplexlabs/anythingllm:latest`，拉取摘要为 `sha256:0ce5e7064036d4be674517bc7a1d602b00fa99846f3e2507936b76ce89208e40`。
- 聊天使用宿主机 Ollama 的 `qwen2.5:3b`，容器通过 `host.docker.internal:11434` 访问。配置使用本地内置嵌入和 LanceDB，遥测已关闭。

## 验证与接手

- `docker compose -f <LOCAL_PATH> config --quiet` 通过，`up -d --pull never` 启动成功，`docker inspect` 确认健康状态为 `healthy`、D 盘持久化挂载正确，宿主目录已生成 `anythingllm.db`。
- 首页 HTTP 200，`/api/ping` 返回 `{"online":true}`。容器请求 Ollama `/api/tags` 拿到本地模型列表，使用合成提示调用 `/api/generate` 得到 `qwen2.5:3b: OK`。
- PMO `/health` 仍返回 `UP`，`1.0.46` / `6370c0b`。本次部署独立于 PMO，无应用代码、数据库迁移、接口和权限改动。未导入公司资料，未验证嵌入或端到端检索。
- 下一步打开本机网页，用少量无敏感内容的中文样本试跑切分、嵌入和 Query 模式。停用可执行 `docker compose -f <LOCAL_PATH> down`，宿主机数据目录可用于备份与恢复。重新启动使用同一 Compose 文件执行 `up -d --pull never`。

## Ollama 嵌入模型安装

2026-11-16 通过本机 Ollama `/api/pull` 安装 `bge-m3:latest`。模型清单和 blobs 位于已有的 `<LOCAL_PATH>`，按清单校验所有文件存在且大小一致。`/api/tags` 报告 1,157,672,605 字节。从 AnythingLLM 容器请求宿主机 `/api/embed` 成功返回 1024 维向量，首次单条测试总耗时约 58.5 秒。测试使用 512 token 上下文及 `keep_alive:0`。AnythingLLM 嵌入器配置本次未切换，常驻延迟和批量嵌入吞吐待实测。
