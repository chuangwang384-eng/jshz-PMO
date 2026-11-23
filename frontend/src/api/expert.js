import request, { downloadFile } from './request'

// 专家接口（对应后端 /api/experts/*，键为中文）

// 分页列表：keyword / page / size（登录用户一律返回明文，仅登录鉴权）
export function listExperts(params) {
  return request.get('/experts', { params })
}

// 详情：登录用户一律返回明文
export function getExpert(id, params) {
  return request.get(`/experts/${id}`, { params })
}

// 新增：支持 JSON（中文键）或 multipart FormData（中文字段 + photo/idCardFront/idCardBack 文件）
export function createExpert(data) {
  return request.post('/experts', data)
}

// 修改：JSON 或 multipart FormData
export function updateExpert(id, data) {
  return request.put(`/experts/${id}`, data)
}

// 删除
export function deleteExpert(id) {
  return request.delete(`/experts/${id}`)
}

// 下载 Excel 模板（.xlsx，文件）
export function downloadTemplate() {
  return downloadFile('/experts/template')
}

// 导入 Excel（.xlsx，multipart 字段名 file）
export function importExperts(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/experts/import', formData)
}

// 全量 Excel（完整字段，自动留痕 full_xlsx）
export function exportAllExcel() {
  return downloadFile('/experts/export')
}

// 员工脱敏 ZIP：脱敏 Excel + 照片原图（自动留痕 employee_zip）
export function exportEmployeeZip() {
  return downloadFile('/experts/export/employee')
}

// 管理员完整 ZIP（完整 Excel + 三类照片原图，自动留痕 full_zip）
export function exportFullZip() {
  return downloadFile('/experts/export/full')
}

// 勾选导出 Excel（自动留痕 selected_xlsx）
export function exportSelectedExcel(ids) {
  return downloadFile('/experts/selected/export', { ids: ids.join(',') })
}

// 勾选导出 ZIP（Excel + 照片，自动留痕 selected_zip）
export function exportSelectedZip(ids) {
  return downloadFile('/experts/selected/zip', { ids: ids.join(',') })
}

// 照片访问（需登录，转 blob URL 供 <img> 使用）
export function fetchPhotoBlob(filename) {
  return request.get(`/files/${encodeURIComponent(filename)}`, { responseType: 'blob' })
}

// 临时照片访问（审批流预上传目录 /api/files-tmp，需登录；供对比弹窗预览待审批单的新照片）
export function fetchTempPhotoBlob(filename) {
  return request.get(`/files-tmp/${encodeURIComponent(filename)}`, { responseType: 'blob' })
}

// 高级筛选字典：单位/科室/职称去重（登录即可）
export function expertFilters() {
  return request.get('/experts/filters', { silent: true })
}

// 简历解析（新增专家辅助填充）：OCR 场景耗时较长，超时 180s
export function parseResume(file, { contentNameOnly = false } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/experts/resume/parse', formData, {
    timeout: 180000,
    params: contentNameOnly ? { contentNameOnly: true } : undefined
  })
}

// 专家资料文件（医生证等资质资料，2026-11-20）：列表 / 上传 / 删除
export function listExpertDocuments(id) {
  return request.get(`/experts/${id}/documents`)
}

export function uploadExpertDocument(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/experts/${id}/documents`, formData)
}

export function deleteExpertDocument(id, docId) {
  return request.delete(`/experts/${id}/documents/${docId}`)
}

// 专家资料文件访问（/api/files-doc，需登录；供 <a> 下载或图片预览）
export function expertDocumentUrl(storedName) {
  return '/api/files-doc/' + encodeURIComponent(storedName)
}
