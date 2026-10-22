// 从路由表生成菜单树：路由是唯一数据源，菜单结构与路由嵌套保持一致
// 规则：
// - meta.menu === false 的路由不显示
// - meta.requiresAdmin 且当前用户非管理员的路由不显示
// - 无 meta.title 的路由不显示（如重定向/兜底路由）
// - 子路由 path 为相对路径时拼接父级完整路径，保证菜单 index 可直接用于跳转与高亮

function resolveFullPath(parentPath, childPath) {
  if (!childPath) return parentPath
  if (childPath.startsWith('/')) return childPath
  if (!parentPath || parentPath === '/') return `/${childPath}`
  return `${parentPath}/${childPath}`
}

export function buildMenuTree(routes, isAdmin, parentPath = '') {
  return routes
    .filter((r) => r.meta?.menu !== false && !(r.meta?.requiresAdmin && !isAdmin))
    .map((r) => {
      const path = resolveFullPath(parentPath, r.path)
      return {
        path,
        title: r.meta?.title,
        children: r.children ? buildMenuTree(r.children, isAdmin, path) : null
      }
    })
    .filter((m) => m.title)
}
