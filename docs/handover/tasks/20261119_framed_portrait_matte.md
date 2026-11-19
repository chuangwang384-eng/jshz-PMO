# 圆形排版简历照片透明抠图

2026-11-19，主责 Codex。基线为 `main@39b7f73`，开工工作区干净。修复分支为 `codex/framed-portrait-matting`。

原图片带圆形排版裁切的透明角，旧 worker 将其判为已经完成抠图，直接返回原有透明通道，圆内背景及装饰边框仍然存在。新实现以原 alpha 的外形识别圆或椭圆排版，允许长轴两侧被裁断。外缘用圆内背景色为模型提供输入，输出 alpha 仍受原剪裁区域约束。已有真实透明人像维持原通道直通。

修改后端 Python worker 和原件回归测试。继续使用本地 MODNet 与 U²-Net 回退，模型、接口、数据库及生产配置沿用现状。原件只在本地用于验证，未纳入 Git。功能已合入 main 并随 1.0.80 上线，登录后重新解析待业务验收。见 [生产发布与回滚](../../releases/20261119-framed-portrait-1.0.80.md)。

JDK 17 隔离 Maven 运行 `-Dtest=PhotoMatteServiceTest,ResumeParseServiceTest`，通过 35 项、跳过 5 项。配置 `PORTRAIT_FRAME_FIXTURE_FIRST` 和 `PORTRAIT_FRAME_FIXTURE_SECOND` 运行两份原始 PPTX 的候选回归。已检查两张证件照的透明区域分别约 45% 与 47%，目检黑白背景及橙色装饰边框已移除。独立运行 U²-Net 也输出透明照片，胡鸿涛发丝边缘较粗。已有直接透明人像的像素与原裁剪结果一致。胡鸿涛 PPTX 另有装饰图候选，候选顺序沿用现有规则。原始排版裁去的边缘无法从文档恢复。
