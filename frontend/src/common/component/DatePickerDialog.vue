<template>
  <v-dialog v-model="internalShow" persistent width="auto">
    <v-card>
      <v-card-title>{{ title }}</v-card-title>
      <v-card-text>
        <v-date-picker v-model="internalDate" :min="minDate" :max="maxDate" />
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text @click="handleConfirm"> 确定 </v-btn>
        <v-btn text @click="handleCancel"> 取消 </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
interface Props {
  title: string
  minDate: unknown
  maxDate: unknown
}

interface Emits {
  (e: 'confirm', value: Date): void
  (e: 'cancel'): void
}
withDefaults(defineProps<Props>(), {
  title: '请选择日期',
  minDate: undefined,
  maxDate: undefined,
})
const emit = defineEmits<Emits>()

const internalShow = defineModel<boolean>()
const internalDate = defineModel<string | number | Date>('date')

function handleConfirm() {
  const dateValue = internalDate.value
  if (dateValue) {
    emit('confirm', new Date(dateValue))
    internalShow.value = false
  } else {
    emit('cancel')
  }
}

function handleCancel() {
  emit('cancel')
  internalShow.value = false
}
</script>
