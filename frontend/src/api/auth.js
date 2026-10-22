import request from './request'

// 认证接口（对应后端 /api/auth/*）
export function login(data) {
  return request.post('/auth/login', data)
}

// 登出：调用后端清除 httpOnly cookie
export function logout() {
  return request.post('/auth/logout')
}

export function me() {
  return request.get('/auth/me')
}

export function changePassword(data) {
  return request.post('/auth/change-password', data)
}
