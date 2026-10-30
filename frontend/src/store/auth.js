import { defineStore } from 'pinia'
import { login as apiLogin, me as apiMe, changePassword as apiChangePassword, logout as apiLogout } from '../api/auth'

// 认证状态管理：用户信息（username / isAdmin / mustChangePassword）。
// JWT 存后端 httpOnly cookie（expert_token），不在 localStorage 落盘；
// TOKEN_KEY 仅用于清理历史遗留数据。
const TOKEN_KEY = 'expert_token'
const USER_KEY = 'expert_user'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

// 归一化登录/me 返回：保留 isAdmin 兼容旧逻辑，并同步 roles / permissions 供 RBAC 使用
function normalizeUser(data) {
  return {
    username: data.username,
    isAdmin: !!data.isAdmin,
    mustChangePassword: !!data.mustChangePassword,
    roles: Array.isArray(data.roles) ? data.roles : [],
    permissions: Array.isArray(data.permissions) ? data.permissions : [],
    id: data.id ?? null,
    createTime: data.createTime || null,
    roleNames: Array.isArray(data.roleNames) ? data.roleNames : []
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: readUser()
  }),
  getters: {
    isLoggedIn: (state) => !!state.user,
    isAdmin: (state) => !!state.user?.isAdmin,
    username: (state) => state.user?.username || '',
    mustChangePassword: (state) => !!state.user?.mustChangePassword,
    roles: (state) => state.user?.roles || [],
    permissions: (state) => state.user?.permissions || [],
    id: (state) => state.user?.id ?? null,
    createTime: (state) => state.user?.createTime || null,
    roleNames: (state) => state.user?.roleNames || [],
    hasRole: (state) => (code) => (state.user?.roles || []).includes(code),
    hasPermission: (state) => (code) => {
      if (!code) return true
      const perms = state.user?.permissions || []
      if (perms.includes('*')) return true
      return perms.includes(code)
    }
  },
  actions: {
    async login(username, password) {
      const data = await apiLogin({ username, password })
      // token 由后端写入 httpOnly cookie，前端不落盘
      this.user = normalizeUser(data)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return this.user
    },
    // 从服务端刷新用户信息（保证强制改密标记最新）
    async fetchMe() {
      const data = await apiMe()
      this.user = normalizeUser(data)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return this.user
    },
    async changePassword(oldPassword, newPassword) {
      await apiChangePassword({ oldPassword, newPassword })
      this.user = { ...this.user, mustChangePassword: false }
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    // 登出：先调后端清除 httpOnly cookie，再清理本地用户信息
    async logout() {
      try {
        await apiLogout()
      } catch (e) {
        // 后端不可达时也继续清理本地登录态
      }
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
