import request from './request'

// 企微通讯录同步接口（对应后端 /api/admin/wecom/*，权限 wecom:sync）
export function triggerSync(data) {
  return request.post('/admin/wecom/sync', data)
}

export function getSyncStatus(logId) {
  return request.get('/admin/wecom/sync/status', { params: { logId } })
}

export function getRecentSyncLogs(params) {
  return request.get('/admin/wecom/sync/recent', { params })
}

export function getDepartments() {
  return request.get('/admin/wecom/departments')
}

export function getUnbound() {
  return request.get('/admin/wecom/unbound')
}

export function bindWecom(userId, wecomUserId) {
  return request.post(`/admin/users/${userId}/bind-wecom`, { wecomUserId })
}

// 登录模式配置（对应后端 GET /api/auth/wecom/config，登录页自适应）
export function getWecomConfig() {
  return request.get('/auth/wecom/config')
}

// 普通员工个人通讯录（对应后端 GET /api/wecom/directory/mine，登录即可）
export function getMyDirectory() {
  return request.get('/wecom/directory/mine')
}
