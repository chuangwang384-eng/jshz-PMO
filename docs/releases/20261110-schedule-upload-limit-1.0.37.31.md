# 发布记录：日程上传限额 20MB→50MB + 413 友好提示（1.0.37.31）

- 日期：2026-11-10
- 状态：**已发布生产（11:19，后端蓝绿+前端 graceful+nginx 配置重载），发布后核验通过**
- 背景：Owner 上传《3.14重庆活动日程.pptx》（27.7MB）报 "Request failed with status code 413"——请求未到后端即被 Nginx `client_max_body_size 20m` 拦截；目录内另有多份 27-30MB 真实日程，20MB 上限定得过紧（Owner 确认方案：提至 50MB+前端友好提示）。

## 变更
- 后端：`ScheduleParseService.MAX_SIZE` 20MB→50MB（错误文案同步），与 Spring multipart 全局 50MB/60MB 对齐；仅日程上传，简历批量建档 ≤20MB 维持不变。
- 前端：日程上传提示 ≤50MB；`onScheduleFile` 增加 50MB 前端预检（超限弹中文提示，不再出现英文 413）。
- Nginx：`conf.d/jshz-pmo.conf` `/api/` location `client_max_body_size` 20m→**60m**（与 Spring max-request-size 对齐），`nginx -t` 通过，随发布流程 reload 生效。
- 版本：version.txt / frontend/package.json → 1.0.37.31。

## 发布与核验（Owner 手动执行 deploy-13731.cmd，11:19 完成）
- `/health` 直连与经 Nginx 均 **1.0.37.31 UP**（buildTime=10:03:24 与制品一致）。
- root → `frontend/releases/1.0.37.31-upload-limit-50m-20261110`，线上 index 主 bundle=磁盘哈希（index-CH2cY75y.js）。
- **413 解除实证**：25MB 随机体 POST `/api/project/1/schedule`（未登录）返回 **401**（到达后端），不再是 413。
- SPA `/` 200。

## 回滚
后端切上一版归档；前端 root 指回 `1.0.37.30-parse-generalize-20261110`；nginx `client_max_body_size` 改回 20m 并 reload。
