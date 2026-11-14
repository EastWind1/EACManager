<template>
  <v-progress-linear
    absolute
    :active="loading"
    :indeterminate="loadingProgress < 0"
    :model-value="loadingProgress < 0 ? 0 : loadingProgress"
    style="z-index: 9999"
  ></v-progress-linear>
</template>

<script lang="ts" setup>
import { storeToRefs } from 'pinia'
import { useUIStore } from '@/common/store/UIStore'

const { loading, loadingProgress } = storeToRefs(useUIStore())
</script>
