# 1.0.133 身份证过期风险提示 + 正面身份证号自动提取（2026-11-23）

## 范围
专家库 + 项目详情（引入专家提示）。后端蓝绿（gitShort f89bb85）+ 前端 graceful（`1.0.133-idcard-risk`）。无数据库迁移（复用 1.0.132 的 id_card_expiry 列）。

## 新功能
1. **引入专家过期风险自动提示**：
   - `IdCardExpiryExtractor.endDate/risk`：解析有效期终止日（长期=无风险），EXPIRED=已过期、EXPIRING_SOON=6 个月内到期（含边界）。
   - `ProjectService.listExperts` 每位专家附带 `idCardExpiry` + `idCardExpiryRisk`。
   - 前端 ProjectDetail：确认参会专家保存后、库外建档关联后自动弹风险提醒（列出姓名+有效期+风险）；参会专家表「档案关联」列常驻风险徽标（红=已过期/黄=6个月内到期）。
2. **身份证正面 OCR 自动填身份证号**：
   - `IdCardExpiryExtractor.extractIdNumber/parseIdNumber/checksumValid`：18 位号码 + GB 11643 校验码（ISO 7064 MOD 11-2），**校验通过才采纳**防 OCR 误识；优先「身份号码」关键词后匹配。
   - `ExpertService.autoFillIdCardNumber`：仅现有身份证号为空时填入，不覆盖手工/已审批值。
   - 触发点与反面有效期一致：管理员直写新增/修改（multipart）、审批通过归档后（新增/修改两路）。

## 验证
- `mvn test`：246 通过 / 0 失败 / 16 跳过（提取器单测扩至 12 例，含 6 个月边界、校验码正反例）。
- 生产 API 端到端：临时项目引入杨光（2005.12.26-2025.12.26）→ EXPIRED；张建生（2007.05.22-2027.05.22，距 6 个月边界 1 天）→ EXPIRING_SOON。临时项目与关联行已清理（project 64）。
- 浏览器：v1.0.133 项目详情页渲染正常。
- `npm run build` 通过，is-not-exported=0。
- 正面 OCR 真实图片端到端：未执行（需真实正面图上传触发；解析逻辑已单测覆盖，OCR 运行时与反面共用已验证链路）。
- 风险提示弹窗的完整参会专家确认流 UI：未执行（需日程解析数据，建议业务验收时过一遍）。

## 更正
1.0.132 汇报中「王文斌年内到期」有误：2026.06.15 早于当前日期，实为**已过期**。

## 回滚
后端回上一归档 + 前端 rollback-frontend.ps1 回 1.0.132-expert-idcard-expiry。
