<template>
  <div>
    <v-app>
      <v-app-bar>
        <v-btn-group>
          <h1 class="ml-3 mr-3">单据管理</h1>
          <v-btn prepend-icon="mdi-home-outline" to="/">
            <span>看板</span>
          </v-btn>
          <v-btn prepend-icon="mdi-home-outline" to="/basic">
            <span>信息维护</span>
          </v-btn>
          <v-btn prepend-icon="mdi-format-list-checkbox" to="/list">
            <span>列表</span>
          </v-btn>
        </v-btn-group>
        <v-spacer></v-spacer>
        <v-btn
          class="text-none ms-4 text-white"
          variant="flat"
          color="blue-darken-4"
          prepend-icon="mdi-plus"
        >
          <span>新单据</span>
        </v-btn>
        <v-btn icon="mdi-account"></v-btn>
      </v-app-bar>
      <v-main>
        <RouterView />
      </v-main>
    </v-app>
  </div>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<style scoped></style>
