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
    // 需求 2：一级菜单仅作展开容器（无页面组件），访问父路径重定向到第一个二级菜单
    redirect: '/knowledge-planet/meeting-execution',
    meta: { title: '知识星球', requiresAuth: true },
    children: [
      {
        path: 'meeting-execution',
        name: 'meeting-execution',
        component: () => import('../views/MeetingExecution.vue'),
        meta: { title: '会议执行标准' }
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
        path: 'customer-info',
        name: 'customer-info',
        component: () => import('../views/CustomerInfo.vue'),
        meta: { title: '客户信息' }
      },
      {
        path: 'customer-detail/:id',
        name: 'customer-detail',
        component: () => import('../views/CustomerDetail.vue'),
        meta: { title: '客户详情', menu: false, activeMenu: '/knowledge-planet/customer-info' }
      },
      {
        path: 'customer-relation-graph',
        name: 'customer-relation-graph',
        component: () => import('../views/CustomerRelationGraph.vue'),
        meta: { title: '客户关系网络', menu: false, activeMenu: '/knowledge-planet/customer-info' }
      },
      {
        path: 'customer-relation-type-manage',
        name: 'customer-relation-type-manage',
        component: () => import('../views/CustomerRelationTypeManage.vue'),
        meta: { title: '关系类型管理', menu: false, activeMenu: '/knowledge-planet/customer-info' }
      }
    ]
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/profile/ProfileHome.vue'),
    meta: { title: '个人中心', requiresAuth: true },
    children: [
      {
        path: 'overview',
        name: 'profile-overview',
        component: () => import('../views/profile/ProfileCard.vue'),
        meta: { title: '我的主页' }
      },
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
        path: 'directory',
        name: 'directory',
        component: () => import('../views/profile/Directory.vue'),
        meta: { title: '通讯录' }
      },
      {
        path: 'announcements',
        name: 'announcements',
        component: () => import('../views/announcement/AnnouncementList.vue'),
        meta: { title: '公告', requiresAuth: true }
      },
      {
        path: 'announcements/manage',
        name: 'announcements-manage',
        component: () => import('../views/announcement/AnnouncementManage.vue'),
        meta: { title: '公告管理', requiresAuth: true, permissions: ['announcement:manage'] }
      },
      {
        path: 'announcements/:id',
        name: 'announcement-detail',
        component: () => import('../views/announcement/AnnouncementDetail.vue'),
        meta: { title: '公告详情', requiresAuth: true, menu: false, activeMenu: '/profile/announcements' }
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
    component: () => import('../views/project/ProjectList.vue'),
    meta: { title: '项目管理', requiresAuth: true }
  },
  {
    path: '/project/:id',
    name: 'project-detail',
    component: () => import('../views/project/ProjectDetail.vue'),
    meta: { title: '项目详情', requiresAuth: true, menu: false }
  },
  {
    path: '/admin',
    name: 'admin-settings',
    redirect: () => {
      let user = null
      try {
        user = JSON.parse(localStorage.getItem('expert_user') || 'null')
      } catch (e) {
        user = null
      }
      const perms = user?.permissions || []
      if (perms.includes('expert:manage')) return '/admin/experts/manage'
      if (perms.includes('project:manage')) return '/admin/project/config'
      if (perms.includes('user:manage')) return '/admin/users'
      if (perms.includes('role:manage')) return '/admin/roles/manage'
      if (perms.includes('audit:view')) return '/admin/audit/downloads'
      if (perms.includes('approval:config')) return '/admin/approval/config'
      return '/experts'
    },
    meta: {
      title: '管理员设置',
      permissions: ['expert:manage', 'project:manage', 'audit:view', 'approval:config', 'user:manage', 'role:manage']
    },
    children: [
      {
        path: 'experts/manage',
        name: 'experts-manage',
        component: () => import('../views/ExpertManage.vue'),
        meta: { title: '专家管理', permissions: ['expert:manage'] }
      },
      {
        path: 'project/config',
        name: 'project-config',
        component: () => import('../views/project/ProjectConfig.vue'),
        meta: { title: '项目配置', permissions: ['project:manage'] }
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
      },
      {
        path: 'wecom',
        name: 'wecom-sync',
        component: () => import('../views/WecomSyncManage.vue'),
        meta: { title: '企微通讯录同步', permissions: ['wecom:sync'] }
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
