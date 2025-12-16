<template>
  <v-container class="fill-height d-flex flex-column">
    <v-expansion-panels>
      <v-expansion-panel title="过滤条件">
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
                <v-text-field v-model="queryParam.projectName" clearable label="项目名称" />
              </v-col>
              <v-col cols="12" lg="4" md="6" sm="12" xl="3">
                <v-date-input
                  v-model="queryParam.orderDateRange"
                  clearable
                  label="创建日期"
                  multiple="range"
                  prepend-icon=""
                  prepend-inner-icon="$calendar"
                ></v-date-input>
              </v-col>
              <v-col cols="12" lg="4" md="6" sm="12" xl="3">
                <v-date-input
                  v-model="queryParam.processedDateRange"
                  clearable
                  label="完工日期"
                  multiple="range"
                  prepend-icon=""
                  prepend-inner-icon="$calendar"
                ></v-date-input>
              </v-col>
              <v-col class="text-right" cols="12">
                <v-btn @click="search = new Date().toString()">查询</v-btn>
              </v-col>
            </v-row>
          </v-container>
        </template>
      </v-expansion-panel>
    </v-expansion-panels>
    <v-toolbar class="mt-2" density="compact">
      <template #append>
        <v-btn :disabled="loading" color="primary" @click="create">新增</v-btn>
        <v-btn :disabled="loading" @click="importFile">导入</v-btn>
        <v-btn :disabled="loading" @click="exportToZip">导出</v-btn>
        <v-btn :disabled="loading" @click="process(selectedIds)">开始处理</v-btn>
        <v-btn :disabled="loading" @click="processed(selectedIds)">处理完成</v-btn>
        <v-btn :disabled="loading" @click="finish(selectedIds)">回款完成</v-btn>
        <v-btn :disabled="loading" color="red" @click="remove(selectedIds)">删除</v-btn>

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
        <RouterLink :to="`/service/${item.id}`">{{ item.number }}</RouterLink>
      </template>
      <template #[`item.state`]="{ item }">
        <v-badge
          :color="ServiceBillState[item.state].color"
          :content="ServiceBillState[item.state].title"
          inline
        ></v-badge>
      </template>
      <template #[`item.type`]="{ item }">
        <v-badge
          :color="ServiceBillType[item.type].color"
          :content="ServiceBillType[item.type].title"
          inline
        ></v-badge>
      </template>
      <template #[`item.totalAmount`]="{ item }">
        {{ item.totalAmount.toFixed(2) }}
      </template>
      <template #[`item.orderDate`]="{ item }">
        {{ item.orderDate ? dateUtil.format(item.orderDate, 'keyboardDate') : '' }}
      </template>
      <template #[`item.processedDate`]="{ item }">
        {{ item.processedDate ? dateUtil.format(item.processedDate, 'keyboardDate') : '' }}
      </template>
    </v-data-table-server>

    <v-dialog v-model="resultDialog.show">
      <v-card>
        <template #title>批量处理结果</template>
        <template #subtitle
          >成功: {{ resultDialog.successCount }} 条，失败: {{ resultDialog.failedCount }} 条
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
  type ServiceBill,
  type ServiceBillQueryParam,
  ServiceBillState,
  type ServiceBillStateValue,
  ServiceBillType,
} from '@/model/ServiceBill.ts'
import ServiceBillApi from '@/api/ServiceBillApi.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { VDateInput } from 'vuetify/labs/components'
import { useRoute, useRouter } from 'vue-router'
import { useUIStore } from '@/store/UIStore.ts'
import { useFileSelector } from '@/composable/FileSelector.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import { useBillActions } from '@/composable/BillActions.ts'
import { storeToRefs } from 'pinia'
import { useRouterStore } from '@/store/RouterStore.ts'
import { useDate } from 'vuetify/framework'

const store = useUIStore()
const { success, warning } = store
const { loading } = storeToRefs(store)
const router = useRouter()
const { setData } = useRouterStore()
const dateUtil = useDate()

// 筛选条件区域
// 查询状态下拉框
const stateOptions = Object.values(ServiceBillState)
// 查询参数类型
type QueryParam = {
  // 单据编号
  number?: string
  // 项目名称
  projectName?: string
  // 单据状态
  states: ServiceBillStateValue[]
  // 创建日期范围
  orderDateRange: Date[]
  // 处理完成日期范围
  processedDateRange: Date[]
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
const QUERY_PARAM_CACHE_KEY = 'BillListQueryParam'
const queryParam = ref<QueryParam>({
  number: '',
  projectName: '',
  states: [
    ServiceBillState.CREATED.value,
    ServiceBillState.PROCESSING.value,
    ServiceBillState.PROCESSED.value,
  ],
  orderDateRange: [],
  processedDateRange: [],
  pageSize: 20,
  pageIndex: 0,
  sorts: [
    {
      key: 'state',
      order: 'asc',
    },
    {
      key: 'orderDate',
      order: 'desc',
    },
  ],
})

// 处理路由参数
const route = useRoute()
if (route.query.hasOwnProperty('query')) {
  const data = JSON.parse(route.query['query'] as string) as QueryParam
  Object.assign(queryParam.value, data)
} else {
  // 尝试从缓存恢复
  const cache = sessionStorage.getItem(QUERY_PARAM_CACHE_KEY)
  if (cache) {
    Object.assign(queryParam.value, JSON.parse(cache))
  }
}

// 数据表格区域
// 表头
const headers = [
  { title: '单号', key: 'number', sortable: false },
  { title: '状态', key: 'state', sortable: false },
  { title: '类型', key: 'type', sortable: false },
  { title: '项目', key: 'projectName', sortable: false },
  { title: '总金额', key: 'totalAmount', sortable: false },
  { title: '创建时间', key: 'orderDate', sortable: false },
  { title: '完工时间', key: 'processedDate', sortable: false },
]

// 列表数据
const data = ref<PageResult<ServiceBill>>({
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
  const param: ServiceBillQueryParam = {}
  if (queryParam.value.number) {
    param.number = queryParam.value.number
  }
  if (queryParam.value.projectName) {
    param.projectName = queryParam.value.projectName
  }
  if (queryParam.value.states && queryParam.value.states.length) {
    param.states = queryParam.value.states
  }
  param.pageIndex = options.page - 1
  param.pageSize = options.itemsPerPage
  if (queryParam.value.orderDateRange) {
    if (queryParam.value.orderDateRange.length >= 1) {
      param.orderStartDate = queryParam.value.orderDateRange[0]
    }
    if (queryParam.value.orderDateRange.length >= 2) {
      param.orderEndDate =
        queryParam.value.orderDateRange[queryParam.value.orderDateRange.length - 1]
    }
  }
  if (queryParam.value.processedDateRange) {
    if (queryParam.value.processedDateRange.length >= 1) {
      param.processedStartDate = queryParam.value.processedDateRange[0]
    }
    if (queryParam.value.processedDateRange.length >= 2) {
      param.processedEndDate =
        queryParam.value.processedDateRange[queryParam.value.processedDateRange.length - 1]
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
  data.value = (await ServiceBillApi.getByQueryParam(param)) ?? {
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
  const blob = await ServiceBillApi.export(selectedIds.value).catch(() => undefined)

  if (blob) {
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '导出.zip'
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

// 按钮回调

/**
 * 新建
 */
function create() {
  router.push({
    path: '/service',
    query: {
      action: 'create',
    },
  })
}

/**
 * 导入
 */
async function importFile() {
  const fileList = await useFileSelector('.pdf,.jpg,.jpeg,.xls,.xlsx', false)
  const bill = await ServiceBillApi.import(fileList[0]).catch(() => undefined)
  if (!bill) {
    return
  }
  setData(bill)
  await router.push({
    path: '/service',
    query: {
      action: 'import',
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

const { process, processed, finish, remove } = useBillActions(processResult)
</script>
