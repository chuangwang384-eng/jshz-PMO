import request from './request'

// 审批流接口（对应后端 /api/approval/*）

// 审批流配置：GET /approval/config
export function getApprovalConfig() {
  return request.get('/approval/config')
}

// 更新审批流配置：PUT /approval/config，body {enabled, approverRole}
export function updateApprovalConfig(data) {
  return request.put('/approval/config', data)
}

// 提交修改申请：POST /approval/requests，body {expertId, changes: {中文键: 新值}, reason}
export function submitApprovalRequest(data) {
  return request.post('/approval/requests', data)
}

// 提交新增专家申请：POST /approval/create-request，body {changes: {中文键: 新值}, reason}（无需 expertId）
export function createExpertApprovalRequest(data) {
  return request.post('/approval/create-request', data)
}

// 临时照片上传：POST /approval/photo-upload（multipart 字段 file），返回 {fileName}
export function uploadApprovalPhoto(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/approval/photo-upload', formData)
}

// 我的申请：GET /approval/my-requests?page=&size=
export function getMyApprovalRequests(params) {
  return request.get('/approval/my-requests', { params })
}

// 全部申请单：GET /approval/requests?status=&keyword=&page=&size=（仅管理员）
export function getApprovalRequests(params) {
  return request.get('/approval/requests', { params })
}

// 审批通过：POST /approval/requests/{id}/approve，body {comment}
export function approveRequest(id, data) {
  return request.post(`/approval/requests/${id}/approve`, data)
}

// 审批驳回：POST /approval/requests/{id}/reject，body {comment}
export function rejectRequest(id, data) {
  return request.post(`/approval/requests/${id}/reject`, data)
}

// 撤回申请：POST /approval/requests/{id}/withdraw（仅申请人本人，status=0 可撤）
export function withdrawRequest(id) {
  return request.post(`/approval/requests/${id}/withdraw`)
}

// ==================== 审批申请字段模板（R-B4） ====================

// 启用模板列表：GET /approval/templates（登录即可，员工提交申请下拉）
export function getApprovalTemplates() {
  return request.get('/approval/templates')
}

// 全部模板（含停用）：GET /approval/templates/all（权限码 approval:config）
export function getAllApprovalTemplates() {
  return request.get('/approval/templates/all')
}

// 可选字段清单：GET /approval/template-fields，返回 {textFields, photoFields, createRequiredFields}
export function getApprovalFieldCatalog() {
  return request.get('/approval/template-fields')
}

// 新增模板：POST /approval/templates，body {templateName, fields: [中文键], enabled}
export function createApprovalTemplate(data) {
  return request.post('/approval/templates', data)
}

// 修改/启停模板：PUT /approval/templates/{id}
export function updateApprovalTemplate(id, data) {
  return request.put(`/approval/templates/${id}`, data)
}

// 删除模板：DELETE /approval/templates/{id}
export function deleteApprovalTemplate(id) {
  return request.delete(`/approval/templates/${id}`)
}
