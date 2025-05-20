import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // 首页
    {
      path: '/',
      redirect: '/home',
    },
    {
      path: '/home',
      component: () => import('@/view/HomeView.vue'),
      children: [
        // 仪表盘
        {
          path: '/dashboard',
          component: () => import('@/view/DashboardView.vue'),
        },
        // 订单列表
        {
          path: '/list',
          component: () => import('@/view/BillListView.vue'),
        },
        {
          path: '/bill',
          component: () => import('@/view/BillFormView.vue'),
        },
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
  ],
})

export default router
