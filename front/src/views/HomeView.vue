<!-- 首页 -->
<template>
  <v-app>
    <!-- 标题栏 -->
    <v-app-bar>
      <!-- 标题 -->
      <h1 class="ml-3 mr-3">服务单管理</h1>
      <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
      <v-spacer></v-spacer>
      <!-- 右侧新增按钮 -->
      <v-btn v-if="route.path !== '/bill'" color="primary" to="/bill">
        <template v-slot:prepend>
          <v-icon :icon="mdiPlus"></v-icon>
        </template>
        <span>新单据</span>
      </v-btn>
      <!-- 登录用户图标 -->
      <v-avatar>
        <v-icon :icon="mdiAccount"></v-icon>
      </v-avatar>
    </v-app-bar>
    <v-main>
      <!-- 左侧导航栏 -->
      <v-navigation-drawer v-model="drawer">
        <v-list density="compact" nav>
          <!-- 仪表盘 -->
          <v-list-item to="/">
            <template v-slot:prepend>
              <v-icon :icon="mdiHome"></v-icon>
            </template>
            仪表盘
          </v-list-item>
          <!-- 基本信息维护 -->
          <v-list-item to="/basic">
            <template v-slot:prepend>
              <v-icon :icon="mdiListBox"></v-icon>
            </template>
            基本信息
          </v-list-item>
          <!-- 单据列表 -->
          <v-list-item to="/list">
            <template v-slot:prepend>
              <v-icon :icon="mdiMenu"></v-icon>
            </template>
            单据列表
          </v-list-item>
          <!-- 表单  TODO: 测试用 -->
          <v-list-item to="/bill">
            <template v-slot:prepend>
              <v-icon :icon="mdiFormatFloatLeft"></v-icon>
            </template>
            表单
          </v-list-item>
          <!-- 文件处理  TODO: 测试用 -->
          <v-list-item to="/file">
            <template v-slot:prepend>
              <v-icon :icon="mdiFormatFloatLeft"></v-icon>
            </template>
            文件处理
          </v-list-item>
        </v-list>
      </v-navigation-drawer>
      <!-- 右侧内容区域 -->
      <RouterView />
    </v-main>

  </v-app>
</template>

<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import { ref } from 'vue'
import { mdiHome, mdiPlus, mdiListBox, mdiMenu, mdiFormatFloatLeft, mdiAccount } from '@mdi/js'

// 左侧抽屉是否显示
const drawer = ref(true)
// 当前路由
const route = useRoute()

</script>

<style scoped></style>
