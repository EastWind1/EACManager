<!-- 订单明细 -->
<template>
  <v-container>
    <!-- 条数大于 10 时才显示分页 -->
    <v-data-table
      :headers="detailHeaders"
      :hide-default-footer="serviceBill!.details.length <= 10"
      :items="serviceBill!.details"
      item-key="id"
    >
      <!-- 最后一列显示操作按钮 -->
      <!-- 使用字符串表示插槽名称，防止 ESLint 报错 -->
      <template #[`item.actions`]="{ item }">
        <v-btn :icon="mdiPencil" size="small" @click="editDetail(item)" variant="plain"></v-btn>
        <v-btn :icon="mdiDelete" size="small" @click="deleteDetail(item)" variant="plain"></v-btn>
      </template>

      <!-- 最后一行添加加号按钮 -->
      <template v-if="!readonly" #[`body.append`]>
        <tr>
          <td :colspan="detailHeaders.length" class="align-center">
            <v-btn :disabled="readonly" block variant="plain" @click="addDetail">
              <v-icon :icon="mdiPlus"></v-icon>
            </v-btn>
          </td>
        </tr>
      </template>
    </v-data-table>
    <!-- 模态框，用于新增或编辑时更改数据 -->
    <v-dialog v-model="showDialog" width="50rem">
      <v-card title="明细">
        <template #text>
          <v-row>
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field
                v-model="dialogData.device"
                :rules="[requiredRule]"
                label="设备类型"
              ></v-text-field>
            </v-col>
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-number-input v-model="dialogData.unitPrice" label="单价"></v-number-input>
            </v-col>
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-number-input v-model="dialogData.quantity" label="数量"></v-number-input>
            </v-col>

            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field v-model="dialogData.remark" label="备注"></v-text-field>
            </v-col>
          </v-row>
        </template>
        <template #actions>
          <v-row justify="end">
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-number-input v-model="dialogData.subtotal" label="小计"></v-number-input>
            </v-col>
            <v-col cols="2">
              <v-btn text="保存" @click="saveDialog"></v-btn>
            </v-col>
          </v-row>
        </template>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script lang="ts" setup>
import { mdiDelete, mdiPencil, mdiPlus } from '@mdi/js'
import { type ServiceBill, type ServiceBillDetail } from '../model/ServiceBill.ts'
import { computed, ref, toRefs, watchEffect } from 'vue'

// 表单标题
const detailHeaders = computed(() => {
  const base: { title: string; key: string; sortable?: boolean }[] = [
    { title: '设备类型', key: 'device' },
    { title: '数量', key: 'quantity' },
    { title: '单价', key: 'unitPrice' },
    { title: '小计', key: 'subtotal' },
    { title: '备注', key: 'remark' },
  ]
  if (!readonly.value) {
    base.push({ title: '操作', key: 'actions', sortable: false })
  }
  return base
})
// 当前订单数据
const serviceBill = defineModel<ServiceBill>()
// 是否可编辑
const props = defineProps<{
  readonly: boolean
}>()
const { readonly } = toRefs(props)

// 是否显示模态框
const showDialog = ref(false)

// 模态框默认值
const DEFAULT_VALUE = {
  device: '',
  quantity: 1,
  unitPrice: 0,
  subtotal: 0,
  remark: '',
}
// 模态框当前数据
const dialogData = ref<ServiceBillDetail>({ ...DEFAULT_VALUE })
// 必填验证
const requiredRule = (v: unknown) => !!v || '此项为必填项'
// 当前编辑项，用于 save 时判断是否是新增项
let curEditItem: ServiceBillDetail | undefined = undefined
// 监听模态框单价数量变化，计算小计以及总金额
watchEffect(() => {
  dialogData.value.subtotal = dialogData.value.quantity * dialogData.value.unitPrice
})

// 添加明细
function addDetail() {
  curEditItem = undefined
  dialogData.value = { ...DEFAULT_VALUE }
  showDialog.value = true
}

// 编辑明细
function editDetail(item: ServiceBillDetail) {
  curEditItem = item
  dialogData.value = { ...item }
  showDialog.value = true
}

// 重新计算总金额
function calTotalAmount() {
  let totalAmount = 0
  serviceBill.value!.details.forEach((detail) => (totalAmount += detail.subtotal))
  serviceBill.value!.totalAmount = totalAmount
}

// 删除明细
function deleteDetail(item: ServiceBillDetail) {
  serviceBill.value?.details.splice(
    serviceBill.value.details.findIndex((i) => i === item),
    1,
  )
  calTotalAmount()
}

// 保存
function saveDialog() {
  // 单独处理新增
  if (!curEditItem) {
    serviceBill.value!.details.push({
      ...dialogData.value,
    })
  } else {
    Object.assign(curEditItem, dialogData.value)
  }

  showDialog.value = false

  calTotalAmount()
}
</script>

<style scoped></style>
