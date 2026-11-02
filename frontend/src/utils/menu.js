// 从路由表生成菜单树：路由是唯一数据源，菜单结构与路由嵌套保持一致
// 规则：
// - meta.menu === false 的路由不显示
// - meta.permissions 权限码数组存在时，当前用户必须拥有其中任意一个才显示
// - meta.roles 角色编码数组存在时，当前用户必须拥有其中任意一个才显示（如仅 SUPER_ADMIN 可见的板块）
// - 无 meta.title 的路由不显示（如重定向/兜底路由）
// - 子路由 path 为相对路径时拼接父级完整路径，保证菜单 index 可直接用于跳转与高亮

function resolveFullPath(parentPath, childPath) {
  if (!childPath) return parentPath
  if (childPath.startsWith('/')) return childPath
  if (!parentPath || parentPath === '/') return `/${childPath}`
  return `${parentPath}/${childPath}`
}

export function buildMenuTree(routes, hasPermission, hasRole, parentPath = '') {
  return routes
    .filter((r) => {
      if (r.meta?.menu === false) return false
      const required = r.meta?.permissions
      if (Array.isArray(required) && required.length) {
        return required.some((p) => (typeof hasPermission === 'function' ? hasPermission(p) : false))
      }
      const requiredRoles = r.meta?.roles
      if (Array.isArray(requiredRoles) && requiredRoles.length) {
        return requiredRoles.some((role) => (typeof hasRole === 'function' ? hasRole(role) : false))
      }
      return true
    })
    .map((r) => {
      const path = resolveFullPath(parentPath, r.path)
      return {
        path,
        title: r.meta?.title,
        children: r.children ? buildMenuTree(r.children, hasPermission, hasRole, path) : null
      }
    })
    .filter((m) => m.title)
}
