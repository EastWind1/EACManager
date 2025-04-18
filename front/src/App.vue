<template>
  <v-app>
    <v-app-bar>
      <h1 class="ml-3 mr-3">服务单管理</h1>
      <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
      <v-spacer></v-spacer>
      <v-btn v-if="route.path !== '/form'" color="primary" to="/form">
        <template v-slot:prepend>
          <v-icon :icon="mdiPlus"></v-icon>
        </template>
        <span>新单据</span>
      </v-btn>
      <v-avatar>
        <v-icon :icon="mdiAccount"></v-icon>
      </v-avatar>
    </v-app-bar>
    <v-main>
      <v-navigation-drawer v-model="drawer">
        <v-list density="compact" nav>
          <v-list-item to="/">
            <template v-slot:prepend>
              <v-icon :icon="mdiHome"></v-icon>
            </template>
            仪表盘
          </v-list-item>
          <v-list-item to="/basic">
            <template v-slot:prepend>
              <v-icon :icon="mdiListBox"></v-icon>
            </template>
            基本信息
          </v-list-item>
          <v-list-item to="/list">
            <template v-slot:prepend>
              <v-icon :icon="mdiMenu"></v-icon>
            </template>
            单据列表
          </v-list-item>
          <v-list-item prepend-icon="mdi-format-float-left" to="/form">
            <template v-slot:prepend>
              <v-icon :icon="mdiFormatFloatLeft"></v-icon>
            </template>
            表单
          </v-list-item>
        </v-list>
      </v-navigation-drawer>
      <RouterView />
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { RouterView, useRoute } from "vue-router";
import { ref } from "vue";
import {
  mdiHome,
  mdiPlus,
  mdiListBox,
  mdiMenu,
  mdiFormatFloatLeft,
  mdiAccount
} from "@mdi/js";

const drawer = ref(true);
const route = useRoute()

</script>

<style scoped></style>
