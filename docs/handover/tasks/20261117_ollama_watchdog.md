# Ollama 服务健康看门狗

## 1. 基本信息
- 任务编号：20261117-ollama-watchdog
- 任务名称：Ollama 假死风险检查 + 定时健康检查（60s 超时自动重启）
- 主责 Agent：ZCode
- 交接时间：2026-11-17
- 当前状态：已上线运行（计划任务每 5 分钟执行）
- 优先级：P2

## 2. 目标与验收条件
- 业务目标：Ollama 服务假死（进程活着但推理无响应，如 GPU 驱动挂起、模型加载卡死）时自动恢复，避免简历语义抽取长期不可用。
- 验收条件：
  - [x] 诊断当前服务状态（健康，无历史假死记录）
  - [x] 健康探测走真实推理链路（非仅 /api/tags），60s 超时阈值
  - [x] 超时/失败自动重启 ExpertSystemOllama，重启后回连+预热
  - [x] 计划任务注册为 SYSTEM 每 5 分钟，试运行 result=0
  - [x] 重启恢复链路实测（一次真实 Restart-Service：114s 完全恢复，等待窗口已放宽至 180s）

## 3. 方案与事实依据
- 服务：`ExpertSystemOllama`（NSSM，`E:\AI助手开发\Ollama\ollama.exe serve`），后端消费 `OLLAMA_URL=http://127.0.0.1:11434`、`OLLAMA_MODEL=qwen2.5:3b`（1650 GPU）。
- 假死风险评估：**存在**。NSSM 仅在进程退出时重启；GPU 挂起/加载卡死时进程不退出，后端 180s 超时只让请求失败，服务不会自愈。当前日志（110KB）无 error/panic/cuda 失败记录，今日实测推理 0.8s，属健康但无自愈能力。
- 实现：`scripts/ollama-health-watch.ps1`（UTF-8 BOM）+ 计划任务 `OllamaHealthWatch`（schtasks /SC MINUTE /MO 5 /RU SYSTEM /RL HIGHEST）。
  - 探测：POST /api/generate（qwen2.5:3b，num_predict=1，stream=false），`Invoke-WebRequest -TimeoutSec 60`。
  - 服务未运行→Start-Service；探测失败→锁文件防并发→Restart-Service→最长 180s 轮询 /api/tags→预热推理（120s，非致命）。
  - 日志 `logs/ollama-watch.log`（每次运行前裁剪保留 300 行）；健康静默。

## 4. 实际变更
- 新增：`scripts/ollama-health-watch.ps1`
- 系统变更：计划任务 `OllamaHealthWatch`（不在 git 内，重建命令见 deployment.md 小节）
- 文档：`docs/deployment.md` 新增「Ollama 服务健康看门狗」小节
- 代码/数据库/接口：无变更（未动后端应用）

## 5. 测试与验证
| 验证项 | 步骤 | 结果 |
| --- | --- | --- |
| 当前健康诊断 | /api/tags 0.3s、/api/generate 0.8s、日志 89 处 error 类均为信息性 CUDA 输出 | 健康，无假死史 |
| 健康路径 | 手动执行脚本 | exit=0 静默通过 |
| 重启恢复链路 | 提权 Restart-Service 实测 | 114s 完全恢复（含模型重载） |
| 计划任务 | schtasks 注册+手动触发 | state=Ready，result=0，下次运行 11:27:27 |

## 6. 风险与回滚
- 重启期间（约 2 分钟）LLM 请求会失败：与假死持续不可用相比是净收益；后端对 Ollama 失败已有降级（文件名兜底/关键词回退）。
- 误重启风险：探测走真实推理，正常负载下 <1s，60s 阈值宽裕；锁文件防多实例并发重启。
- 回滚：`schtasks /Change /TN OllamaHealthWatch /DISABLE` 后删除任务即可，无其他依赖。

## 7. 下一位 Agent 的接手指令
1. 检查：`schtasks /Query /TN OllamaHealthWatch`（需提权）与 `logs/ollama-watch.log` 是否出现 ERROR 行
2. 出现「still unresponsive after restart」说明看门狗也救不回（如显卡驱动级故障），需人工介入
3. 未经确认禁止：调整探测间隔/超时阈值（与后端 180s 请求超时的配比有意为之）
