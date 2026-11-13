# temp/ 清理执行审计记录（2026-11-13）

> 依据：`docs/handover/tasks/20261113_temp_cleanup_plan.md`（方案已获负责人批准）。
> 执行时间：2026-11-13 15:2x；执行方式：`Move-Item` 逐项移动（隔离区/归档），`login.json` 直接删除（不复制副本）。
> 机器可读清单：`temp/.quarantine-20261113/manifest.csv`（UTF-8 BOM，含 处置类别|动作|源|目标|字节|修改时间|SHA256）。

## 1. 执行前校验（全部通过）

| 校验项 | 方法 | 结果 |
| --- | --- | --- |
| 引用依赖 | 全仓库检索 `login.json`、`supplier-pre-purge`、`supplier-image-insert-20261113`、`supplier-import-20261113`（排除 temp 自身） | 0 引用 ✓ |
| 生产脚本依赖 | 检索 `scripts/` 中 `temp\(run-\|apply-\|deploy-\|edit-\|patch-)` 引用 | 0 引用 ✓ |
| 供应商图片导入已生效 | `SELECT COUNT(*) FROM supplier_image` → 121；distinct supplier_id → 120；`供应商图库\` 文件 → 121 | 与交接记录一致 ✓ |
| 导入数据已入库留档 | `docs/data/` 下 SQL/映射/TSV 4 文件齐全 | 齐全 ✓ |
| 表结构澄清 | `supplier.images` 为遗留列（全 NULL），真实数据在 `supplier_image` 表 | 已核实 ✓ |

## 2. 执行结果汇总

| 类别 | 处置 | 项数 | 占用 | 位置 |
| --- | --- | --- | --- | --- |
| A 可直接删除 | 移入隔离区（7 天观察） | 2861 文件 | 271.6 MB | `temp\.quarantine-20261113\A\` |
| B 建议归档 | 移动归档 | 31 文件 | 129.5 MB | `builds\archive\`（jar）+ `temp\archive\*`（证据/工具） |
| C 需确认（已授权部分） | 移入隔离区（7 天观察） | 51 文件 | 30.2 MB | `temp\.quarantine-20261113\C\` |
| C login.json | **立即删除**（凭据文件，不保留副本） | 1 文件 | <10 KB | SHA256 `48673E38929024A4182D7431687DB111F379C9B266DA270CAD44F5B2ACC9EC9A`（已入 manifest 审计） |

- 清理前 temp 总计：2958 文件 / 432.1 MB
- 清理后 temp 根部散文件：仅剩 **9 个未授权项**（见 §4）
- 隔离 + 归档合计：约 431 MB 已从根部收敛

## 3. 归档明细与索引

### 3.1 `builds\archive\`（历史制品）

| 文件 | 大小 |
| --- | --- |
| expert-system-1.0.21-current.jar | 57.3 MB |
| expert-system-1.0.21-new.jar | 57.3 MB |

### 3.2 `temp\archive\`（证据与工具，含索引 `README.md`）

| 子目录 | 内容 | 数量 |
| --- | --- | --- |
| 20261112-schedule-codex-review\ | 日程解析评审证据样本 | 20 |
| java-probes\ | 7 个一次性 Java 检查工具（OCR 质量对比可复用） | 7 |
| ppocr-benchmark\ | PPOCR 基准图与页码 TSV | 2 |
| installers\ | Photomatte/PPOCR 安装与自检脚本、自检日志 | 4 |

### 3.3 A 类隔离内容构成（按族）

deploy-*.cmd 25、run-deploy-frontend-*.ps1 12、run-release-1.0.38.2x.ps1 3、run-*-isolated*.ps1 5、run-opening-chair-13812.cmd 1、apply-*.ps1 22、*.log 24、*-edge.mjs 10、excel-preview-live.mjs 1、compare-lifecycle-assets.mjs 1、opening-chair-edge-13812.mjs 1、*.py 17、*-build.txt 4、compliance-*.txt 8、*-stage.txt 1、compliance-compare-*.json 3、ppocr-selftest.* 3、matte-*.png 3、m3.png 1、expert-system-thin-*.jar 1，目录 11 个（verify-*×3、schedule-pptx-probe、frontend-build-*×3、dist-verify-1.0.34.2、compliance-maintenance-dist、ocr-probe-classes、excel-preview-sample）。

### 3.4 C 类隔离内容

supplier-images-20261113\（39 素材）、backup\supplier-pre-purge-20261113.sql（清空前备份，随隔离区保留）、supplier-image-insert/import SQL、映射 JSON、导入 TSV、3 个供应商导入 py、4 个 html 快照（f_0ff4c0/f_91609a/f_c56a9a/leda）。

## 4. 未处置项（未获授权，原地保留）

| 文件 | 大小 | 原因 |
| --- | --- | --- |
| 3 某某医院 李测试.docx | 1.4 KB | 真实客户文档样本，个人信息处置需另行确认 |
| dinghuixin.doc | 134 KB | 同上 |
| resume-cand-1~3.{jpg,png} | 约 620 KB | 简历解析样本（个人信息） |
| resume-sample.docx | 1.6 KB | 同上 |

## 5. 恢复与最终删除

- **恢复**：将 `.quarantine-20261113\A|C` 下同名项移回 `temp\` 根目录即可（manifest.csv 记录原路径）。
- **最终删除**：7 天观察期至 **2026-11-20**，经负责人再次确认后清空 `.quarantine-20261113\`。
- `login.json` 不可恢复（凭据文件按安全要求不留副本）。

## 6. 文档同步

- 本审计记录（新增）
- `docs/handover/tasks/20261113_temp_cleanup_plan.md`（状态更新为"已批准并执行"）
- `builds\README.md`（archive 索引追加 1.0.21 制品）
- `temp\archive\README.md`（归档索引，随 temp 忽略不入库）
- `docs/handover/README.md`（摘要条目）
