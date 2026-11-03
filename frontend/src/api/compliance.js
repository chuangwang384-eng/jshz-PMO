import request from './request'

// 企业合规事项接口（对应后端 /api/compliance/*，REQ/DES-20261102）
// 全局编辑类需 knowledge:maintain；个人维度接口仅本人数据

// 全局事项列表（登录可读；维护人可传 includeDraft 看草稿）
export function listComplianceItems(params) {
  return request.get('/compliance/items', { params })
}

// 手动新增事项（维护人）
export function createComplianceItem(data) {
  return request.post('/compliance/items', data)
}

// 编辑事项（维护人）
export function updateComplianceItem(id, data) {
  return request.put(`/compliance/items/${id}`, data)
}

// 删除事项（维护人）
export function deleteComplianceItem(id) {
  return request.delete(`/compliance/items/${id}`)
}

// 批量确认草稿发布（维护人）：{ documentId, itemIds? }
export function confirmComplianceDrafts(data) {
  return request.post('/compliance/items/confirm', data)
}

// 跨版本匹配确认（维护人）：{ oldItemId }
export function confirmComplianceMatch(itemId, oldItemId) {
  return request.post(`/compliance/items/${itemId}/match-confirm`, { oldItemId })
}

// 事项修改历史
export function complianceItemHistory(itemId) {
  return request.get(`/compliance/items/${itemId}/history`)
}

// 某事项全部责任人个人进度（维护人只读）
export function assigneeProgress(itemId) {
  return request.get(`/compliance/items/${itemId}/assignee-progress`)
}

// 某责任人全部事项个人进度（维护人只读）
export function userProgress(userId) {
  return request.get(`/compliance/users/${userId}/progress`)
}

// 我的待办（收藏 ∪ 责任人）
export function myComplianceTodo() {
  return request.get('/compliance/my-todo')
}

// 个人进度更新（本人）：{ progressStatus?, remark?, blockReason? }
export function updateMyComplianceProgress(itemId, data) {
  return request.put(`/compliance/my-progress/${itemId}`, data)
}

// 提醒设置（本人）：{ remindEnabled?, remindDays? }
export function updateMyComplianceReminder(itemId, data) {
  return request.put(`/compliance/my-reminder/${itemId}`, data)
}
