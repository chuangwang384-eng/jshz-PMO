# 1.0.129 串场向导职称联动修复（2026-11-23）

## 背景
串场制作向导第二步（专家信息）补填的职称（专家库为空）在预览中不显示。

## 根因
`ShowWizard.vue` 中 `deck()` 导出时仅从专家资料池刷新头像（`portraits.get(sourceKey)`），文本字段（姓名/职称/单位）不联动。无草稿时 `builtSlides` 计算属性可实时联动；但草稿保存后 `currentDeckSlides = editableDeck || builtSlides` 走已恢复的草稿快照，池内补填的职称不会写入页面 `persons`，预览/归档均缺职称。

## 修复
`frontend/src/views/project/ShowWizard.vue`：新增对 `experts` 的深度 watch，资料池文本字段变化时按 `sourceKey` 同步到 `editableDeck` 各页面 persons 的 name/title/unit，并同步简历页标题（"姓名 职称"，仅在标题仍为旧值或为空时重建，避免覆盖页内 inline 编辑）。纯前端，无数据库/接口变更。

## 验证
- `npm run build` 通过（is-not-exported 告警 0）
- graceful 发布 `1.0.129-show-title-sync`：Nginx root 切换成功、旧 assets 保留、curl 200、线上主 bundle 含 1.0.129
- 浏览器端到端复验：未执行（待业务在向导中补填职称后预览确认）

## 回滚
`scripts/rollback-frontend.ps1` 回到 1.0.128-show-single-archive。
