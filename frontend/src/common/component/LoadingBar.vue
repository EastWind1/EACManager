<template>
  <v-progress-linear
    absolute
    v-model="loadingProgress"
    :active
    style="z-index: 9999"
    :indeterminate="loadingProgress < 0"
  ></v-progress-linear>
</template>

<script lang="ts" setup>
import { ref } from 'vue'

const loadingProgress = ref(0)
const active = ref(false)

function show(progress?: number) {
  loadingProgress.value = progress ?? -1
  active.value = true
}

function hide() {
  active.value = false
}

defineExpose({ show, hide })
</script>
