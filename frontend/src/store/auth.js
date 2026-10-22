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

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: readUser()
  }),
  getters: {
    isLoggedIn: (state) => !!state.user,
    isAdmin: (state) => !!state.user?.isAdmin,
    username: (state) => state.user?.username || '',
    mustChangePassword: (state) => !!state.user?.mustChangePassword
  },
  actions: {
    async login(username, password) {
      const data = await apiLogin({ username, password })
      // token 由后端写入 httpOnly cookie，前端不落盘
      this.user = {
        username: data.username,
        isAdmin: data.isAdmin,
        mustChangePassword: data.mustChangePassword
      }
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return this.user
    },
    // 从服务端刷新用户信息（保证强制改密标记最新）
    async fetchMe() {
      const data = await apiMe()
      this.user = {
        username: data.username,
        isAdmin: data.isAdmin,
        mustChangePassword: data.mustChangePassword
      }
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
