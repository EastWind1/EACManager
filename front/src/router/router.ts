import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import BillList from '@/views/BillList.vue'
import BillForm from '@/views/BillForm.vue'
import LoginForm from '@/views/LoginForm.vue'
import { useUserStore } from '@/stores/UserStore.ts'



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
          component: LoginForm,
        },
        // 订单列表
        {
          path: '/list',
          component: BillList,
        },
        {
          path: '/bill',
          component: BillForm,
        },
        {
          path: '/bill/:id',
          component: BillForm,
        }
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
    const {getToken} = useUserStore()
    if (!getToken()) {
      return '/login'
    }
  }
})

export default router
