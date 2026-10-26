<!-- 首次管理员初始化 -->
<template>
  <v-container class="fill-height d-flex justify-center align-center">
    <v-card class="pa-2" elevation="4" max-width="560" rounded="lg" width="100%">
      <template #title>初始化管理员</template>
      <template #subtitle>
        系统尚未创建管理员账号，请先创建首个管理员。创建完成后即可使用该账号登录系统。
      </template>
      <template #text>
        <v-alert class="mb-4" type="info" variant="tonal">
          用户名、密码和姓名为必填项；手机号和邮箱选填。
        </v-alert>

        <v-form v-model="valid" @submit.prevent="submit">
          <v-text-field
            v-model="form.username"
            autocomplete="username"
            label="用户名"
            :rules="[usernameRule]"
            variant="outlined"
          ></v-text-field>

          <v-text-field
            v-model="form.name"
            autocomplete="name"
            label="姓名"
            :rules="[nameRule]"
            variant="outlined"
          ></v-text-field>

          <v-text-field
            v-model="form.phone"
            autocomplete="tel"
            label="手机号（选填）"
            :rules="[phoneRule]"
            variant="outlined"
          ></v-text-field>

          <v-text-field
            v-model="form.email"
            autocomplete="email"
            label="邮箱（选填）"
            :rules="[emailRule]"
            variant="outlined"
          ></v-text-field>

          <v-text-field
            v-model="form.password"
            autocomplete="new-password"
            label="密码"
            :rules="[passwordRule]"
            type="password"
            variant="outlined"
          ></v-text-field>

          <v-text-field
            v-model="form.passwordAgain"
            autocomplete="new-password"
            label="再次输入密码"
            :rules="[passwordAgainRule(form.password)]"
            type="password"
            variant="outlined"
          ></v-text-field>

          <v-btn
            block
            color="primary"
            :loading="saving"
            size="large"
            type="submit"
            variant="flat"
          >
            创建管理员
          </v-btn>
          <v-btn block class="mt-2" to="/login" variant="text">返回登录</v-btn>
        </v-form>
      </template>
    </v-card>
  </v-container>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import UserApi from '../api/UserApi'
import {
  emailRule,
  nameRule,
  passwordAgainRule,
  passwordRule,
  phoneRule,
  USER_INIT_DEFAULT,
  type UserInitForm,
  usernameRule,
} from '../model/UserInitValidation'
import { useUIStore } from '@/common/store/UIStore'

const router = useRouter()
const { success } = useUIStore()
const form = reactive<UserInitForm>({ ...USER_INIT_DEFAULT })
const valid = ref(false)
const saving = ref(false)

async function submit() {
  if (!valid.value || saving.value) {
    return
  }
  saving.value = true
  try {
    await UserApi.initialize({
      username: form.username.trim(),
      password: form.password,
      name: form.name.trim(),
      phone: form.phone.trim() || undefined,
      email: form.email.trim() || undefined,
    })
    success('管理员创建成功，请登录')
    await router.push('/login')
  } finally {
    saving.value = false
  }
}
</script>
