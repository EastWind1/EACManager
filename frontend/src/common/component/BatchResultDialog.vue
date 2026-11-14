<template>
  <v-dialog v-model="show" width="auto">
    <v-card v-if="data">
      <template #title>
        <v-icon :icon="mdiInformation" class="me-2"></v-icon>
        批量处理结果
      </template>
      <template #subtitle>
        成功: {{ data.successCount }} 条，失败: {{ data.failedCount }} 条
      </template>
      <template #text>
        <v-data-table :headers="headers" :items="data.rows"></v-data-table>
      </template>
      <template #actions>
        <v-btn @click="show = false">
          <v-icon :icon="mdiClose" class="me-2"></v-icon>
          关闭
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { mdiClose, mdiInformation } from '@mdi/js'
import { useUIStore } from '@/common/store/UIStore'

const store = useUIStore()
const { batchRequest } = storeToRefs(store)
const data = batchRequest

const show = computed({
  get: () => !!data.value,
  set: (value: boolean) => {
    if (!value) {
      store.resolveBatchResult()
    }
  },
})

const headers = [
  { title: '单号', key: 'key', sortable: false },
  { title: '原因', key: 'message', sortable: false },
]
</script>
