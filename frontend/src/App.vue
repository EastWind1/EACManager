<template>
  <v-app>
    <!-- 全局进度条 -->
    <LoadingBar ref="loadingBarRef" />
    <!-- 全局通知 -->
    <NotifySnackbar ref="notificationRef" />
    <!-- 全局确认框 -->
    <ConfirmDialog ref="confirmRef" />
    <!-- 全局日期选择框 -->
    <DatePickerDialog ref="datePickerRef" />
    <!-- 全局批量结果显示弹窗 -->
    <BatchResultDialog ref="batchResultRef" />

    <RouterView />
  </v-app>
</template>

<script lang="ts" setup>
import { onMounted, useTemplateRef } from 'vue'
import { useUIStore } from '@/common/store/UIStore'
import LoadingBar from '@/common/component/LoadingBar.vue'
import NotifySnackbar from '@/common/component/NotifySnackbar.vue'
import ConfirmDialog from '@/common/component/ConfirmDialog.vue'
import DatePickerDialog from '@/common/component/DatePickerDialog.vue'
import BatchResultDialog from '@/common/component/BatchResultDialog.vue'

const uiStore = useUIStore()
const notificationRef = useTemplateRef('notificationRef')
const confirmRef = useTemplateRef('confirmRef')
const datePickerRef = useTemplateRef('datePickerRef')
const loadingBarRef = useTemplateRef('loadingBarRef')
const batchResultRef = useTemplateRef('batchResultRef')

onMounted(() => {
  uiStore.registerUI({
    showLoading: loadingBarRef.value!.show,
    hideLoading: loadingBarRef.value!.hide,
    notify: notificationRef.value!.notify,
    confirm: confirmRef.value!.confirm,
    selectDate: datePickerRef.value!.selectDate,
    showBatchResult: batchResultRef.value!.handleResult,
  })
})
</script>
