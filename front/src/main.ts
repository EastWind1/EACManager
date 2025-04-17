import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { zhHans } from 'vuetify/locale'
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg'

const app = createApp(App)
const vuetify = createVuetify({
  locale: {
    locale: 'zhHans',
    messages: { zhHans }
  },
  icons: {
    aliases,
    sets: {
      mdi
    }
  },
  defaults:{
    VTextField: {
      variant: "outlined",
      density: "compact"
    },
    VSelect: {
      variant: "outlined",
      density: "compact"
    },
    VTextarea: {
      variant: "outlined",
      density: "compact"
    }

  }
})
app.use(createPinia())
app.use(router)
app.use(vuetify)
app.mount('#app')
