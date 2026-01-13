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
    defaultTheme: 'system'
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
