# AI 助手本机向量检索可行性评估

- 任务编号 `20261116-ai-assistant-rag-feasibility`，主责 Codex，核实日期 2026-11-16。
- 评估结论为小规模系统操作知识库具备本地试点条件。原型、延迟、并发和回答精度待验证。
- 基线为 `main` / `4c3a60a`，评估前工作区干净。本机后端健康接口返回 `UP`、`1.0.46`、`6370c0b`。

## 实测证据

| 命令或检查 | 结果 |
| --- | --- |
| `Get-CimInstance Win32_Processor` | Ryzen 7 3750H，4 核 8 线程 |
| `Get-CimInstance Win32_PhysicalMemory`、`Get-CimInstance Win32_OperatingSystem` | 安装 16 GiB，系统可见约 13.9 GiB，检查时可用约 2.4 GiB |
| `nvidia-smi --query-gpu=name,memory.total,memory.used --format=csv` | GTX 1650，4096 MiB，检查时已用 2846 MiB |
| `Get-PSDrive -Name C,D,E` | D 盘剩余约 147.5 GiB |
| Ollama `/api/tags`、`/api/ps` | 已安装 `qwen2.5:3b` 和 `minicpm-v4:latest`，前者在 GPU 驻留约 2.0 GiB，未列出专用嵌入模型 |
| PMO `http://127.0.0.1:8080/health` | 返回 `UP`，版本与提交标识见基线 |
| 阅读 `AiAssistantService.java` | 现有检索以标题及示例问法进行字符二元组打分，先按条目权限过滤。弱命中可进入无资料上下文的闲聊。 |

## 建议试点

1. 从现有启用问答、经核对的系统操作指南开始。短问答整条入索引，长文按标题和完整操作段切分，保留模块、角色权限、版本、来源及站内链接。
2. 本机 CPU 用轻量中文嵌入模型做离线建库与在线问题向量化。候选为 [bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5)，可评估 Windows x64 CPU 的 [ONNX Runtime](https://onnxruntime.ai/docs/get-started/with-java.html)。1 万个 512 维 float32 向量约占 19.5 MiB 原始空间，另计文本、运行时和索引开销。先验证内存精确余弦检索，复用现有关键词匹配。
3. 明确命中时返回维护过的答案和操作卡片。需要汇总时才让现有 Qwen 根据少量授权片段整理答案并显示来源。证据不足时提示补充信息。链接从已维护来源提取，文档命中不触发自动跳转。
4. 查询时按当前权限和有效版本过滤。文档更新触发增量索引，下架、删除和撤权在查询时立即排除。首批以系统操作资料为范围，接入知识星球前单独验证原文权限。
5. 在隔离环境用 30–50 条真实问法对照当前助手，覆盖同义改写、具体入口、跨章节、无答案和无权限。比较 Top 3 召回、带出处的答案准确率、误跳转和 P95 延迟，并记录 OCR 并行时的可用内存。批量索引安排在低峰，限制 CPU 线程和批量大小。

本次交付仅为评估和交接文档。代码、API、数据库、权限和生产配置无变更，未运行嵌入模型、端到端检索或负载测试。后续先准备已核对的首批操作资料及标准答案并在隔离环境试点。建议试点以独立配置开关接入，关闭时回到现有问答路径。正式接入按仓库规则另行核对权限、部署与回滚。
