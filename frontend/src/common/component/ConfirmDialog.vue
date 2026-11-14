<template>
  <v-dialog :model-value="!!request" persistent width="auto">
    <v-card v-if="request">
      <template #title>
        <v-icon :icon="mdiHelpCircleOutline" class="me-2"></v-icon>
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
import { storeToRefs } from 'pinia'
import { mdiHelpCircleOutline } from '@mdi/js'
import { useUIStore } from '@/common/store/UIStore'

const store = useUIStore()
const { confirmRequest: request } = storeToRefs(store)

function settle(confirmed: boolean) {
  store.resolveConfirm(confirmed)
}
</script>
