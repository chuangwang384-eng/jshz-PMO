# 发布与回滚记录：1.0.107 串场所见即所得编辑与结构收敛（wysiwyg-edit）

- 发布时间：2026-11-21 11:54–11:59（`release-full.ps1` 编排一次成功，state=SUCCESS/verified）
- ReleaseId：`1.0.107-wysiwyg-edit`

## 内容（对应 2026-11-21 五项修改需求）

- **所见即所得编辑**：后端 render 为全部可编辑文本元素注入 `data-edit="p{页序}.{字段路径}"` 锚点 + 编辑模式脚本（页面内按 **E** 或向导「编辑模式」按钮切换 contentEditable，虚线框标记）；向导第三步改为**内嵌 iframe 预览**，「保存修改」按 data-edit 路径把页内文字写回 deck 快照 → 保存草稿 → 重新渲染，形成闭环。编辑粒度覆盖：封面（机构/主标题/副标题/信息行 k·v）、过场（编号/标题/主领人）、出场（角色/姓名/职称/单位）、简历（标题/条目）、讨论墙（标题/嘉宾）、致谢/文字页。
- **结构收敛**：删除议程页与无专家文字页；稿件结构=封面 → 章节过场 → （讨论墙）→ 专家出场 → 简历 → … → 致谢。
- **照片圆框**：出场页照片卡由方形竖版改圆形（1:1，border-radius:50%）；简历页新增专家圆形头像（150px，rp-head 布局）。
- **专家信息保存**：向导每位专家卡新增「保存」按钮（保存整稿草稿）。
- **编辑器入口删除**：向导第三步「进入编辑器微调/归档」按钮移除，「归档 HTML」搬入向导（publishShow 同端点）；旧编辑器页面与路由保留（兼容），不再从向导露出。

## 验证

- 后端目标测试 6/6 绿（新增 rp-avatar / data-edit 路径断言）；全量随发布构建通过；前端构建 is-not-exported=0。
- preview 冒烟：data-edit 路径（p1.persons[0].name / p3.items[0] / p4.persons[1].unit）、__toggleEdit/hppt-editing/editTip、photo-card 圆框、rp-avatar 全部命中。
- 真机（项目 57）：向导 Step3 内嵌预览渲染（17 页新结构）；编辑模式激活后页面文字出现虚线编辑框、提示条与按钮态正确。

## 回滚

- 前端：`frontend/release-metadata/1.0.107-wysiwyg-edit.nginx-before.txt`（指向 1.0.106-show-structured）。
- 后端：`backend/releases/1.0.107-*/previous-expert-system.jar`。数据库无迁移。

## 遗留

- 页内编辑仅覆盖文本内容；字号/位置/换行等排版级编辑留待后续迭代。
- 编辑模式中翻页快捷键禁用（避免输入冲突），翻页用 HUD 按钮。
- 冒烟项目 62 待管理员删除。
