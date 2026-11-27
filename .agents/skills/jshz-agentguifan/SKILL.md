---
name: jshz-agentguifan
name_en: PMO Module Development and Release Verification
name_zh: PMO 模块开发与发布验证
version: 1.2.0
description: Implements or extends business modules, ledgers, dictionaries, and master-detail features in jshz-PMO. Checks migration SQL, fresh-database baselines, Spring Boot APIs, Vue pages, and permission entry points against repository conventions, then verifies and hands off based on the actual change. Also used for PMO defensive security review, pre-release checks, post-release smoke testing, and business acceptance.
description_en: Implements or extends business modules, ledgers, dictionaries, and master-detail features in jshz-PMO. Checks migration SQL, fresh-database baselines, Spring Boot APIs, Vue pages, and permission entry points against repository conventions, then verifies and hands off based on the actual change. Also used for PMO defensive security review, pre-release checks, post-release smoke testing, and business acceptance.
description_zh: 用于在 jshz-PMO 新增或扩展业务模块、台账、字典或主子表功能。按仓库约定核对迁移 SQL、新库基线、Spring Boot API、Vue 页面和权限入口，并按实际改动验证和交接。也用于 PMO 防御性安全审查、发布前检查、发布后冒烟和业务验收。
argument-hint: Describe the jshz-PMO module, change, review, or release scope to work on
argument-hint-en: Describe the jshz-PMO module, change, review, or release scope to work on
argument-hint-zh: 说明要处理的 jshz-PMO 模块、改动、审查范围或发布范围
user-invocable: true
---

# PMO 模块开发与发布验证

防御性安全审查，或改动文件访问、项目数据范围、敏感资料处理时，读取 [PMO 防御性安全审查清单](references/defensive-security-review.md)。仅做安全审查时，先确认工作树和仓库规则，再按清单审查并记录证据。

仅做发布检查、上线或发布后验收时，直接进入第 5 步。

1. 从当前工作树确认这是 jshz-PMO。读取仓库的 `AGENTS.md`（包括作用域内的规则）和本需求相关文档，查看分支、未提交改动及现有调用链。用客户或供应商模块定位同类实现，再以当前代码、配置、测试和已批准的需求为准。
2. 把验收条件映射到涉及的层：数据结构和既有数据、API 与业务规则、页面和导航、角色与数据范围。每层写明实际改动或省略理由。按 [模块参照与检查项](references/module-conventions.md) 检查涉及的层。权限有变化时核对完整访问链，包括直接调用 API。
3. 新增或修改业务行为、修复可自动化复现的缺陷时，按 [PMO 测试先行](references/test-first.md) 先写并运行失败测试，再做最小实现。沿用同类模块的契约，从必要的增量迁移到可调用 API、页面和权限入口逐层联通。跨层能力只实现需求要求的部分。已有审批、状态流转、历史记录、数据隔离和回滚语义以对应模块的规则与测试为准。
4. 在隔离或获准的环境验证相关后端、前端、数据库和权限路径。涉及升级时分别检查新库初始化与既有库增量；涉及权限时至少核实允许和拒绝路径。同步仓库要求的接口、架构、部署、权限与交接文档。
5. 涉及发布前检查、上线或发布后验收时，按 [PMO 发布验证](references/release-verification.md) 核对本次变更和目标环境。
6. 交付时给出变更点、实际运行的验证及结果、未验证项和发布状态。按仓库规则另行取得生产变更授权；生产迁移和部署由仓库发布流程控制。
