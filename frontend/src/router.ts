import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/user/store/UserStore.ts'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/common/view/HomeView.vue'),
      redirect: '/dashboard',
      beforeEnter: (to) => {
        const hasLogin = useUserStore().getUser()
        if (!hasLogin) {
          return {
            path: '/login',
            query: { redirect: to.fullPath },
          }
        }
        return true
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
        },
        // 公司管理
        {
          path: '/company',
          component: () => import('@/company/view/CompanyView.vue'),
        },
        // 服务单列表
        {
          path: '/services',
          component: () => import('@/service-bill/view/BillListView.vue'),
        },
        // 服务单卡片
        {
          path: '/service/:id?',
          component: () => import('@/service-bill/view/BillFormView.vue'),
        },
        // 报销单列表
        {
          path: '/reimburses',
          component: () => import('@/reimburse/view/ReimburseListView.vue'),
        },
        // 报销单卡片
        {
          path: '/reimburse/:id?',
          component: () => import('@/reimburse/view/ReimburseFormView.vue'),
        },
      ],
    },
    {
      path: '/login',
      component: () => import('@/user/view/LoginView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('@/common/view/NotFoundView.vue'),
    },
  ],
})

export default router
