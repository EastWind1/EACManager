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
import { computed, onUnmounted, ref, watch } from 'vue'

/**
 * 日期选择弹窗消息
 */
type DatePickerInfo = {
  title?: string
  min?: Date
  max?: Date
  resolve: (value: Date | undefined) => void
}

const queue = ref<DatePickerInfo[]>([])
const request = computed(() => queue.value[0])
const selectedDate = ref<string | number | Date | null>(null)

// 每次打开重置已选日期
watch(request, () => {
  selectedDate.value = null
})

/**
 * 选择日期
 * @param title 标题
 * @param minDate 最小日期
 * @param maxDate 最大日期
 */
function selectDate(title?: string, minDate?: Date, maxDate?: Date): Promise<Date | undefined> {
  return new Promise<Date | undefined>((resolve) => {
    queue.value.push({ title, min: minDate, max: maxDate, resolve })
  })
}

function confirm() {
  const value = selectedDate.value
  queue.value.shift()?.resolve(value ? new Date(value) : undefined)
}

function cancel() {
  queue.value.shift()?.resolve(undefined)
}

onUnmounted(() => {
  // 取消所有后续
  queue.value.splice(0).forEach((item) => item.resolve(undefined))
})

defineExpose({ selectDate })
</script>
