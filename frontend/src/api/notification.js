import request from './request'

// 通用消息通知中心接口（对应后端 /api/notifications/*，登录即可）

// 本人消息分页列表
export function fetchNotifications(params) {
  return request.get('/notifications', { params })
}

// 本人未读消息数
export function fetchUnreadCount() {
  return request.get('/notifications/unread-count')
}

// 单条标记已读
export function markNotificationRead(id) {
  return request.post(`/notifications/${id}/read`)
}

// 全部标记已读
export function markAllNotificationsRead() {
  return request.post('/notifications/read-all')
}
