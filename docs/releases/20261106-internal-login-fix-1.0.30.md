# 2026-11-06 内网登录故障修复发布（1.0.30-compliance-visual-hotfix-20261105）

状态：**COMPLETE**（部署与验证通过，连续性零失败）。

## 故障与根因
内网入口 http://<PRIVATE_IP>/ 登录不正常。实测服务端登录链路本身正常（curl 200），根因是**浏览器加载了旧版前端缓存**：
1. nginx 未对 `index.html` 显式设置 no-cache（原注释"默认 no-cache"与实际不符），浏览器启发式缓存旧入口页，用户长期跑旧版前端；
2. 旧版前端登录后调用早已下线的 `GET /api/user/info`，后端返回 500（`NoResourceFoundException: No static resource api/user/info`，见 logs/expert-backend.out.log 11-05 19:23/19:28），前端表现为登录后白屏/异常。

## 修复
1. 新增 `UserInfoCompatController`：`GET /api/user/info` 按 `/api/auth/me` 同结构返回，旧缓存页面无需清缓存也能完成登录；登录即可（不命中 permission 规则）。新前端不调用，仅兼容保留。
2. nginx `jshz-pmo.conf` 增加 `location = /index.html`：`Cache-Control: no-cache, no-store, must-revalidate` + `Pragma: no-cache` + `expires -1`；`/assets/` 的 30 天 immutable 长缓存保持不变。配置无 BOM（PS/Node 均以 UTF-8 无 BOM 写入），`nginx -t` 通过。

## 发布
- 版本 1.0.30；执行器 Hotfix 模式（无迁移）；备份见 stage release-state；蓝绿 COM 平滑重载 + 前端 root 切换至 `releases/1.0.30-compliance-visual-hotfix-20261105`。
- 制品哈希：jar SHA256=E3971F5A…383935（73 依赖、version.properties=1.0.30）；index SHA256=CFDB13FF…5B97F9。

## 验证
- 内网 LAN IP：login=200、`/api/user/info`=200（返回用户信息）、首页 200。
- 缓存头：`/` 返回 `no-cache, no-store, must-revalidate`；`/assets/index-*.js` 返回 `public, max-age=2592000, immutable`。
- 连续性：pooled 1284 / fresh 1282，**双双 0 失败**；health=1.0.30 UP；production-verification PASS。
- 回滚：previous-expert-system.jar（1.0.29）+ 前端 release-metadata 回滚 conf；nginx no-cache 段为独立配置，回滚代码不影响。

## 说明
no-cache 段位于发布脚本只替换 `root` 行的区块之外，后续优雅发布不会清除该配置。
