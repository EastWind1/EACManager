<!-- 订单明细 -->
<template>
  <!-- 条数大于 10 时才显示分页 -->
  <v-data-table
    :headers="detailHeaders"
    :items="serviceBill!.details"
    item-key="id"
    :hide-default-footer="serviceBill!.details.length <= 10"
  >
    <!-- 最后一列显示操作按钮 -->
    <!-- 使用字符串表示插槽名称，防止 ESLint 报错 -->
    <template #[`item.actions`]="{ item }" v-if="!readonly">
      <div class="d-flex ga-3">
        <v-icon :icon="mdiPencil" size="small" @click="editDetail(item)"></v-icon>
        <v-icon :icon="mdiDelete" size="small" @click="deleteDetail(item)"></v-icon>
      </div>
    </template>

    <!-- 最后一行添加加号按钮 -->
    <template #[`body.append`] v-if="!readonly">
      <tr>
        <td :colspan="detailHeaders.length" class="align-center">
          <v-btn block @click="addDetail" variant="plain">
            <v-icon :icon="mdiPlus"></v-icon>
          </v-btn>
        </td>
      </tr>
    </template>
  </v-data-table>
  <!-- 模态框，用于新增或编辑时更改数据 -->
  <v-dialog v-model="showDialog" width="50rem">
    <v-card title="Dialog">
      <template #text>
        <v-row>
          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-text-field
              v-model="dialogData.device"
              label="设备类型"
              :rules="[requiredRule]"
            ></v-text-field>
          </v-col>
          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-number-input v-model="dialogData.unitPrice" label="单价"></v-number-input>
          </v-col>
          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-number-input v-model="dialogData.quantity" label="数量"></v-number-input>
          </v-col>

          <v-col cols="12" sm="12" md="6" lg="4" xl="3">
            <v-text-field v-model="dialogData.remark" label="备注"></v-text-field>
          </v-col>
        </v-row>
      </template>
      <template #actions>
        <span
          >小计: <span class="text-red">￥ {{ dialogData.subtotal }}</span></span
        >
        <v-btn text="保存" @click="saveDialog"></v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { mdiDelete, mdiPencil, mdiPlus } from '@mdi/js'
import { type ServiceBill, type ServiceBillDetail } from '@/entity/ServiceBill.ts'
import { ref, watchEffect } from 'vue'

// 表单标题
const detailHeaders = [
  { title: '设备类型', key: 'device' },
  { title: '数量', key: 'quantity' },
  { title: '单价', key: 'unitPrice' },
  { title: '小计', key: 'subtotal' },
  { title: '备注', key: 'remark' },
  { title: '操作', key: 'actions', sortable: false },
]
// 当前订单数据
const serviceBill = defineModel<ServiceBill>()
// 是否可编辑
const { readonly = false } = defineProps<{
  readonly: boolean
}>()

// 是否显示模态框
const showDialog = ref(false)
// 是否是新增动作
const isAddAction = ref(true)
// 模态框默认值
const DEFAULT_VALUE = {
  device: '',
  quantity: 1,
  unitPrice: 0,
  subtotal: 0,
  remark: '',
}
// 模态框当前数据
const dialogData = ref<ServiceBillDetail>(DEFAULT_VALUE)
// 必填验证
const requiredRule = (v: unknown) => !!v || '此项为必填项'

// 监听模态框单价数量变化，计算小计以及总金额
watchEffect(() => {
  dialogData.value.subtotal = dialogData.value.quantity * dialogData.value.unitPrice
})

// 添加明细
function addDetail() {
  isAddAction.value = true
  dialogData.value = DEFAULT_VALUE
  showDialog.value = true
}

// 编辑明细
function editDetail(item: ServiceBillDetail) {
  isAddAction.value = false
  dialogData.value = item
  showDialog.value = true
}

// 删除明细
function deleteDetail(item: ServiceBillDetail) {
  serviceBill.value?.details.splice(
    serviceBill.value.details.findIndex((i) => i === item),
    1,
  )
}

// 保存
function saveDialog() {
  // 单独处理新增
  if (isAddAction.value) {
    serviceBill.value!.details.push({
      device: dialogData.value.device,
      quantity: dialogData.value.quantity,
      unitPrice: dialogData.value.unitPrice,
      subtotal: dialogData.value.subtotal,
      remark: dialogData.value.remark,
    })
  }

  showDialog.value = false
  // 重新计算总金额
  let totalAmount = 0

  serviceBill.value!.details.forEach((detail) => (totalAmount += detail.subtotal))
  serviceBill.value!.totalAmount = totalAmount
}
</script>

<style scoped></style>
