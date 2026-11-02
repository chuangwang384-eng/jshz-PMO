import request from './request'

// 客户信息模块接口（对应后端 /api/customer/*）
// 查看类接口登录即可；管理类接口需权限码 customer:manage（ADMIN / 知识星球维护人）

// 新增客户档案（customer:manage）
export function createCustomer(data) {
  return request.post('/customer', data)
}

// 分页检索：keyword / deleted(0正常 1已删除) / page / pageSize
export function listCustomers(params) {
  return request.get('/customer', { params })
}

// 客户详情
export function getCustomer(id) {
  return request.get(`/customer/${id}`)
}

// 编辑客户档案（customer:manage）
export function updateCustomer(id, data) {
  return request.put(`/customer/${id}`, data)
}

// 软删除客户档案（customer:manage，默认隐藏可恢复）
export function deleteCustomer(id) {
  return request.delete(`/customer/${id}`)
}

// 恢复已删除客户档案（customer:manage）
export function restoreCustomer(id) {
  return request.post(`/customer/${id}/restore`)
}

// 附件列表
export function listCustomerAttachments(id) {
  return request.get(`/customer/${id}/attachments`)
}

// 上传附件（customer:manage，multipart 字段 file）
export function uploadCustomerAttachment(id, data) {
  return request.post(`/customer/${id}/attachments`, data)
}

// 删除附件（customer:manage）
export function deleteCustomerAttachment(attachmentId) {
  return request.delete(`/customer/attachments/${attachmentId}`)
}

// 附件下载（blob）
export function fetchCustomerAttachment(fileName) {
  return request.get(`/customer/files/${encodeURIComponent(fileName)}`, { responseType: 'blob' })
}

// 关系类型列表（启用优先、内置优先）
export function listRelationTypes() {
  return request.get('/customer/relations/types')
}

// 新增自定义关系类型（customer:manage，后端自动生成编码）
export function createRelationType(data) {
  return request.post('/customer/relations/types', data)
}

// 编辑关系类型（customer:manage，可改名/停用，不可物理删除）
export function updateRelationType(id, data) {
  return request.put(`/customer/relations/types/${id}`, data)
}

// 以某客户为中心的关系圈（详情页脑图）
export function getCenterGraph(id) {
  return request.get(`/customer/${id}/relations`)
}

// 全局关系网络脑图
export function getNetworkGraph() {
  return request.get('/customer/relations/network')
}

// 搜索可选目标客户（customerId 必传时排除自身与已关联客户；为空返回全部未软删客户）
export function searchTargets(params) {
  return request.get('/customer/relations/search-targets', { params })
}

// 建立客户间关系（customer:manage，幂等：单向按 from:to:type、双向/无向按双方任意顺序判重）
export function createRelation(data) {
  return request.post('/customer/relations', data)
}

// 删除关系（customer:manage）
export function deleteRelation(id) {
  return request.delete(`/customer/relations/${id}`)
}
