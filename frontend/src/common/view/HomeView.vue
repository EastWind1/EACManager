<!-- 首页 -->
<template>
  <v-responsive>
    <!-- 标题栏 -->
    <v-app-bar scroll-behavior="hide">
      <template #prepend>
        <!-- 左侧导航抽屉 -->
        <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
        <!-- 标题 -->
        <img alt="" src="/favicon.ico" style="width: 50px; height: 25px" />
        <h2 class="ml-3 mr-3">服务单管理</h2>
      </template>
      <!-- 登录用户图标 -->
      <v-menu>
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
    <!-- 左侧导航栏 -->
    <v-navigation-drawer v-model="drawer">
      <v-list :items="menuItems" density="compact" nav slim> </v-list>
    </v-navigation-drawer>
    <!-- 全局确认框 -->
    <v-dialog v-model="confirmData.show" persistent width="auto">
      <v-card>
        <template #title>
          {{ confirmData.title }}
        </template>
        <template #text>
          {{ confirmData.text }}
        </template>
        <template #actions>
          <v-spacer></v-spacer>
          <v-btn color="primary" text @click="confirmData.confirm">确定</v-btn>
          <v-btn text @click="confirmData.cancel">取消</v-btn>
        </template>
      </v-card>
    </v-dialog>
    <!-- 日期选择框 -->
    <DatePickerDialog
      v-model="dataPickerData.show"
      :min-date="dataPickerData.minDate"
      :max-date="dataPickerData.maxDate"
      :title="dataPickerData.title"
      @confirm="confirmData.confirm"
      @cancel="confirmData.cancel"
    >
    </DatePickerDialog>
    <v-main>
      <RouterView />
    </v-main>
  </v-responsive>
</template>

<script lang="ts" setup>
import { RouterView, useRouter } from 'vue-router'
import { computed, ref } from 'vue'
import { mdiAccount, mdiCash, mdiDomain, mdiMenu, mdiMonitorDashboard } from '@mdi/js'
import { useUserStore } from '@/user/store/UserStore.ts'
import { useTheme } from 'vuetify/framework'
import { storeToRefs } from 'pinia'
import { useUIStore } from '@/common/store/UIStore.ts'
import DatePickerDialog from '@/common/component/DatePickerDialog.vue'

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

const { confirmData, dataPickerData } = storeToRefs(useUIStore())
</script>

<style scoped></style>
