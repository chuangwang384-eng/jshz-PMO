import request from './request'

// 供应商管理接口（对应后端 /api/supplier/*，档案/行价/评价/绑定登录即可，字典管理需 supplier:dict）

// 类型字典：全部类型（含 bizKey，酒店=HOTEL / 打印店=PRINT）
export function listSupplierTypes() {
  return request.get('/supplier/types')
}

// 新增类型（supplier:dict）
export function createSupplierType(data) {
  return request.post('/supplier/types', data)
}

// 修改类型（supplier:dict）
export function updateSupplierType(id, data) {
  return request.put(`/supplier/types/${id}`, data)
}

// 删除类型（supplier:dict；有引用时后端拒绝）
export function deleteSupplierType(id) {
  return request.delete(`/supplier/types/${id}`)
}

// 城市字典（固定只读）：全部城市（按 province 分组用）；province 可选，支持省→市两级联动
export function listSupplierCities(province) {
  return request.get('/supplier/cities', { params: { province } })
}

// 供应商分页列表：keyword / typeId / cityId 组合筛选
export function listSuppliers(params) {
  return request.get('/supplier/list', { params })
}

// 供应商详情：{supplier, priceItems, reviews, bindings}
export function getSupplierDetail(id) {
  return request.get(`/supplier/${id}`)
}

// 新增供应商（cityId 或 cityName；城市字典固定只读，cityName 仅精确匹配、未收录报错）
export function createSupplier(data) {
  return request.post('/supplier', data)
}

// 修改供应商
export function updateSupplier(id, data) {
  return request.put(`/supplier/${id}`, data)
}

// 删除供应商（级联行价/评价/绑定/图片）
export function deleteSupplier(id) {
  return request.delete(`/supplier/${id}`)
}

// 上传单张图片（v2.0）：登记到 supplier_image 表；name 自定义命名≤50字符（留空显示原文件名），返回 {id,fileName,isMain,...}
export function uploadSupplierImage(id, file, name) {
  const formData = new FormData()
  formData.append('file', file)
  if (name) formData.append('name', name)
  return request.post(`/supplier/${id}/images`, formData)
}

// 图片列表（含 isMain 主图标记、自定义命名；按 sort_order 升序）
export function listSupplierImages(id) {
  return request.get(`/supplier/${id}/images`)
}

// 设为主图
export function setSupplierMainImage(id, imageId) {
  return request.put(`/supplier/${id}/images/${imageId}/main`)
}

// 图片改名（v2.1）：body {name}，≤50字符允许空与重名；仅改展示名
export function renameSupplierImage(id, imageId, name) {
  return request.put(`/supplier/${id}/images/${imageId}/name`, { name })
}

// 删除图片（同步删除物理文件与 DB 行）
export function deleteSupplierImage(id, imageId) {
  return request.delete(`/supplier/${id}/images/${imageId}`)
}

// 图片展示地址（inline 流，登录态）
export function supplierImageUrl(fileName) {
  return `/api/supplier/images/${encodeURIComponent(fileName)}`
}

// 保存行价（全量替换）
export function saveSupplierPrices(id, items) {
  return request.put(`/supplier/${id}/prices`, { items })
}

// 提交评价（即发即显）
export function addSupplierReview(id, data) {
  return request.post(`/supplier/${id}/reviews`, data)
}

// 建立绑定（酒店或打印店任意一端发起，后端按类型定向）
export function bindSupplier(id, targetId) {
  return request.post(`/supplier/${id}/bindings`, { targetId })
}

// 解绑
export function unbindSupplier(id, targetId) {
  return request.delete(`/supplier/${id}/bindings/${targetId}`)
}

// 跨城绑定清单（v2.1）：GET /bindings/cross-city?keyword=&page=&size=
export function crossCityBindings(params) {
  return request.get('/supplier/bindings/cross-city', { params })
}

// 批量解绑（v2.1）：body {relIds}，返回 {unboundCount}
export function unbindSupplierBatch(relIds) {
  return request.post('/supplier/bindings/unbind-batch', { relIds })
}
