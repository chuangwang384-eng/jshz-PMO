import request from './request'

// 系统公告接口（对应后端 /api/announcement/*）
// 列表/详情/标记已读登录即可；创建/编辑/删除/上下架/置顶/已读统计需 announcement:manage

// 首页生效列表：最多 5 条，置顶优先 + 发布时间倒序
export function getEffectiveAnnouncements(limit = 5) {
  return request.get('/announcement/effective', { params: { limit } })
}

// 分页列表：管理视角含已读进度；员工视角仅 ONLINE
export function listAnnouncements(params) {
  return request.get('/announcement', { params })
}

// 详情
export function getAnnouncement(id) {
  return request.get(`/announcement/${id}`)
}

// 创建（announcement:manage）
export function createAnnouncement(data) {
  return request.post('/announcement', data)
}

// 编辑（announcement:manage）
export function updateAnnouncement(id, data) {
  return request.put(`/announcement/${id}`, data)
}

// 删除（announcement:manage）
export function deleteAnnouncement(id) {
  return request.delete(`/announcement/${id}`)
}

// 上架/下架（announcement:manage）
export function changeAnnouncementStatus(id, status) {
  return request.put(`/announcement/${id}/status`, null, { params: { status } })
}

// 置顶/取消置顶（announcement:manage）
export function toggleAnnouncementTop(id, top) {
  return request.put(`/announcement/${id}/top`, null, { params: { top } })
}

// 标记已读（登录即可，幂等，不入操作日志）
export function markAnnouncementRead(id) {
  return request.post(`/announcement/${id}/read`)
}

// 已读进度与已读/未读名单（announcement:manage）
export function getAnnouncementReadStats(id) {
  return request.get(`/announcement/${id}/read-stats`)
}

// 富文本插图上传：复用 /api/upload/richtext
export function uploadRichtextImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload/richtext', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
