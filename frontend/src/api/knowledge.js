import request from './request'

// 知识星球内容管理接口（对应后端 /api/knowledge/*）

// 上传文档：multipart 字段 category/title/file
export function uploadKnowledgeDoc(data) {
  return request.post('/knowledge/docs', data)
}

// 分页列表：category 必填；keyword/page/pageSize 可选
export function listKnowledgeDocs(params) {
  return request.get('/knowledge/docs', { params })
}

// 更新文档（上传新文件覆盖）：multipart 字段 title(可选)/file
export function updateKnowledgeDoc(id, data) {
  return request.put(`/knowledge/docs/${id}`, data)
}

// 下架：DELETE（需权限码 knowledge:manage）
export function offShelfKnowledgeDoc(id) {
  return request.delete(`/knowledge/docs/${id}`)
}

// 永久删除：DELETE（需权限码 knowledge:manage；删除主记录+全部版本历史+磁盘文件，不可恢复）
export function permanentDeleteKnowledgeDoc(id) {
  return request.delete(`/knowledge/docs/${id}/permanent`)
}

// 版本历史
export function getKnowledgeVersions(id) {
  return request.get(`/knowledge/docs/${id}/versions`)
}

// 文件下载/预览（登录即可，blob 响应，response.data 为 Blob）
export function fetchKnowledgeFile(fileName, download = false) {
  const q = download ? '?download=1' : ''
  return request.get(`/knowledge/docs/files/${encodeURIComponent(fileName)}${q}`, { responseType: 'blob' })
}
