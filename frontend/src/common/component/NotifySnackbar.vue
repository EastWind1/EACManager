<template>
  <v-snackbar
    v-model="show"
    :color="notification?.color"
    :timeout="notification?.timeout"
    location="top right"
  >
    {{ notification?.text }}
  </v-snackbar>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useUIStore } from '@/common/store/UIStore'

const store = useUIStore()
const { notification } = storeToRefs(store)

const show = computed({
  get: () => !!notification.value,
  set: (value: boolean) => {
    if (!value) {
      store.clearNotification()
    }
  },
})
</script>
