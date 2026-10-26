import request from './request'

// 下载留痕接口（对应后端 /api/audit/*，仅 ADMIN）
// 参数：expertId / username / page / size
export function listDownloads(params) {
  return request.get('/audit/downloads', { params })
}

// 通用用户操作日志（用户操作日志改版）：GET /audit/operations
// 参数：username（模糊）/ actionType / startTime / endTime（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）/ page / size
export function listOperations(params) {
  return request.get('/audit/operations', { params })
}
