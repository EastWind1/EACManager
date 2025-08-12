<template>
  <v-container>
    <!-- 条数大于 10 时才显示分页 -->
    <v-data-table
      :headers="detailHeaders"
      :hide-default-footer="reimbursement!.details.length <= 10"
      :items="reimbursement!.details"
      item-key="id"
    >
      <!-- 最后一列显示操作按钮 -->
      <!-- 使用字符串表示插槽名称，防止 ESLint 报错 -->
      <template v-if="!readonly" #[`item.actions`]="{ item }">
        <div class="d-flex ga-3">
          <v-icon
            :disabled="readonly"
            :icon="mdiPencil"
            size="small"
            @click="editDetail(item)"
          ></v-icon>
          <v-icon
            :disabled="readonly"
            :icon="mdiDelete"
            size="small"
            @click="deleteDetail(item)"
          ></v-icon>
        </div>
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
            <v-col cols="12" lg="6" md="6" sm="12">
              <v-text-field
                v-model="dialogData.name"
                :rules="[requiredRule]"
                label="项目名称"
              ></v-text-field>
            </v-col>
            <v-col cols="12" lg="6" md="6" sm="12">
              <v-number-input
                v-model="dialogData.amount"
                :precision="2"
                label="金额"
                prefix="￥"
              ></v-number-input>
            </v-col>
          </v-row>
        </template>
        <template #actions>
          <v-btn text="保存" @click="saveDialog"></v-btn>
        </template>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script lang="ts" setup>
import { mdiDelete, mdiPencil, mdiPlus } from '@mdi/js'
import { type Reimbursement, type ReimburseDetail } from '@/model/Reimbursement.ts'
import { ref, toRefs } from 'vue'

// 表单标题
const detailHeaders = [
  { title: '项目名称', key: 'name' },
  { title: '金额', key: 'amount' },
  { title: '操作', key: 'actions', sortable: false }
]
// 当前订单数据
const reimbursement = defineModel<Reimbursement>()
// 是否可编辑
const props = defineProps<{
  readonly: boolean
}>()
const { readonly } = toRefs(props)

// 是否显示模态框
const showDialog = ref(false)

// 模态框默认值
const DEFAULT_VALUE = {
  name: '',
  amount: 0,
}
// 模态框当前数据
const dialogData = ref<ReimburseDetail>({ ...DEFAULT_VALUE })
// 必填验证
const requiredRule = (v: unknown) => !!v || '此项为必填项'
// 当前编辑项，用于 save 时判断是否是新增项
let curEditItem: ReimburseDetail | undefined = undefined

// 添加明细
function addDetail() {
  curEditItem = undefined
  dialogData.value = { ...DEFAULT_VALUE }
  showDialog.value = true
}

// 编辑明细
function editDetail(item: ReimburseDetail) {
  curEditItem = item
  dialogData.value = { ...item }
  showDialog.value = true
}

// 重新计算总金额
function calTotalAmount() {
  let totalAmount = 0
  reimbursement.value!.details.forEach((detail) => {
    if (detail.amount) {
      totalAmount += detail.amount
    }
  })
  reimbursement.value!.totalAmount = totalAmount
}

// 删除明细
function deleteDetail(item: ReimburseDetail) {
  reimbursement.value?.details.splice(
    reimbursement.value.details.findIndex((i) => i === item),
    1,
  )
  calTotalAmount()
}

// 保存
function saveDialog() {
  // 单独处理新增
  if (!curEditItem) {
    reimbursement.value!.details.push({
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
