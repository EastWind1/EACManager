import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/UserStore.ts'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/view/HomeView.vue'),
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
          component: () => import('@/view/DashboardView.vue'),
        },
        // 用户管理
        {
          path: '/user',
          component: () => import('@/view/UserView.vue'),
        },
        // 公司管理
        {
          path: '/company',
          component: () => import('@/view/CompanyView.vue'),
        },
        // 服务单列表
        {
          path: '/services',
          component: () => import('@/view/BillListView.vue'),
        },
        // 服务单卡片
        {
          path: '/service/:id?',
          component: () => import('@/view/BillFormView.vue'),
        },
        // 报销单列表
        {
          path: '/reimburses',
          component: () => import('@/view/ReimburseListView.vue'),
        },
        // 报销单卡片
        {
          path: '/reimburse/:id?',
          component: () => import('@/view/ReimburseFormVIew.vue'),
        },
      ],
    },
    {
      path: '/login',
      component: () => import('@/view/LoginView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('@/view/NotFoundView.vue'),
    },
  ],
})

export default router
