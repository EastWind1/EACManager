<template>
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
  <v-data-table-server
    :headers="headers"
    :items="data.items"
    :items-length="data.totalCount"
    :items-per-page="data.pageSize"
    :sort-by="defaultSortBy"
    :loading="loading"
    @update:options="loadItems"
    class="mt-2"
    :search="search"
  >
    <template #[`item.number`]="{ item }">
      <RouterLink :to="`/bill/${item.id}`">{{item.number}}</RouterLink>
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
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  type ServiceBill,
  type ServiceBillQueryParam,
  ServiceBillState,
  ServiceBillType,
} from '@/model/ServiceBill.ts'
import { ServiceBillApi } from '@/api/Api.ts'
import { storeToRefs } from 'pinia'
import { useGlobalStore } from '@/stores/global.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { VDateInput } from 'vuetify/labs/components'

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
// 加载状态
const { loading } = storeToRefs(useGlobalStore())
// 表头
const headers = [
  { title: '单号', key: 'number', sortable: false },
  { title: '状态', key: 'state', sortable: false },
  { title: '类型', key: 'type', sortable: false },
  { title: '数量', key: 'count', sortable: false },
  { title: '地址', key: 'address', sortable: false },
  { title: '联系电话', key: 'contactPhoneNumber', sortable: false },
  { title: '联系人', key: 'contact', sortable: false },
  { title: '电梯品牌', key: 'elevator', sortable: false },
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
</script>
