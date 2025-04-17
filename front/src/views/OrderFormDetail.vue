<template>
  <v-data-table
    :headers="detailHeaders"
    :items="details"
    item-key="id"
    :hide-default-footer="details!.length <= 10"
  >

    <template #[`item.actions`]="{ item }">
      <div class="d-flex ga-2 justify-end">
        <v-icon :icon="mdiPencil" size="small"></v-icon>
        <v-icon :icon="mdiDelete" size="small" @click="deleteDetail(item)"></v-icon>
      </div>
    </template>

    <!-- 添加加号按钮 -->
    <template #[`body.append`]>
      <tr>
        <td :colspan="detailHeaders.length" style="text-align: center;">
          <v-btn block @click="addDetail" variant="plain">
            <v-icon :icon="mdiPlus"></v-icon>
          </v-btn>
        </td>
      </tr>
    </template>
  </v-data-table>
</template>

<script setup lang="ts">

import { mdiDelete, mdiPencil, mdiPlus } from "@mdi/js";
import type { ServiceBillDetail } from "@/entity/ServiceBill.ts";
// 表单标题
const detailHeaders = [
  { title: "设备类型", key: "device" },
  { title: "数量", key: "quantity" },
  { title: "单价", key: "unitPrice" },
  { title: "小计", key: "subtotal" },
  { title: "备注", key: "remark" },
  {title: "操作", key: "actions", sortable: false }
];

const details = defineModel<ServiceBillDetail[]>();

function addDetail() {
  details.value!.push({
    device: "",
    quantity: 0,
    unitPrice: 0,
    subtotal: 0,
    remark: ""
  })
}

function deleteDetail(item: ServiceBillDetail) {
  details.value!.splice(details.value!.findIndex(i => i === item), 1);
}

</script>


<style scoped>

</style>
