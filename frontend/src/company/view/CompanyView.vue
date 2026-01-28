<!-- 公司管理 -->
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
      <template #[`item.actions`]="{ item }">
        <template v-if="curUser?.authority === AuthorityRole.ROLE_ADMIN.value">
          <v-btn :icon="mdiPencil" @click="edit(item)" size="small" variant="plain"></v-btn>
          <v-btn :icon="mdiClose" @click="disable(item)" size="small" variant="plain"></v-btn>
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
      <v-card v-if="dialogData.company">
        <template #title>{{ dialogData.title }}</template>
        <template #text>
          <v-form v-model="dialogData.valid">
            <v-text-field
              v-model="dialogData.company.name"
              label="名称"
              :rules="[required]"
            ></v-text-field>
            <v-text-field v-model="dialogData.company.contactName" label="联系人"></v-text-field>
            <v-text-field v-model="dialogData.company.contactPhone" label="电话"></v-text-field>
            <v-text-field
              v-model="dialogData.company.email"
              label="邮箱"
              :rules="[emailValid]"
            ></v-text-field>
            <v-text-field v-model="dialogData.company.address" label="地址"></v-text-field>
            <div class="text-right mt-4">
              <v-btn color="primary" @click="save">保存</v-btn>
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
import { AuthorityRole } from '@/user/model/User.ts'
import { mdiClose, mdiPencil, mdiPlus } from '@mdi/js'
import { useUIStore } from '@/common/store/UIStore.ts'
import { useUserStore } from '@/user/store/UserStore.ts'
import type { Company } from '../model/Company.ts'
import CompanyApi from '../api/CompanyApi.ts'
import type { PageResult } from '@/common/model/PageResult.ts'

// 表头
const headers = [
  { title: '名称', key: 'name', sortable: false },
  { title: '联系人', key: 'contactName', sortable: false },
  { title: '电话', key: 'contactPhone', sortable: false },
  { title: '地址', key: 'address', sortable: false },
  { title: '操作', key: 'actions', sortable: false },
]
// 列表数据
const data = ref<PageResult<Company>>({
  pageIndex: 0,
  totalPages: 0,
  items: [],
  totalCount: 0,
  pageSize: 20,
})
// 当前登录用户，用于权限控制
const curUser = useUserStore().getUser()
// 新增默认值
const COMPANY_DEFAULT = {
  name: '',
  contactName: '',
  contactPhone: '',
  email: '',
  address: '',
}
// 弹窗内容
const dialogData = ref<{
  show: boolean
  title: string
  valid: boolean
  company: Company
}>({
  // 是否显示
  show: false,
  // 标题
  title: '新增',
  // 表单是否合法
  valid: true,
  // 表单当前公司
  company: { ...COMPANY_DEFAULT },
})
// 必填
const required = (v: string) => !!v || '必填'
// 邮箱验证
const emailValid = (v: string) =>
  !v || /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/.test(v) || '邮箱格式错误'
// 添加
function add() {
  dialogData.value.company = { ...COMPANY_DEFAULT }
  dialogData.value.title = '新增'
  dialogData.value.show = true
}

// 编辑
function edit(company: Company) {
  dialogData.value.company = { ...company }
  dialogData.value.title = '编辑'
  dialogData.value.show = true
}

// 禁用
async function disable(company: Company) {
  const { confirm } = useUIStore()
  if (company.id && (await confirm('确认', '确定要禁用该公司吗？'))) {
    await CompanyApi.disable(company.id)
    const index = data.value.items.findIndex((u) => u.id === company.id)
    data.value.items.splice(index, 1)
  }
}

// 保存公司信息
async function save() {
  if (!dialogData.value.valid) {
    return
  }
  // 上传公司信息，复制一份避免修改影响界面
  const postCompany = { ...dialogData.value.company }
  // 修改
  if (postCompany.id) {
    const index = data.value.items.findIndex((u) => u.id === postCompany.id)
    data.value.items[index] = await CompanyApi.update(postCompany)
  } else {
    // 创建
    data.value.items.push(await CompanyApi.create(postCompany))
  }

  dialogData.value.show = false
}

// 初始化
async function loadItems(options: {
  page: number
  itemsPerPage: number
  sortBy: { key: string; order: 'asc' | 'desc' | boolean }[]
}) {
  data.value = await CompanyApi.getAll({
    pageIndex: options.page - 1,
    pageSize: options.itemsPerPage,
  })
}
</script>
