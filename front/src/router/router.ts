import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import OrderList from '@/views/OrderList.vue'
import OrderForm from '@/views/OrderForm.vue'
import LoginForm from '@/views/LoginForm.vue'
import { useGlobalStore } from '@/stores/global.ts'



const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 首页
    {
      path: '/',
      component: HomeView,
      children: [
        // 基本信息
        {
          path: '/basic',
          component: LoginForm,
        },
        // 订单列表
        {
          path: '/list',
          component: OrderList,
        },
        // 表单 TODO：调试用
        {
          path: '/form',
          component: OrderForm,
        },
      ],
    },
    {
      path: '/login',
      component: LoginForm,
    },
  ],
})

router.beforeEach((to) => {
  if (to.path !== '/login') {
    const {getToken} = useGlobalStore()
    if (!getToken()) {
      return '/login'
    }
  }
})

export default router
