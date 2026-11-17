# 简历照片抠图失效与解析变慢修复

## 1. 基本信息
- 任务编号：20261117-resume-matte-timeout
- 任务名称：简历解析照片抠图失效 + 解析变慢根因修复
- 主责：CodeBuddy
- 交接时间：2026-11-17
- 状态：已修复并全量测试通过，**待发布生效**（生产 1.0.50 仍带旧逻辑）
- 优先级：P1（影响每次简历解析：+15s 延迟且证件照回退白底原图）

## 2. 现象与根因（诊断过程保留，供后人参考）
### 现象
- 生产日志连续 `证件照抠图不可用，回退原图：TimeoutException`（2026-11-17 13:53~13:57 用户实测 10 连败）
- 每次服务重启后 `抠图预热未完成，将在后续请求重试`——**8/8 次重启 100% 失败**（11-16 16:53/16:56/16:59/17:02、11-17 09:05/10:18/10:58/12:47）
- 用户感知：解析明显变慢（每请求多 15s 等待）且照片无抠图（透明证件照退化为白底 JPEG 原图）

### 根因链（层层排除后收敛）
1. `PhotoMatteService.awaitReply()` 固定 **15s** 超时；worker READY（import rembg + new_session）冷启动实测 **4~34s 波动**（本机多次复测：6.1s / 6.4s / 4.4s / **33.6s**）——慢源与首次执行新临时脚本文件触发的系统实时扫描等干扰相关（C/E 盘、SSD/HDD 均复现，非介质/账户决定）
2. 预热（ApplicationReady）超时失败后**没有重试**，只能等首个用户请求触发重建
3. 失败后每次请求都 stopWorker→重建进程→再等 15s→超时回退原图（时间间隔模式与日志吻合）
4. worker stderr 被 `redirectError(DISCARD)` 吞掉 + worker.py 异常吞 traceback → 失败原因长期无法定位（11-16 排查即卡在此处）

### 排除项（有证据）
- 权重文件：E 盘/C 盘/D 盘三份 `models\models\u2net\u2net.onnx`（167.8MB）均存在且可读；rembg（本机版本）模型规则为 `U2NET_HOME/models/<name>/`，双层结构恰好匹配，权重加载正常
- Python 运行时：`<venv>/runtime/Scripts/python.exe`（E/C 两份）存在；SYSTEM 账户视角一次性计划任务实测 import+session+推理全部正常（TOTAL 6.05s、单图 2.22s）
- 解析主流程/照片候选逻辑（`ResumeParseService.buildPhotoCandidates`）无回归，回退路径按设计工作

## 3. 修复内容（PhotoMatteService.java + photomatte-worker.py，行为兼容）
| 改动 | 说明 |
| --- | --- |
| 超时 15s → 60s | READY 与单图回复分离参数化（`READY_TIMEOUT_SEC`/`REPLY_TIMEOUT_SEC`），仍远小于后端 180s 请求超时，假死兜底语义不变 |
| 预热自动重试 | 预热失败后守护线程 60s 间隔重试，最多 3 次（成功即复位），不再等用户请求踩坑 |
| worker 脚本固定文件名 | `%TEMP%/photomatte-worker.py` 同内容覆盖写（原随机名 `photomatte-worker-<rand>.py` 每次都是"新文件"反复触发扫描） |
| stderr 落盘 | `redirectError` 改为 appendTo `%TEMP%/photomatte-worker.err`，失败日志 WARN 直接带 stderr 尾部根因 |
| worker traceback | photomatte-worker.py 异常分支 `traceback.print_exc()` 输出到 stderr（配合落盘形成排障闭环） |
| 分段计时日志 | 预热就绪耗时（INFO）、单图抠图耗时（DEBUG），下次再慢可直接看到卡在哪段 |

## 4. 验证结果
| 验证项 | 结果 |
| --- | --- |
| 定向测试 `PhotoMatteServiceTest`（真实 worker 启动+真实 U²-Net 抠图） | 通过（EXIT=0）：垃圾输入按预期回退 null 且 WARN 带 `PIL.UnidentifiedImageError` traceback（stderr 闭环生效） |
| 后端全量 `mvn test`（注入 revision/gitShort + 测试占位 JWT_SECRET） | 通过（EXIT=0，0 失败 0 错误） |
| 覆盖面 | 解析字段（姓名/单位等）与照片候选（抠图透明 PNG / 回退白底 JPEG）链路均在全量测试回归内 |
| 字段准确性 | 本次未改任何解析/抽取逻辑（纯运行时容错修复），准确性不受影响 |

## 5. 影响范围与未变更项
- 仅 `PhotoMatteService.java`、`photomatte-worker.py` 两文件；接口/前端/数据库/配置零变更
- 简历文本抽取、LLM 语义抽取、字段兜底逻辑均未触碰

## 6. 风险与回滚
- 风险：worker 真假死（如显卡/系统级挂起）时兜底从 15s 放宽到 60s——单图最坏多等 60s 后回退原图，仍被后端 180s 包住；预热重试线程为守护线程，不阻塞退出
- 回滚：还原两个文件即可（git revert），无配置/数据依赖

## 7. 下一位 Agent 的接手指令
1. **发布生效**：修复需走 `build-all.ps1` + `deploy-backend.ps1` 标准流程发布（生产 1.0.50 当前仍带旧 15s 逻辑）
2. 发布后验证：重启后观察 `logs/expert-backend.out.log` 应出现 `抠图工作进程就绪，耗时 Ns`（N<60）而非 `预热未完成`；真实简历解析应返回透明 PNG 证件照且总耗时回到热解析 ~2s/冷 ~10s 水平
3. 排障新通道：`%TEMP%/photomatte-worker.err`（SYSTEM 账户即 `<LOCAL_PATH>`）+ 失败 WARN 里的 stderr 尾部
4. 若发布后 READY 仍接近 60s：考虑把 worker 常驻（避免每次停机后重建）或预热重试提前到启动即并行，属后续优化
