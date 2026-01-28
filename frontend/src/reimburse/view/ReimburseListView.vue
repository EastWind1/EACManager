<template>
  <v-container class="fill-height d-flex flex-column">
    <v-expansion-panels>
      <v-expansion-panel>
        <template #title>
          <v-icon :icon="mdiFilter" class="me-2"></v-icon>
          过滤条件
        </template>
        <template #text>
          <v-container>
            <v-row>
              <v-col cols="12" lg="4" md="6" sm="12" xl="3">
                <v-text-field v-model="queryParam.number" clearable label="单号" />
              </v-col>
              <v-col cols="12" lg="4" md="6" sm="12" xl="3">
                <v-select
                  v-model="queryParam.states"
                  :items="stateOptions"
                  chips
                  clearable
                  closable-chips
                  label="状态"
                  multiple
                />
              </v-col>
              <v-col cols="12" lg="4" md="6" sm="12" xl="3">
                <v-date-input
                  v-model="queryParam.reimburseDateRange"
                  clearable
                  label="报销日期"
                  multiple="range"
                  prepend-icon=""
                  prepend-inner-icon="$calendar"
                ></v-date-input>
              </v-col>
              <v-col cols="12" lg="4" md="6" sm="12" xl="3">
                <v-text-field v-model="queryParam.summary" clearable label="摘要" />
              </v-col>
              <v-col cols="12" class="text-right">
                <v-btn @click="search = new Date().toString()">
                  <v-icon :icon="mdiMagnify" class="me-2"></v-icon>
                  查询
                </v-btn>
              </v-col>
            </v-row>
          </v-container>
        </template>
      </v-expansion-panel>
    </v-expansion-panels>
    <v-toolbar class="mt-2" density="compact">
      <template #append>
        <v-btn
          :disabled="loading"
          color="primary"
          @click="create"
          v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
          >新增</v-btn
        >
        <v-btn :disabled="loading" @click="exportToZip">导出</v-btn>
        <v-btn
          :disabled="loading"
          @click="process(selectedIds)"
          v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
          >提交</v-btn
        >
        <v-btn
          :disabled="loading"
          @click="finish(selectedIds)"
          v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
          >完成</v-btn
        >
        <v-btn
          :disabled="loading"
          color="error"
          @click="remove(selectedIds)"
          v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
          >删除</v-btn
        >

        <v-spacer></v-spacer>
        <div v-if="selectedIds.length > 0" class="text-caption mr-4">
          已选中 {{ selectedIds.length }} 项
        </div>
      </template>
    </v-toolbar>
    <v-data-table-server
      v-model="selectedIds"
      :headers="headers"
      :items="data.items"
      :items-length="data.totalCount"
      :items-per-page="data.pageSize ? data.pageSize : 20"
      :items-per-page-options="[
        { value: 10, title: '10' },
        { value: 25, title: '25' },
        { value: 50, title: '50' },
        { value: 100, title: '100' },
      ]"
      :search="search"
      :sort-by="queryParam.sorts"
      class="mt-2 flex-grow-1"
      mobile-breakpoint="sm"
      show-select
      @update:options="loadItems"
    >
      <template #[`item.number`]="{ item }">
        <RouterLink :to="`/reimburse/${item.id}`">{{ item.number }}</RouterLink>
      </template>
      <template #[`item.state`]="{ item }">
        <v-chip :color="ReimburseState[item.state].color" size="small" class="text-white">
          {{ ReimburseState[item.state].title }}
        </v-chip>
      </template>
      <template #[`item.totalAmount`]="{ item }">
        <div class="text-right">{{ item.totalAmount ? item.totalAmount.toFixed(2) : '0.00' }}</div>
      </template>
      <template #[`item.reimburseDate`]="{ item }">
        {{ item.reimburseDate ? dateUtil.format(item.reimburseDate, 'keyboardDate') : '' }}
      </template>
    </v-data-table-server>

    <v-dialog v-model="resultDialog.show">
      <v-card>
        <template #title>
          <v-icon :icon="mdiInformation" class="me-2"></v-icon>
          批量处理结果
        </template>
        <template #subtitle>
          成功: {{ resultDialog.successCount }} 条，失败: {{ resultDialog.failedCount }} 条
        </template>
        <template #text>
          <v-data-table
            :headers="[
              { title: '单号', key: 'number', sortable: false },
              { title: '原因', key: 'message', sortable: false },
            ]"
            :items="resultDialog.rows"
          ></v-data-table>
        </template>
        <template #actions>
          <v-btn @click="resultDialog.show = false">关闭</v-btn>
        </template>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import {
  type Reimbursement,
  type ReimburseQueryParam,
  ReimburseState,
  type ReimburseStateValue,
} from '../model/Reimbursement.ts'
import ReimburseApi from '../api/ReimburseApi.ts'
import type { PageResult } from '@/common/model/PageResult.ts'
import { VDateInput } from 'vuetify/labs/components'
import { useRouter } from 'vue-router'
import { useUIStore } from '@/common/store/UIStore.ts'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'
import { storeToRefs } from 'pinia'
import { useReimburseActions } from '../composable/ReimburseActions.ts'
import { AuthorityRole } from '@/user/model/User.ts'
import { mdiFilter, mdiMagnify, mdiInformation } from '@mdi/js'
import { useDate, useHotkey } from 'vuetify/framework'

const store = useUIStore()
const { success, warning } = store
const { loading } = storeToRefs(store)
const router = useRouter()
const dateUtil = useDate()

// 筛选条件区域
// 查询状态下拉框
const stateOptions = Object.values(ReimburseState)
// 查询参数类型
type QueryParam = {
  // 单据编号
  number?: string
  // 单据状态
  states: ReimburseStateValue[]
  // 报销日期范围
  reimburseDateRange: Date[]
  // 摘要
  summary?: string
  // 每页大小
  pageSize: number
  // 页索引
  pageIndex: number
  // 排序规则
  sorts: {
    key: string
    order?: boolean | 'asc' | 'desc'
  }[]
}
// 查询参数
const QUERY_PARAM_CACHE_KEY = 'ReimburseListQueryParam'
const queryParam = ref<QueryParam>({
  number: '',
  states: [ReimburseState.CREATED.value, ReimburseState.PROCESSING.value],
  reimburseDateRange: [],
  pageSize: 20,
  pageIndex: 0,
  sorts: [
    {
      key: 'state',
      order: 'asc',
    },
    {
      key: 'reimburseDate',
      order: 'desc',
    },
  ],
})

// 尝试从缓存恢复查询条件
const cache = sessionStorage.getItem(QUERY_PARAM_CACHE_KEY)
if (cache) {
  Object.assign(queryParam.value, JSON.parse(cache))
}
// 搜索快捷键
useHotkey('enter', () => (search.value = new Date().toString()))
// 数据表格区域
// 表头
const headers = [
  { title: '单号', key: 'number', sortable: false },
  { title: '状态', key: 'state', sortable: false },
  { title: '摘要', key: 'summary', sortable: false },
  { title: '总金额', key: 'totalAmount', sortable: false },
  { title: '报销日期', key: 'reimburseDate', sortable: false },
]

// 列表数据
const data = ref<PageResult<Reimbursement>>({
  items: [],
  totalCount: 0,
  totalPages: 0,
  pageSize: 20,
  pageIndex: 0,
})

// 触发数据搜索
const search = ref('')
// 选择的项 id
const selectedIds = ref<number[]>([])

/**
 * 加载数据
 * @param options 参数
 */
async function loadItems(options: {
  page: number
  itemsPerPage: number
  sortBy: { key: string; order: 'asc' | 'desc' | boolean }[]
}) {
  // 同步列表属性至本地查询参数
  queryParam.value.pageSize = options.itemsPerPage
  queryParam.value.pageIndex = options.page - 1
  queryParam.value.sorts = options.sortBy

  // 缓存查询参数
  sessionStorage.setItem(QUERY_PARAM_CACHE_KEY, JSON.stringify(queryParam.value))
  // 组装查询参数
  const param: ReimburseQueryParam = {}
  if (queryParam.value.number) {
    param.number = queryParam.value.number
  }
  if (queryParam.value.states && queryParam.value.states.length) {
    param.states = queryParam.value.states
  }
  param.pageIndex = options.page - 1
  param.pageSize = options.itemsPerPage
  if (queryParam.value.summary) {
    param.summary = queryParam.value.summary
  }
  if (queryParam.value.reimburseDateRange) {
    if (queryParam.value.reimburseDateRange.length >= 1) {
      param.reimburseStartDate = queryParam.value.reimburseDateRange[0]
    }
    if (queryParam.value.reimburseDateRange.length >= 2) {
      param.reimburseEndDate =
        queryParam.value.reimburseDateRange[queryParam.value.reimburseDateRange.length - 1]
    }
  }
  if (queryParam.value.sorts && queryParam.value.sorts.length) {
    param.sorts = queryParam.value.sorts.map((item) => {
      return {
        field: item.key,
        direction: item.order === true || item.order === 'asc' ? 'ASC' : 'DESC',
      }
    })
  }
  selectedIds.value = []
  data.value = (await ReimburseApi.getByQueryParam(param)) ?? {
    items: [],
    totalCount: 0,
    totalPages: 0,
    pageSize: 0,
    pageIndex: 0,
  }
}

/**
 * 导出
 */
async function exportToZip() {
  if (!selectedIds.value || selectedIds.value.length === 0) {
    warning('请选择要导出的项')
    return
  }
  const blob = await ReimburseApi.export(selectedIds.value).catch(() => undefined)

  if (blob) {
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '报销单导出.zip'
    a.click()
    window.URL.revokeObjectURL(url)
  }
}

// 结果展示弹窗
// Dialog 状态
const resultDialog = ref<{
  show: boolean
  successCount: number
  failedCount: number
  rows: {
    number: string | undefined
    message: string
  }[]
}>({
  show: false,
  successCount: 0,
  failedCount: 0,
  rows: [],
})

/**
 * 新建
 */
function create() {
  router.push({
    path: '/reimburse',
    query: {
      action: 'create',
    },
  })
}

/**
 * 将批量操作结果设置到 Dialog
 * @param result
 */
function setResultDialogData(result: ActionsResult<number, void>) {
  const rows = []
  resultDialog.value.successCount = result.successCount
  resultDialog.value.failedCount = result.failCount
  for (const res of result.results) {
    if (!res.success) {
      rows.push({
        number: data.value.items.find((item) => item.id === res.param)?.number,
        message: res.message,
      })
    }
  }
  resultDialog.value.rows = rows
}

/**
 * 处理动作结果
 */
function processResult(result: ActionsResult<number, void>) {
  if (!result.failCount) {
    success(`${result.successCount} 条单据操作成功, 0 条失败`)
  } else {
    setResultDialogData(result)
    resultDialog.value.show = true
  }
  if (result.successCount) {
    search.value = new Date().toString()
  }
}

const { process, finish, remove } = useReimburseActions(processResult)
</script>
