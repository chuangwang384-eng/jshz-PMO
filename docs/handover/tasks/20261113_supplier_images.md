# 交接记录：供应商真实图片导入（2026-11-13）

## 1. 基本信息
- 任务编号：20261113-supplier-images；主责 Agent：ZCode；状态：已完成待验收
- 授权：Owner 指示「导入可收集到的真实图片，拓展到十城」（实际按已选 12 城执行）

## 2. 范围与口径
- 120 家供应商全覆盖，12 城各 10 家；共 **121 张**（金陵饭店 2 张：外观主图+夜景副图），每家 1 张主图。
- 来源：image-search MCP 工具返回的真实 original_url（阿里云 OSS），Python 直连下载、无代理；逐条核对 caption 与任务目标匹配。
- **7 家命中真实本店/品牌图**：首汽租车(61)、神州租车(62)、浦东香格里拉(67)、深圳五洲宾馆(87)、杭州洲际(107)、南京金陵饭店(117)、济南山东大厦(157)。
- 其余为**同行业真实照片作示意图**，图片 name 字段如实标注「示意：…（网络图片，非本店实拍/未核实为本店）」，不冒充本店照片。
- 4 张原图超 8MB 存储上限（PIL/ffmpeg 不可用），改用已下载成功的同行业真实照片替换该 4 个酒店示意位，未新增 URL。

## 3. 数据库/文件变更（生产，数据层）
- supplier_image 新增 121 行（is_main：120 主图+1 副图）；物理文件落盘 `<LOCAL_PATH><uuid>.<ext>`。
- 无表结构/代码/接口变更；后端未重启。
- 留档：`docs/data/supplier-image-insert-20261113.sql`、`supplier-image-mapping-20261113.json`（含 assign/ok/fail/missing）。
- 回滚：`DELETE FROM supplier_image WHERE create_time>='2026-11-13'` 并删除对应 supplier_ 文件（映射 JSON 可定位）。

## 4. 验证（真实执行）
- DB 实测：images=121、distinct supplier=120、mains=120；12 城各 10 家 ✓
- 文件：下载时校验魔数(JPEG/PNG)+大小(1KB~8MB)，39 张直接通过、4 张超限替换；落盘 121 文件 ✓
- 抽样行：57/61/117/157 名称、is_main、file_name 正确 ✓
- 后端 /health UP(1.0.38.19)；图片流端点 `GET /api/supplier/images/{fileName}` 存在 ✓
- 登录接口 UTF-8 正常返回 token ✓
- **端到端 HTTP 图片流：未通过/受阻** —— 测试账号「测试」请求 `/api/supplier/57/images` 返回 401（该账号缺 supplier 权限，与导入无关；鉴权头格式已核对正确）。需 Owner 用有 supplier 权限的账号（如王创）在浏览器复核图片显示。
- 教训：Git Bash curl 传中文 body 会被 GBK 编码致后端 500，验证脚本须用 Python UTF-8 显式编码。

## 5. 未完成/待确认
- 浏览器页面图片显示人工验收：待 Owner。
- 示意图非本店实拍，业务对外使用前应替换为实地照片或取得授权。
