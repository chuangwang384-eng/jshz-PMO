import request from './request'

// 下载留痕接口（对应后端 /api/audit/*，仅 ADMIN）
// 参数：expertId / username / page / size
export function listDownloads(params) {
  return request.get('/audit/downloads', { params })
}
