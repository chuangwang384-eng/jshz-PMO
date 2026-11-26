# scripts/legacy — 一次性历史脚本归档（2026-11-26 目录结构整理）

本目录收纳"已完成历史使命"的一次性脚本，**不再被任何现行流程调用**，仅为可追溯保留：

- `apply-migration-*.ps1`：2026-11-09～11-25 各次存量数据迁移的执行器（对应迁移 SQL 已并入 `database/migration/` 台账，由 `db-migrate.ps1` 统一管理；新环境不再重放这些脚本）。
- `install-ppocr-runtime-20261111.ps1` / `install-photomatte-20261111.ps1`：OCR/抠图运行时的一次性安装器（目标环境已装好；重装步骤见对应 docs/releases 记录）。
- `migrate_sqlite_to_mysql.py` / `migrate_supplier_image.py` / `normalize_supplier_city.py` / `seed_supplier_city_code.py` / `compliance-ppocr-table.py`：一次性数据迁移/清洗工具。

注意：历史交接/发布文档（docs/handover、docs/releases、docs/design）中以 `scripts/apply-migration-...` 等旧路径引用本目录文件时，一律按 `scripts/legacy/<同名文件>` 定位（文件名未改，git 历史可查）。
禁止在本目录新增脚本；新的运维脚本放 `scripts/` 顶层并遵守 AGENTS.md 第 18/19 节（凭据解析 dot-source common-credentials.ps1）。
