<!-- 用户管理 -->
<template>
  <v-container>
    <v-data-table :headers="headers" :items="users" hide-default-footer items-per-page="-1">
      <template #[`item.authority`]="{ item }">
        {{ AuthorityRole[item.authority].title }}
      </template>
      <template #[`item.actions`]="{ item }">
        <template
          v-if="curUser?.authority === AuthorityRole.ROLE_ADMIN.value || curUser?.id === item.id"
        >
          <v-btn :icon="mdiPencil" @click="edit(item)"></v-btn>
          <v-btn :icon="mdiClose" @click="disable(item)"></v-btn>
        </template>
      </template>
      <!-- 最后一行添加加号按钮 -->
      <template v-if="curUser?.authority === AuthorityRole.ROLE_ADMIN.value" #[`body.append`]>
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
              :rules="[required]"
              label="用户名"
            ></v-text-field>
            <v-text-field
              v-model="dialogData.user.password"
              :rules="[required]"
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
import { AuthorityRole, type User } from '@/model/User.ts'
import { mdiClose, mdiPencil, mdiPlus } from '@mdi/js'
import UserApi from '@/api/UserApi.ts'
import { useUIStore } from '@/store/UIStore.ts'
import { useUserStore } from '@/store/UserStore.ts'

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
const users = ref<User[]>([])
// 映射选项，禁用管理员选项
const options = Object.values(AuthorityRole).map((item) => ({
  title: item.title,
  value: item.value,
  props: {
    disabled: item.value === AuthorityRole.ROLE_ADMIN.value,
  },
}))
// 当前登录用户，用于权限控制
const curUser = useUserStore().getUser()
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
  user: {
    username: '',
    name: '',
    email: '',
    authority: AuthorityRole.ROLE_USER.value,
  },
})
// 必填
const required = (v: string) => !!v || '必填'
// 二次密码校验
const passwordAgainEqual = (v: string) =>
  v === dialogData.value?.user.password || '两次输入的密码不一致'

// 添加
function add() {
  dialogData.value.user = {
    username: '',
    name: '',
    email: '',
    authority: AuthorityRole.ROLE_USER.value,
  }
  dialogData.value.show = true
}

// 编辑
function edit(user: User) {
  dialogData.value.user = { ...user }
  dialogData.value.show = true
}

// 禁用
async function disable(user: User) {
  const { confirm } = useUIStore()
  if (user.id && (await confirm('确认', '确定要禁用该用户吗？'))) {
    await UserApi.disable(user.id)
    const index = users.value.findIndex((u) => u.id === user.id)
    users.value.splice(index, 1)
  }
}

// 保存用户信息
async function saveUser() {
  if (!dialogData.value.valid) {
    return
  }
  let newUser
  if (dialogData.value.user.id) {
    newUser = await UserApi.update(dialogData.value.user)
    const index = users.value.findIndex((u) => u.id === dialogData.value.user.id)
    users.value[index] = newUser
  } else {
    newUser = await UserApi.create(dialogData.value.user)
    users.value.push(newUser)
  }

  dialogData.value.show = false
}

// 初始化
async function init() {
  users.value = await UserApi.getAll()
}

init()
</script>
