import request from './request'

// 审批流接口（对应后端 /api/approval/*）

// 审批流配置列表：GET /approval/config/list（多审批流配置）
export function getApprovalConfigList() {
  return request.get('/approval/config/list')
}

// 审批流配置：GET /approval/config?flowKey=（flowKey 可选，兼容旧调用）
export function getApprovalConfig(flowKey) {
  return request.get('/approval/config', flowKey ? { params: { flowKey } } : undefined)
}

// 更新审批流配置：PUT /approval/config，body {flowKey, enabled, approverRole}
// 兼容旧调用 updateApprovalConfig(data)（data 内含 flowKey）
export function updateApprovalConfig(flowKey, data) {
  if (flowKey && typeof flowKey === 'object') {
    return request.put('/approval/config', flowKey)
  }
  return request.put('/approval/config', { ...(data || {}), flowKey })
}

// 审批流具体审批人列表：GET /approval/config/{flowKey}/users
export function getConfigUsers(flowKey) {
  return request.get(`/approval/config/${flowKey}/users`)
}

// 保存审批流具体审批人：PUT /approval/config/{flowKey}/users，body {usernames}
export function saveConfigUsers(flowKey, usernames) {
  return request.put(`/approval/config/${flowKey}/users`, { usernames })
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

// 我的申请（新接口）：GET /approval/my-applications?page=&size=&status=
// 仅返回本人申请（后端按 applicant_id/applicant 双条件过滤），含审批人/审批时间/进度记录
export function getMyApplications(params) {
  return request.get('/approval/my-applications', { params })
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

// 退回修改：POST /approval/requests/{id}/return，body {comment}（意见必填）
export function returnRequest(id, comment) {
  return request.post(`/approval/requests/${id}/return`, { comment })
}

// 重新提交：POST /approval/requests/{id}/resubmit（仅申请人本人，status=4 可重提）
export function resubmitRequest(id, data) {
  return request.post(`/approval/requests/${id}/resubmit`, data)
}

// ==================== 审批转办 / 加签（B2，或签语义） ====================

// 审批转办：POST /approval/requests/{id}/transfer，body {targetUser}
export function transferRequest(id, data) {
  return request.post(`/approval/requests/${id}/transfer`, data)
}

// 审批加签（或签）：POST /approval/requests/{id}/add-approver，body {targetUser, comment?}
export function addApproverRequest(id, data) {
  return request.post(`/approval/requests/${id}/add-approver`, data)
}

// 可转办/加签用户列表：GET /approval/assignable-users，返回 [{id, username}]（仅拥有 approval:audit 的启用用户）
export function getAssignableUsers() {
  return request.get('/approval/assignable-users')
}


