# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261119-unit-membership-dept-llm
- 任务名称：任职行不作单位/Ollama 不覆盖本地职称/科室 LLM 语义判定（王俊生案例）
- 主责 Agent：ZCode
- 交接时间：2026-11-19
- 当前状态：已完成待发布（提交见 git log，版本 1.0.77，候选包+预检 ALL PASS）

## 2. 口径（Owner 2026-11-19）
1. 王俊生案例（`安阳市肿瘤医院--王俊生 .pptx`）：单位应为安阳市肿瘤医院（文件名+内容 LOGO 图片均有），曾误解析为"安阳市医学会"。
2. 部分简历有教授+主治医师，职称被解析成教授——原因：Ollama 语义结果无条件覆盖本地已解析职称。
3. 科室优先找文件内"科"字，用 LLM 判定前后组合语义是否科室。

## 3. 实现与变更
- `ResumeParseService.java`：
  - 新增 `MEMBERSHIP_WORDS`（委员/理事/常委/主委/会员/秘书/组组长）：含这些词的任职行不再作为单位候选（散行扫描）；Ollama 的单位/科室候选若来自任职行也拒绝（`membershipLineValue`）——医院名常在 LOGO 图片里，文本无佐证属正常，由文件名兜底。
  - 单位文件名**绝对第一优先**：内容单位与文件名不一致时取文件名（旧测试断言已同步）。
  - Ollama 合并：职称/科室增加 `fields.containsKey` 守卫，本地已解析字段不被覆盖。
  - 新增 `collectDeptCandidates`：收集文中"科"字前连续汉字段（机构名后截取、≤10 字、任职/教育行排除）；本地未解析出科室时交 `OllamaService.pickResumeDepartment` 语义判定（委员会/学会/公司/大学/研究领域不算科室），命中候选才填入。
- `OllamaService.java`：新增 `pickResumeDepartment(List<String>)`（completeJson 模式，失败/未命中返回 null 回退）。
- 测试：`ResumeParseServiceUnitTest` 新增 4 用例（任职行不作单位/候选收集/科室 LLM 辅助/本地职称不被覆盖）；`ResumeParseServiceTest` 单位文件名优先断言更新。

## 4. 验证
| 项 | 结果 |
| --- | --- |
| 全量 mvn test（JDK17+凭据+live Ollama env） | **Tests run: 216, Failures: 0** |
| 王俊生原件 | 姓名=王俊生、单位=安阳市肿瘤医院、职称=主任医师（修复前单位=安阳市医学会）✅ |
| 李晓龙/李宏原件回归 | 全对 ✅ |
| 预检 | deploy-unit-membership-dept-1077.ps1 ALL PASS |

## 5. 发布与回滚
- 发布：管理员 PowerShell `temp\deploy-unit-membership-dept-1077.ps1 -Apply`（基线 1.0.76/319aa09，生产已于 13:31 上线 1.0.76）
- 回滚：`deploy-backend.ps1 -JarPath builds\1.0.76-build-20261119_123643\expert-system.jar -Version 1.0.76 -GracefulSwitch`
- 注意：1.0.77 包连带包含尚未单独发布的 1.0.75（职称顺序优先）内容——1.0.76 起职称新逻辑已在生产。

## 6. 已知限制
- 王俊生案例中"河南科技大学第四附属医院"（LOGO 副名）未 OCR，如需图片文字佐证需另立项（pptx 图片 OCR 成本高，未做）。
