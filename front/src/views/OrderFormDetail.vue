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
  <v-dialog
    v-model="dialog"
  >
    <v-card title="Dialog">
      <template #text>
        <v-row>
          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-text-field
              v-model="dialogData.device"
              label="设备类型"
            ></v-text-field>
          </v-col>
          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-number-input
              v-model="dialogData.unitPrice"
              label="单价"
            ></v-number-input>
          </v-col>
          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-number-input
              v-model="dialogData.quantity"
              label="数量"
            ></v-number-input>
          </v-col>

          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-text-field
              v-model="dialogData.remark"
              label="备注"
            ></v-text-field>
          </v-col>
        </v-row>
      </template>
      <template #actions>
        <span>小计: <span class="text-red">{{dialogData.subtotal}}</span></span>
        <v-btn
          text="保存"
          @click="dialog = false"
        ></v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">

import { mdiDelete, mdiPencil, mdiPlus } from "@mdi/js";
import type { ServiceBillDetail } from "@/entity/ServiceBill.ts";
import { ref, watchEffect } from "vue";

const dialog = ref(false)

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

const dialogData = ref<ServiceBillDetail>({
  device: "",
  quantity: 1,
  unitPrice: 0,
  subtotal: 0,
  remark: ""
});

watchEffect(() => {
  dialogData.value.subtotal = dialogData.value.quantity * dialogData.value.unitPrice
})

function addDetail() {
  details.value!.push({
    device: "",
    quantity: 0,
    unitPrice: 0,
    subtotal: 0,
    remark: ""
  })

  dialog.value = true
}

function deleteDetail(item: ServiceBillDetail) {
  details.value!.splice(details.value!.findIndex(i => i === item), 1);
}


</script>


<style scoped>

</style>
