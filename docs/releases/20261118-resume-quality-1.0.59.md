# 专家单位/科室解析与照片质量修复 1.0.59 发布

2026-11-18 12:22 CST 完成生产前后端切换，主责 ZCode。提交 `main` 的 `7ef6ba7`，工作区干净。业务反馈六件：郭双双单位误抓"河南省医学会"、李泉江单位混入"主治医师"、许鹏程单位混"07"日期残留、胡晓辰/吕红琼照片模糊（新增强制超分）、任婧抠图残留。数据库、API 契约、权限无变更。

## 修复内容

| 问题 | 根因 | 修复（ResumeParseService/photomatte-worker） |
| --- | --- | --- |
| 郭双双单位=河南省医学会 | 弱截断"河南省医学会青年委员→河南省医学会"先命中，压过正确行 | 单位候选改**强/弱分级**：整行以机构后缀结尾为强候选（含机构回声剥除"医院XX医院"→"XX医院"），行中截断为弱；强优先、同级取最长 |
| 李泉江单位=主治医师XX医院 | Ollama 输出职称前缀、sanitizeUnit 无职称剥离 | sanitizeUnit 增加职称词前缀剥离（最长优先，仅当剩余含机构特征才生效） |
| 许鹏程单位混"07" | 教育时间轴行"2013/09-2018/07南方医科大学"按后缀截断 | 跳过以 4 位年份开头的行 + 已有"毕业"行排除 |
| 科室污染（李泉江/任婧） | "主治医师XX医院放疗科"整行当科室 | 行含机构后缀时取尾段（→"放疗科"/"肿瘤内科"），并校验不含职称词 |
| 吕红琼姓名丢失 | 文件名后缀"简介最新"剥离表未覆盖 | 剥离表补"最新" |
| 任婧抠图残留 | 发间深蓝阴影色距超容差 40 | 残留清理容差 40→64；鲁少贝/胡晓辰/许鹏程/吕红琼四样本回归无破坏 |
| 胡晓辰/吕红琼照片模糊 | 原件像素低 | **低像素照片自动超分**：≤350px 用 EDSR_x4、351–800px 用 LapSRN_x4（OpenCV dnn_superres CPU 推理），>800px 不处理；失败自动回退原图 |

## 超分运行时（本机已安装，2026-11-18）

- 安装位置 `<LOCAL_PATH> Files\JSHZ\sr-upscale\`（与 pp-ocrv5 同级）：`venv\`（Python 3.12 + opencv-contrib-python-headless **5.0.0.93 固定版** + numpy）、`sr-upscale.py`、`models\{EDSR_x4.pb,LapSRN_x4.pb}`。
- 版本钉死原因：opencv 5.1 的 dnn 无法解析 TF 格式 .pb 模型（实测 onnx_importer2 断言失败）。
- Java 侧 `PhotoMatteService.resolveSrPython()` 探测：`SR_UPSCALE_PYTHON` 显式配置优先，缺省探测上述路径；不存在则不下发环境变量，worker 整体跳过超分（零行为变化）。worker 侧另有 `SR_UPSCALE_SCRIPT`/`SR_UPSCALE_MODELS` 覆盖口。NSSM 服务环境无需变更。
- 本机验证：Vulkan（Real-ESRGAN-ncnn-vulkan）因驱动未注册不可用（`vkEnumeratePhysicalDevices failed`），故选 CPU 方案；EDSR 质量 > LapSRN > FSRCNN，速度 123s/9s/0.5s（590px 照片），混合方案兼顾两者。

## 验证

- `ResumeParseServiceTest` 33 过 0 败（新增：强候选分级、职称剥离、日期行、六原件探针 `optionalJuaaiSixOriginals`）。六原件实测：郭双双/胡晓辰/李泉江→河南科技大学第一附属医院、吕红琼→洛阳市中心医院、任婧科室=肿瘤内科、许鹏程单位空（原件无任职信息，待人工填）。
- 抠图：任婧残留清零；鲁少贝 0.13%、其余样本无回归；SR 开启后胡晓辰/吕红琼/许鹏程三照片更清晰（对比图 `temp/fix1059/sr-mat-compare2.png`）。
- 生产布局冒烟：`<LOCAL_PATH> Files\JSHZ\sr-upscale` 双模型推理 OK。

## 上线核验（2026-11-18 12:22 实测）

- `/api/health` 双路 `UP / 1.0.59 / 7ef6ba7`；前端 root `1.0.59-resume-quality-20261118`，入口 bundle `index-DsM2uYU3.js` 含 1.0.59、经 Nginx 200。
- 登录后六原件重新解析与照片验收待业务执行。

## 回滚

- 后端回滚包 `backend/releases/1.0.59-*/previous-expert-system.jar`（1.0.57，蓝绿脚本发布）。
- 前端回滚配置 `frontend/release-metadata/1.0.59-resume-quality-20261118.rollback.conf.txt`。
- 超分回滚：删除 worker 收到的 `SR_UPSCALE_PYTHON`（或重命名 sr-upscale 目录）即整体停用，解析主链路不受影响。

## 已知限制

- 任婧原件文本本身缺"南"（"河科技大学"），解析如实提取不做猜测修正，提交时人工核对。
- 许鹏程 docx 全文无现任单位，解析留空。
- EDSR 最长边 ≤350px 时单张耗时可达 30s+，批量解析会相应变慢；超分只在低像素照片触发。
