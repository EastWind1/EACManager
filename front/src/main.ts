import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { zhHans } from 'vuetify/locale'
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg'
import DateFnsAdapter from "@date-io/date-fns"
const app = createApp(App)
const vuetify = createVuetify({
  // 中文
  locale: {
    locale: 'zhHans',
    messages: { zhHans },
  },
  date: {
    adapter: DateFnsAdapter,
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
})
app.use(vuetify)
app.use(createPinia())
app.use(router)

app.mount('#app')
