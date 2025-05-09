<template>
  <v-data-table-server
    v-model:items-per-page="itemsPerPage"
    :headers="headers"
    :items="orderItems"
    :items-length="totalItems"
    :loading="loading"
    item-value="name"
    @update:options="loadItems"
  >
  </v-data-table-server>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const itemsPerPage = ref(5)
const totalItems = ref(0)
const loading = ref(false)
const headers = [
  { title: 'ID', sortable: false, key: 'id' },
  { title: '单号', key: 'orderNumber', sortable: false },
  { title: '状态', key: 'state', sortable: false },
  { title: '类型', key: 'kind', sortable: false },
  { title: '数量', key: 'count', sortable: false },
  { title: '地址', key: 'address', sortable: false },
  { title: '联系电话', key: 'contactPhoneNumber', sortable: false },
  { title: '联系人', key: 'contact', sortable: false },
  { title: '电梯品牌', key: 'elevator', sortable: false },
]

const orderItems = ref([])
function loadItems() {}
</script>
