<!-- 订单表单 -->
<template>
  <v-container>
    <v-form ref="form" v-model="valid" :readonly="loading || !canEdit" @submit.prevent="save">
      <!-- 单据头部 -->
      <v-card>
        <template #text>
          <v-row justify="space-between">
            <!-- 左侧单据头 -->
            <v-col cols="6">
              <v-row justify="start">
                <!-- 单号 -->
                <v-col>
                  <h3>单号: {{ serviceBill.number ? serviceBill.number : ' (保存后自动生成)' }}</h3>
                </v-col>
                <!-- 单据状态 -->
                <v-col>
                  <h3>
                    状态:
                    <v-badge
                      :color="serviceBillStates[serviceBill.state].color"
                      :content="serviceBillStates[serviceBill.state].label"
                      inline
                    ></v-badge>
                  </h3>
                </v-col>
              </v-row>
            </v-col>
            <!-- 右侧按钮区域 -->
            <v-col justify-end>
              <v-row justify="end" class="ga-2">
                <!-- 导入按钮 -->
                <v-btn color="primary" @click="importFile" :loading="loading" :disabled="isEditState"> 导入</v-btn>
                <!-- 编辑按钮 -->
                <v-btn
                  color="primary"
                  v-if="serviceBill.state === ServiceBillState.CREATED"
                  :disabled="isEditState"
                  @click="isEditState = true"
                  >编辑
                </v-btn>
                <!-- 保存按钮 -->
                <v-btn :disabled="!isEditState" type="submit" :loading="loading">提交</v-btn>
              </v-row>
            </v-col>
          </v-row>
        </template>
      </v-card>
      <!-- 基本信息 -->
      <v-card class="mt-5">
        <template #title>基本信息</template>
        <template #text>
          <v-row>
            <!-- 单据类型 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-select
                v-model="serviceBill.type"
                :items="serviceBillTypes"
                item-title="label"
                item-value="value"
                label="单据类型"
              ></v-select>
            </v-col>
            <!-- 项目名称 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.projectName"
                label="项目名称"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <!-- 项目地址 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.projectAddress"
                label="项目地址"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <!-- 项目联系人 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.projectContact"
                label="项目联系人"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <!-- 项目联系人电话 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.projectContactPhone"
                label="项目联系人电话"
                :rules="[phoneRule]"
              ></v-text-field>
            </v-col>
          </v-row>
        </template>
      </v-card>
      <!-- 现场信息 -->
      <v-card class="mt-5">
        <template #title>现场信息</template>
        <template #text>
          <v-row>
            <!-- 现场联系人 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field v-model="serviceBill.onSiteContact" label="现场联系人"></v-text-field>
            </v-col>
            <!-- 现场联系人电话 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field v-model="serviceBill.onSitePhone" label="现场联系人电话"></v-text-field>
            </v-col>
            <!-- 电梯信息 -->
            <v-col cols="12">
              <v-textarea
                v-model="serviceBill.elevatorInfo"
                label="电梯信息"
                variant="outlined"
              ></v-textarea>
            </v-col>
          </v-row>
        </template>
      </v-card>
      <!-- 明细 -->
      <v-card class="mt-5">
        <template #title>
          <v-tabs v-model="tab">
            <v-tab value="details" class="v-card-title">明细</v-tab>
            <!-- 处理明细非新建状态显示 -->
            <v-tab
              value="processors"
              class="v-card-title"
              v-if="serviceBill.state != ServiceBillState.CREATED"
            >
              处理人
            </v-tab>
          </v-tabs>
        </template>
        <template #text>
          <v-tabs-window v-model="tab">
            <!-- 服务单明细 -->
            <v-tabs-window-item value="details">
              <OrderFormDetail v-model="serviceBill" :readonly="canEdit"></OrderFormDetail>
            </v-tabs-window-item>
            <!-- 处理人明细 TODO: 独立为单个组件 -->
            <v-tabs-window-item
              value="processors"
              v-if="serviceBill.state != ServiceBillState.CREATED"
            >
              <div v-for="(detail, index) in serviceBill.processDetails" :key="index">
                <v-divider></v-divider>
                <v-row>
                  <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                    <v-text-field
                      v-model="detail.processUser.name"
                      label="处理人姓名"
                      :rules="[requiredRule]"
                    ></v-text-field>
                  </v-col>
                  <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                    <v-text-field
                      v-model="detail.processCount"
                      label="处理数量"
                      :rules="[requiredRule]"
                    ></v-text-field>
                  </v-col>
                  <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                    <v-text-field
                      v-model="detail.processedAmount"
                      label="处理金额"
                      :rules="[requiredRule]"
                    ></v-text-field>
                  </v-col>
                  <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                    <v-text-field
                      v-model="detail.acceptDate"
                      label="接受时间"
                      type="date"
                      :rules="[requiredRule]"
                    ></v-text-field>
                  </v-col>
                  <v-col cols="12" sm="12" md="6" lg="4" xl="3">
                    <v-text-field
                      v-model="detail.processedDate"
                      label="处理完成时间"
                      type="date"
                      :rules="[requiredRule]"
                    ></v-text-field>
                  </v-col>
                </v-row>
              </div>
            </v-tabs-window-item>
          </v-tabs-window>
        </template>
      </v-card>
      <!-- 其他字段 -->
      <v-card class="mt-5">
        <template #title>其它信息</template>
        <template #text>
          <v-row>
            <!-- 完成时间 -->
            <!-- 处理完之后的状态显示 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-if="
                  serviceBill.state === ServiceBillState.PROCESSED ||
                  serviceBill.state === ServiceBillState.FINISHED
                "
                v-model="serviceBill.processedDate"
                label="完工时间"
                type="date"
              ></v-text-field>
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="serviceBill.remark" label="备注"></v-textarea>
            </v-col>
            <!-- 创建时间 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <label class="text-subtitle-1"
                >创建时间
                {{
                  serviceBill.createdDate ? date.format(serviceBill.createdDate, 'yyyy-MM-dd') : ''
                }}</label
              >
            </v-col>
            <!-- 最后修改时间 -->
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <label class="text-subtitle-1"
                >最后修改时间
                {{
                  serviceBill.lastModifiedDate
                    ? date.format(serviceBill.lastModifiedDate, 'yyyy-MM-dd')
                    : ''
                }}</label
              >
            </v-col>
          </v-row>
        </template>
      </v-card>
    </v-form>
    <!-- 底部空间， 用于滚动到底部是不会遮挡 -->
    <div class="bottom-empty"></div>
    <!-- 总金额显示 -->
    <v-container class="position-fixed bottom-0 bg-white d-flex justify-end ga-2">
      <span
        >总额: <span class="text-red">￥ {{ serviceBill.totalAmount }}</span></span
      >
    </v-container>
  </v-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { type ServiceBill, ServiceBillState, ServiceBillType } from '@/entity/ServiceBill.ts'
import OrderFormDetail from '@/views/OrderFormDetail.vue'
import * as date from 'date-fns'
import { ServiceBillApi } from '@/api/Api.ts'
import { useGlobalStore } from '@/stores/global.ts'
import { storeToRefs } from 'pinia'

const store = useGlobalStore()
const {loading} = storeToRefs(store)
const {warning} = store
// 页面是否编辑状态
const isEditState = ref(false)
// 单据是否可编辑
const canEdit = computed(
  () =>
    isEditState.value &&
    serviceBill.value.state === ServiceBillState.CREATED,
)
// 初始化表单数据
const serviceBill = ref<ServiceBill>({
  id: undefined,
  number: undefined,
  type: ServiceBillType.INSTALL,
  state: ServiceBillState.CREATED,
  projectName: '',
  projectAddress: '',
  projectContact: '',
  projectContactPhone: '',
  onSiteContact: '',
  onSitePhone: '',
  cargoSenderPhone: '',
  elevatorInfo: '',
  processDetails: [],
  details: [],
  totalAmount: 0,
  processedDate: '',
  remark: '',
  createdDate: new Date(),
})

// 枚举值映射
const serviceBillTypes = [
  {
    value: ServiceBillType.INSTALL,
    label: '安装单',
  },
  {
    value: ServiceBillType.FIX,
    label: '维修单',
  },
]

// 状态显示映射
const serviceBillStates = {
  [ServiceBillState.CREATED]: { label: '新建', color: 'light-blue' },
  [ServiceBillState.PROCESSING]: { label: '处理中', color: 'amber' },
  [ServiceBillState.PROCESSED]: { label: '处理完成', color: 'light-green' },
  [ServiceBillState.FINISHED]: { label: '完成', color: 'green' },
}

// 表单验证状态
const valid = ref(false)
// 必填验证
const requiredRule = (v: unknown) => !!v || '必填项'
// 电话号码验证
const phoneRule = (v: string) => /^\d{10,11}$/.test(v) || '请输入有效的电话号码'

// 当前 Tab 页
const tab = ref('details')

// 导入
function importFile() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,.jpg,.jpeg,.pdf'
  input.onchange = () => {
    const file = input.files?.[0]
    if (file) {
      if (file.size > 1024 * 1024 * 50) {
        warning('文件大小不能超过50M')
        return
      }
      ServiceBillApi.import(file)
        .then((bill) => {
          Object.assign(serviceBill.value, bill)
          isEditState.value = true
        })
        .finally(() => {
          input.remove()
        })
    }
  }
  input.click()
}

// 提交表单
function save() {
  if (valid.value) {
    ServiceBillApi.save(serviceBill.value)
      .then((bill) => {
        isEditState.value = false
        Object.assign(serviceBill.value, bill)
      })
  }
}
</script>

<style scoped>
.bottom-empty {
  height: 68px;
}
</style>
