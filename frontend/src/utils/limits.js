import { reactive } from 'vue'
import request from '../api/request'

// 上传限额单一真源（方案 D，2026-11-13）：
// 唯一来源为后端 application.yml expert.upload（GET /api/meta/limits 读取）。
// 此处默认值仅作后端接口不可用时的兜底，调整限额时与 yml 同步。
export const uploadLimits = reactive({
  photoMaxBytes: 5 * 1024 * 1024,
  resumeMaxBytes: 30 * 1024 * 1024,
  docMaxBytes: 50 * 1024 * 1024
})

let fetched = false

// 上传相关页面挂载（登录后）时调用一次；失败静默保留默认值，不阻断上传流程
export async function ensureUploadLimits() {
  if (fetched) return
  fetched = true
  try {
    const data = await request.get('/meta/limits', { silent: true })
    if (data && typeof data === 'object') Object.assign(uploadLimits, data)
  } catch (e) {
    // 后端不可用或未登录时保持默认值（默认值与 yml 一致，不影响既有行为）
  }
}

/** 字节数转 MB 展示值（提示文案用）。 */
export function toMb(bytes) {
  return Math.round(bytes / 1024 / 1024)
}
