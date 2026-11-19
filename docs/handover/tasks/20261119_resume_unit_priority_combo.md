# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261119-resume-unit-priority
- 任务名称：简历解析单位识别优先级重构（李宏案例）
- 主责 Agent：ZCode
- 交接时间：2026-11-19
- 当前状态：已发布待业务验收（提交 `5b2b786`，1.0.74 已于 2026-11-19 生产上线，发布核验见 [发布记录](../../releases/20261119-resume-unit-1.0.74.md)；业务侧需用李宏原件复测）
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：`副本李宏简介-湖北省肿瘤医院 1.pptx` 的单位应识别为"湖北省肿瘤医院"，而不是经历行"日本上尾中央综合医院访问学者"中的医院。
- 本次范围：`ResumeParseService` 单位识别优先级改为：**组合行（单位+科室+职称）> 标签行 > 文件名 > 散行机构扫描 >（Ollama 兜底不变）**；组合行同时落定科室与职称。
- 不在本次范围：前端、数据库、接口契约（解析端点出入参不变）、其他解析策略。
- 验收条件：
  - [x] 真实李宏 PPTX：单位=湖北省肿瘤医院、科室=微创介入科、职称=副主任医师、姓名=李宏（实测通过）
  - [x] 既有回归探针（郭双双/李泉江/孙明亮/卫利民/许鹏程等）全部通过（208 测试 0 失败）
  - [ ] 负责人业务验收 + 生产发布（未做）

## 3. 项目基线
- 分支：main；提交号 `3752358`（fix: 简历解析单位优先级改为组合行>标签行>文件名>散行扫描 1.0.74）
- 工作区状态：干净（改动已提交：ResumeParseService.java、ResumeParseServiceTest.java、新增 ResumeParseServiceUnitTest.java、交接文档、版本号）
- 运行环境：本机即生产（<LOCAL_PATH> 1.0.73 运行中，本次改动**未编译进生产 jar**（仅 mvn compile/test 到 target/classes）
- 核实时间：2026-11-19

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据 |
| --- | --- | --- | --- |
| 组合行识别 `matchUnitDeptTitle`：医院单位+科室+医师职称一行命中，限定医院/卫生院+医师类职称防"加州大学医学中心助理教授"误判；相邻 2/3 行拼接兼容 PPT 拆行；学位词防御 | ResumeParseService.java | 完成 | 单测 comboGuards |
| 单位优先级重排：`resolveUnit` 改纯函数 `unitFromFileName`，parse 链路在 buildResult 内按优先级落定 | ResumeParseService.java | 完成 | 208 测试通过 |
| 散行扫描候选改为 extractFields 返回值（原直接写 fields） | ResumeParseService.java + ResumeParseServiceTest.java | 完成 | 强/弱候选两个用例改断言返回值 |
| 新增 4 项单元测试（组合行胜经历行、扫描候选返回值契约、组合行防御、文件名单位） | ResumeParseServiceUnitTest.java（新增） | 完成 | Tests run: 4, Failures: 0 |

## 5. 实际变更
- 修改文件：
  - `backend/src/main/java/com/jshz/expert/service/ResumeParseService.java`（extractFields 签名 void→String、buildResult/六个 parseXxx 增加 fileName 参数、matchUnitDeptTitle/unitFromFileName 新增、resolveUnit 删除）
  - `backend/src/test/java/com/jshz/expert/service/ResumeParseServiceTest.java`（2 用例改断言 extractFields 返回值，其余 34 项不动）
- 新增文件：`backend/src/test/java/com/jshz/expert/service/ResumeParseServiceUnitTest.java`
- 数据库/API/页面/配置：无变更。

## 6. 测试与验证
| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 真实李宏原件 | mvn test（临时用例，验后已删） | 通过：单位=湖北省肿瘤医院 科室=微创介入科 职称=副主任医师 |
| 全量单测 | mvn test -Drevision=… -DgitShort=…（JAVA_HOME=JDK17，MYSQL_PASSWORD/JWT_SECRET 注入） | **Tests run: 208, Failures: 0, Errors: 0, Skipped: 15**（skip 为环境探针 assumeTrue） |
| 编译 | mvn compile（JDK17） | 通过 |
| 生产验证 | 未执行（未发布） | 未执行 |

⚠️ 环境事实：本机默认 JAVA_HOME 已切到 **JDK 25**，后端（Java 17 + 旧 Lombok）在其下 **mvn compile 全量失败**（找不到 Lombok 生成 getter，clean main 亦失败，与本次改动无关）。构建必须 `JAVA_HOME=<LOCAL_PATH> Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`。

## 7. 未完成事项与阻塞
| 事项 | 原因 | 下一步 |
| --- | --- | --- |
| 生产发布 | 按规范待批准 | bump version.txt → 按发布三件套发布 → /health 核验 → 用李宏原件在生产页面复测 |
| 业务验收 | 未发布 | 发布后负责人复核 |

## 8. 风险与回滚
- 行为变化面：文件名单位候选现在**优先于**散行扫描（原来相反）；文件名含单位但内容扫描命中不同医院时结果会变——已核对既有 12 个回归探针无回退。
- 回滚：`git checkout -- backend/src/main/java/com/jshz/expert/service/ResumeParseService.java backend/src/test/`（本次未提交，直接还原即可）。

## 9. 文档同步
- [x] 本交接记录
- [x] 交接摘要（handover/README.md 顶部追加）
- [ ] 发布记录（待发布时写）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应有上述 3 个文件改动）+ `curl -s http://127.0.0.1:8080/health`（应为 1.0.73，不含本次改动）。
2. 验收标准：李宏原件在解析端点返回单位=湖北省肿瘤医院。
3. 未经确认禁止：发布生产、`mvn package` 在 backend/ 内执行、用 JDK 25 构建。
4. 需要负责人决策：文件名优先于内容散行扫描的排序是否符合预期。
