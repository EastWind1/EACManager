import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import OrderList from '@/views/OrderList.vue'
import OrderForm from '@/views/OrderForm.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: HomeView
    },
    {
      path: '/basic',
      component: HomeView
    },
    {
      path: '/list',
      component: OrderList
    },
    {
      path: '/form',
      component: OrderForm
    },
  ]
})

export default router
