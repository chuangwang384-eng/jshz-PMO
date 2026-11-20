# 1.0.85 发布记录：同名专家对比卡来源标识

- 日期：2026-11-20
- 提交：`63651a8`（main）
- 类型：纯前端发布（graceful，零迁移，后端保持 1.0.84）

## 1. 变更内容

背景：批量简历解析遇同名同单位专家时，对比弹窗左右两张卡只有小字标签（「库内专家卡 #265」/文件名），难以分清哪侧是专家库已有档案、哪侧是本次解析结果。

| 内容 | 文件 |
| --- | --- |
| BatchResumeCard 新增 `badge`/`badgeType` 属性：类别徽标 + 顶部色条（蓝=专家库已有档案，橙=本次简历解析） | `frontend/src/components/BatchResumeCard.vue` |
| 对比弹窗左卡标「专家库已有档案」(蓝)、右卡标「本次简历解析」(橙)，说明行补充左右侧含义 | `frontend/src/components/BatchResumeDialog.vue` |

## 2. 发布与验证证据（实测）

| 步骤 | 结果 |
| --- | --- |
| `npm run build` | 通过；is-not-exported 警告 0 |
| `build-all.ps1` | version=1.0.85 gitShort=63651a8 |
| `deploy-frontend-graceful.ps1 -Apply`（日志 `logs/deploy-frontend.1.0.85.agent.log`） | EXITCODE=0；Nginx root → `frontend/releases/1.0.85-dup-source-badge`（conf 第 26 行实测）；graceful reload master PID 未变 |
| 线上主 bundle `index-DZlocCKE.js` | 含 `v1.0.85` |
| 连续性探针 `GET /` ×3 | 200/200/200；`/api/health` = 1.0.84（后端未动，符合预期） |

## 3. 待验收（业务侧）

- [ ] 批量上传简历触发同名弹窗：左卡蓝色徽标「专家库已有档案」+ 蓝顶条，右卡橙色徽标「本次简历解析」+ 橙顶条
- [ ] 多候选下拉选择对比档案时徽标保持

## 4. 回滚

- `frontend/release-metadata/1.0.85-dup-source-badge.nginx-before.txt` 存旧 root（`1.0.84-sched-candidate-fix`），改回 conf 后 `nginx -s reload`。
