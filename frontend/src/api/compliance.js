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

// ==================== 企业合规记事本（Owner 2026-11-10） ====================

// 记事本查询（收藏文档分组；view=hidden 返回已隐藏条目）
export function listNotebookItems(params) {
  return request.get('/compliance/notebook/items', { params })
}

// 隐藏一条（仅影响本人记事本）
export function hideNotebookItem(itemId) {
  return request.put(`/compliance/notebook/items/${itemId}/hide`)
}

// 恢复显示（幂等）
export function unhideNotebookItem(itemId) {
  return request.put(`/compliance/notebook/items/${itemId}/unhide`)
}

// 本人真实参与且文档绑定兼容的项目（推送选择器）
export function listPushableProjects(documentId) {
  return request.get('/compliance/notebook/pushable-projects', { params: { documentId } })
}

// 批量推送（同一文档；首次绑定、快照去重）
export function pushNotebookItems(projectId, itemIds) {
  return request.post('/compliance/notebook/push', { projectId, itemIds })
}

// 撤下预览（维护端：文档标题+全量已发布条数）
export function withdrawPreview(documentId) {
  return request.post(`/compliance/notebook/documents/${documentId}/withdraw-preview`)
}

// ==================== 候选事项清空（Owner 2026-11-11） ====================

// 候选事项清空预览（维护端：文档标题+草稿条数）
export function clearDraftsPreview(documentId) {
  return request.get(`/compliance/documents/${documentId}/drafts-clear-preview`)
}

// 清空候选事项（维护端：expectedCount 集合版本校验）
export function clearDrafts(documentId, expectedCount) {
  return request.post(`/compliance/documents/${documentId}/drafts-clear`, { expectedCount })
}

// 撤下提交（维护端：expectedCount 集合版本校验）
export function withdrawDocument(documentId, expectedCount) {
  return request.post(`/compliance/notebook/documents/${documentId}/withdraw`, { expectedCount })
}

// 处理项目合规待办（row_version 乐观锁）
export function handleProjectComplianceTodo(todoId, status, rowVersion) {
  return request.put(`/compliance/notebook/todos/${todoId}`, { status, rowVersion })
}
