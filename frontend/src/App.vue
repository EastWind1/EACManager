<template>
  <!-- 全局进度条 -->
  <v-progress-linear indeterminate absolute :active="loading" style="z-index: 9999"></v-progress-linear>
  <!-- 全局通知 -->
  <v-snackbar :color="notify.color" :text="notify.text" :timeout="notify.timeout" v-model="notify.show"
    location="top right">
  </v-snackbar>
  <!-- 全局确认框 -->
  <v-dialog v-model="dialogData.show" width="auto" persistent>
    <v-card>
      <template #title>
        {{dialogData.title}}
      </template>
      <template #text>
        {{dialogData.text}}
      </template>
      <template #actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text @click="dialogData.confirm">确定</v-btn>
        <v-btn text @click="dialogData.cancel">取消</v-btn>
      </template>
    </v-card>
  </v-dialog>
  <RouterView />
</template>

<script setup lang="ts">
// 全局进度条、通知
import { storeToRefs } from 'pinia'
import { useUIStore } from '@/store/UIStore.ts'

const { loading, notify, dialogData } = storeToRefs(useUIStore())
</script>
