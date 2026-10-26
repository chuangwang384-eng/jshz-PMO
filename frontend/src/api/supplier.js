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

// 城市字典：全部城市（按 province 分组用；下拉/筛选/管理页同源）
export function listSupplierCities() {
  return request.get('/supplier/cities')
}

// 新增城市（supplier:dict）
export function createSupplierCity(data) {
  return request.post('/supplier/cities', data)
}

// 删除城市（supplier:dict；有引用时后端拒绝）
export function deleteSupplierCity(id) {
  return request.delete(`/supplier/cities/${id}`)
}

// 供应商分页列表：keyword / typeId / cityId 组合筛选
export function listSuppliers(params) {
  return request.get('/supplier/list', { params })
}

// 供应商详情：{supplier, priceItems, reviews, bindings}
export function getSupplierDetail(id) {
  return request.get(`/supplier/${id}`)
}

// 新增供应商（cityName 未收录自动沉淀）
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

// 上传单张压缩图片，返回 {fileName}
export function uploadSupplierImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/supplier/image', formData)
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
