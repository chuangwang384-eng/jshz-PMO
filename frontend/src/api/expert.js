import request, { downloadFile } from './request'

// 专家接口（对应后端 /api/experts/*，键为中文）

// 分页列表：keyword / page / size / sensitive=1（管理员全量）
export function listExperts(params) {
  return request.get('/experts', { params })
}

// 详情：sensitive=1 管理员全量
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
  return downloadFile('/experts/selected/export', { ids: ids.join(','), sensitive: 1 })
}

// 勾选导出 ZIP（Excel + 照片，自动留痕 selected_zip）
export function exportSelectedZip(ids) {
  return downloadFile('/experts/selected/zip', { ids: ids.join(','), sensitive: 1 })
}

// 照片访问（需登录，转 blob URL 供 <img> 使用）
export function fetchPhotoBlob(filename) {
  return request.get(`/files/${encodeURIComponent(filename)}`, { responseType: 'blob' })
}
