import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    redirect: '/company/experts'
  },
  {
    path: '/company',
    name: 'company',
    redirect: '/company/experts',
    meta: { title: '公司内部资料', requiresAuth: true },
    children: [
      {
        path: 'experts',
        name: 'experts',
        component: () => import('../views/ExpertList.vue'),
        meta: { title: '专家库' }
      }
      // 以后新增二级菜单：在 children 里继续追加即可
    ]
  },
  {
    path: '/profile',
    name: 'profile',
    redirect: '/profile/my-approvals',
    meta: { title: '个人中心', requiresAuth: true },
    children: [
      {
        path: 'my-approvals',
        name: 'my-approvals',
        component: () => import('../views/MyApproval.vue'),
        meta: { title: '我的审批' }
      },
      {
        path: 'change-password',
        name: 'change-password',
        component: () => import('../views/profile/ChangePassword.vue'),
        meta: { title: '修改密码' }
      }
    ]
  },
  {
    path: '/supplier',
    name: 'supplier',
    component: () => import('../views/SupplierPlaceholder.vue'),
    meta: { title: '供应商管理' }
  },
  {
    path: '/project',
    name: 'project',
    component: () => import('../views/ProjectPlaceholder.vue'),
    meta: { title: '项目管理' }
  },
  {
    path: '/experts/manage',
    name: 'experts-manage',
    component: () => import('../views/ExpertManage.vue'),
    meta: { title: '专家管理', requiresAdmin: true }
  },
  {
    path: '/audit/downloads',
    name: 'audit-downloads',
    component: () => import('../views/DownloadAudit.vue'),
    meta: { title: '下载留痕', requiresAdmin: true }
  },
  {
    path: '/approval/manage',
    name: 'approval-manage',
    component: () => import('../views/ApprovalManage.vue'),
    meta: { title: '审批管理', requiresAdmin: true }
  },
  {
    path: '/approval/config',
    name: 'approval-config',
    component: () => import('../views/ApprovalConfig.vue'),
    meta: { title: '审批流配置', requiresAdmin: true }
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('../views/UserManage.vue'),
    meta: { title: '用户管理', requiresAdmin: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/company/experts'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：
// - 登录态以 expert_user（非敏感用户信息）为准，JWT 存 httpOnly cookie 由浏览器自动携带
// - 未登录访问任意页面跳登录页
// - 已登录访问登录页跳转公司内部资料（专家库）
// - is_admin 控制管理页访问（cookie 失效时由 request 拦截器 401 兜底跳登录）
router.beforeEach((to) => {
  let user = null
  try {
    user = JSON.parse(localStorage.getItem('expert_user') || 'null')
  } catch (e) {
    user = null
  }
  const loggedIn = !!user

  if (to.meta.public) {
    if (loggedIn && to.path === '/login') {
      return '/company/experts'
    }
    return true
  }

  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !user?.isAdmin) {
    ElMessage.error('无权限访问管理页面')
    return '/company/experts'
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - 专家库系统` : '专家库系统'
})

export default router
