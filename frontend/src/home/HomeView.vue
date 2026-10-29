<!-- 首页 -->
<template>
  <v-responsive>
    <!-- 标题栏 -->
    <v-app-bar scroll-behavior="hide">
      <template #prepend>
        <!-- 左侧导航抽屉 -->
        <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
        <!-- 标题 -->
        <img alt="" src="/favicon.ico" style="width: 50px; height: 25px"/>
        <h2 class="ma-auto">服务单管理</h2>
      </template>
      <!-- 暗色模式切换 -->
      <v-switch v-model="isDark" color="primary" hide-details @click="isDark = !isDark"
                size="small"
                :true-icon="mdiWeatherNight" :false-icon="mdiWeatherSunny">
      </v-switch>
      <!-- 登录用户图标 -->
      <v-menu>
        <template #activator="{ props }">
          <v-avatar :icon="mdiAccount" v-bind="props"></v-avatar>
        </template>

        <v-list>
          <v-list-item>
            <template v-slot:prepend>
              <v-icon :icon="mdiAccount"></v-icon>
            </template>
            <v-list-item-title> {{ userStore.getUser()?.name }}</v-list-item-title>
          </v-list-item>
          <v-list-item @click="logout">
            <template v-slot:prepend>
              <v-icon :icon="mdiLogout"></v-icon>
            </template>
            <v-list-item-title>退出登录</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-app-bar>
    <!-- 左侧导航栏 -->
    <v-navigation-drawer v-model="drawer">
      <v-list :items="menuItems" density="compact" nav slim></v-list>
    </v-navigation-drawer>
    <v-main class="bg-surface-light">
      <div class="main">
        <RouterView/>
      </div>
    </v-main>
  </v-responsive>
</template>

<script lang="ts" setup>
import {RouterView, useRouter} from 'vue-router'
import {computed, ref} from 'vue'
import {
  mdiAccount,
  mdiCash,
  mdiDomain,
  mdiLogout,
  mdiMenu,
  mdiMonitorDashboard,
  mdiShieldAccount,
  mdiWeatherNight,
  mdiWeatherSunny
} from '@mdi/js'
import {useUserStore} from '@/user/store/UserStore.ts'
import {useTheme} from 'vuetify/framework'
import UserApi from '@/user/api/UserApi.ts'
import {Permission as UserPermission} from '@/user/model/Permission'
import {Permission as CompanyPermission} from '@/company/model/Permission'
import {Permission as ServiceBillPermission} from '@/service-bill/model/Permission'
import {Permission as ReimbursePermission} from '@/reimburse/model/Permission'
import {Permission as ConfigPermission} from '@/permission/model/PermissionCode'

// 当前路由
const router = useRouter()
// 主题切换
const theme = useTheme()
// 当前用户
const userStore = useUserStore()
// 移除
const {removeUser} = useUserStore()

// 左侧抽屉是否显示
const drawer = ref(true)

const isDark = computed({
  get: () => theme.global.name.value === 'dark',
  set: (value) => {
    theme.global.name.value = value ? 'dark' : 'light'
  },
})

const allMenuItems: {
  title: string
  props: { prependIcon: string; to: string }
  permission?: string
}[] = [
  {
    title: '仪表盘',
    props: {
      prependIcon: mdiMonitorDashboard,
      to: '/dashboard',
    },
  },
  {
    title: '权限配置',
    props: {
      prependIcon: mdiShieldAccount,
      to: '/permission',
    },
    permission: ConfigPermission.PERMISSION_QUERY,
  },
  {
    title: '用户管理',
    props: {
      prependIcon: mdiAccount,
      to: '/user',
    },
    permission: UserPermission.USER_QUERY,
  },
  {
    title: '公司管理',
    props: {
      prependIcon: mdiDomain,
      to: '/company',
    },
    permission: CompanyPermission.COMPANY_QUERY,
  },
  {
    title: '服务单',
    props: {
      prependIcon: mdiMenu,
      to: '/services',
    },
    permission: ServiceBillPermission.SERVICE_BILL_QUERY,
  },
  {
    title: '报销单',
    props: {
      prependIcon: mdiCash,
      to: '/reimburses',
    },
    permission: ReimbursePermission.REIMBURSE_QUERY,
  },
]

// 根据动作权限过滤菜单
const menuItems = computed(() =>
  allMenuItems
    .filter((item) => !item.permission || userStore.hasPermission(item.permission))
    .map((item) => ({title: item.title, props: item.props})),
)

// 退出登录
async function logout() {
  await UserApi.logout()
  removeUser()
  router.push('/login')
}
</script>

<style scoped>
.main {
  margin: 0 auto;
  max-width: 100rem;
  min-height: 100vh;
}
</style>
