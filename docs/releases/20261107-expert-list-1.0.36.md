# 2026-11-07 专家库列表改造发布（1.0.36，后端+前端）

状态：**COMPLETE**（后端蓝绿 + 前端 graceful，真机验证通过，无数据库变更）。

## 变更内容
按 [需求 v1.1](../requirements/2026-11-07-专家库列表改造-需求文档.md)（Owner 决策：地区筛选拍板去掉、档案状态按口径 B 实施）：

**后端**：
- `GET /api/experts` 新增可选筛选参数：`unit`/`department`（精确）、`titles`（CSV 多选）、`photo=uploaded|missing`、`complete=complete|incomplete`（照片+身份证正反面+银行卡+联系方式齐全=完整，Owner 口径）、`updatedFrom/updatedTo`（yyyy-MM-dd）；非法取值/日期/区间 400；旧调用（仅 keyword）行为不变。
- `GET /api/experts/filters`：单位/科室/职称去重字典（登录即可，按出现次数降序）。
- `Expert.toView` 附带 `profileComplete` 瞬时字段；单测 `ExpertProfileCompleteTest` 2 用例通过。

**前端**（ExpertList.vue 列表区重构，详情弹窗/审批流/照片脱敏未动）：
- 三层结构：搜索筛选区（统一搜索 480px + 高级筛选含徽标计数 + 重置 + 单位查询外链 + 新增专家 + 导出下拉）→ 工具行（共 N 人 + 卡片/列表视图切换）→ 内容区。
- 紧凑卡片：头像 128→80px 左置横排，姓名/单位/科室一行视线内可读。
- 列表视图（表格）：勾选/头像/姓名/单位/科室/职称/联系方式/更新时间/操作；与卡片视图共用数据、勾选、页码。
- 导出收敛：默认仅「新增专家 + 导出 ▾」（有 export 权限=全量 Excel/ZIP；所有人可选中导出）；勾选后出现**底部悬浮批量栏**（已选 N 位 | 下载 Excel | 下载 ZIP | 清空）。
- 一键重置清空全部高级筛选；筛选变更自动回第 1 页，勾选跨搜索/翻页保留。

## 发布
- build-all 隔离构建 jar（69 依赖）→ deploy-1.0.36-wrapper.ps1（UAC）→ 蓝绿全阶段 OK，/health=1.0.36 UP。
- 前端 `VITE_DASHBOARD_LAYOUT_V2=true` 构建 → graceful，ReleaseId `1.0.36-expert-list-20261107`，master PID 39504 不变。

## 真机验证（测试账号）
- 三层结构渲染、高级筛选面板六字段齐全；按科室"肿瘤内科"筛选 148→10 人；重置恢复 148。
- 勾选 2 位 → 悬浮批量栏出现（已选 2 位 | Excel | ZIP | 清空）；切换列表视图（20 行表格）勾选保留、批量栏常驻；批量栏「清空」后消失。
- 列表视图表格列齐全、查看详情按钮在位。
- 回滚：前端 release-metadata conf；后端 deploy-backend 归档 jar（KeepVersions=5）。
