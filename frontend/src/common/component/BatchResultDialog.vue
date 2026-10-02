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
import { ref, watch } from 'vue'
import { mdiClose, mdiInformation } from '@mdi/js'
import { useUIStore } from '@/common/store/UIStore'
import type { ActionsResult } from '@/common/model/ActionsResult'

type BatchResult = {
  successCount: number
  failedCount: number
  rows: {
    key: string
    message: string
  }[]
}

const { success, warning } = useUIStore()

const data = ref<BatchResult | undefined>(undefined)
const show = ref(false)
const resolvers: (() => void)[] = []
const headers = [
  { title: '单号', key: 'key', sortable: false },
  { title: '原因', key: 'message', sortable: false },
]

// 关闭弹窗时兑现等待，保证执行动作后的刷新不会被卡住
watch(show, (value) => {
  if (!value) {
    resolvers.splice(0).forEach((resolve) => resolve())
  }
})

/**
 * 处理动作结果：返回结果 ≤ 1 条只提示，多条存在失败时弹窗
 *
 * @param result 结果
 * @param getKeyFn 获取 key 回调
 */
function handleResult(
  result: ActionsResult<number, void>,
  getKeyFn: (id: number) => string = (id) => String(id),
): Promise<void> {
  if (result.results.length <= 1) {
    // 单条：只提示
    const item = result.results[0]
    if (item) {
      if (item.success) {
        success('操作成功')
      } else {
        warning(`操作失败：${item.message}`)
      }
    }
  } else if (result.failCount) {
    // 多条且有失败：弹窗
    data.value = {
      successCount: result.successCount,
      failedCount: result.failCount,
      rows: result.results
        .filter((item) => !item.success)
        .map((item) => ({
          key: getKeyFn(item.param),
          message: item.message,
        })),
    }
    show.value = true
    return new Promise<void>((resolve) => {
      resolvers.push(resolve)
    })
  } else {
    success(`${result.successCount} 条单据操作成功, 0 条失败`)
  }
  return Promise.resolve()
}

defineExpose({ handleResult })
</script>
