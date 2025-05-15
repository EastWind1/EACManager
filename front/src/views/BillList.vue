<template>
  <v-container class="fill-height d-flex flex-column" >
    <v-expansion-panels>
      <v-expansion-panel title="过滤条件">
        <template #text>
          <v-container>
            <v-row>
              <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                <v-text-field v-model="queryParam.number" label="单号" />
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
                <v-text-field v-model="queryParam.projectName" label="项目名称" />
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
                <v-btn @click="search = 'reload'">查询</v-btn>
              </v-col>
            </v-row>
          </v-container>
        </template>
      </v-expansion-panel>
    </v-expansion-panels>
    <v-toolbar density="compact" class="mt-2">
      <template #append>
        <v-btn @click="create">新增</v-btn>
        <v-btn @click="importFile">导入</v-btn>
        <v-btn>处理完成</v-btn>
        <v-btn>回款完成</v-btn>

        <!-- 可选：显示已选中项数量 -->
        <v-spacer></v-spacer>
        <!--    <div v-if="selectedItems.length > 0" class="text-caption mr-4">-->
        <!--      已选中 {{ selectedItems.length }} 项-->
        <!--    </div>-->
      </template>
    </v-toolbar>
    <v-data-table-server
      :headers="headers"
      :items="data.items"
      :items-length="data.totalCount"
      :items-per-page="data.pageSize"
      :sort-by="defaultSortBy"
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
          :color="stateMap[item.state].color"
          :content="stateMap[item.state].label"
          inline
        ></v-badge>
      </template>
      <template #[`item.type`]="{ item }">
        <v-badge
          :color="typeMap[item.type].color"
          :content="typeMap[item.type].label"
          inline
        ></v-badge>
      </template>
    </v-data-table-server>
  </v-container>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  type ServiceBill,
  type ServiceBillQueryParam,
  ServiceBillState,
  ServiceBillType,
} from '@/model/ServiceBill.ts'
import ServiceBillApi from '@/api/ServiceBillApi.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { VDateInput } from 'vuetify/labs/components'
import { useRouter } from 'vue-router'
import { useUIStore } from '@/stores/UIStore.ts'
import { useRouterStore } from '@/stores/RouterStore.ts'

// 查询参数
const queryParam = ref<ServiceBillQueryParam>({
  state: [ServiceBillState.CREATED],
  pageSize: 20,
  pageIndex: 0,
  sorts: [
    {
      field: 'createdDate',
      direction: 'DESC',
    },
  ],
})
// 创建日期范围
const createdDateRange = ref([])
watch(createdDateRange, (value) => {
  queryParam.value.createdStartDate = value[0]
  queryParam.value.createdEndDate = value[value.length - 1]
})
// 完工日期范围
const processedDateRange = ref([])
watch(processedDateRange, (value) => {
  queryParam.value.processedStartDate = value[0]
  queryParam.value.processedEndDate = value[value.length - 1]
})
// 默认排序
const defaultSortBy = queryParam.value.sorts
  ? (queryParam.value.sorts.map((sort) => ({
      key: sort.field,
      order: sort.direction === 'ASC' ? 'asc' : 'desc',
    })) as { key: string; order: 'asc' | 'desc' }[])
  : []
const store = useUIStore()
const {warning} = store
const router = useRouter()
const {setData} = useRouterStore()
// 表头
const headers = [
  { title: '单号', key: 'number', sortable: false },
  { title: '状态', key: 'state', sortable: false },
  { title: '类型', key: 'type', sortable: false },
  { title: '项目', key: 'projectName', sortable: false },
  { title: '地址', key: 'projectAddress', sortable: false },
]
// 数据
const data = ref<PageResult<ServiceBill>>({
  items: [],
  totalCount: 0,
  totalPages: 0,
  pageSize: 20,
  pageIndex: 0,
})
// 查询状态下拉框
const stateOptions = [
  { title: '新建', value: ServiceBillState.CREATED },
  { title: '处理中', value: ServiceBillState.PROCESSING },
  { title: '处理完成', value: ServiceBillState.PROCESSED },
  { title: '完成', value: ServiceBillState.FINISHED },
]
// 列表状态显示映射
const stateMap = {
  [ServiceBillState.CREATED]: { label: '新建', color: 'light-blue' },
  [ServiceBillState.PROCESSING]: { label: '处理中', color: 'amber' },
  [ServiceBillState.PROCESSED]: { label: '处理完成', color: 'light-green' },
  [ServiceBillState.FINISHED]: { label: '完成', color: 'green' },
}
// 类型映射
const typeMap = {
  [ServiceBillType.FIX]: { label: '维修', color: 'light-blue' },
  [ServiceBillType.INSTALL]: { label: '安装', color: 'light-green' },
}
// 触发数据搜索
const search = ref('')
// 选择的项 id
const selectedIds = ref<number[]>([])
/**
 * 加载数据
 * @param options 参数
 */
function loadItems(options: {
  page: number
  itemsPerPage: number
  sortBy: { key: string; order: 'asc' | 'desc' | boolean }[]
}) {
  queryParam.value.pageIndex = options.page - 1
  queryParam.value.pageSize = options.itemsPerPage
  queryParam.value.sorts = options.sortBy.map((sort) => ({
    field: sort.key,
    direction: sort.order === 'asc' ? 'ASC' : 'DESC',
  }))
  ServiceBillApi.getByQueryParam(queryParam.value).then((inData) => {
    data.value = inData
  })
}

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
function importFile() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,.jpg,.jpeg'
  input.onchange = () => {
    if (!input.files || !input.files.length) {
      return
    }
    if (input.files.length > 1) {
      warning('只能选择一个文件')
      return
    }
    const file = input.files[0]
    if (file) {
      if (file.size > 1024 * 1024 * 50) {
        warning('文件大小不能超过50M')
        return
      }
      ServiceBillApi.import(file)
        .then((bill) => {
          setData(bill)
          router.push({
            path: '/bill',
            query: {
              action: 'import'
            }
          })
        })
        .finally(() => {
          input.remove()
        })
    }
  }
  input.click()
}
</script>
