<!-- 首页 -->
<template>
  <v-app>
    <!-- 标题栏 -->
    <v-app-bar scroll-behavior="hide" height="48" >
      <!-- 标题 -->
      <h2 class="ml-3 mr-3">服务单管理</h2>
      <!-- 左侧导航抽屉 -->
      <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
      <v-spacer></v-spacer>
      <!-- 暗色模式切换 -->
      <v-switch
        v-model="isDark"
        label="暗色模式"
        color="primary"
        @click="isDark = !isDark"
        hide-details
      />
      <!-- 登录用户图标 -->
      <v-menu open-on-hover open-on-click class="ml-3">
        <template #activator="{ props }">
          <v-avatar v-bind="props" :icon="mdiAccount"> </v-avatar>
        </template>

        <v-list>
          <v-list-item @click="logout">
            <v-list-item-title>退出登录</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-app-bar>
    <v-main>
      <!-- 左侧导航栏 -->
      <v-navigation-drawer v-model="drawer">
        <v-list density="compact" nav :items="menuItems" slim>
        </v-list>
      </v-navigation-drawer>
      <!-- 右侧内容区域 -->
      <RouterView />
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import { computed, ref } from 'vue'
import { mdiMenu, mdiAccount, mdiMonitorDashboard } from '@mdi/js'
import { useUserStore } from '@/store/UserStore.ts'
import { useTheme } from 'vuetify/framework'

// 左侧抽屉是否显示
const drawer = ref(true)
// 当前路由
const router = useRouter()
// 主题切换
const theme = useTheme()
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
      to: '/user'
    },
  },
  {
    title: '单据列表',
    props: {
      prependIcon: mdiMenu,
      to: '/list'
    },
  }
]

// 移除 token
const { removeToken, removeUser } = useUserStore()

// 退出登录
function logout() {
  removeToken()
  removeUser()
  router.push('/login')
}
</script>

<style scoped></style>
