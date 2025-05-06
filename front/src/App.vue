<template>
  <!-- 全局进度条 -->
  <v-progress-linear
    indeterminate
    absolute
    :active="loading"
    style="z-index: 9999"
  ></v-progress-linear>
  <!-- 全局通知 -->
  <v-snackbar
    :color="notify.color"
    :text="notify.text"
    :timeout="notify.timeout"
    v-model="notify.show"
    location="top right"
  >
  </v-snackbar>
  <RouterView />
</template>

<script setup lang="ts">
// 全局进度条、通知
import { storeToRefs } from 'pinia'
import { useGlobalStore } from '@/stores/global.ts'

const { loading, notify } = storeToRefs(useGlobalStore())
</script>
