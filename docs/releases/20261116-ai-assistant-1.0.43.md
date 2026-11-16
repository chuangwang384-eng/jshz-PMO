# 2026-11-16 AI 助手 发布记录（1.0.42 功能 / 1.0.43 短超时热修）

## 功能
系统使用问答 + 操作指引卡片：全局右下角「AI 助手」悬浮按钮 → 对话抽屉；用户提问 → 关键词打分取候选 →（候选不唯一时）本地 Ollama qwen2.5:3b 在候选内做意图选择（20s 短超时，1.0.43）→ 返回人工维护的条目卡片（答案 + 操作步骤 + 站内跳转按钮）。

## 边界（AGENTS.md 合规）
- 纯只读问答：不代执行任何操作、不碰业务数据、无工具调用
- 链接/步骤/答案全部来自 ai_assistant_entry（管理员维护），模型不生成操作路径；模型返回候选外键视为无效
- Ollama 不可用/超时自动回退关键词命中；无匹配如实告知
- 条目按 perm_code 过滤（无权限用户看不到受限条目）
- /ask 登录即可用；条目管理 GET/PUT /api/ai-assistant/entries 绑定 ai:manage（ADMIN）

## 数据库
迁移 20261116_ai_assistant.sql（已执行，一次成功）：ai_assistant_entry 表 + 20 条种子 + ai:manage 权限 3 行绑定 ADMIN。

## 人工维护
管理员设置 → AI 助手知识库（/admin/ai-assistant）：表格 + 编辑弹窗，可增改标题/问法样例/答案/跳转/步骤/权限/启停，保存即生效。

## 验证
- mvn test 157/157（含 AiAssistantServiceTest 7 用例：命中/无匹配/权限过滤/LLM 候选约束/回退/校验）
- 前端构建 is-not-exported=0
- 线上实测：「我想新建一个项目」→ CREATE_PROJECT 卡片（/project + 4 步）；「收费标准在哪改」→ 有权限命中；「日程专家」歧义问句 → 短超时内回退关键词命中 SCHEDULE_UPLOAD
- /health=1.0.43 gitShort=1db4872；前端 1.0.43-version-sync-20261116
- 浏览器人工验收：待 Owner（抽屉交互/卡片跳转/管理页编辑）

## 回滚
后端归档 1.0.42/1.0.41 可回；前端 rollback.conf 备份齐；数据库 DROP TABLE ai_assistant_entry + 删 ai:manage 权限行（SQL 头注释）。
