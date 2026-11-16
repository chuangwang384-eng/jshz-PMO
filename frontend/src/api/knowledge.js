import request from './request'

// 知识星球内容管理接口（对应后端 /api/knowledge/*）

// 上传文档：multipart 字段 category/title/file（仅知识星球维护人 knowledge:maintain）
export function uploadKnowledgeDoc(data) {
  return request.post('/knowledge/docs', data)
}

// 分页列表：category / keyword / fileType / page / pageSize 可选
// fileType 按格式等值过滤（pdf/docx/doc/xlsx/pptx/image=图片集合），与 category/keyword 组合生效
export function listKnowledgeDocs(params) {
  return request.get('/knowledge/docs', { params })
}

// 更新文档（上传新文件覆盖）：multipart 字段 title(可选)/file（仅知识星球维护人 knowledge:maintain）
export function updateKnowledgeDoc(id, data) {
  return request.put(`/knowledge/docs/${id}`, data)
}

// 下架：DELETE（需权限码 knowledge:maintain）
export function offShelfKnowledgeDoc(id) {
  return request.delete(`/knowledge/docs/${id}`)
}

// 永久删除：DELETE（需权限码 knowledge:maintain；删除主记录+全部版本历史+磁盘文件，不可恢复）
export function permanentDeleteKnowledgeDoc(id) {
  return request.delete(`/knowledge/docs/${id}/permanent`)
}

// 上架：POST（仅知识星球维护人 knowledge:maintain；上架后普通用户立即可见，收藏列表自动恢复，通知收藏者）
export function relistKnowledgeDoc(id) {
  return request.post(`/knowledge/docs/${id}/relist`)
}

// 版本历史（仅知识星球维护人 knowledge:maintain）
export function getKnowledgeVersions(id) {
  return request.get(`/knowledge/docs/${id}/versions`)
}

// 文件下载/预览（登录即可，blob 响应，response.data 为 Blob）
// download=true 返回原文件流（?download=1）；preview=true 走预览模式（?preview=true）：
// 成功返回文件流（SUCCESS 语义）；失败返回结构化 JSON {code:CONVERT_FAILED|PREVIEW_FAILED,message}
export function fetchKnowledgeFile(fileName, download = false, preview = false) {
  const params = new URLSearchParams()
  if (download) params.set('download', '1')
  if (preview) params.set('preview', 'true')
  const qs = params.toString()
  return request.get(`/knowledge/docs/files/${encodeURIComponent(fileName)}${qs ? '?' + qs : ''}`, { responseType: 'blob' })
}

// ==================== 文档收藏（C3） ====================

// 收藏文档：POST /knowledge/favorites/{docId}（登录即可，幂等）
export function favoriteKnowledgeDoc(docId) {
  return request.post(`/knowledge/favorites/${docId}`)
}

// 取消收藏：DELETE /knowledge/favorites/{docId}（登录即可，幂等）
export function unfavoriteKnowledgeDoc(docId) {
  return request.delete(`/knowledge/favorites/${docId}`)
}

// 我的收藏列表：GET /knowledge/favorites，返回 [{doc, favoriteTime}]（收藏时间倒序）
export function listMyFavoriteDocs() {
  return request.get('/knowledge/favorites')
}

export function fetchKnowledgeWorkbook(fileName, params) {
  return request.get(`/knowledge/docs/workbooks/${encodeURIComponent(fileName)}`, { params })
}
