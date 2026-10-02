<template>
  <v-dialog :model-value="!!request" persistent width="auto">
    <v-card v-if="request">
      <template #title>
        {{ request.title }}
      </template>
      <template #text>
        {{ request.text }}
      </template>
      <template #actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text @click="settle(true)">确定</v-btn>
        <v-btn text @click="settle(false)">取消</v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import { computed, onUnmounted, ref } from 'vue'

type ConfirmInfo = {
  title: string
  text: string
  resolve: (value: boolean) => void
}

const queue = ref<ConfirmInfo[]>([])
const request = computed(() => queue.value[0])

/**
 * 确认弹窗
 * @param title 标题
 * @param text 内容
 */
function confirm(title: string, text: string): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    queue.value.push({ title, text, resolve })
  })
}

function settle(confirmed: boolean) {
  queue.value.shift()?.resolve(confirmed)
}

onUnmounted(() => {
  // 取消所有后续
  queue.value.splice(0).forEach((item) => item.resolve(false))
})

defineExpose({ confirm })
</script>
