import request from './request'

// 用户管理接口（对应后端 /api/users/*，仅 ADMIN）
export function listUsers(params) {
  return request.get('/users', { params })
}

export function createUser(data) {
  return request.post('/users', data)
}

export function resetPassword(id) {
  return request.post(`/users/${id}/reset-password`)
}

export function deleteUser(id) {
  return request.delete(`/users/${id}`)
}

// 为用户分配角色（覆盖式，RBAC）
export function assignUserRoles(id, roleIds) {
  return request.put(`/users/${id}/roles`, { roleIds })
}
