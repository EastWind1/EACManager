<!-- 用户管理 -->
<template>
  <v-container>
    <v-data-table
      :headers="headers"
      :items="data.items"
      :items-length="data.totalCount"
      :items-per-page="data.pageSize ? data.pageSize : 20"
      class="mt-2 flex-grow-1"
      mobile-breakpoint="sm"
      show-select
      @update:options="loadItems"
    >
      <template #[`item.authority`]="{ item }">
        {{ AuthorityRole[item.authority].title }}
      </template>
      <template #[`item.actions`]="{ item }">
        <template
          v-if="curUser?.authority === AuthorityRole.ROLE_ADMIN.value || curUser?.id === item.id"
        >
          <v-btn :icon="mdiPencil" @click="edit(item)" size="small" variant="plain"></v-btn>
          <v-btn :icon="mdiClose" @click="disable(item)" size="small" variant="plain"></v-btn>
        </template>
      </template>
      <!-- 最后一行添加加号按钮 -->
      <template #[`body.append`] v-if="AuthorityRole.ROLE_ADMIN.value">
        <tr>
          <td :colspan="headers.length" class="align-center">
            <v-btn block variant="plain" @click="add">
              <v-icon :icon="mdiPlus"></v-icon>
            </v-btn>
          </td>
        </tr>
      </template>
    </v-data-table>

    <!-- 编辑用户弹窗 -->
    <v-dialog v-model="dialogData.show" max-width="500">
      <v-card v-if="dialogData.user">
        <template #title>{{ dialogData.title }}</template>
        <template #text>
          <v-form v-model="dialogData.valid">
            <v-label>基本信息</v-label>
            <v-divider class="pb-4"></v-divider>

            <v-text-field v-model="dialogData.user.name" label="名称"></v-text-field>
            <v-text-field v-model="dialogData.user.phone" label="电话"></v-text-field>
            <v-text-field v-model="dialogData.user.email" label="邮箱"></v-text-field>
            <v-select v-model="dialogData.user.authority" :items="options" label="角色"></v-select>

            <v-label>登录信息</v-label>
            <v-divider class="pb-4"></v-divider>

            <v-text-field
              v-model="dialogData.user.username"
              :disabled="!!dialogData.user.id"
              :rules="[required]"
              label="用户名"
            ></v-text-field>
            <v-text-field
              v-model="dialogData.user.password"
              :rules="[requiredNew]"
              label="密码"
              type="password"
            ></v-text-field>
            <v-text-field
              v-model="dialogData.user.passwordAgain"
              :rules="[passwordAgainEqual]"
              label="再次输入密码"
              type="password"
            >
            </v-text-field>

            <div class="text-right mt-4">
              <v-btn color="primary" @click="saveUser">保存</v-btn>
              <v-btn @click="dialogData.show = false">取消</v-btn>
            </div>
          </v-form>
        </template>
      </v-card>
    </v-dialog>
  </v-container>
</template>
<script lang="ts" setup>
import { ref } from 'vue'
import { AuthorityRole, type User } from '../model/User.ts'
import { mdiClose, mdiPencil, mdiPlus } from '@mdi/js'
import UserApi from '../api/UserApi.ts'
import { useUIStore } from '@/common/store/UIStore.ts'
import { useUserStore } from '../store/UserStore.ts'
import Crypto from '@/common/util/Crypto.ts'
import type { PageResult } from '@/common/model/PageResult.ts'

const curUser = useUserStore().getUser()
// 表头
const headers = [
  { title: '用户名', key: 'username', sortable: false },
  { title: '名称', key: 'name', sortable: false },
  { title: '电话', key: 'phone', sortable: false },
  { title: '邮箱', key: 'email', sortable: false },
  { title: '角色', key: 'authority', sortable: false },
  { title: '操作', key: 'actions', sortable: false },
]
// 列表数据
const data = ref<PageResult<User>>({
  items: [],
  totalCount: 0,
  totalPages: 0,
  pageSize: 0,
  pageIndex: 0,
})
// 映射选项，禁用管理员选项
const options = Object.values(AuthorityRole).map((item) => ({
  title: item.title,
  value: item.value,
  props: {
    disabled: item.value === AuthorityRole.ROLE_ADMIN.value,
  },
}))

// 新增默认值
const USER_DEFAULT = {
  username: '',
  name: '',
  email: '',
  authority: AuthorityRole.ROLE_USER.value,
}
// 弹窗内容
const dialogData = ref<{
  show: boolean
  title: string
  valid: boolean
  user: User & { passwordAgain?: string }
}>({
  // 是否显示
  show: false,
  // 标题
  title: '新增',
  // 表单是否合法
  valid: true,
  // 表单当前用户
  user: { ...USER_DEFAULT },
})
// 必填
const required = (v: string) => !!v || '必填'
// 新增必填
const requiredNew = (v: string) => {
  if (dialogData.value.user.id) {
    return true
  }
  return required(v)
}
// 二次密码校验
const passwordAgainEqual = (v: string) =>
  v === dialogData.value?.user.password || '两次输入的密码不一致'

// 添加
function add() {
  dialogData.value.user = { ...USER_DEFAULT }
  dialogData.value.title = '新增'
  dialogData.value.show = true
}

// 编辑
function edit(user: User) {
  dialogData.value.user = { ...user }
  dialogData.value.user.password = undefined
  dialogData.value.user.passwordAgain = undefined
  dialogData.value.title = '编辑'
  dialogData.value.show = true
}

// 禁用
async function disable(user: User) {
  const { confirm } = useUIStore()
  if (user.id && (await confirm('确认', '确定要禁用该用户吗？'))) {
    await UserApi.disable(user.username)
    const index = data.value.items.findIndex((u) => u.id === user.id)
    data.value.items.splice(index, 1)
  }
}

// 保存用户信息
async function saveUser() {
  if (!dialogData.value.valid) {
    return
  }
  // 上传用户信息，复制一份避免修改影响界面
  const postUser = { ...dialogData.value.user }

  // 密码 hash 混淆
  if (postUser.password) {
    postUser.password = await Crypto.SHA256(postUser.password, postUser.username)
  }
  // 修改
  if (postUser.id) {
    // 当用户不更改密码时，删除密码字段
    if (!postUser.password && !postUser.passwordAgain) {
      delete postUser.password
      delete postUser.passwordAgain
    }
    const index = data.value.items.findIndex((u) => u.id === postUser.id)
    data.value.items[index] = await UserApi.update(postUser)
  } else {
    // 创建
    data.value.items.push(await UserApi.create(postUser))
  }

  dialogData.value.show = false
}

// 查询
async function loadItems(options: {
  page: number
  itemsPerPage: number
  sortBy: { key: string; order: 'asc' | 'desc' | boolean }[]
}) {
  data.value = await UserApi.getAll({
    pageIndex: options.page - 1,
    pageSize: options.itemsPerPage,
  })
}
</script>
