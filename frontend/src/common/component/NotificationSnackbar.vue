<template>
  <v-snackbar
    v-model="show"
    :color="color"
    :text="curText"
    :timeout="curTimeout"
    location="top right"
  >
  </v-snackbar>
</template>

<script lang="ts" setup>
import { ref } from 'vue'

const show = ref(false)
const curText = ref('')
const color = ref('info')
const curTimeout = ref(2000)

function open(
  msg: string,
  type: 'primary' | 'success' | 'info' | 'warning' | 'error',
  timeout: number,
) {
  curText.value = msg
  color.value = type
  curTimeout.value = timeout
  show.value = true
}
defineExpose({ open })
</script>
