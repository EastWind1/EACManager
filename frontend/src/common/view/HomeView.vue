<!-- 首页 -->
<template>
  <v-app>
    <!-- 标题栏 -->
    <v-app-bar height="48" scroll-behavior="hide">
      <!-- 左侧导航抽屉 -->
      <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
      <!-- 标题 -->
      <img src="/favicon.ico" alt="" style="width:50px;height: 25px">
      <h2 class="ml-3 mr-3">服务单管理</h2>
      <v-spacer></v-spacer>

      <!-- 登录用户图标 -->
      <v-menu class="ml-3">
        <template #activator="{ props }">
          <v-avatar :icon="mdiAccount" v-bind="props"></v-avatar>
        </template>

        <v-list>
          <v-list-item>
            {{ userStore.getUser()?.name }}
          </v-list-item>
          <v-list-item>
            <!-- 暗色模式切换 -->
            <v-switch v-model="isDark" color="primary" hide-details @click="isDark = !isDark">
              <template #prepend> 暗色模式 </template>
            </v-switch>
          </v-list-item>
          <v-list-item @click="logout">
            <v-list-item-title>退出登录</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-app-bar>
    <v-main>
      <!-- 左侧导航栏 -->
      <v-navigation-drawer v-model="drawer">
        <v-list :items="menuItems" density="compact" nav slim> </v-list>
      </v-navigation-drawer>
      <!-- 右侧内容区域 -->
      <RouterView />
    </v-main>
  </v-app>
</template>

<script lang="ts" setup>
import { RouterView, useRouter } from 'vue-router'
import { computed, ref } from 'vue'
import { mdiAccount, mdiCash, mdiMenu, mdiMonitorDashboard, mdiDomain } from '@mdi/js'
import { useUserStore } from '@/user/store/UserStore.ts'
import { useTheme } from 'vuetify/framework'

// 左侧抽屉是否显示
const drawer = ref(true)
// 当前路由
const router = useRouter()
// 主题切换
const theme = useTheme()
// 当前用户
const userStore = useUserStore()
const isDark = computed({
  get: () => theme.global.name.value === 'dark',
  set: (value) => {
    theme.global.name.value = value ? 'dark' : 'light'
  },
})

const menuItems = [
  {
    title: '仪表盘',
    props: {
      prependIcon: mdiMonitorDashboard,
      to: '/dashboard',
    },
  },
  {
    title: '用户管理',
    props: {
      prependIcon: mdiAccount,
      to: '/user',
    },
  },
  {
    title: '公司管理',
    props: {
      prependIcon: mdiDomain,
      to: '/company',
    },
  },
  {
    title: '服务单',
    props: {
      prependIcon: mdiMenu,
      to: '/services',
    },
  },
  {
    title: '报销单',
    props: {
      prependIcon: mdiCash,
      to: '/reimburses',
    },
  },
]

// 移除
const { removeUser } = useUserStore()

// 退出登录
function logout() {
  removeUser()
  router.push('/login')
}
</script>

<style scoped></style>
