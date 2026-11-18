# 专家简历人像抠图本地对照

- 任务编号 20261118-portrait-matting-modnet-evaluation，主责 Codex，交接日期 2026-11-18。已完成方案验证，集成与发布待定。
- 本地基线为 `main@c6a634b`，工作区原有部署文档改动。生产健康接口实测 `UP / 1.0.65 / e305588`。业务代码、数据库、权限和生产模型配置未修改。
- 样本为负责人提供的 PPTX 中的单张 1080×1542 JPEG。原图两侧均为白西装。原件与推理产物留在本机，实验目录 `temp/matte-evaluation-20261118/`。

## 复现

- 用当前 `photomatte-worker.py` 分别抓取 rembg 原始输出、掩码填孔、灰底清理和最终 RGBA，复现衣服变灰与耳侧背景残留。
- 安装的 rembg 默认 cutout 将 RGBA 与透明黑按掩码混合。工作进程随后硬化 alpha，把部分先前被压暗的 RGB 作为不透明主体输出。只把最终 alpha 应用到原图 RGB，可纠正暗衣但不改变轮廓。该对照与现版输出的 alpha 逐像素相等。
- 在最长边约 1200 的源图坐标中，约为 `(0.18w, 0.82h)` 的白襟像素从 `(104,103,107,200)` 恢复为 `(247,246,254,200)`，MODNet 输出为 `(247,246,254,255)`。约为 `(0.70w, 0.53h)` 的耳侧背景像素在现版 alpha 为 239，原图 RGB 对照仍为 239，MODNet 为 2。

## 本地候选

- [MODNet 官方 ONNX 指南](https://github.com/ZHKKKe/MODNet/tree/master/onnx)。使用现有 ONNX Runtime 的 CPU provider、4 个 intra-op 线程，依官方归一化与尺寸处理思路直接预测软 alpha。验证权重来自 [公开 ONNX 镜像](https://huggingface.co/DavG25/modnet-pretrained-models/blob/main/models/modnet_photographic_portrait_matting.onnx)，25,888,640 字节，SHA-256 为 `07C308CF0FC7E6E8B2065A12ED7FC07E1DE8FEBB7DC7839D7B7F15DD66584DF9`。镜像哈希用于复现实验，生产前应确定正式权重来源和校验值。
- 本机加载约 1.31 秒，目标样本单次 CPU 推理约 1.16 秒，另两张蓝底对照约 0.50 和 0.32 秒。计时不含上传、OCR、候选缩放和返回。目标照白西装及领口恢复，耳侧大块灰底消失。蓝底蓝衣对照恢复了大面积衣物，另一张蓝底深色西装对照轮廓完整。通过 `compare.py` 复测，目标对照图为 `liu-portrait-comparison.png`。两张小尺寸对照禁用了上游超分以便孤立比较当前处理与候选模型。
- [BiRefNet 项目](https://github.com/ZhengPeng7/BiRefNet) 的 portrait 模型也由 [rembg](https://github.com/danielgatis/rembg) 支持。可作为细节质量复评的候选，本次未测试。

## 集成建议与验收

先保证前景 RGB 从原图取值，候选人像模型输出 alpha，避免在生产逻辑中继续叠加颜色阈值。若确认接入，可在现有常驻 worker 加本地 MODNet 模型开关，模型按部署步骤预置并验哈希，运行时无需下载。启动异常、超时、空掩码等明确失败时保留原图可选。模型开关、原图候选行为及生产更新需单独确认。

接手先复跑目标和对照，用同一裁剪与 600px 输出路径核对白襟、肩部及耳侧，同时增加蓝衣蓝底、深发深底、白衣浅底和低分辨率样本。查看透明图在明暗两种背景的效果，分别测首张和批量吞吐，再决定默认模型。若试点不达标，关闭新模型开关即可恢复旧链路。

## 1.0.66 集成与发布尝试

`a043b19` 集成 MODNet，`db9c4e3` 构建前后端 1.0.66。正式 worker 对目标 PPTX 照片与两张对照图通过透明通道和衣物覆盖检查，目标图耳侧背景样点 alpha 小于 10。移除模型路径后 U²-Net 正常输出。构建目录为 `builds/1.0.66-build-20261118_155440/`。权重已放到 `<LOCAL_PATH> Files/JSHZ/portrait-matting/modnet.onnx`，SHA-256 验证通过。

蓝绿发布影子实例 1.0.66 通过健康检查。Nginx 信号任务返回 267009，脚本恢复 8080 转发并验证 reload，生产前后端仍为 1.0.65。8081 仍监听，需由管理员核实其命令行属于该候选后清理，确认生产继续走 8080，再运行原发布脚本 `temp/run-deploy-modnet-10066.ps1`。自动执行策略拒绝了清理影子进程并重试的动作。发布日志为 `logs/20261118-modnet-1.0.66-deploy.log`。回滚候选为 `builds/1.0.65-build-20261118_153359/expert-system.jar`。数据库无迁移。
