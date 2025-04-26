import { createApp } from 'vue'

import App from './App.vue'
import router from './router/router.ts'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { zhHans } from 'vuetify/locale'
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg'
import pinia from '@/stores'

createApp(App)
  // 状态管理
  .use(pinia)
  // 路由
  .use(router)
  .use(
    createVuetify({
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
      },
    }),
  )
  .mount('#app')
