<template>
  <v-card>
    <template #text>
      <v-container>
        <v-row>
          <v-col cols="12" sm="4">
            <v-text-field v-model="queryParam.number" label="单号" />
          </v-col>
          <v-col cols="12" sm="4">
            <v-select v-model="queryParam.state" :items="stateOptions" item-title="title" item-value="value" label="状态" />
          </v-col>
          <v-col cols="12" sm="4">
            <v-select v-model="query.type" :items="typeOptions" item-title="title" item-value="value" label="类型" />
          </v-col>
          <v-col cols="12" class="text-right">
            <v-btn @click="searchBills">查询</v-btn>
          </v-col>
        </v-row>
      </v-container>
    </template>
  </v-card>
  <v-data-table-server
    :headers="headers"
    :items="data.items"
    :items-length="data.totalCount"
    :items-per-page="data.pageSize"
    :sort-by="defaultSortBy"
    :loading="loading"
    @update:options="loadItems"
  >
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
import { ref } from 'vue'
import { type ServiceBill, type ServiceBillQueryParam, ServiceBillState, ServiceBillType } from '@/model/ServiceBill.ts'
import { ServiceBillApi } from '@/api/Api.ts'
import { storeToRefs } from 'pinia'
import { useGlobalStore } from '@/stores/global.ts'
import type { PageResult } from '@/model/PageResult.ts'

// 查询参数
const queryParam = ref<ServiceBillQueryParam>({
  state: ServiceBillState.CREATED,
  pageSize: 20,
  pageIndex: 0,
  sorts: [
    {
      field: 'createDate',
      direction: 'DESC',
    },
  ],
})
// 默认排序
const defaultSortBy = queryParam.value.sorts? queryParam.value.sorts.map((sort) => ({
  key: sort.field,
  order: sort.direction === 'ASC' ? 'asc' : 'desc',
})) as {  key: string; order: 'asc' | 'desc'}[] : [];
// 加载状态
const {loading} = storeToRefs(useGlobalStore())
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
// 状态映射
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
  ServiceBillApi.getByQueryParam(queryParam.value).then(inData => {
    data.value = inData
  })
}


</script>
