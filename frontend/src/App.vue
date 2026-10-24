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
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#ffffff"
        class="side-menu"
      >
        <SideMenuItem v-for="(m, i) in menuTree" :key="i" :item="m" />
      </el-menu>
      <div class="sidebar-version">V1.001</div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ pageTitle }}</div>
        <div class="user-area">
          <NotificationBell />
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
import ChangePasswordDialog from './components/ChangePasswordDialog.vue'
import SideMenuItem from './components/SideMenuItem.vue'
import NotificationBell from './components/NotificationBell.vue'
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
  // 刷新用户信息（保证强制改密标记最新），401 时 request 拦截器会自动跳登录
  auth
    .fetchMe()
    .then(() => {
      if (auth.mustChangePassword) {
        showChangePwd.value = true
      }
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
