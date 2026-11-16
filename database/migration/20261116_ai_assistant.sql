-- =============================================================
-- 20261116_ai_assistant.sql
-- AI 助手（系统使用问答+操作指引卡片，Owner 2026-11-16）
-- 内容（幂等，可重复执行）：
--   1. 新表 ai_assistant_entry 知识条目（管理员「AI 助手知识库」页维护）
--   2. 种子 ~20 条核心操作条目（INSERT IGNORE，管理员可改）
--   3. 权限码 ai:manage（菜单+条目管理接口，绑定 ADMIN）；/ask 接口不注册=登录即可用
-- 回滚：DROP TABLE ai_assistant_entry;
--       DELETE FROM role_permission WHERE permission_code='ai:manage';
--       DELETE FROM permission WHERE code='ai:manage';
-- =============================================================
USE `expert_system`;

-- ---------- 1. 知识条目表 ----------
CREATE TABLE IF NOT EXISTS `ai_assistant_entry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `entry_key` VARCHAR(50) NOT NULL COMMENT '稳定键',
  `title` VARCHAR(200) NOT NULL COMMENT '条目标题',
  `questions` TEXT NULL COMMENT '问法样例，每行一条（意图匹配用）',
  `answer` TEXT NOT NULL COMMENT '答案正文（模型仅润色，不生成链接/步骤）',
  `link_url` VARCHAR(255) NULL COMMENT '跳转深链（站内路由）',
  `link_label` VARCHAR(100) NULL COMMENT '跳转按钮文案',
  `steps` TEXT NULL COMMENT '操作步骤，每行一步',
  `perm_code` VARCHAR(50) NULL COMMENT '所需权限码；NULL=全员可见',
  `sort` INT NOT NULL DEFAULT 0,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_aae_key` (`entry_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 助手知识条目';

-- ---------- 2. 种子条目 ----------
INSERT IGNORE INTO `ai_assistant_entry` (`entry_key`,`title`,`questions`,`answer`,`link_url`,`link_label`,`steps`,`perm_code`,`sort`) VALUES
('CREATE_PROJECT','新建项目','怎么新建项目\n如何立项\n添加一个新项目\n建项目\n帮我新建项目','在项目管理页点击「新建项目」，填写项目名称、会议大类（线上/线下）、团队、项目起止时间、举办时间等信息后保存。新项目默认从「会议预算制作」阶段开始推进。','/project?action=create','去新建项目','打开项目管理页\n点击「新建项目」按钮\n填写名称/大类/团队/起止时间等必填项\n保存后进入项目详情，从会议预算制作阶段开始',NULL,1),
('EDIT_PROJECT','编辑项目信息','怎么修改项目信息\n改项目名称\n编辑项目','在项目详情页点击「编辑项目」可修改基础信息。项目成员（负责人/创建人/协助人）均可编辑。','/project','去项目管理','进入项目详情页\n点击右上角「编辑项目」\n修改后保存',NULL,2),
('PHASE_ADVANCE','项目阶段推进','怎么推进阶段\n项目到下一阶段\n阶段回退','项目详情页顶部有生命周期步骤条，点击「推进下一阶段」进入下一阶段；如需回退可用「更多操作」中的回退入口。当前流程：会议预算制作 → 日程上传。','/project','去项目管理','进入项目详情页\n确认当前阶段任务已完成\n点击「推进下一阶段」',NULL,3),
('BUDGET_OFFLINE','线下会议预算填写','线下预算怎么填\n预算明细怎么录\n会议费用怎么报','线下项目在「会议预算制作」阶段按七类费用填写：酒店（场地/住宿/用餐/茶歇）、交通（大交通+地面交通）、搭建（非必需）、物料、媒体（非必需）、礼品、人员。每行填数量×单价自动算金额，可增删行；填完点「确认报价」。收费标准默认值已预填，可修改。','/project','去项目管理','进入线下项目详情（会议预算制作阶段）\n按分节填写数量与单价\n非必需分节可整节移除\n核对合计后点「确认报价」',NULL,4),
('BUDGET_ONLINE','线上项目报价单','线上报价怎么做\n线上项目预算\n点选报价','线上项目在「会议预算制作」阶段直接点选收费项生成报价单：勾选需要的收费项即计入合计，数量可调整，也可添加自定义费用项，确认后生成报价单。收费项由管理员在「项目配置-线上收费标准」维护。','/project','去项目管理','进入线上项目详情（会议预算制作阶段）\n勾选需要的收费项\n可调整数量或添加自定义项\n点「确认报价」',NULL,5),
('FEE_CONFIG','收费标准配置','收费标准在哪改\n串场费怎么调\n预算默认单价修改\n线上收费项配置','管理员在「管理员设置→项目配置」维护收费标准，分线下/线上两个页签。可修改默认值（区间填「低,高」）、单位、备注，可启停或新增收费项。修改只影响之后新打开的预算单。','/admin/project/config','去项目配置','打开管理员设置→项目配置\n选择线下/线上页签\n修改或新增收费项\n点「保存修改」','project:manage',6),
('SCHEDULE_UPLOAD','日程上传与解析','日程怎么传\n上传会议日程\n议程解析','在项目的「日程上传」阶段上传日程文件（pdf/doc/docx/ppt/pptx/图片/xlsx，≤50MB），系统自动解析参会专家为候选名单，需人工逐条确认后保存。重新上传会替换当前日程并重新解析。','/project','去项目管理','进入项目详情并推进到日程上传阶段\n在阶段资料区上传日程文件\n等待自动解析\n在参会专家区逐条确认候选并保存',NULL,7),
('SPEAKER_CONFIRM','参会专家确认与建档','参会专家怎么确认\n库外专家建档\n专家名单确认','日程解析出的专家为候选，需在参会专家区确认：能匹配专家库的直接关联，库外专家可「去建档」或批量上传简历建档后自动回绑。确认后成为正式记录。','/project','去项目管理','进入项目详情参会专家区\n核对候选名单与专家库匹配\n库外专家点「去建档」或用批量建档\n点「确认保存参会专家」',NULL,8),
('EXPERT_SEARCH','专家库查询','怎么查专家\n搜索专家\n专家库在哪','专家库支持关键词搜索与高级筛选（地区/职称/单位等），提供列表与卡片双视图，可查看详情、简历与联系方式。','/experts','去专家库','打开专家库\n输入关键词或使用高级筛选\n点击专家查看详情',NULL,9),
('EXPERT_RESUME','专家简历上传解析','简历怎么传\n批量上传简历\n简历解析','专家库支持上传简历自动解析建档（姓名/单位/职称/简介等），支持批量上传；解析结果为候选，需人工核对后保存。项目内也可在参会专家区批量建档。','/experts','去专家库','打开专家库\n点击上传简历（支持批量）\n核对解析结果\n确认保存建档',NULL,10),
('COMPLIANCE_NOTEBOOK','合规记事本','合规待办怎么处理\n合规记事本在哪\n合规要求注意事项','项目详情右侧「合规要求注意事项」展示该项目绑定的合规待办，可标记完成/待处理；企业合规板块支持文档解析生成事项草稿（需人工确认）、收藏关注与个人进度跟踪。','/knowledge-planet/enterprise-compliance','去企业合规','打开项目详情查看右侧合规待办\n或进入企业合规板块管理事项\n处理完成后标记状态',NULL,11),
('KNOWLEDGE_UPLOAD','知识文档上传','知识星球怎么传文档\n上传规范文档\n知识库上传','知识星球支持上传文档（pdf/office/图片等），系统解析入库后可全文检索与在线预览；文档有版本管理，删除会同步索引。','/knowledge-planet','去知识星球','打开知识星球\n点击上传文档\n等待解析完成\n可检索/预览/管理版本',NULL,12),
('KNOWLEDGE_SEARCH','知识检索与AI问答','怎么搜知识文档\n知识库搜索\n文档内容查询','知识星球支持关键词检索与语义问答，检索结果遵守文档权限；点击结果可在线预览原文。','/knowledge-planet','去知识星球','打开知识星球\n输入关键词或问题\n查看结果并预览原文',NULL,13),
('APPROVAL_FLOW','审批流程','审批怎么发起\n审批流配置\n审批在哪看','系统内置审批流（如项目相关审批），可在审批板块查看待办与历史；管理员可在「审批流配置」调整审批节点与审批人。','/profile/my-approvals','去我的审批','打开审批板块查看待办\n按流程提交/审批\n管理员可在审批流配置调整节点',NULL,14),
('DASHBOARD_LAYOUT','工作台布局调整','工作台怎么自定义\n首页卡片拖动\n工作台布局','工作台支持拖拽调整卡片布局，布局按用户保存；登录后可在卡片目录添加/移除卡片。','/dashboard','去工作台','打开工作台\n进入编辑模式拖动卡片\n保存布局',NULL,15),
('LOGIN_PASSWORD','登录与改密','怎么登录\n忘记密码\n修改密码','使用账号密码登录；首次登录或管理员重置后需修改密码。忘记密码请联系管理员重置。',NULL,NULL,'使用分配的账号密码登录\n按提示修改初始密码\n忘记密码联系管理员重置',NULL,16),
('CUSTOMER_SUPPLIER','客户与供应商管理','客户信息在哪\n供应商查询\n供应商报价','客户与供应商模块支持档案维护、关联项目查看；供应商含报价项与图片资料，支持列表/详情视图。','/supplier','去供应商','打开客户/供应商模块\n搜索或新建档案\n查看详情与关联信息',NULL,17),
('STORAGE_ADMIN','存储管理','磁盘占用查看\n孤儿文件清理\n存储管理','管理员存储管理工具：查看各存储目录占用与预警、扫描清理孤儿文件、清理预览缓存。硬删除项目需输入完整项目名确认。','/admin/storage','去存储管理','打开管理员设置→存储管理\n查看占用统计\n按需扫描/清理孤儿文件','storage:manage',18),
('USER_ROLE_ADMIN','用户与角色管理','怎么加用户\n角色权限配置\n给用户开权限','管理员在用户管理新增/停用用户，在角色管理配置角色权限码；权限为数据驱动，接口与菜单按权限码控制。','/admin/users','去用户管理','打开管理员设置→用户管理/角色管理\n新增用户或调整角色权限\n用户重新登录生效','user:manage',19),
('PROJECT_FILE_PREVIEW','项目文件预览下载','项目文件怎么预览\n文件打不开\n下载项目附件','项目详情右侧「项目文件」列出各阶段上传的文件，点击文件名在线预览（pdf/图片/Office 转 PDF），也可下载。','/project','去项目管理','进入项目详情\n右侧项目文件区点击文件名预览\n或点下载',NULL,20);

-- ---------- 3. 权限码 ai:manage ----------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('ai:manage', 'AI助手知识库', 1, 'MENU', 'ai:manage');
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('ai:manage', 'AI助手-条目列表', 2, 'GET', '/api/ai-assistant/entries'),
('ai:manage', 'AI助手-条目保存', 2, 'PUT', '/api/ai-assistant/entries');
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'ai:manage' FROM `role` r WHERE r.`code` = 'ADMIN';

-- ---------- 4. 标记表 ----------
CREATE TABLE IF NOT EXISTS `mig_20261116_ai_assistant_done` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `done_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
