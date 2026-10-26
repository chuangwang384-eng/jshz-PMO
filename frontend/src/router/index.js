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
    redirect: '/experts'
  },
  {
    path: '/experts',
    name: 'experts',
    component: () => import('../views/ExpertList.vue'),
    meta: { title: '专家库', requiresAuth: true }
  },
  {
    path: '/knowledge-planet',
    name: 'knowledge-planet',
    component: () => import('../views/KnowledgePlanetHome.vue'),
    meta: { title: '知识星球', requiresAuth: true },
    children: [
      {
        path: 'meeting-execution',
        name: 'meeting-execution',
        component: () => import('../views/MeetingExecution.vue'),
        meta: { title: '会议执行' }
      },
      {
        path: 'association-compliance',
        name: 'association-compliance',
        component: () => import('../views/AssociationCompliance.vue'),
        meta: { title: '协会合规要求' }
      },
      {
        path: 'enterprise-compliance',
        name: 'enterprise-compliance',
        component: () => import('../views/EnterpriseCompliance.vue'),
        meta: { title: '企业合规要求' }
      },
      {
        path: 'customer-preferences',
        name: 'customer-preferences',
        component: () => import('../views/CustomerPreferences.vue'),
        meta: { title: '客户喜好' }
      }
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
        meta: { title: '我的申请' }
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('../views/MessageCenter.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: 'approval/manage',
        name: 'approval-manage',
        component: () => import('../views/ApprovalManage.vue'),
        meta: { title: '审批管理', permissions: ['approval:audit'] }
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
    component: () => import('../views/supplier/SupplierList.vue'),
    meta: { title: '供应商管理', requiresAuth: true }
  },
  {
    path: '/supplier/:id',
    name: 'supplier-detail',
    component: () => import('../views/supplier/SupplierDetail.vue'),
    meta: { title: '供应商详情', requiresAuth: true, menu: false }
  },
  {
    path: '/project',
    name: 'project',
    component: () => import('../views/ProjectPlaceholder.vue'),
    meta: { title: '项目管理' }
  },
  {
    path: '/admin',
    name: 'admin-settings',
    redirect: '/admin/experts/manage',
    meta: {
      title: '管理员设置',
      permissions: ['expert:manage', 'audit:view', 'approval:config', 'user:manage', 'role:manage']
    },
    children: [
      {
        path: 'experts/manage',
        name: 'experts-manage',
        component: () => import('../views/ExpertManage.vue'),
        meta: { title: '专家管理', permissions: ['expert:manage'] }
      },
      {
        path: 'audit/downloads',
        name: 'audit-downloads',
        component: () => import('../views/DownloadAudit.vue'),
        meta: { title: '用户操作日志', permissions: ['audit:view'] }
      },
      {
        path: 'approval/config',
        name: 'approval-config',
        component: () => import('../views/ApprovalConfig.vue'),
        meta: { title: '审批流配置', permissions: ['approval:config'] }
      },
      {
        path: 'approval/templates',
        name: 'approval-templates',
        component: () => import('../views/ApprovalTemplateManage.vue'),
        meta: { title: '审批模板管理', permissions: ['approval:config'] }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UserManage.vue'),
        meta: { title: '用户管理', permissions: ['user:manage'] }
      },
      {
        path: 'roles/manage',
        name: 'role-manage',
        component: () => import('../views/RoleManage.vue'),
        meta: { title: '角色管理', permissions: ['role:manage'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/experts'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：
// - 登录态以 expert_user（非敏感用户信息）为准，JWT 存 httpOnly cookie 由浏览器自动携带
// - 未登录访问任意页面跳登录页
// - 已登录访问登录页跳转专家库
// - meta.permissions 权限码数组控制静态路由访问：未命中任一权限码则拦截并提示，
//   cookie 失效时由 request 拦截器 401 兜底跳登录
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
      return '/experts'
    }
    return true
  }

  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const permissions = user?.permissions || []
  const required = to.meta?.permissions
  if (Array.isArray(required) && required.length && !required.some((p) => permissions.includes(p))) {
    ElMessage.error('无权限访问该页面')
    return '/experts'
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - 景帅会展内部管理系统` : '景帅会展内部管理系统'
})

export default router
