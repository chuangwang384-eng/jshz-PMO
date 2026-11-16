# P1-2 / P1-5 / P2-7 / P2-9 审查问题修复交接

> 任务编号：REVIEW-FIX-20261106
> 完成时间：2026-11-06
> 基线分支：`codex/personal-dashboard-v11`，基线提交 `b221ad4`
> 施工文档：[2026-11-06-P1P2审查问题修复-施工文档.md](../design/2026-11-06-P1P2审查问题修复-施工文档.md)
> 审查报告：[20261106-代码审查报告.md](../reviews/20261106-代码审查报告.md)
> 当前状态：**代码已实现、测试库迁移演练通过、生产备份与恢复演练通过、后端测试通过；生产迁移未执行、接口未实测、未发布。**

---

## 1. 结论摘要

| 问题 | 结论 | 关键证据 |
| --- | --- | --- |
| P1-2 停用/删除后 Token 仍可用 | 已修复 | JWT `tv` + `sys_user.token_version`；过滤器校验存在/启用/版本；停用/删除/改密/改角色自增版本并清缓存 |
| P1-5 下架知识文档仍可下载 | 已修复 | 文件流入口 `requireDownloadable`：不存在 404、下架且非维护人 403 |
| P2-7 大文件同步解析阻塞 | 已修复 | 上传/更新只流式落盘并标记 `PENDING`；正文抽取/合规解析异步；`id+version` 条件更新 |
| P2-9 依赖版本偏旧 | 已核实并闭环 | 不升级；移除零引用 `spring-boot-starter-validation`；其余“未使用”告警为 starter/Lombok 误报 |

**上线最重要的一条**：升级后所有存量 Token（无 `tv` claim）都会被拒绝，**全体已登录用户需重新登录一次**。这是有意设计，不是故障。

---

## 2. P1-2：账号停用/删除后 Token 失效

### 2.1 实现方式

1. **数据库**：`database/migration/20261106_add_user_token_version.sql`
   - `sys_user.token_version INT NOT NULL DEFAULT 0`，幂等可重复执行。
2. **签发**：`JwtTokenProvider.createToken(..., long tokenVersion)` 写入 `tv` claim；`extractTokenVersion` 兼容数字/字符串，缺失返回 `null`。
3. **校验**：`JwtAuthenticationFilter`
   - 解析 `userId/username/tv`；
   - `tv == null` → 不建立认证（存量 Token 拒绝）；
   - 调用 `UserAuthStateService.get(userId)` 校验用户存在、`status=1`、版本一致；
   - 查库异常 `fail-closed`，不建立认证；
   - 校验通过后照旧从 DB/缓存加载权限码。
4. **缓存**：`UserAuthStateService`
   - 缓存 `exists/status/tokenVersion`，TTL 默认 60 秒（`expert.auth.state-cache-seconds`）；
   - `evict` 立即清本地缓存；若处于事务中，`afterCommit` 再清一次，避免提交前被并发请求回填旧状态；
   - `bumpAndEvict` 原子执行 `token_version = token_version + 1` 后失效缓存。
5. **触发点**
   - `UserService.updateUser`：账号信息（含停用/启用/管理员标记/姓名）变更后自增；
   - `UserService.resetPassword`：重置密码后自增；
   - `UserService.deleteUser`：删除后清缓存（行已不存在）；
   - `UserService.assignRoles`：角色变更后自增；
   - `AuthService.changePassword`：改密后自增并重新签发当前会话；
   - `ContactSyncService`：企微离职/全量消失停用、重新启用时自增；新建用户初始化 `tokenVersion=0`。
6. **AuthUser**：增加 `enabled` 字段，`isEnabled()/isAccountNonLocked()` 返回真实值；保留 6 参构造默认启用，避免破坏既有测试与 `ComplianceV4AccessService`。

### 2.2 改密后的会话行为

- 改密成功 → `token_version+1` → 旧 Token 全部失效；
- `AuthService.changePassword` 返回新的 `LoginResponse`；
- `AuthController` 将新 Token 写回 `expert_token` Cookie，当前会话无需重新登录；
- 其他设备/浏览器的旧 Cookie 下一次请求即 401（单实例）。

### 2.3 兼容性与回滚

- 旧 Token 无 `tv`：拒绝认证，需重新登录一次；未采用“缺失按 0 放行”，否则停用账号旧 Token 仍可用至过期。
- 多实例：本地缓存各自 60 秒 TTL；停用/改密后最迟 60 秒在所有实例生效。当前部署为单实例。
- 回滚：代码回退即可；`token_version` 列可保留，旧代码不读取。回滚后新 Token 的多余 `tv` claim 会被旧代码忽略，不会造成登录中断。

---

## 3. P1-5：知识文档下载校验上下架状态

### 3.1 实现方式

- `KnowledgeDocumentService.canAccessFile(doc, user)`（纯函数）：
  - 文档为空 → false；
  - `knowledge:maintain` → true；
  - 否则仅 `status=1` 可访问。
- `KnowledgeDocumentService.requireDownloadable(storedName, user)`：
  - 按存储文件名反查不到 → `BusinessException(404, "文档不存在或已删除")`（业务码 404）；
  - 下架且非维护人 → `BusinessException(403, "文档已下架，暂不可访问")`（业务码 403）；
  - HTTP 状态沿用项目统一的 200 + `body.code` 约定，前端 `request.js` 按 code 提示。
  - 通过 → 返回文档对象。
- `KnowledgeController.file(...)` 第一行调用 `requireDownloadable`，后续审计/预览复用同一个 `doc`，不再重复查库。

### 3.2 影响范围

- 上架文档：行为不变，登录即可下载/预览。
- 下架文档：普通用户业务码 403；`knowledge:maintain` 仍可下载（维护需要）。
- 已永久删除：业务码 404，不再依赖“列表隐藏”。
- 兼容旧调用：`preview` 未传时仍走原 inline 预览逻辑。

### 3.3 回滚

- 回退 `KnowledgeDocumentService`/`KnowledgeController` 即可；无数据库变更。
- 回退后下架文档旧链接会重新变为可下载，属于回退已知风险。

---

## 4. P2-7：知识文档上传/更新异步解析

### 4.1 实现方式

1. **数据库**：`database/migration/20261106_add_knowledge_parse_status.sql`
   - `parse_status VARCHAR(16) NULL`（PENDING/RUNNING/SUCCESS/FAILED，NULL=历史数据）；
   - `parse_message VARCHAR(500) NULL`；
   - `parse_time DATETIME NULL`；幂等。
2. **线程池**：`KnowledgeAsyncConfig`
   - Bean 名 `knowledgeParseExecutor`；
   - core 2 / max 4 / queue 100 / `AbortPolicy`；
   - 参数 `expert.knowledge.parse.*`。
3. **异步任务**：`KnowledgeParseTaskService.parseAsync(...)`
   - 先检查 `id+version` 是否仍为当前版本；
   - 状态置 `RUNNING`；
   - 从磁盘路径流式抽取正文（PDF/DOC/DOCX）；xlsx/pptx 等返回 null；
   - `updateContent` 带 `id+version` 条件，旧任务不覆盖新版本；
   - `enterprise-compliance` 分类再调 `ComplianceService.parseAndCreateDrafts(Path...)` 生成草稿；
   - 成功置 `SUCCESS`，异常置 `FAILED` 并记录原因；
   - `markFailed` 供线程池拒绝时同步标记。
4. **上传/更新**：
   - `KnowledgeDocumentService.upload/updateVersion` 不再调用 `file.getBytes()`、不再同步抽取/解析；
   - 初始化 `parseStatus=PENDING`、`parseMessage="等待解析"`；
   - 更新版本时显式清空旧 `content`（MyBatis-Plus 默认忽略 null，避免新版本解析前仍命中旧正文）；
   - 控制器落库成功后 `triggerParse` 提交异步任务；`TaskRejectedException` 同步标记 FAILED，上传仍成功。
5. **流式重载**：
   - `KnowledgeDocTextExtractor.extract(Path, fileName)`、`extract(InputStream, fileName)`；
   - `ComplianceParseService.parse(..., Path, ...)`；
   - `ComplianceService.parseAndCreateDrafts(..., Path, ...)`；
   - 原 `byte[]` 入口保留，兼容其他调用方。

### 4.2 并发与状态语义

- 用户在上一次解析期间上传新版本：旧任务 `updateContent` 影响 0 行 → 直接退出，不覆盖新版本；新版本有自己的任务。
- 旧任务不会为旧版本生成合规草稿（合规解析前再次检查 `id+version`）。
- 线程池拒绝：上传成功，状态 `FAILED`，消息“解析任务排队已满…”。
- 解析失败：不回滚已上传文件，`FAILED` + 原因；PDF/DOC/DOCX 抽取为空按失败处理（可能损坏、加密或为扫描件），其余不支持正文的格式标记成功但不参与正文检索。当前未提供重试接口。
- 历史数据 `parse_status=NULL`：接口原样返回，不做批量回填。
- 预览转换仍同步（需即时返回，已有缓存/锁/超时），本次未改其契约。

### 4.3 回滚

- 代码回退即可；三列可保留（旧代码忽略）。
- 回退后恢复同步解析；已处于 PENDING/RUNNING 的记录不会再被旧代码更新，可忽略或人工置空。
- 回退前建议先停止上传，避免旧代码与新列状态混用。

---

## 5. P2-9：依赖核实与清理

### 5.1 版本清单（`pom.xml` 直接声明）

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| `spring-boot-starter-parent` | 3.2.5 | Spring Boot 基线（Spring Framework 6.1.6） |
| `spring-boot-starter-web` | 3.2.5 | MVC / Tomcat / Jackson |
| `spring-boot-starter-security` | 3.2.5 | 认证授权过滤器链 |
| `spring-boot-starter-aop` | 3.2.5 | `@OperationLog` 切面、事务/异步代理 |
| `spring-boot-starter-validation` | 3.2.5 | **已移除**：主代码零 `jakarta.validation`/`@Valid` 引用 |
| `jsoup` | 1.17.2 | 公告富文本 XSS 清洗 |
| `jjwt-api/impl/jackson` | 0.11.5 | JWT 签发/解析 |
| `mybatis-plus-spring-boot3-starter` | 3.5.7 | ORM / Mapper |
| `mysql-connector-j` | 由 Boot 管理 | MySQL 驱动（runtime） |
| `poi-ooxml` / `poi-scratchpad` | 5.4.1 | Excel 导入导出、DOC/DOCX 抽取 |
| `pdfbox` | 2.0.31 | PDF 文本抽取/预览 |
| `lombok` | 1.18.32 | 编译期注解处理（optional） |
| `spring-boot-starter-test` | 3.2.5 | JUnit/Mockito/AssertJ/Spring Test |

### 5.2 执行过的核实命令

```powershell
cd <LOCAL_PATH>
mvn -o dependency:analyze -DignoreNonCompile=true
mvn -o dependency:tree -Dscope=compile
```

结果文件（本地 `target/`，未入库）：

- `target/dependency-analyze-20261106.txt`
- `target/dependency-tree-20261106.txt`

### 5.3 `dependency:analyze` 结果与判断

`Unused declared dependencies found` 共 6 条：

| 告警项 | 判断 |
| --- | --- |
| `spring-boot-starter-security` | 误报：Spring Security 自动配置/过滤器链，静态分析看不到自动装配 |
| `spring-boot-starter-web` | 误报：MVC/Tomcat/Jackson 自动配置 |
| `spring-boot-starter-aop` | 误报：`@OperationLog` 切面、`@Async`/事务代理依赖 AOP 基础设施 |
| `mybatis-plus-spring-boot3-starter` | 误报：Mapper 自动配置 |
| `lombok` | 误报：编译期注解处理，运行期无需引用 |
| `spring-boot-starter-validation` | **真实未使用**：主代码检索 `jakarta.validation` / `@Valid` / `@NotBlank` / `@NotNull` / `@Size` / `@Min` / `@Max` / `@Email` 均为 0 处 |

`Used undeclared dependencies found` 39 条（如 `spring-web`、`mockito-core`、`spring-test`、`jackson-*`、`mybatis-*`、`spring-security-*`），均为传递依赖，不影响编译/运行；本次不做“补声明”改造，避免扩大依赖面。

### 5.4 清理动作

- 仅从 `backend/pom.xml` 删除：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

- 移除后 `mvn -o test` 通过（见第 6 节），证明无编译/运行依赖。
- **未删除**任何 starter、Lombok、数据库/POI/PDFBox/JJWT 依赖。

### 5.5 未升级的原因与限制

- Spring Boot 3.2 → 3.3+/4.x、jjwt 0.11 → 0.12、PDFBox 2 → 3 均涉及 API/包名/行为变更，回归面大；本次审查修复不做版本升级。
- **未联网核对 CVE**：`dependency:analyze` 只分析“是否使用”，不判断漏洞；不得把“未发现未使用”写成“无漏洞”。
- 后续建议：单独开升级专项，先跑 `mvn dependency-check`/SCA，再按 Spring Boot → jjwt → PDFBox 顺序分批升级，每批附回归清单。

---

## 6. 改动文件清单

### 6.1 新增

| 文件 | 说明 |
| --- | --- |
| `database/migration/20261106_add_user_token_version.sql` | P1-2 加列，幂等 |
| `database/migration/20261106_add_knowledge_parse_status.sql` | P2-7 加三列，幂等 |
| `backend/.../common/KnowledgeParseStatus.java` | 解析状态常量 |
| `backend/.../config/KnowledgeAsyncConfig.java` | 解析线程池 |
| `backend/.../service/KnowledgeParseTaskService.java` | 异步解析任务 |
| `backend/.../service/UserAuthStateService.java` | 登录态短 TTL 缓存 |
| `backend/src/test/.../security/JwtTokenProviderTest.java` | `tv` 签发/读取 |
| `backend/src/test/.../security/JwtAuthenticationFilterTest.java` | 过滤器放行/停用/版本不匹配/无 tv |
| `backend/src/test/.../service/UserAuthStateServiceTest.java` | 缓存/TTL/失效 |
| `backend/src/test/.../service/KnowledgeDocAccessPolicyTest.java` | 下载授权 |
| `backend/src/test/.../service/KnowledgeDocStreamingParseTest.java` | Path 流式抽取/解析 |
| `docs/design/2026-11-06-P1P2审查问题修复-施工文档.md` | 施工文档 + 实施记录 |
| `docs/handover/20261106_p1p2_review_fixes.md` | 本交接 |

### 6.2 修改

| 文件 | 说明 |
| --- | --- |
| `backend/pom.xml` | 移除未使用 validation starter |
| `backend/.../entity/SysUser.java` | `tokenVersion` |
| `backend/.../mapper/SysUserMapper.java` | `bumpTokenVersion` |
| `backend/.../security/JwtTokenProvider.java` | `tv` claim |
| `backend/.../security/JwtAuthenticationFilter.java` | 存在/启用/版本校验 |
| `backend/.../security/AuthUser.java` | `enabled` 真实值 |
| `backend/.../service/AuthService.java` | 签发带版本；改密返回新登录响应 |
| `backend/.../controller/AuthController.java` | 改密写回新 Cookie；抽取 `authCookie` |
| `backend/.../service/UserService.java` | 停用/删除/改密/改角色失效 |
| `backend/.../service/ContactSyncService.java` | 企微停用/启用失效；新用户版本 0 |
| `backend/.../entity/KnowledgeDocument.java` | `parseStatus/parseMessage/parseTime` |
| `backend/.../service/KnowledgeDocumentService.java` | 下载授权；异步化；显式清空旧正文 |
| `backend/.../controller/KnowledgeController.java` | 下载授权入口；异步触发；移除同步解析 |
| `backend/.../service/KnowledgeDocTextExtractor.java` | Path/InputStream 流式重载 |
| `backend/.../service/ComplianceParseService.java` | Path 解析重载 |
| `backend/.../service/ComplianceService.java` | Path 版 `parseAndCreateDrafts` |
| `backend/src/main/resources/application.yml` | 缓存 TTL + 解析线程池参数 |
| `backend/src/test/.../service/AuthServiceTest.java` | 适配新增依赖 |
| `docs/architecture.md` | 数据模型/安全/异步解析说明 |
| `docs/deployment.md` | 迁移顺序、影响、配置、冒烟、回滚 |
| `docs/reviews/20261106-代码审查报告.md` | 四项状态与验证结果 |
| `docs/design/2026-11-06-P1P2审查问题修复-施工文档.md` | 状态与实施记录 |

---

## 7. 验证记录（实际执行）

环境：Windows，JDK 17.0.20，Maven 3.10.0，离线 `-o`。

| 命令 | 结果 |
| --- | --- |
| `mvn -o -q -DskipTests compile` | 通过（无输出） |
| `mvn -o test`（注入测试用 `JWT_SECRET`） | **Tests run: 64, Failures: 0, Errors: 0, Skipped: 9；BUILD SUCCESS** |
| `mvn -o dependency:analyze -DignoreNonCompile=true` | BUILD SUCCESS；6 条 unused declared（5 条误报 + validation 真未使用） |
| `mvn -o dependency:tree -Dscope=compile` | BUILD SUCCESS；版本清单见 5.1 |

本次相关测试类（非全量清单）：

- `JwtTokenProviderTest`：3
- `JwtAuthenticationFilterTest`：4
- `UserAuthStateServiceTest`：4
- `KnowledgeDocAccessPolicyTest`：4
- `KnowledgeDocStreamingParseTest`：3
- 既有回归：`AuthServiceTest` 10（新增改密版本自增用例）、`ComplianceReminderRegressionTest` 7、`ComplianceV4EditingRegressionTest` 7、`ProjectAccessRegressionTest` 4、`DashboardRulesTest` 4、`ProjectStorageRegressionTest` 2、`ComplianceAssignmentRegressionTest` 1、`SupplierDeletionRegressionTest` 1、`ExpertSystemApplicationTests` 1、`DashboardReadIntegrationTest` 6（全部 skipped）

**验证备注**：一次全量运行曾出现 `DashboardRulesTest.partialFailureAndDeadlineDoNotBecomeEmpty` 时序抖动（100ms 截止窗口内 `good` 任务被判超时），单独复跑该测试及随后全量复跑均通过；该测试与本次改动无关，属既有时间敏感用例。

**关于 `JWT_SECRET`**：未注入时 `ExpertSystemApplicationTests.contextLoads` 会因 `WeakKeyException` 失败，这是 `application.yml` 的既有 fail-fast 设计，不是本次改动引入；注入 ≥32 字节测试密钥后通过。CI/本地跑测试时需设置该环境变量。

### 7.1 测试库迁移演练（2026-11-06）

**环境护栏**：仅使用本机隔离 MySQL `127.0.0.1:13319`，datadir 为 `<LOCAL_PATH>`；执行前校验 `@@port=13319` 且 `@@datadir` 含 `.build-dashboard-acceptance`。未连接、未修改生产 `3306/expert_system`。

**演练库**：`expert_system_migration_test`（专用一次性库），载入 `database/testing/dashboard_baseline.sql` 的 35 张测试表元数据，并插入 1 条 `sys_user`（id=900001）与 1 条 `knowledge_document`（id=900001，正文“旧正文”）作为存量样本。

| 步骤 | 实际结果 |
| --- | --- |
| 首轮执行两个脚本 | 均成功；`sys_user.token_version int NOT NULL DEFAULT 0`；`knowledge_document.parse_status varchar(16) NULL`、`parse_message varchar(500) NULL`、`parse_time datetime NULL` |
| 存量数据 | `sys_user`/`knowledge_document` 行数均保持 1；样本 `token_version=0`、`parse_*` 均为 NULL；标题、正文、status 未变 |
| 第二轮重复执行 | 两个脚本均成功；新增列仍为 4 个，无重复列错误；行数与样本值不变 |
| 写入可用性 | `token_version=token_version+1` 可正常自增；`parse_status/parse_message/parse_time` 可写入并在查询中回读；随后已恢复样本原值 |
| 测试实例版本 | MySQL `26.7.0`（隔离实例），非生产版本；迁移仅使用 `information_schema` 判断 + 标准 `ALTER TABLE ... ADD COLUMN` |

**结论**：两个脚本在测试库可按顺序执行、可重复执行、不丢存量数据。生产库已实测为 MySQL 26.7.0（`127.0.0.1:3306/expert_system`），与测试实例版本一致；原“生产 MySQL 8.x”假设不成立。生产执行仍须另行批准并备份。演练完成后已关闭 13319 隔离实例并确认端口无监听；专用库仍保留在 `.build-dashboard-acceptance/mysql-data`，未删除其他数据。

**未执行（不得视为通过）**：

- 生产库迁移未执行；测试库为 MySQL 26.7.0，非生产同版本验证；
- 未做真实登录/停用/删除/改密/改角色联调；
- 未做知识文档下载 403/404、上传异步状态、合规草稿生成的接口实测；
- 未做前端构建；
- 未联网做 CVE/SCA 扫描；
- 未发布、未做生产健康检查。

### 7.2 生产备份与恢复演练（2026-11-06）

**版本与实例**：生产 `127.0.0.1:3306/expert_system` 实测 MySQL `26.7.0`，datadir 为 `<LOCAL_PATH> Files\MySQL\MySQL Server 26.7\data\`；`SHOW COLUMNS` 确认 `sys_user` 11 列、`knowledge_document` 14 列，`token_version` 与 `parse_*` 均不存在，两表各 7 行。

**备份**：`backup\backup_20261106_101945`，含 SQL（2,246,574 字节，SHA256 `bbe3dd24d32159d20a50835921b8792cec0bc2ef6d99d8e980dda82940215abf`）、照片库/知识文档库 ZIP 与 `manifest.txt`；SQL/ZIP 哈希与内容校验通过。

**恢复演练**：`scripts\backup.ps1 -Mode Drill -From backup\backup_20261106_101945`，重建 `expert_system_restore_test` 并导入。表集合 74/74；独立逐表 `COUNT(*)` 复核生产/恢复均为 **2714 行**、逐表差异 0；全库 74 表 `CHECKSUM TABLE` 全部一致；`sys_user`、`knowledge_document` 列结构与生产一致。

**Drill 脚本修复与复跑**：首次自动记录曾显示 3797 行；已修复 `scripts\backup.ps1` 的成功退出码，并将逐表 148 次 `mysql` 调用改为每库一条 `UNION ALL` 汇总。2026-11-06 10:38 复跑退出码 0，输出 74 表 / 2714 行，与独立逐表 `COUNT(*)` 及全库 CHECKSUM 一致；`docs/guides/2026-10-26-备份与恢复-演练文档.md` 已标注旧 3797 记录为汇总异常。

**未执行**：生产迁移脚本、接口联调、前端构建、CVE 扫描、发布。

---

## 8. 发布步骤（建议）

1. **备份**数据库与知识文档库。
2. 停写或选低峰；按顺序执行：
   - `20261106_add_user_token_version.sql`
   - `20261106_add_knowledge_parse_status.sql`
3. 核对列：
   ```sql
   SHOW COLUMNS FROM sys_user LIKE 'token_version';
   SHOW COLUMNS FROM knowledge_document LIKE 'parse_%';
   ```
4. 构建并发布后端（按既有蓝绿流程），保持 `JWT_SECRET` 不变。
5. 冒烟：
   - 旧 Cookie 访问 `/api/auth/me` → 401；重新登录 → 200；
   - 管理员停用测试账号 → 该账号下一次请求 401；
   - 改密 → 当前会话保持登录，另一浏览器旧 Cookie 401；
   - 上传 PDF/DOCX → `parseStatus` PENDING → SUCCESS，`content` 非空；
   - 上传 `enterprise-compliance` Excel → 生成合规草稿；
   - 下架文档：普通账号文件链接 403，维护人 200；永久删除后 404。
6. 通知全员：本次升级需重新登录一次。

**回滚**：回退后端包；保留新增列；旧代码忽略 `tv`/`parse_*`。回滚后下架文档旧链接会重新可下载（P1-5 回退风险）。

---

## 9. 已知限制与后续建议

1. **P2-4 仅部分解决**：`tokenVersion` 已能实现改密/停用/角色变更吊销，但“刷新令牌”“退出所有设备”未实现；当前仍为 8 小时固定有效期。
2. **多实例缓存**：当前单实例；扩容时需把 `UserAuthStateService` 换成 Redis 或接受 60 秒延迟。
3. **解析无重试**：队列拒绝/解析失败后只能重新上传或人工处理；后续可加“重新解析”按钮与任务表。
4. **解析状态无前端展示**：本次只落库并随文档接口返回，前端列表未新增状态列；如需可见，另开前端任务。
5. **`ComplianceV4Controller`/`ComplianceWorkspaceSourceService` 仍有 `Files.readAllBytes`**：属于合规可视化抽取链路，不在本次 P2-7 范围；50MB 上限下仍有堆压力，建议后续一并流式化。
6. **CVE 未核对**：版本偏旧问题只完成了“使用情况核实 + 一处清理”，不等于安全审计通过。
7. **测试覆盖边界**：过滤器测试验证“不建立认证”，未启动完整 `SecurityConfig` 验证 HTTP 401 响应；上线前需接口实测。
8. **生产迁移未实跑**：已在隔离测试库验证顺序执行、幂等与数据保持；生产实测为 MySQL 26.7.0，恢复演练已通过（74 表 / 2714 行 / 全表 CHECKSUM 一致），但迁移脚本仍未在生产执行，需另行批准并选择低峰窗口。
