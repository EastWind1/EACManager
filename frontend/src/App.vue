<template>
  <v-app>
    <!-- 全局进度条 -->
    <v-progress-linear :active="loading" absolute class="z-9999" indeterminate></v-progress-linear>
    <!-- 全局通知 -->
    <NotificationSnackbar ref="snackbarRef" />
    <!-- 全局确认框 -->
    <ConfirmDialog ref="confirmRef" />
    <RouterView />
  </v-app>
</template>

<script lang="ts" setup>
// 全局进度条、通知
import { onMounted, useTemplateRef } from 'vue'
import { storeToRefs } from 'pinia'
import { useUIStore } from '@/common/store/UIStore'
import NotificationSnackbar from '@/common/component/NotificationSnackbar.vue'
import ConfirmDialog from '@/common/component/ConfirmDialog.vue'

const uiStore = useUIStore()
const { loading } = storeToRefs(uiStore)
const snackbarRef = useTemplateRef('snackbarRef')
const confirmRef = useTemplateRef('confirmRef')

onMounted(() => {
  uiStore.registerNotifyFn(snackbarRef.value!.open)
  uiStore.registerConfirmFn(confirmRef.value!.open)
})
</script>
