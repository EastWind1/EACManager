<!-- 404 界面 -->
<template>
  <v-empty-state
    :icon="mdiMagnify"
    title="访问的页面不存在"
    action-text="返回首页"
    @click:action="toHome"
  ></v-empty-state>
</template>
<script setup lang="ts">
import {mdiMagnify} from '@mdi/js'
import {useRouter} from 'vue-router'
const router = useRouter()
function toHome() {
  router.push('/').then()
}
</script>
<style scoped>

</style>
