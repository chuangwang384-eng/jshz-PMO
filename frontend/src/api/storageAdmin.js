import request from './request'

// ==================== 存储管理与硬删除（Owner 2026-11-12，权限 storage:manage） ====================

// 存储占用统计与预警（各目录字节/文件数 + 阈值级别 + 磁盘剩余）
export function storageStats() {
  return request.get('/storage-admin/stats')
}

// 全目录孤儿文件清单（scope 缺省 all：project/knowledge/photo/photo-tmp/customer/supplier）
export function fetchOrphans(scope) {
  return request.get('/storage-admin/orphans', { params: scope ? { scope } : {} })
}

// 指定目录孤儿文件硬删除（expectedCount/expectedBytes 集合版本校验，不可恢复）
export function purgeOrphans(data) {
  return request.post('/storage-admin/orphans/purge', data)
}

// 清空文档预览缓存（可按需再生成；expectedCount 校验）
export function clearPreviewCache(data) {
  return request.post('/storage-admin/preview-cache/purge', data)
}

// 项目硬删预览：项目名/状态/可删性 + 各类关联行计数 + 物理文件数与字节
export function purgeProjectPreview(id) {
  return request.get(`/storage-admin/projects/${id}/purge-preview`)
}

// 硬删除项目全部关联数据（仅已取消/已删除项目；confirmName=完整项目名；不可恢复）
export function purgeProject(id, confirmName) {
  return request.post(`/storage-admin/projects/${id}/purge`, { confirmName })
}
