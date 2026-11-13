# 发布记录：我的主页信息卡精简（前端 1.0.38.22）

- 发布时间：2026-11-13 10:47 左右；ReleaseId：`1.0.38.22-profile-card-trim-20261113`（UAC 提权 graceful 切换）
- 变更（仅前端 `frontend/src/views/profile/ProfileCard.vue`）：
  1. 移除卡片头部「个人中心」标题（Owner 2026-11-13 标记删除；页面已有浏览器标题与左侧导航，卡片头冗余）。
  2. 移除「我的待办（企业合规）」按钮及其跳转（REQ/DES-20261102 D19 入口退役；企业合规入口保留在知识星球导航）。
  3. 保留：用户名/角色/账号ID/注册时间与修改密码。
- 版本 1.0.38.22。回滚：恢复 release-metadata 下对应 nginx-before.txt 的 root 指针。
- 验证：构建门禁 is-not-exported=0；线上 index 与发布目录一致、入口引用最新 chunk（无被删元素、保留修改密码）；后端未动。
- 未执行：浏览器人工复核（Owner 自验）。
