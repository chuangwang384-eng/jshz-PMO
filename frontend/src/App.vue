<template>
  <!-- 登录页独立布局 -->
  <router-view v-if="isLoginPage" />

  <!-- 主布局：侧边菜单 + 顶栏 -->
  <el-container v-else class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        景帅会展内部管理系统
      </div>
      <el-menu
        ref="sideMenuRef"
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#ffffff"
        class="side-menu"
      >
        <SideMenuItem v-for="(m, i) in menuTree" :key="i" :item="m" />
      </el-menu>
      <div class="sidebar-version">{{ versionPrefix }}{{ versionLabel }}</div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ pageTitle }}</div>
        <div class="user-area">
          <MessageDrawerBell />
          <span class="username">{{ auth.username }}</span>
          <el-button link type="primary" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 首次登录强制改密弹窗（不可关闭） -->
  <ChangePasswordDialog v-model="showChangePwd" />
</template>

<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from './store/auth'
import { versionPrefix, versionLabel } from './config/version'
import ChangePasswordDialog from './components/ChangePasswordDialog.vue'
import SideMenuItem from './components/SideMenuItem.vue'
import MessageDrawerBell from './components/MessageDrawerBell.vue'
import { listAnnouncements, markAnnouncementRead } from './api/announcement'
import { buildMenuTree } from './utils/menu'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 侧边菜单实例：供 SideMenuItem 展开/收起子菜单（点标题跳父路由、点箭头展开）
const sideMenuRef = ref(null)
provide('sideMenuRef', sideMenuRef)

const isLoginPage = computed(() => route.path === '/login')
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta?.title || '景帅会展内部管理系统')

// 一级菜单仅作展开容器：当前路由所属的一级菜单默认展开子菜单（避免切页后菜单全部收起）
const defaultOpeneds = computed(() => {
  const p = route.path
  if (!p || p === '/login') return []
  const top = router.options.routes.find((r) => {
    if (!r.path || r.path === '/') return false
    return p === r.path || p.startsWith(r.path + '/')
  })
  return top ? [top.path] : []
})

// 从路由表生成菜单树：只取业务路由（排除登录页），管理菜单由 meta.permissions 权限码自动过滤
const menuTree = computed(() => {
  const businessRoutes = router.options.routes.filter((r) => r.path !== '/login')
  return buildMenuTree(businessRoutes, auth.hasPermission)
})

const showChangePwd = ref(false)

onMounted(() => {
  if (!auth.isLoggedIn) {
    return
  }
  // 全局未读公告提示（挂载点击跳转入口，供 ElMessageBox 弹窗条目 onclick 调用）
  window.__jshzOpenAnnouncement = openAnnouncement
  // 先刷新用户信息（权限与强制改密标记最新）再检查未读，避免缓存陈旧导致漏弹/错弹；401 时 request 拦截器会自动跳登录
  auth
    .fetchMe()
    .then(() => {
      if (auth.mustChangePassword) {
        showChangePwd.value = true
      }
      checkUnreadAnnouncements()
    })
    .catch(() => {})
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出登录', { type: 'warning' })
  } catch (e) {
    return
  }
  await auth.logout()
  router.push('/login')
}

// 全局未读公告检查（从专家库首页迁移至全局）：非超管且非公告管理员才检查
// sessionStorage key 沿用 jshz_announce_popup_shown_${id}，避免会话内重复弹窗
function escHtml(v) {
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

function annBriefText(a) {
  if (!a || !a.content) return ''
  const div = document.createElement('div')
  div.innerHTML = a.content
  return (div.textContent || '').replace(/\s+/g, ' ').trim()
}

async function checkUnreadAnnouncements() {
  if (!auth.isLoggedIn || auth.isAdmin || auth.hasPermission('announcement:manage')) return
  // 当天弹一次节流：localStorage 按"用户+日期"记录，避免刷新/多标签重复弹窗
  const dateKey = `jshz_announce_popup_date_${auth.id ?? 'anon'}`
  const today = new Date().toLocaleDateString('sv-SE')
  try {
    if (localStorage.getItem(dateKey) === today) return
  } catch (e) {
    // localStorage 不可用时退化为不节流
  }
  try {
    const data = await listAnnouncements({ page: 1, size: 50 })
    const items = (data && data.items) || []
    const key = `jshz_announce_popup_shown_${auth.id ?? 'anon'}`
    let shown = []
    try {
      shown = JSON.parse(sessionStorage.getItem(key) || '[]')
    } catch (e) {
      shown = []
    }
    const unread = items.filter((a) => !a.read && !a.expired && !shown.includes(a.id))
    if (!unread.length) return
    // 弹窗展示前先记录已弹 id，避免会话内重复提示
    sessionStorage.setItem(key, JSON.stringify([...new Set([...shown, ...unread.map((a) => a.id)])]))
    const html = unread
      .map(
        (a) =>
          `<div class="jshz-unread-item" onclick="window.__jshzOpenAnnouncement(${a.id})" style="padding:10px 12px;border-bottom:1px solid #f2f4f7;cursor:pointer;">
            <div style="display:flex;align-items:center;gap:6px;font-size:13px;font-weight:600;color:#303133;">
              ${a.isTop === 1 ? '<span style="color:#f56c6c;font-size:12px;font-weight:400;">置顶</span>' : ''}
              <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escHtml(a.title)}</span>
            </div>
            <div style="margin-top:4px;font-size:12px;color:#909399;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escHtml(annBriefText(a))}</div>
          </div>`
      )
      .join('')
    // 弹窗前记录当天已弹日期（展示即视为当天已提示，不重复打扰）
    try {
      localStorage.setItem(dateKey, today)
    } catch (e) {
      // 写入失败不阻断弹窗
    }
    await ElMessageBox.confirm(html, `未读公告（${unread.length} 条）`, {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '查看全部',
      cancelButtonText: '稍后再说',
      type: 'warning'
    })
    router.push('/profile/announcements')
  } catch (e) {
    // 取消或接口失败均静默，不影响页面
  }
}

// 点击未读公告弹窗条目：标记已读并跳详情（由 ElMessageBox HTML 的 onclick 调用）
function openAnnouncement(id) {
  const n = Number(id)
  if (!Number.isFinite(n)) {
    return
  }
  ElMessageBox.close()
  markAnnouncementRead(n).catch(() => {})
  router.push(`/profile/announcements/${n}`)
}
</script>

<style>
body {
  margin: 0;
  font-family: 'Microsoft YaHei', sans-serif;
  background: #f0f2f5;
}
.layout {
  height: 100vh;
}
.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
}
.logo {
  flex-shrink: 0;
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  background: #002140;
  white-space: nowrap;
}
.side-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.25) transparent;
}
.side-menu::-webkit-scrollbar {
  width: 6px;
}
.side-menu::-webkit-scrollbar-track {
  background: transparent;
}
.side-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.25);
  border-radius: 3px;
}
.side-menu::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.45);
}
.sidebar-version {
  flex-shrink: 0;
  height: 36px;
  line-height: 36px;
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: #001529;
  user-select: none;
}
.side-menu .el-menu-item {
  height: 48px;
  line-height: 48px;
}
.side-menu .el-sub-menu__title {
  height: 48px;
  line-height: 48px;
}
/* 自定义箭头接管展开交互，隐藏 el-sub-menu 自带箭头 */
.side-menu .el-sub-menu__icon-arrow {
  display: none;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 20px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.username {
  color: #303133;
  font-weight: 500;
}
.main {
  padding: 16px;
}
</style>
