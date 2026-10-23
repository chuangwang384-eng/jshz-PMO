// 全局权限指令：v-permission="'expert:add'" 或 v-permission="['expert:add', 'expert:edit']"
// 元素无对应权限码时直接从 DOM 移除（按钮级控制）。
// 若登录返回的 permissions 含 '*'（如 SUPER_ADMIN 全量权限）则放行全部。
import { useAuthStore } from '../store/auth'

export default {
  mounted(el, binding) {
    const raw = binding.value
    const codes = raw == null ? [] : Array.isArray(raw) ? raw : [raw]
    if (!codes.length) return
    const auth = useAuthStore()
    const perms = auth.permissions || []
    const ok = perms.includes('*') || codes.some((c) => perms.includes(c))
    if (!ok) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
