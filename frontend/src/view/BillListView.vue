<template>
  <v-container class="fill-height d-flex flex-column">
    <v-expansion-panels>
      <v-expansion-panel title="过滤条件">
        <template #text>
          <v-container>
            <v-row>
              <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                <v-text-field v-model="queryParam.number" clearable label="单号" />
              </v-col>
              <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                <v-select
                  v-model="queryParam.state"
                  :items="stateOptions"
                  label="状态"
                  multiple
                  chips
                  closable-chips
                  clearable
                />
              </v-col>
              <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                <v-text-field v-model="queryParam.projectName" clearable label="项目名称" />
              </v-col>
              <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                <v-date-input
                  v-model="createdDateRange"
                  multiple="range"
                  label="创建日期"
                  prepend-icon=""
                  prepend-inner-icon="$calendar"
                  clearable
                ></v-date-input>
              </v-col>
              <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                <v-date-input
                  v-model="processedDateRange"
                  multiple="range"
                  label="完工日期"
                  prepend-icon=""
                  prepend-inner-icon="$calendar"
                  clearable
                ></v-date-input>
              </v-col>
              <v-col cols="12" class="text-right">
                <v-btn @click="search = new Date().toString()">查询</v-btn>
              </v-col>
            </v-row>
          </v-container>
        </template>
      </v-expansion-panel>
    </v-expansion-panels>
    <v-toolbar density="compact" class="mt-2">
      <template #append>
        <v-btn color="primary" @click="create" :disabled="loading">新增</v-btn>
        <v-btn @click="importFile" :disabled="loading">导入</v-btn>
        <v-btn @click="exportToZip" :disabled="loading">导出</v-btn>
        <v-btn @click="process(selectedIds)" :disabled="loading">开始处理</v-btn>
        <v-btn @click="processed(selectedIds)" :disabled="loading">处理完成</v-btn>
        <v-btn @click="finish(selectedIds)" :disabled="loading">回款完成</v-btn>
        <v-btn color="red" @click="remove(selectedIds)" :disabled="loading">删除</v-btn>

        <v-spacer></v-spacer>
        <div v-if="selectedIds.length > 0" class="text-caption mr-4">
          已选中 {{ selectedIds.length }} 项
        </div>
      </template>
    </v-toolbar>
    <v-data-table-server
      :headers="headers"
      :items="data.items"
      :items-length="data.totalCount"
      :items-per-page="data.pageSize ? data.pageSize : 20"
      @update:options="loadItems"
      class="mt-2 flex-grow-1"
      :search="search"
      show-select
      v-model="selectedIds"
    >
      <template #[`item.number`]="{ item }">
        <RouterLink :to="`/bill/${item.id}`">{{ item.number }}</RouterLink>
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
      <template #[`item.orderDate`]="{ item }">
        {{ item.orderDate ? date.format(item.orderDate, 'yyyy-MM-dd') : '' }}
      </template>
      <template #[`item.processedDate`]="{ item }">
        {{ item.processedDate ? date.format(item.processedDate, 'yyyy-MM-dd') : '' }}
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

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  type ServiceBill,
  type ServiceBillQueryParam,
  ServiceBillState,
  ServiceBillType
} from '@/model/ServiceBill.ts'
import ServiceBillApi from '@/api/ServiceBillApi.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { VDateInput } from 'vuetify/labs/components'
import { useRoute, useRouter } from 'vue-router'
import { useUIStore } from '@/store/UIStore.ts'
import { useRouterStore } from '@/store/RouterStore.ts'
import { useFileSelector } from '@/composable/FileSelector.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import { useBillActions } from '@/composable/BillActions.ts'
import { storeToRefs } from 'pinia'
import * as date from 'date-fns'

const store = useUIStore()
const { success, warning } = store
const { loading } = storeToRefs(store)
const router = useRouter()
const { setData } = useRouterStore()

// 筛选条件区域
// 查询状态下拉框
const stateOptions = Object.values(ServiceBillState)
// 查询参数
const queryParam = ref<ServiceBillQueryParam>({
  state: [ServiceBillState.CREATED.value, ServiceBillState.PROCESSING.value, ServiceBillState.PROCESSED.value],
  pageSize: 20,
  pageIndex: 0,
  sorts: [
    {
      field: 'createdDate',
      direction: 'DESC'
    }
  ]
})
// 处理查询情况
const route = useRoute()
if (route.query.hasOwnProperty('query')) {
  const data = useRouterStore().getData() as ServiceBillQueryParam
  Object.assign(queryParam.value, data)
}
// 创建日期范围
const createdDateRange = ref([])
watch(createdDateRange, (value) => {
  queryParam.value.orderStartDate = value[0]
  queryParam.value.orderEndDate = value[value.length - 1]
})
// 完工日期范围
const processedDateRange = ref([])
watch(processedDateRange, (value) => {
  queryParam.value.processedStartDate = value[0]
  queryParam.value.processedEndDate = value[value.length - 1]
})

// 数据表格区域
// 表头
const headers = [
  { title: '单号', key: 'number', sortable: false },
  { title: '状态', key: 'state', sortable: false },
  { title: '类型', key: 'type', sortable: false },
  { title: '项目', key: 'projectName', sortable: false },
  { title: '地址', key: 'projectAddress', sortable: false },
  { title: '创建时间', key: 'orderDate', sortable: false },
  { title: '完工时间', key: 'processedDate', sortable: false }
]
// 默认数据
const defaultData = {
  items: [],
  totalCount: 0,
  totalPages: 0,
  pageSize: 20,
  pageIndex: 0
}
// 数据
const data = ref<PageResult<ServiceBill>>(defaultData)

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
  queryParam.value.pageIndex = options.page - 1
  queryParam.value.pageSize = options.itemsPerPage
  queryParam.value.sorts = options.sortBy.map((sort) => ({
    field: sort.key,
    direction: sort.order === 'asc' ? 'ASC' : 'DESC'
  }))

  data.value = await ServiceBillApi.getByQueryParam(queryParam.value).catch(() => defaultData)
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
  rows: []
})

// 按钮回调

/**
 * 新建
 */
function create() {
  router.push({
    path: '/bill',
    query: {
      action: 'create'
    }
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
    path: '/bill',
    query: {
      action: 'import'
    }
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
        message: res.message
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
