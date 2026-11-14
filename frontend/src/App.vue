<template>
  <v-app>
    <!-- 全局进度条 -->
    <LoadingBar />
    <!-- 全局通知 -->
    <NotifySnackbar />
    <!-- 全局确认框 -->
    <ConfirmDialog />
    <!-- 全局日期选择框 -->
    <DatePickerDialog />
    <!-- 全局批量结果显示弹窗 -->
    <BatchResultDialog />

    <RouterView />
  </v-app>
</template>

<script lang="ts" setup>
import { onUnmounted } from 'vue'
import { useUIStore } from '@/common/store/UIStore'
import LoadingBar from '@/common/component/LoadingBar.vue'
import NotifySnackbar from '@/common/component/NotifySnackbar.vue'
import ConfirmDialog from '@/common/component/ConfirmDialog.vue'
import DatePickerDialog from '@/common/component/DatePickerDialog.vue'
import BatchResultDialog from '@/common/component/BatchResultDialog.vue'

const uiStore = useUIStore()

onUnmounted(() => {
  uiStore.cancelAll()
})
</script>
