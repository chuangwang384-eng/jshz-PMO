import request from './request'

// 角色与权限点接口（对应后端 /api/roles、/api/permissions，由 role:manage 权限码控制）

// 角色列表：GET /roles
export function listRoles(params) {
  return request.get('/roles', { params })
}

// 新增角色：POST /roles，body {code, name, description, status}
export function createRole(data) {
  return request.post('/roles', data)
}

// 修改角色：PUT /roles/{id}，body {code, name, description, status}
export function updateRole(id, data) {
  return request.put(`/roles/${id}`, data)
}

// 删除角色：DELETE /roles/{id}
export function deleteRole(id) {
  return request.delete(`/roles/${id}`)
}

// 角色已绑定权限码：GET /roles/{id}/permissions，返回 {permissionCodes: [...]}
export function getRolePermissions(id) {
  return request.get(`/roles/${id}/permissions`)
}

// 绑定权限码（覆盖式）：PUT /roles/{id}/permissions，body {permissionCodes}
export function assignRolePermissions(id, permissionCodes) {
  return request.put(`/roles/${id}/permissions`, { permissionCodes })
}

// 权限点列表：GET /permissions，返回 {menus: [...], apis: [...]}
export function listPermissions() {
  return request.get('/permissions')
}
