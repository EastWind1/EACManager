import { createRouter, createWebHistory } from 'vue-router'
import { Permission as UserPermission } from '@/user/model/Permission'
import { Permission as CompanyPermission } from '@/company/model/Permission'
import { Permission as ServiceBillPermission } from '@/service-bill/model/Permission'
import { Permission as ReimbursePermission } from '@/reimburse/model/Permission'
import { Permission as ConfigPermission } from '@/permission/model/PermissionCode'
import { useUserStore } from '@/user/store/UserStore'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/home/HomeView.vue'),
      redirect: '/dashboard',
      beforeEnter: (to) => {
        const hasLogin = useUserStore().getUser()
        if (!hasLogin) {
          return {
            path: '/login',
            query: { redirect: to.fullPath },
          }
        }
      },
      children: [
        // 仪表盘
        {
          path: '/dashboard',
          component: () => import('@/statistic/view/DashboardView.vue'),
        },
        // 用户管理
        {
          path: '/user',
          component: () => import('@/user/view/UserView.vue'),
          meta: { permissions: [UserPermission.USER_QUERY] },
        },
        // 公司管理
        {
          path: '/company',
          component: () => import('@/company/view/CompanyView.vue'),
          meta: { permissions: [CompanyPermission.COMPANY_QUERY] },
        },
        // 服务单列表
        {
          path: '/services',
          component: () => import('@/service-bill/view/ServiceBillListView.vue'),
          meta: { permissions: [ServiceBillPermission.SERVICE_BILL_QUERY] },
        },
        // 服务单卡片
        {
          path: '/service/:id?',
          component: () => import('@/service-bill/view/ServiceBillFormView.vue'),
          meta: { permissions: [ServiceBillPermission.SERVICE_BILL_DETAIL, ServiceBillPermission.SERVICE_BILL_CREATE] },
        },
        // 报销单列表
        {
          path: '/reimburses',
          component: () => import('@/reimburse/view/ReimburseListView.vue'),
          meta: { permissions: [ReimbursePermission.REIMBURSE_QUERY] },
        },
        // 报销单卡片
        {
          path: '/reimburse/:id?',
          component: () => import('@/reimburse/view/ReimburseFormView.vue'),
          meta: { permissions: [ReimbursePermission.REIMBURSE_DETAIL, ReimbursePermission.REIMBURSE_CREATE] },
        },
        // 权限配置
        {
          path: '/permission',
          component: () => import('@/permission/view/PermissionView.vue'),
          meta: { permissions: [ConfigPermission.PERMISSION_QUERY] },
        },
      ],
    },
    {
      path: '/login',
      component: () => import('@/user/view/LoginView.vue'),
    },
    {
      path: '/user/init',
      component: () => import('@/user/view/UserInitView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('@/home/NotFoundView.vue'),
    },
  ],
})

// 动作权限路由守卫
router.beforeEach((to) => {
  const userStore = useUserStore()
  // 未登录时交给父路由的 beforeEnter 跳转登录页
  if (!userStore.getUser()) {
    return
  }
  const required = to.meta.permissions as string[] | undefined
  if (required?.length && !userStore.hasAnyPermission(required)) {
    return to.path === '/dashboard' ? false : { path: '/dashboard' }
  }
})

export default router
