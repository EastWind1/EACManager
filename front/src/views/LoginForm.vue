<template>
  <div class="text-h3 d-flex justify-center mt-4">订单管理系统</div>
  <v-container class="d-flex align-center justify-center">
    <v-card elevation="6" width="400" class="pa-6">
      <v-card-title class="text-h5 text-center mb-6">登录</v-card-title>
      <v-form v-model="valid" @submit.prevent="login">
        <v-text-field v-model="username" label="用户名" :prepend-inner-icon="mdiAccount" :rules="[required]" />
        <v-text-field
          v-model="password"
          label="密码"
          :type="showPassword ? 'text' : 'password'"
          :prepend-inner-icon="mdiLock"
          :append-inner-icon="showPassword ? mdiEye : mdiEyeOff"
          @click:append-inner="showPassword = !showPassword"
          :rules="[required]"
        />
        <v-btn type="submit" color="primary" block class="mt-4" :loading="loading">登录</v-btn>
      </v-form>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { mdiAccount, mdiLock, mdiEye, mdiEyeOff } from '@mdi/js'
import { UserApi } from '@/api/Api.ts'
import { useGlobalStore } from '@/stores/global.ts'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

const store = useGlobalStore()
const { setToken, success } = store
const {loading} = storeToRefs(store)
const router = useRouter()

// 用户名
const username = ref('')
// 密码
const password = ref('')
// 是否显示密码
const showPassword = ref(false)
// 表单验证
const valid = ref(true)
// 必填
const required = (value: string) => !!value || '不能为空'

function login() {
  if (!valid.value) {
    return
  }
  UserApi.login(username.value, password.value).then((token) => {
    setToken(token)
    success('登录成功')
    router.push('/')
  })
}
</script>
