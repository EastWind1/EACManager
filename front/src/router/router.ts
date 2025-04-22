import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import OrderList from '@/views/OrderList.vue'
import OrderForm from '@/views/OrderForm.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 首页
    {
      path: '/',
      component: HomeView,
    },
    // 基本信息
    {
      path: '/basic',
      component: HomeView,
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
})

export default router
