import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/UserStore.ts'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/view/HomeView.vue'),
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
        {
          path: '/bill/:id',
          component: () => import('@/view/BillFormView.vue'),
        },
        // 服务按列表
        {
          path: '/list',
          component: () => import('@/view/BillListView.vue'),
        },
        // 服务单卡片，新增用
        {
          path: '/bill',
          component: () => import('@/view/BillFormView.vue'),
        },
        // 服务单卡片，非新增用
        {
          path: '/bill/:id',
          component: () => import('@/view/BillFormView.vue'),
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
