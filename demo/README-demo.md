---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_b249822dcb9f11f1acd7525400cd780f
    ReservedCode1: KfMKqJzSicG/Dc28dSraWuSqUDXmcbrjXDoszmbwaEkSb1nmq3vTZZmZ/h5IbaBCGuoXJDv8vLp5GX+5XMm3y+moNJruod02xHqPgJHfU8t6u7UtgvRj7jKTywEqYCcw0p8L8ebPOVXatGVsouJBrEzAwdIj6CfbFnlEMBPU0tPi+IDEYqdhcHnOlJA=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_b249822dcb9f11f1acd7525400cd780f
    ReservedCode2: KfMKqJzSicG/Dc28dSraWuSqUDXmcbrjXDoszmbwaEkSb1nmq3vTZZmZ/h5IbaBCGuoXJDv8vLp5GX+5XMm3y+moNJruod02xHqPgJHfU8t6u7UtgvRj7jKTywEqYCcw0p8L8ebPOVXatGVsouJBrEzAwdIj6CfbFnlEMBPU0tPi+IDEYqdhcHnOlJA=
---





# 专家库系统 Demo（轻量版）

基于 Python 3.11 **标准库**实现的可运行 Demo，无任何第三方依赖。
用于演示专家库系统的核心流程，可与前后端分离正式版框架（`backend/` + `frontend/`）配合使用。

## 快速启动

```bash
cd D:\jshz-PMO\demo
python server.py
```

或双击 `start_demo.bat`。

启动后服务监听 `0.0.0.0:8000`，公司内网其他机器通过本机内网 IP 访问：

| 页面 | 地址 |
|---|---|
| 员工查询页 | `http://<本机内网IP>:8000/` |
| 管理员维护页 | `http://<本机内网IP>:8000/admin` |

数据文件：`data/experts.db`（SQLite 主存储，`data/experts.json` 保留为历史备份）；上传照片目录：`uploads/`（自动创建）。

## 功能清单

### 1. 专家数据结构（升级后）

| 字段 | 说明 |
|---|---|
| id | 自动递增唯一编号 |
| 姓名 | 必填 |
| 科室 | 必填（旧数据中“部门”字段启动时自动迁移为“科室”） |
| 职称 / 研究方向 / 擅长领域 / 简介 | 文本信息 |
| 联系方式 | 必填，校验 11 位手机号或邮箱 |
| 身份证号 | 必填，校验 18 位（前 17 位数字，末位数字或 X） |
| 银行卡号 | 必填，校验 13-19 位纯数字 |
| 照片 | 上传后保存文件名；无照片显示占位图 |
| 创建时间 / 修改时间 | 自动记录 |
| 修改人id | 新增时表单输入（默认 admin）；编辑时**保留原修改人** |

- 旧数据兼容：缺失字段自动补默认值，照片为空，启动时自动迁移并保存。
- 当前库共 148 条真实专家数据（含 138 条照片），覆盖人工智能实验室、网络安全部、大数据中心、项目管理办公室、软件研发部、产品设计部、客户服务部、财务部等科室。

### 2. 照片上传

- 管理员维护页表单支持新增/编辑专家（照片上传为后续增强项，当前通过数据导入或后端接口维护照片）；后端接口支持 multipart 照片上传（jpg/jpeg/png/gif/webp/bmp，≤5MB）。
- 后端用标准库 `cgi.FieldStorage` 解析 multipart 表单，保存至 `uploads/` 目录。
- 存储文件名规则：`avatar_<专家id>_<时间戳>.<扩展名>`（如 `avatar_1_1699000000.jpg`）。
- JSON 中仅存文件名（SQLite 中同）；员工页与管理页均展示缩略图，无照片显示占位图。
- 编辑换照片会删除旧照片文件；删除专家会一并删除其照片文件。

### 3. Excel 批量导入

管理员页提供：

- **下载 Excel 模板**：`GET /api/experts/template`（返回 `import_template.xlsx`）
- **导入 Excel**：`POST /api/experts/import`（multipart 上传 `file` 字段，`.xlsx` 格式，后续以 Excel 为准）

模板字段（导入表头须包含，首行为表头）：

```
姓名,单位,科室,职称,研究方向,擅长领域,联系方式,简介,身份证号,银行卡号
```

导入校验规则：

| 校验项 | 规则 |
|---|---|
| 必填字段 | 姓名、身份证号、银行卡号、联系方式 |
| 身份证号 | 18 位（前 17 位数字，末位数字或 X） |
| 银行卡号 | 13-19 位纯数字 |
| 联系方式 | 11 位手机号或邮箱 |
| 重复姓名 | 与库中或同文件已有姓名重复时跳过并提示 |

导入完成后反馈“成功 N 条，失败 M 条”，并列出失败行号、姓名与原因。
Excel 导入的数据照片字段默认为空，修改人id 默认 `admin`。

### 4. 数据下载（导出 Excel / ZIP / MD 说明文档）

- 管理员页“导出 Excel”按钮：`GET /api/experts/export`
- 员工页“下载数据”按钮：`GET /api/export` → 脱敏 ZIP
- 管理员页“导出 ZIP（含照片）”按钮：`GET /api/export/full` → 完整 ZIP
- 员工页“下载 MD 说明文档”按钮：`GET /api/export/md?keyword=` → 按当前筛选条件生成 MD 说明文档
- 管理员页“下载 MD 说明文档”按钮：`GET /api/export/md?keyword=&full=1` → 完整版 MD 说明文档

**Excel 导出**（`/api/experts/export`，管理员专用）导出**当前全部专家**，包含完整字段：

```
id,姓名,科室,职称,研究方向,擅长领域,联系方式,简介,身份证号,银行卡号,照片,创建时间,修改时间,修改人id
```

- 生成 `.xlsx` 文件，Excel/WPS 直接打开不乱码。

**ZIP 打包下载**（标准库 `zipfile` 生成）：

| 接口 | 适用场景 | Excel 内容 | 照片 |
|---|---|---|---|
| `GET /api/export` | 员工查询页“下载数据” | **脱敏版**：身份证号、银行卡号仅保留前3后4（`110***1234`），其余字段完整，含“照片”列（文件名） | 照片原图打包进 `photos/` 子目录 |
| `GET /api/export/full` | 管理员“导出 ZIP（含照片）” | **完整版**：全部字段含完整证件号 | 照片原图打包进 `photos/` 子目录 |

- ZIP 文件名含日期，如 `experts_20261019.zip` / `experts_full_20261019.zip`。
- 压缩包内 Excel 文件名统一为 `experts.xlsx`，Excel/WPS 可直接打开。
- 无照片的专家不打包其照片文件；照片引用缺失时自动跳过（不中断打包）。

**MD 说明文档下载**（`GET /api/export/md`，供 AI 据此把数据与图片下载到本地）：

| 接口 | 适用场景 | 内容 |
|---|---|---|
| `GET /api/export/md?keyword=xxx` | 员工页“下载 MD 说明文档”（按当前搜索框筛选条件） | 脱敏版说明文档 |
| `GET /api/export/md?keyword=xxx&full=1` | 管理员“下载 MD 说明文档”（导出全部） | 完整版说明文档 |

- 文档文件名含日期与筛选关键词，如 `expert_download_20261019_ai.md`（UTF-8 编码）。
- 文档包含五大章节：元信息（生成时间 / 筛选条件 / 命中条数）、系统内网接口、所需信息字段索引（全部 14 字段含义 / 示例 / 脱敏说明）、图片命名规则、本次筛选结果清单（id / 姓名 / 科室 / 照片文件名）、AI 下载指引。
- 服务根地址自动取本机内网 IP（`http://内网IP:8000`），保证发往 AI 后可按接口直接访问。

### 5. 敏感信息处理

- **员工查询页**不展示完整身份证号、银行卡号，仅显示**前 3 后 4**（如 `110***********1234`）。
- 后端在默认列表接口自动脱敏（`GET /api/experts`），管理员页通过 `?sensitive=1` 获取完整信息用于编辑。
- 员工查询页“下载数据”得到的 ZIP 内 Excel 同样只保留前3后4，照片为原图。
- 导出 Excel 与完整 ZIP 为完整字段（管理员使用场景），请妥善保管。

### 6. 修改日期与修改人

- 新增：记录创建时间、修改时间，修改人id 取表单输入（默认 admin）。
- 编辑：自动更新修改时间，**保留原修改人id**（表单中只读显示）。

### 7. 页面适配

- 员工查询页：卡片展示照片、姓名、科室、职称、研究方向、擅长领域、联系方式、简介、脱敏证件信息、修改时间/修改人；搜索支持姓名/科室/职称/研究方向/擅长领域/联系方式/简介多字段模糊匹配。
- 管理员维护页：完整字段列表 + 新增/编辑表单（含照片上传）+ Excel 导入/模板/导出 + 删除二次确认；搜索式输入框不因刷新重建导致失焦。

## 接口速览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/experts | 专家列表（默认脱敏；`?sensitive=1` 返回完整；`?keyword=` 模糊搜索） |
| POST | /api/experts | 新增（JSON 或 multipart，multipart 可带照片与修改人id） |
| PUT | /api/experts/{id} | 修改（保留原修改人，更新修改时间） |
| DELETE | /api/experts/{id} | 删除（连带删除照片文件） |
| GET | /api/experts/export | 导出完整 Excel（.xlsx） |
| GET | /api/export | 员工脱敏 ZIP（脱敏 Excel + 照片原图） |
| GET | /api/export/full | 管理员完整 ZIP（完整 Excel + 照片原图） |
| GET | /api/export/md | 筛选数据下载为 Markdown 说明文档（员工脱敏；`?full=1` 管理员完整版） |
| GET | /api/experts/template | 下载 Excel 模板 |
| POST | /api/experts/import | 批量导入 Excel |
| GET | /uploads/{文件名} | 照片静态资源 |

## 敏感数据提醒

> 身份证号、银行卡号属于个人敏感信息。本 Demo 的 JSON 数据文件与导出的 Excel 均含完整字段，仅限授权管理员使用；员工查询页已强制脱敏。生产环境建议：正式存储加密、传输走 HTTPS、导出操作审计留痕。

## 目录结构

```
demo/
├── server.py          # 服务端（标准库，主入口）
├── start_demo.bat     # Windows 一键启动脚本
├── README-demo.md     # 本文档
├── data/experts.db    # 专家数据（SQLite 主存储，148 条）
├── data/experts.json  # 历史备份（启动时迁移保留）
├── static/index.html  # 员工查询页
├── static/admin.html  # 管理员维护页
└── uploads/           # 上传照片目录（运行时自动创建，138 张在库照片）
```
*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*
