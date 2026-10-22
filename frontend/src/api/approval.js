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
