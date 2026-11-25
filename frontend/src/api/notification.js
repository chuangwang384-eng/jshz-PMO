import request from './request'

// 通用消息通知中心接口（对应后端 /api/notifications/*，登录即可）

// 本人消息分页列表（消息中心）：isRead（0未读/1已读）与 type 可组合过滤，均空=全部
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

// 批量标记已读：body {ids: []}，返回实际置为已读的条数
export function readNotificationsBatch(ids) {
  return request.post('/notifications/read-batch', { ids })
}

// 全部标记已读
export function markAllNotificationsRead() {
  return request.post('/notifications/read-all')
}

// 单条删除（物理删除）
export function deleteNotification(id) {
  return request.delete(`/notifications/${id}`)
}

// 批量删除（物理删除）：body {ids: []}，返回实际删除条数
export function deleteNotificationsBatch(ids) {
  return request.delete('/notifications/batch', { data: { ids } })
}

// 本人免打扰类型列表（需求 D，2026-11-25）：返回 ["type1","type2"…]
export function fetchMutedTypes() {
  return request.get('/notifications/mutes')
}

// 全量保存本人免打扰类型：body {types: []}（password_reset 与未知类型后端 400 拒绝；空数组=全部恢复接收）
export function saveMutedTypes(types) {
  return request.put('/notifications/mutes', { types })
}
