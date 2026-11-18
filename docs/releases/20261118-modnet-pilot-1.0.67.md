# MODNet 试点 1.0.67 生产发布

2026-11-18 16:14 CST 发布完成，主责 Codex。业务提交为 `642f6b4`，制品提交为 `fdd6e46`。常驻 worker 使用原图 RGB 与本机 MODNet 软 alpha。模型空掩码返回 ERROR，Java 使用原图 JPEG 候选。60 秒 worker 响应超时同样走原图候选。独立开关为 `PHOTOMATTE_MODNET_ENABLED`，生产使用权重目录的 `modnet.enabled` 标记。关闭时移除标记并重启 worker。

- 启用试点验证刘朵平样本正常输出。注入空掩码验证 ERROR，关闭开关验证 U²-Net 初始化。超时回退沿用 Java 现有分支，本次未注入超时做端到端复测。
- 前后端构建通过。采用 `-BluePort 8082` 重新发布，旧 8081 影子实例仍在，自动执行策略拒绝了停止该进程的动作。Nginx 已回切正式 8080，8082 影子已退出。
- 生产直连与代理健康为 `UP / 1.0.67 / fdd6e46`。前端快照为 `1.0.67-modnet-20261118`，首页、专家库及新 JS 入口返回 200。匿名解析返回 401。登录后真实上传待业务验收。
- 无数据库迁移。后端回滚包为 `backend/releases/1.0.67-20261118_161301/previous-expert-system.jar`。前端恢复对应回滚配置后平滑重载。发布日志见 `logs/20261118-modnet-1.0.67-deploy.log`。
