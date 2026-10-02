<template>
  <v-snackbar-queue v-model="queue" location="top right" />
</template>

<script lang="ts" setup>
import { ref } from 'vue'

type NotifyType = 'primary' | 'success' | 'info' | 'warning' | 'error'
type NotifyInfo = string | { text?: string; color?: string; timeout?: number }

const queue = ref<NotifyInfo[]>([])

function notify(text: string, type: NotifyType, timeout: number) {
  queue.value.push({ text, color: type, timeout })
}

defineExpose({ notify })
</script>
