<!-- 首页 -->
<template>
  <v-app>
    <!-- 标题栏 -->
    <v-app-bar>
      <!-- 标题 -->
      <h1 class="ml-3 mr-3">服务单管理</h1>
      <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
      <v-spacer></v-spacer>
      <!-- 登录用户图标 -->
      <v-menu open-on-hover>
        <template #activator="{ props }">
          <v-avatar v-bind="props">
            <v-icon :icon="mdiAccount"></v-icon>
          </v-avatar>
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
        <v-list density="compact" nav>
          <!-- 仪表盘 -->
          <v-list-item to="/home" exact>
            <template #prepend>
              <v-icon :icon="mdiHome"></v-icon>
            </template>
            仪表盘
          </v-list-item>
          <!-- 基本信息维护 -->
          <v-list-item to="/basic">
            <template #prepend>
              <v-icon :icon="mdiListBox"></v-icon>
            </template>
            基本信息
          </v-list-item>
          <!-- 单据列表 -->
          <v-list-item to="/list">
            <template #prepend>
              <v-icon :icon="mdiMenu"></v-icon>
            </template>
            单据列表
          </v-list-item>
        </v-list>
      </v-navigation-drawer>
      <!-- 右侧内容区域 -->
      <RouterView />
    </v-main>

  </v-app>
</template>

<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import { ref } from 'vue'
import { mdiHome, mdiListBox, mdiMenu, mdiAccount } from '@mdi/js'
import { useUserStore } from '@/stores/UserStore.ts'

// 左侧抽屉是否显示
const drawer = ref(true)
// 当前路由
const router = useRouter()
// 移除 token
const {removeToken} = useUserStore()

// 退出登录
function logout() {
  removeToken()
  router.push('/login')
}
</script>

<style scoped></style>
