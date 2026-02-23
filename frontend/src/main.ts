import { createApp } from 'vue'

import './assets/style.css'
import App from '@/App.vue'
import router from '@/router.ts'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { zhHans } from 'vuetify/locale'
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg'
import { createPinia } from 'pinia'
import { useUserStore } from '@/user/store/UserStore.ts'

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
    defaultTheme: matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light',
  },
  // UI 默认样式
  defaults: {
    global: {
      density: 'comfortable'
    },
    VContainer: {
      fluid: true,
    },
    VRow: {
      dense: true,
    },
    VCol: {
      dense: true,
    },
    VTextField: {
      variant: 'outlined',
    },
    VSelect: {
      variant: 'outlined',
    },
    VTextarea: {
      variant: 'outlined',
    },
    VNumberInput: {
      variant: 'outlined',
      controlVariant: 'stacked',
    },
    VDateInput: {
      variant: 'outlined',
    },
  },
})
// 导出以供非 setup 函数内使用
createApp(App)
  .use(createPinia())
  .use(router)
  .use(vuetify)
  // 权限指令，控制元素渲染
  .directive('role', (el, binding) => {
    const userStore = useUserStore()
    if (!binding.value) {
      return
    }
    const args = binding.value instanceof Array ? binding.value : [binding.value]
    if (!userStore.hasAnyRole(args)) {
      el.style.display = 'none'
    } else {
      el.style.display = ''
    }
  })
  .mount('#app')
