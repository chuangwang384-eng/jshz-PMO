import request from './request'

// 企业合规 v4（可复用清单，/api/compliance/v4/*，REQ/DES-COMPLIANCE-V4）
// 权限：执行对象 owner=当前登录用户；模板发布需 knowledge:maintain

export function listPublishedTemplates(documentId) {
  return request.get(`/compliance/v4/documents/${documentId}/templates`)
}
export function createTemplateDraft(documentId, data) {
  return request.post(`/compliance/v4/documents/${documentId}/templates/drafts`, data)
}
export function getTemplateDetail(templateVersionId) {
  return request.get(`/compliance/v4/templates/${templateVersionId}`)
}
export function patchTemplate(templateVersionId, data) {
  return request.patch(`/compliance/v4/templates/${templateVersionId}`, data)
}
export function publishTemplate(templateVersionId) {
  return request.post(`/compliance/v4/templates/${templateVersionId}/publish`)
}
export function createExecution(data) {
  return request.post('/compliance/v4/executions', data)
}
export function startExecution(id, data) {
  return request.post(`/compliance/v4/executions/${id}/start`, data || {})
}
export function listMyExecutions(params) {
  return request.get('/compliance/v4/executions', { params })
}
export function getExecutionDetail(id) {
  return request.get(`/compliance/v4/executions/${id}`)
}
export function reuseExecution(id, data) {
  return request.post(`/compliance/v4/executions/${id}/reuse`, data)
}
export function pauseExecution(id) {
  return request.post(`/compliance/v4/executions/${id}/pause`)
}
export function archiveExecution(id) {
  return request.post(`/compliance/v4/executions/${id}/archive`)
}
export function patchTask(taskId, data) {
  return request.patch(`/compliance/v4/tasks/${taskId}`, data)
}
export function listMyTasks(params) {
  return request.get('/compliance/v4/my-tasks', { params })
}
export function taskEvents(taskId) {
  return request.get(`/compliance/v4/tasks/${taskId}/events`)
}
export function createDraftFromV3(documentId, title) {
  return request.post('/compliance/v4/migration/templates/from-v3', { documentId, title })
}
