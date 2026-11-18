# 专家库简历证件照保留肩部

- 任务编号 20261118-resume-portrait-shoulders，主责 Codex，交接日期 2026-11-18，状态为已完成待验收。
- 基线为 `main@2450900`，工作区已有独立的部署文档修改及未跟踪文件。生产 `GET /health` 返回 UP、1.0.54、`b35471d`。
- 验收目标为用户提供的 PPTX 抠图保留脸部、肩部及原图所含的上半身，专家库候选预览显示完整构图。

## 变更

- `photomatte-worker.py` 对透明掩码提前结束且满足纯色顶部条件的原图，从顶边识别连通背景，检查候选人像与既有主体的重合度及底部中央覆盖情况，再补回肩部和衣服。模型、依赖、工作进程协议和 JPEG 回退沿用现有实现。
- `ExpertList.vue` 将新增和编辑的候选缩略图改为 64×64 容器内等比完整显示。`BatchResumeDialog.vue` 使用现有 44×44 容器等比完整显示候选。
- 架构说明及最新交接摘要已同步。数据库、接口返回结构、权限、文件存储和启动配置沿用现有实现。

## 复测与交付

- 从用户给定 PPTX 解包 `ppt/media/image1.png`，用本机 rembg 解释器运行 `backend/src/main/resources/photomatte-worker.py`，通过 stdin 逐行传入 JSON 数组 `[源图片路径,输出图片路径]`。改动前输出 368×445，底部中央无有效前景，复现原问题。改动后输出 422×619，底部中央衣服区域完整覆盖，保留透明背景。工作进程测试尺寸先于 Java 候选 600px 上限缩放。
- 同一进程处理 PPTX 原图与既有 DOCX 蓝底照片，协议输出 `READY → OK → OK`，DOCX 对照 PNG 与修改前逐字节相同。白底合成预览目检确认肩部轮廓及上衣可见。
- `npm --prefix frontend run build -- --outDir ../temp/resume-portrait/frontend-dist` 构建成功，Vite 报告大分块提示。样式检查使用 `rg -n -A4 'resume-candidate img|photo-choice img' frontend/src/views/ExpertList.vue frontend/src/components/BatchResumeDialog.vue`。
- 样本及对比图位于本机忽略目录 `temp/resume-portrait/`。复杂背景或与背景色高度接近的衣服仍依赖模型分割效果。浏览器登录后的上传和选照未执行。
- 上线需批准。接手先复测上述工作进程，再在隔离或经批准发布后的专家库上传同一份 PPTX，检查候选、选照和实际提交的照片。若需回滚，恢复三个实现文件并使用对应版本的前后端发布流程，涉及数据库迁移的步骤不适用。
