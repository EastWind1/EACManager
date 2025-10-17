import { createApp } from 'vue'

import App from './App.vue'
import router from './router/router.ts'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { zhHans } from 'vuetify/locale'
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg'
import { createPinia } from 'pinia'
import { useUserStore } from '@/store/UserStore.ts'

const vuetify = createVuetify({
  // 中文
  locale: {
    locale: 'zhHans',
    messages: { zhHans },
  },
  // 图标
  icons: {
    aliases,
    sets: {
      mdi,
    },
  },
  // 主题
  theme: {
    defaultTheme: window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light',
  },
  // UI 默认样式
  defaults: {
    VContainer: {
      fluid: true,
    },
    VRow: {
      dense: true,
    },
    VTextField: {
      variant: 'outlined',
      density: 'compact',
    },
    VSelect: {
      variant: 'outlined',
      density: 'compact',
    },
    VTextarea: {
      variant: 'outlined',
      density: 'compact',
    },
    VNumberInput: {
      variant: 'outlined',
      density: 'compact',
      controlVariant: 'stacked',
    },
    VDateInput: {
      variant: 'outlined',
      density: 'compact',
    },
  },
})
// 导出以供非 setup 函数内使用
const pinia = createPinia()
const app = createApp(App)
app
  .use(pinia)
  .use(router)
  .use(vuetify)
  // 权限指令，控制元素渲染
  .directive('role', (el, binding) => {
    const userStore = useUserStore()
    if (binding.value && !userStore.hasRole(binding.value)) {
      el.style.display = 'none'
    } else {
      el.style.display = ''
    }
  })
  .mount('#app')

// 导出上下文以供动态创建 vnode 使用
const appContext = app._context
export { appContext, pinia }
