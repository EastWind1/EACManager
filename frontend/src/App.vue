<template>
  <v-app>
    <!-- 全局进度条 -->
    <v-progress-linear
      :active="loading"
      absolute
      indeterminate
      style="z-index: 9999"
    ></v-progress-linear>
    <!-- 全局通知 -->
    <v-snackbar
      v-model="notifyData.show"
      :color="notifyData.color"
      :text="notifyData.text"
      :timeout="notifyData.timeout"
      location="top right"
    >
    </v-snackbar>
    <RouterView />
  </v-app>
</template>

<script lang="ts" setup>
// 全局进度条、通知
import { storeToRefs } from 'pinia'
import { useUIStore } from '@/common/store/UIStore.ts'

const { loading, notifyData } = storeToRefs(useUIStore())
</script>
