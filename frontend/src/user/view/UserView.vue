<!-- 用户管理 -->
<template>
  <v-container>
    <v-data-table-server
      :headers="headers"
      :items="data.items"
      :items-length="data.totalCount"
      :items-per-page="data.pageSize ? data.pageSize : 25"
      :items-per-page-options="[
      { value: 10, title: '10' },
      { value: 25, title: '25' },
      { value: 50, title: '50' },
      { value: 100, title: '100' },
    ]"
      show-select
      @update:options="loadItems"
    >
      <template #[`item.roles`]="{ item }">
        {{ roleNames(item.roles) }}
      </template>
      <template #[`item.actions`]="{ item }">
        <v-btn
          v-permission="Permission.USER_UPDATE"
          :icon="mdiPencil"
          size="small"
          variant="plain"
          @click="edit(item)"
        ></v-btn>
        <v-btn
          v-permission="Permission.USER_DISABLE"
          :icon="mdiClose"
          size="small"
          variant="plain"
          @click="disable(item)"
        ></v-btn>
      </template>
      <!-- 最后一行添加加号按钮 -->
      <template #[`body.append`]>
        <tr v-permission="Permission.USER_CREATE">
          <td :colspan="headers.length" class="align-center">
            <v-btn block variant="plain" @click="add">
              <v-icon :icon="mdiPlus"></v-icon>
            </v-btn>
          </td>
        </tr>
      </template>
    </v-data-table-server>

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
            <v-select
              v-model="dialogRoles"
              :items="roleOptions"
              chips
              closable-chips
              label="角色"
              multiple
            ></v-select>

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

            <div class="text-right">
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
import {ref} from 'vue'
import {type User} from '../model/User'
import {Permission} from '../model/Permission'
import {mdiClose, mdiPencil, mdiPlus} from '@mdi/js'
import UserApi from '../api/UserApi'
import SecurityApi from '@/permission/api/SecurityApi'
import type {Role} from '@/permission/model/Permission'
import {useUIStore} from '@/common/store/UIStore'
import Crypto from '@/common/util/Crypto'
import type {PageResult} from '@/common/model/PageResult'

// 表头
const headers = [
  {title: '用户名', key: 'username', sortable: false},
  {title: '名称', key: 'name', sortable: false},
  {title: '电话', key: 'phone', sortable: false},
  {title: '邮箱', key: 'email', sortable: false},
  {title: '角色', key: 'roles', sortable: false},
  {title: '操作', key: 'actions', sortable: false},
]

// 角色列表
const roles = ref<Role[]>([])
const roleOptions = ref<{ title: string; value: string; props: { disabled: boolean } }[]>([])

async function loadRoles() {
  roles.value = await SecurityApi.getRoles()
  roleOptions.value = roles.value.map((role) => ({
    title: role.name,
    value: role.code,
    props: {disabled: role.disabled},
  }))
}

function roleNames(userRoles?: Role[]) {
  if (!userRoles || userRoles.length === 0) {
    return '无'
  }
  return userRoles.map((role) => role.name).join('、')
}

// 新增默认值
const USER_DEFAULT = {
  username: '',
  name: '',
  phone: '',
  email: '',
}

// 弹窗中选中的角色编码
const dialogRoles = ref<string[]>(['USER'])

// 列表数据
const data = ref<PageResult<User>>({
  items: [],
  totalCount: 0,
  totalPages: 0,
  pageSize: 25,
  pageIndex: 0,
})
// 弹窗内容
const dialogData = ref<{
  show: boolean
  title: string
  valid: boolean
  user: User & { passwordAgain?: string }
}>({
  show: false,
  title: '新增',
  valid: true,
  user: {...USER_DEFAULT},
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

// 添加
function add() {
  dialogData.value.user = {...USER_DEFAULT}
  dialogRoles.value = ['USER']
  dialogData.value.title = '新增'
  dialogData.value.show = true
}

// 编辑
function edit(user: User) {
  dialogData.value.user = {...user, roles: user.roles ? [...user.roles] : []}
  dialogRoles.value = user.roles?.map((role) => role.code) ?? []
  dialogData.value.user.password = undefined
  dialogData.value.user.passwordAgain = undefined
  dialogData.value.title = '编辑'
  dialogData.value.show = true
}

// 保存用户信息
async function saveUser() {
  if (!dialogData.value.valid) {
    return
  }

  const postUser = {...dialogData.value.user}
  postUser.roles = dialogRoles.value
    .map((code) => roles.value.find((role) => role.code === code))
    .filter((role): role is Role => !!role)

  if (postUser.password) {
    postUser.password = await Crypto.SHA256(postUser.password, postUser.username)
  }
  if (postUser.id) {
    if (!postUser.password && !postUser.passwordAgain) {
      delete postUser.password
      delete postUser.passwordAgain
    }
    const index = data.value.items.findIndex((u) => u.id === postUser.id)
    data.value.items[index] = await UserApi.update(postUser)
  } else {
    data.value.items.push(await UserApi.create(postUser))
  }

  dialogData.value.show = false
}

// 禁用
async function disable(user: User) {
  const {confirm} = useUIStore()
  if (user.id && (await confirm('确认', '确定要禁用该用户吗？'))) {
    await UserApi.disable(user.username)
    const index = data.value.items.findIndex((u) => u.id === user.id)
    data.value.items.splice(index, 1)
  }
}

loadRoles()
</script>
