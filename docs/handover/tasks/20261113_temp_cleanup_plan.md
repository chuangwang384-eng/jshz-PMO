# temp/ 临时文件清理实施方案（2026-11-13）

> 状态：**已批准并执行（2026-11-13）**。执行结果与审计见 `docs/handover/tasks/20261113_temp_cleanup_audit.md`：
> A 类 2861 文件入隔离区（7 天，至 2026-11-20）、B 类 31 文件归档、C 类已授权项 51 文件入隔离区、`login.json` 已直接删除；9 个未授权个人信息样本原地保留。
> 依据：AGENTS.md 0.4 节（gitignore 不等于允许散落）+ 第 14 节（删除文件须负责人批准）。
> 扫描方式：`Get-ChildItem temp -Recurse -Force`（2026-11-13 执行，扫描脚本已清理）。

## 1. 扫描概况

- 总计：**2958 个文件，432.1 MB**
- 构成：14 个子目录（2775 文件）+ 183 个散文件
- 时间跨度：2026-11-06 ~ 2026-11-13（当日仍有新增：供应商导入链路、4 个 html 快照）

## 2. A 类：可直接删除（约 2794 文件 / 约 272 MB）

分类理由：任务已完成并发布/归档的一次性包装脚本、调试日志、构建核验快照、隔离验收编译产物。全部无归档价值，对应结论均已写入交接记录或 deployment.md。

| 路径（temp\ 下） | 数量 | 大小 | 最后修改 | 理由 |
| --- | --- | --- | --- | --- |
| verify-155548\ / verify-orphans-160211\ / verify-reclaim-161023\ | 642/645/641 | 61.34/61.39/61.35 MB | 11-12 15:57~16:12 | 历史隔离验收的 .class+.java 编译转储，结论已入档 |
| schedule-pptx-probe\ | 390 | 57.55 MB | 11-12 10:42 | PPT 解析探针编译产物（305 .class + 68 jar） |
| dist-verify-1.0.34.2\ / compliance-maintenance-dist\ / frontend-build-maintenance-20261111\ / frontend-build-orphans-all-20261112\ / frontend-build-storage-admin-20261112\ | 77~81 each | 各约 5.7 MB，合计 28.5 | 11-07~11-12 | 前端构建核验快照，与 frontend/releases 正式快照重复 |
| deploy-*.cmd（13719~13814） | 25 | ≈0 | 11-09 10:08 ~ 11-12 13:33 | 历史发布包装脚本，对应版本均已发布并有正式记录 |
| run-deploy-frontend-*.ps1 | 14 | ≈0 | 11-09 ~ 11-12 | 同上 |
| run-release-1.0.38.20/21/22.ps1 | 3 | ≈0 | 11-13 上午 | 1.0.38.20~22 已被 23/24 取代 |
| run-*-isolated*.ps1 / run-opening-chair-13812.cmd | 7 | ≈0 | 11-11 ~ 11-12 | 浏览器隔离验收包装，验收结论已入档 |
| apply-*.ps1 | 21 | ≈0 | 11-10 ~ 11-13 | 一次性代码补丁包装，效果已进正式提交 |
| *.log（dbg×7、garbage×2、hotel×2、restr×3、resume×3、dump/fix/fe_build/ocr/p1to4/p3） | 23 | <0.1 | 11-10 ~ 11-13 | 调试日志（其中 release-1.0.38.20.log 本应归 logs/） |
| *-edge.mjs | 12 | ≈0 | 11-10 ~ 11-12 | Edge 浏览器自动化一次性脚本 |
| edit-*/patch-*/compare-*/score-*/extract-*/*.py（layout 系列、docs_reorg、check-github-upload-history 等） | 18 | <0.1 | 11-06 ~ 11-13 | 一次性编辑/对比脚本 |
| *-build.txt / compliance-*-stage.txt / compliance-compare-*.json | 16 | <0.1 | 11-10 ~ 11-12 | 构建输出与阶段证据，结论已入交接记录 |
| ppocr-selftest.{png,tsv,err} / matte-out*.png / matte-test.png / m3.png | 7 | ≈1.7 | 11-11 | OCR/抠图自测输出 |
| expert-system-thin-20261106-1128.jar | 1 | 0.71 | 11-06 | thin 打包试验产物，未采用 |
| excel-preview-sample\（preview_1_v1_fit_width.pdf） / ocr-probe-classes\ | 2 | 0.1 | 11-11 | 预览功能测试样本 / 空探针目录 |

## 3. B 类：建议归档（约 31 项 / 约 128.8 MB）

分类理由：有复用或追溯价值的制品与工具，但不应继续散落在 temp 根部。

| 路径（temp\ 下） | 数量 | 大小 | 最后修改 | 理由与归档位置建议 |
| --- | --- | --- | --- | --- |
| expert-system-1.0.21-current.jar / expert-system-1.0.21-new.jar | 2 | 57.31/57.32 MB | —（归一化） | 历史版本制品基线 → **`builds/archive/`** |
| schedule-codex-review\（14 txt + png 等 20 文件） | 20 | 13.3 MB | 11-12 10:46 | 日程解析评审证据样本（对照交接记录引用）→ **`temp/archive/20261112-schedule-codex-review/`** |
| CompareCompliance.java / ComparePaddleRows.java / DumpParagraphs.java / DirectWorkbookSample.java / ExcelPreviewSample.java / InspectCompliancePdf.java / RenderOcrFixture.java | 7 | <0.1 | 11-10 ~ 11-11 | 一次性 Java 检查工具，OCR 质量对比可复用 → **`temp/archive/java-probes/`** |
| ppocr-benchmark-page2.png / ppocr-page2.tsv | 2 | ≈1.6 | 11-11 | PPOCR 基准测试证据 → **`temp/archive/ppocr-benchmark/`** |
| install-photomatte-20261111.cmd / install-ppocr-20261111.cmd / ppocr-sys-verify.cmd / ppocr-sys-verify.log | 4 | <0.1 | 11-11 | 环境安装/自检脚本，重装有参考价值 → **`temp/archive/installers/`** |

## 4. C 类：需确认（约 56 项 / 约 30.4 MB）

### 4.1 数据操作全链路（2026-11-13 当天，最高优先确认）

| 路径 | 数量 | 大小 | 最后修改 | 风险说明 |
| --- | --- | --- | --- | --- |
| backup\supplier-pre-purge-20261113.sql | 1 | 0.04 | 11-13 10:54 | **供应商清空操作前的数据备份**——删除即丢失该操作的回滚能力。确认导入/清空已验收且数据无异常前禁止删除 |
| supplier-images-20261113\ | 39 | 30.01 | 11-13 11:39 | 供应商图片导入源素材；若导入已验收可归档或删除，若需重跑则必须保留 |
| supplier-image-insert-20261113.sql / supplier-image-mapping-20261113.json / supplier-import-20261113.tsv | 3 | <0.1 | 11-13 | 导入 SQL 与映射表——与上两项同链路，需一并决策 |
| gen_supplier_sql.py / fix_supplier_images.py / import_supplier_images.py | 3 | <0.1 | 11-13 | 导入链路脚本，重跑参考 |

### 4.2 疑似敏感/个人信息

| 路径 | 数量 | 大小 | 最后修改 | 风险说明 |
| --- | --- | --- | --- | --- |
| login.json | 1 | <0.01 | 11-07 16:06 | **含明文 username/password 键**（自动化登录遗留）。留在磁盘是凭据泄露面；建议确认无脚本引用后尽快删除 |
| 3 某某医院 李测试.docx / dinghuixin.doc | 2 | ≈0.13 | 11-07 | 真实客户/个人文档样本，按第 4.6 节最小必要原则不应长期留存 |
| resume-cand-1~3.{jpg,png} / resume-sample.docx | 7 | <0.1 | 11-11 | 简历解析测试样本（含个人信息） |
| f_0ff4c0.html / f_91609a.html / f_c56a9a.html / leda.html | 4 | ≈0.1 | **11-13 11:11（今日）** | 用途未明的页面快照，生成时间与供应商图片修复作业（11:38）接近；确认非进行中工作产物后再删 |

## 5. 汇总统计

| 类别 | 项数（约） | 占用（约） | 处置 |
| --- | --- | --- | --- |
| A 可直接删除 | 2794 文件 | 272 MB | 批准后先移隔离区，7 天后彻底删除 |
| B 建议归档 | 31 文件 | 128.8 MB | 批准后移动到指定归档位置 |
| C 需确认 | 56 文件 | 30.4 MB | 逐项决策后处置（其中数据备份与导入链路优先） |
| **合计** | **2881 / 2958** | **≈431 / 432.1 MB** | 与扫描总量差 77 项为散文件计数近似误差，执行时按模式精确枚举 |

## 6. 待批准后才能进行的执行步骤（当前一律未执行）

1. **你批准清理范围**（可按类别批准，也可剔除个别项）。
2. **A 类执行（两段式保险）**：
   - 第一步：将 A 类全部移入 `temp/.quarantine-20261113/`（保留目录结构，本质是移动而非删除，可整体恢复）；
   - 第二步：**7 天后**经你再次确认，清空隔离区；同时按 AGENTS.md 0.4 约定在本次任务交接记录中登记清理清单。
3. **B 类执行**：按第 3 节"归档位置建议"逐项移动（`builds/archive/` 与 `temp/archive/*`），移动后核对数量与大小。
4. **C 类执行**：逐项向你确认后处置——预计多数可并入 A 类流程（删除）或 B 类（归档），`login.json` 建议优先删除。
5. 执行过程产出清单留痕（移动/删除文件数、释放空间），登记到交接记录。

## 7. 保险与回滚

- A 类采用"先隔离后删除"：7 天观察期内任何项可从 `temp/.quarantine-20261113/` 原路移回。
- B 类为纯移动操作，天然可逆。
- C 类中 `backup\supplier-pre-purge-20261113.sql`：执行时经核实 `supplier_image` 已 121 行生效、供应商数据无异常，按负责人授权并入隔离区（7 天内可整体回移，等于保留回滚能力）；**2026-11-20 清空前需再确认**。
