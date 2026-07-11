<template>
  <v-dialog v-model="show" persistent width="auto">
    <v-card>
      <v-card-title>{{ curTitle }}</v-card-title>
      <v-card-text>
        <v-date-picker v-model="internalDate" :max="maxDate" :min="minDate" />
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
import { ref } from 'vue'

const show = ref(false)
const curTitle = ref('选择日期')
const minDate = ref<Date | undefined>(undefined)
const maxDate = ref<Date | undefined>(undefined)
const internalDate = ref<string | number | Date | null>(null)
let resolveRef: ((value: Date | undefined) => void)

function open(title?: string, min?: Date, max?: Date): Promise<Date | undefined> {
  curTitle.value = title ?? '选择日期'
  minDate.value = min ?? undefined
  maxDate.value = max ?? undefined
  internalDate.value = null
  show.value = true
  return new Promise<Date | undefined>((resolve) => {
    resolveRef = resolve
  })
}

function confirm() {
  const dateValue = internalDate.value
  if (dateValue) {
    resolveRef(new Date(dateValue))
  } else {
    resolveRef(undefined)
  }
  show.value = false
}

function cancel() {
  resolveRef(undefined)
  show.value = false
}

defineExpose({ open })
</script>
