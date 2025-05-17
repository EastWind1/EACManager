import { createApp } from 'vue'

import App from './App.vue'
import router from './router/router.ts'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { zhHans } from 'vuetify/locale'
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg'
import { createPinia } from 'pinia'
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
  // UI 默认样式
  defaults: {
    VContainer: {
      fluid: true,
    },
    VRow: {
      dense: true
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

const app = createApp(App)
  // 状态管理
  .use(createPinia())
  // 路由
  .use(router)
  .use(vuetify)
  .mount('#app')

const context = app.$
export {
  context
}
