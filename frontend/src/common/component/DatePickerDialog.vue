<template>
  <v-dialog v-model="internalShow" persistent width="auto">
    <v-card>
      <v-card-title>{{ title }}</v-card-title>
      <v-card-text>
        <v-date-picker v-model="internalDate" :max="maxDate" :min="minDate" />
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text @click="handleConfirm"> 确定</v-btn>
        <v-btn text @click="handleCancel"> 取消</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
withDefaults(defineProps<{
  title: string
  minDate: unknown
  maxDate: unknown
}>(), {
  title: '请选择日期',
  minDate: undefined,
  maxDate: undefined,
})
const emit = defineEmits<{
  confirm: [value: Date],
  cancel: []
}>()

const internalShow = defineModel<boolean>()
const internalDate = defineModel<string | number | Date>('date')

function handleConfirm() {
  const dateValue = internalDate.value
  if (dateValue) {
    emit('confirm', new Date(dateValue))
    internalShow.value = false
  }
}

function handleCancel() {
  emit('cancel')
  internalShow.value = false
}
</script>
