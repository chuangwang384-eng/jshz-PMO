# 发布记录：简历证件照自动抠图 + 专家详情页简历解析更新（1.0.38.7）

- 日期：2026-11-11
- 状态：**已发布生产（15:21，后端 R-1 蓝绿 + 前端 graceful，无迁移），发布后核验通过；页面真机验收待 Owner**
- 类型：功能迭代；**无数据库迁移**

## 1. Owner 需求
1. 专家库简历解析时**自动扣除照片背景生成透明证件照**（GitHub 方案选型）；
2. **专家详情页加入简历解析**，允许用户上传简历更新专家。

## 2. 方案选型
**rembg**（danielgatis/rembg，MIT，GitHub 事实标准）：U²Net 本地 ONNX 推理，**数据不出内网**；依赖 onnxruntime/pillow/numpy 在既有 pp-ocrv5 venv 中已具备。
- 模型 `u2net.onnx`（176MB）SHA-256 = `8D10D2F3BB75AE3B6D527C77944FC5E7DCD94B29809D47A739A7A728A912B491`，已落 Program Files 与 E 盘镜像双份。
- 脚本 `photomatte.py`：先缩放到 ≤1024px 再抠图（显著缩短耗时），输出 RGBA PNG。

## 3. 实际变更

### 后端
- **新增 `PhotoMatteService`**：子进程调用 rembg，单并发 Semaphore、120s 超时（含冷加载）、失败静默返回 null（调用方回退白底 JPG，**不阻断解析**）；Python 解释器按 `PHOTOMATTE_PYTHON` → E 盘 venv → 受保护 venv 顺序探测；脚本按 `PHOTOMATTE_ROOT` → E 盘镜像探测。
- **`ResumeParseService`**：
  - `ResumeParseResult.photoCandidates` 由 `List<String>` 改为 `List<PhotoCandidate{base64, mime, transparent}>`；
  - 候选处理：原图 → 无损 PNG → rembg 抠图 → **透明 PNG**（`transparent=true`）；失败回退白底 JPG（原行为）；
  - 抠图结果按最长边 **600px 缩放**（保留 Alpha），避免文档大图 base64 膨胀（实测 1.9MB→384KB）。
- 照片保存链路无需改动（现有 `saveTempPhoto` 已支持 png；`PUT /api/experts/{id}` 换照片删旧）。

### 前端 `ExpertList.vue`
- **专家详情（编辑弹窗）新增「选择简历解析更新」**：上传简历→解析→字段预填（仅填空字段，不覆盖已填）+ 照片候选→用户确认保存（走既有 `PUT /api/experts/{id}`，换照片删旧逻辑复用）。
- 新建/编辑两处候选照片均改为对象结构（mime 区分 png/jpg），透明候选显示 **「已抠透明背景」** 绿色标签。
- `useResumePhoto` 按 mime 构造 png/jpg File，分别写入新建或编辑的照片暂存位。

## 4. 验证（实测）
| 验证项 | 结果 |
| --- | --- |
| 真实简历（省医 刘明博教授简历.pptx） | 解析 5 个字段；**3 张候选全部 transparent=true**：RGBA 1470×977→600px（透明 42%）、243×243（19%）、1024×768（34%） |
| 抠图输出校验 | mode=RGBA、alpha 通道有效、输出 ≥1000 字节 |
| 新增单测 `PhotoMatteServiceTest` | 3 用例：null/空输入、非图像输入不抛异常、运行时可用时返回可解析 PNG |
| 后端全量 mvn test | **110 通过 / 0 失败 / 10 跳过** |
| 前端构建 | 40.5s，is-not-exported=0；ExpertList chunk 含「已抠透明背景」 |

## 5. 运行时部署注意事项（重要）
- 受保护 venv（Program Files）内 `pip install rembg` 不稳定（网络中断/robocopy 覆盖后 import 卡死，实测 >400s 无响应）；E 盘 venv 完整可用（同一张图 18.7s）。
- 因此 `PhotoMatteService` 采用**多路径探测**，默认命中 E 盘 venv；Program Files 保留脚本+模型镜像。若后续受保护 venv 修复，可设 `PHOTOMATTE_PYTHON` 显式切换。
- 服务账号（LocalSystem）需可读 E 盘 venv——该目录为 Authenticated Users 可读（与 PP-OCR 同现状）。

## 5.1 发布执行与核验（15:19–15:21 实测）
- 制品：jar `builds/1.0.38.7-build-20261111_151433/expert-system.jar`（build.version=1.0.38.7）；前端 `builds/1.0.38.7-staged-dist`。
- `temp\deploy-1387.cmd`：后端 R-1 11 阶段蓝绿完成；前端 graceful（ReleaseId `1.0.38.7-resume-matte-20261111`，DEPLOY_EXIT=0）。
- 核验：`/api/health` 经 Nginx **1.0.38.7 UP**（buildTime=15:14:56 与制品一致）；Nginx root 已切新 release；线上 index 主 bundle=磁盘哈希（index-Di-WZpEC.js）；**运行 jar SHA256 = 候选 jar**（2c7624c1…acf62）；SPA 200。

## 6. 回滚
revert 提交 + 回退后端制品；抠图生成的是普通 PNG，旧版本可正常展示。删除 `photomatte.py`/u2net 即恢复原行为（白底 JPG 候选）。

## 7. 已知限制
- 首次调用含模型冷加载（约 20-60s），之后常驻快速；解析总耗时仍在 20~45s 提示范围内偏上限。
- u2net 对非标准照片（多人/无人像）可能抠错 → 用户可选择不使用候选、手动上传。
