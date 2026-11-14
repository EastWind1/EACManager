<template>
  <v-dialog :model-value="!!request" persistent width="auto">
    <v-card v-if="request">
      <v-card-title>{{ request.title ?? '选择日期' }}</v-card-title>
      <v-card-text>
        <v-date-picker v-model="selectedDate" :max="request.max" :min="request.min" />
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text @click="confirm"> 确定</v-btn>
        <v-btn text @click="cancel"> 取消</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useUIStore } from '@/common/store/UIStore'

const store = useUIStore()
const { dateRequest: request } = storeToRefs(store)
const selectedDate = ref<string | number | Date | null>(null)

// 每次打开重置已选日期
watch(request, () => {
  selectedDate.value = null
})

function confirm() {
  const value = selectedDate.value
  store.resolveDate(value ? new Date(value) : undefined)
}

function cancel() {
  store.resolveDate(undefined)
}
</script>
