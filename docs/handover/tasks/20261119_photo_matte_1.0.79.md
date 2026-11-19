# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261119-photo-matte-robustness
- 任务名称：抠图透明化健壮性修复（胡鸿涛/黎海亮案例）+ 1.0.78 发布核验
- 主责 Agent：ZCode
- 交接时间：2026-11-19
- 当前状态：1.0.78 前端**已上线**（root 已切、入口 v1.0.78、路由 200，脚本末尾误报已修正）；1.0.79 后端已完成待发布（提交 `bb4c8f5`，预检 ALL PASS）

## 2. 问题与根因（生产日志 logs/expert-app.log）
1. `MODNet returned an invalid portrait mask`：refine 链在 MODNet 掩码偶发无效时**整次抛错回退原图**（同图本地/生产结果不一致，非确定复现）→ 用户看到非透明照片。
2. `PIL.UnidentifiedImageError: cannot identify photomatte-in-*.png`：临时 PNG 偶发被破坏（疑似外部清理/竞态），未重试。

## 3. 修复
- `photomatte-worker.py`：refine 中 MODNet 失败**降级 rembg(u2net) 主链**完成透明抠图，不再整体失败。
- `PhotoMatteService.removeBackground`：worker ERROR 时重写临时文件**重试一次**。
- `photomatte-worker.py`（二次修复，提交见 git log）：`_resolve_u2net_home()` 逐级搜索本地 u2net.onnx（含嵌套 `models/models/u2net/` 布局）。**根因补充**：生产 NSSM 设 `U2NET_HOME=<LOCAL_PATH>`（根目录无 onnx），rembg 降级链会尝试联网下载必失败；修复后离线可解析。已用生产同款 env+cwd 实测降级链输出透明 RGBA（transparent_frac 0.21/0.13）。
- MODNet 在生产（LocalSystem 账户）掩码偶发无效的底层原因未完全定位（同 env/cwd/输入以 guoshuai 复跑均正常）；降级链兜底后不影响交付，worker stderr 新增标记 `MODNet invalid, degrade to u2net` 供运维观察。

## 4. 验证
- 胡鸿涛/黎海亮真实原件（本地 Java 全链 + 生产同款 env 的 worker 直跑）：每份 2 个候选 PNG，firstTransparent=true ✅
- 全量 mvn test：**216 项 0 失败**；mvn compile 通过
- 1.0.79 预检 ALL PASS（候选含降级补丁、回滚 jar=live jar 哈希一致）

## 5. 发布与回滚
- 发布：管理员 PowerShell `temp\deploy-photo-matte-1079.ps1 -Apply`（基线 1.0.77/45f80ca，nginx root 基线=1.0.78 前端）
- 回滚：`deploy-backend.ps1 -JarPath builds\1.0.77-build-20261119_135936\expert-system.jar -Version 1.0.77 -GracefulSwitch`
- 注意：MODNet 掩码偶发无效的底层模型原因未深究（降级后不影响交付）；若日志仍高频出现降级 WARN，建议排查 GPU/CUDA 非确定性。

## 6. 1.0.78 发布核验记录（Owner 手动执行）
- nginx root 已指向 `frontend/releases/1.0.78-supplier-card-fill-20261119`，入口 `assets/index-DjhSKGi7.js` 含 v1.0.78，`/` 与 `/supplier` 均 200 → **发布成功**
- 脚本最后的"Live release does not carry v1.0.78"为校验缺陷（查了 index.html 而版本标记在 JS bundle），已修正；当时 exit 标记误写 1，已改为 0 并注明。
