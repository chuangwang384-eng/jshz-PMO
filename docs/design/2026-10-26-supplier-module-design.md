# 供应商管理模块 设计与实现文档

- 日期：2026-10-26
- 状态：已实现（feature/supplier → main，随 1.005 发布列车）；后端影子 API 实测 11 组全过，浏览器 UI 验收随部署进行
- 关联：`database/migration/20261026_add_supplier_module.sql`（幂等）

## 1. 目标

替换前端占位页，建立供应商管理：全员直接创建维护档案（无审批）、6 类内置可配置类型、全国城市统一字典（自动沉淀）、多图压缩上传、行价条目、即发即显评价、酒店↔打印店多对多绑定（双向展示+双向跳转）。

## 2. 数据模型（6 新表，库基线 17→23）

| 表 | 要点 |
| --- | --- |
| supplier_type | 类型字典；**biz_key 固定业务键**（HOTEL/PRINT/MEDIA/IMAGING/FLEET/TICKET）——管理员可随意改名称，绑定按 biz_key 识别类型，改名不破坏绑定；自定义类型 biz_key NULL |
| supplier_city | 城市字典；UNIQUE(province,name)；预置 34 省级 349 城（含港澳台）；自动沉淀 province='其他' |
| supplier | 档案：type_id/city_id 外键 + address/contact/phone/wechat/description + images(JSON 文件名数组) + create_by |
| supplier_price_item | 行价：item_name+price+unit+remark+sort（全量替换保存） |
| supplier_review | 评价：username+stars(1-5)+content+create_time（即发即显） |
| supplier_hotel_print_rel | 绑定：UNIQUE(hotel_id,print_shop_id)；service 层按 biz_key 定向校验 |

## 3. 权限

- 档案/行价/评价/绑定/图片：登录即可（无 permission 规则，全员）；
- 类型/城市字典管理：新权限码 `supplier:dict`（permission 表 5 条路径规则：POST/PUT/DELETE /api/supplier/types[/**]、POST /api/supplier/cities、DELETE /api/supplier/cities/**），绑定 ADMIN（SUPER_ADMIN 自动全权）；前端 v-permission 控制入口。

## 4. 关键实现

- 图片：前端 canvas 压缩（长边≤1280、JPEG 0.8，utils/imageCompress.js）→ POST /api/supplier/image 落盘 `<LOCAL_PATH>`（随机名防穿越）→ images 数组引用 → GET /api/supplier/images/{fileName} inline 展示；删除/移除图片同步清理文件。
- 城市三处同源（下拉/筛选/管理页均 GET /cities 分组）；表单城市 el-select filterable+allow-create，后端 cityName 命中或自动沉淀（resolveCityIdByName）。
- 绑定：POST /{id}/bindings {targetId} 任意一端发起，service 按双方类型 biz_key 自动定向（hotel_id=HOTEL 端）；重复绑定/类型不匹配 400；详情 bindings 按方向返回对端 [{id,name,cityName,typeName}]，前端点击 router.push(`/supplier/${id}`) 双向跳转，与解绑互不影响。
- 删除供应商：事务级联（行价/评价/绑定/图片文件）。
- 类型/城市删除：有供应商引用时 400 拒绝。

## 5. 影子实测记录（8081，2026-10-26 晚）

| # | 验证项 | 结果 |
| --- | --- | --- |
| 1 | 类型 6 类含 bizKey；城市 349/34 省 | ✅ |
| 2 | 权限边界：张三(EMPLOYEE) 建类型 403；jshz 200 | ✅ |
| 3 | 供应商创建（王创 createBy 记录）×3 | ✅ |
| 4 | 城市自动沉淀：「沙溪镇」→ id350 province=其他 | ✅ |
| 5 | 图片上传（随机名）/登录读取 200 image/jpeg/未登录 401/挂载到供应商 | ✅ |
| 6 | 行价保存/读取（价格两位小数）；非法价格 400 | ✅ |
| 7 | 评价提交返回 username+createTime（即发即显字段）；星数越界 400 | ✅ |
| 8 | 绑定矩阵：正常 200、重复 400、酒店+媒体类型不匹配 400 | ✅ |
| 9 | 双向展示：酒店详情含打印店 / 打印店详情含酒店 / 媒体 bindings 空 | ✅ |
| 10 | 列表组合筛选：typeId / keyword / cityId(沉淀城市) 各命中；avgStars=5.0/reviewCount 聚合 | ✅ |
| 11 | 引用禁删（城市/类型均 400 带数量提示）；删除供应商 200；解绑后两端清空 | ✅ |

测试数据留存（供明日浏览器验收）：测试酒店A（北京市，1 图）、测试打印店B（沙溪镇，2 条行价+1 条五星评价）、二者曾绑定后解绑；「测试自定义类型」「沙溪镇」为字典沉淀样本。
