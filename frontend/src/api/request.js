import axios from 'axios'
import { ElMessage } from 'element-plus'

// 统一请求封装：baseURL /api，httpOnly cookie 鉴权（withCredentials），401 跳登录，统一错误提示
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
  withCredentials: true
})

function redirectLogin() {
  localStorage.removeItem('expert_token')
  localStorage.removeItem('expert_user')
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

// 响应拦截：
// - blob（文件下载）原样返回整个 response，由下载 helper 处理
// - JSON 按后端 {code, message, data} 解包，code=0 返回 data，非 0 统一提示
request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 0) {
        return res.data
      }
      if (res.code === 401) {
        ElMessage.error(res.message || '登录已过期，请重新登录')
        redirectLogin()
        return Promise.reject(new Error(res.message || '未登录'))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      redirectLogin()
    } else if (status === 403) {
      ElMessage.error('无权限执行该操作')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

// 文件下载统一 helper：GET 转 blob，解析 Content-Disposition 文件名并触发浏览器下载
export async function downloadFile(url, params = {}) {
  const response = await request.get(url, { params, responseType: 'blob' })
  let fileName = 'download'
  const cd = response.headers?.['content-disposition'] || ''
  const m = /filename\*=UTF-8''([^;]+)/.exec(cd) || /filename="?([^";]+)"?/.exec(cd)
  if (m && m[1]) {
    fileName = decodeURIComponent(m[1])
  }
  const blob = new Blob([response.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  URL.revokeObjectURL(link.href)
  link.remove()
}

export default request
