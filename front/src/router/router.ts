import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/view/HomeView.vue'
import BillListView from '@/view/BillListView.vue'
import BillFormView from '@/view/BillFormView.vue'
import LoginView from '@/view/LoginView.vue'
import { useUserStore } from '@/store/UserStore.ts'



const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 首页
    {
      path: '/',
      redirect: '/home',
    },
    {
      path: '/home',
      component: HomeView,
      children: [
        // 基本信息
        {
          path: '/basic',
          component: LoginView,
        },
        // 订单列表
        {
          path: '/list',
          component: BillListView,
        },
        {
          path: '/bill',
          component: BillFormView,
        },
        {
          path: '/bill/:id',
          component: BillFormView,
        }
      ],
    },
    {
      path: '/login',
      component: LoginView,
    },
  ],
})

router.beforeEach((to) => {
  if (to.path !== '/login') {
    const {getToken} = useUserStore()
    if (!getToken()) {
      return '/login'
    }
  }
})

export default router
