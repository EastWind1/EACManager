<!-- 订单表单 -->
<template>
  <v-container>
    <v-form ref="form" v-model="valid" :readonly="!isEditState" @submit.prevent="save">
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
                      :color="stateMap[serviceBill.state].color"
                      :content="stateMap[serviceBill.state].label"
                      inline
                    ></v-badge>
                  </h3>
                </v-col>
              </v-row>
            </v-col>
            <!-- 右侧按钮区域 -->
            <v-col justify-end>
              <v-row justify="end" class="ga-2">
                <v-btn
                  color="primary"
                  v-if="serviceBill.id && serviceBill.state === ServiceBillState.CREATED"
                  :disabled="isEditState"
                  @click="isEditState = true"
                  >编辑
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.CREATED"
                  @click="process([serviceBill.id!])"
                  :loading="loading"
                  >开始处理
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.PROCESSING"
                  @click="processed([serviceBill.id!])"
                  :loading="loading"
                  >处理完成
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.PROCESSED"
                  @click="finish([serviceBill.id!])"
                  :loading="loading"
                  >回款完成
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.CREATED"
                  @click="remove([serviceBill.id!])"
                  :loading="loading"
                  >删除
                </v-btn>
                <v-btn v-if="isEditState" type="submit" :loading="loading">保存</v-btn>
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
                item-title="title"
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
            <v-tab value="attachment" class="v-card-title">附件</v-tab>
          </v-tabs>
        </template>
        <template #text>
          <v-tabs-window v-model="tab">
            <!-- 服务单明细 -->
            <v-tabs-window-item value="details">
              <BillFormDetail v-model="serviceBill" :readonly="!isEditState"></BillFormDetail>
            </v-tabs-window-item>
            <!-- 附件 -->
            <v-tabs-window-item value="attachment">
              <BillFormAttachDetail
                v-model="serviceBill"
                :readonly="!isEditState"
              ></BillFormAttachDetail>
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
import { onMounted, ref } from 'vue'
import { type ServiceBill, ServiceBillState, ServiceBillType } from '@/model/ServiceBill.ts'
import BillFormDetail from '@/view/BillFormDetail.vue'
import * as date from 'date-fns'
import ServiceBillApi from '@/api/ServiceBillApi.ts'
import { storeToRefs } from 'pinia'
import BillFormAttachDetail from '@/view/BillFormAttachDetail.vue'
import { useRoute } from 'vue-router'
import { useUIStore } from '@/store/UIStore.ts'
import { useRouterStore } from '@/store/RouterStore.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import { useBillActions } from '@/composable/BillActions.ts'

const store = useUIStore()
const { loading } = storeToRefs(store)
const { warning, success } = store
const route = useRoute()
// 页面是否编辑状态
const isEditState = ref(false)

// 初始化表单数据
const serviceBill = ref<ServiceBill>({
  type: ServiceBillType.INSTALL,
  state: ServiceBillState.CREATED,
  projectName: '',
  projectAddress: '',
  projectContact: '',
  projectContactPhone: '',
  details: [],
  attachments: [],
  totalAmount: 0,
})

// 枚举值映射
const serviceBillTypes = [
  {
    value: ServiceBillType.INSTALL,
    title: '安装单',
  },
  {
    value: ServiceBillType.FIX,
    title: '维修单',
  },
]

// 状态映射
const stateMap = {
  [ServiceBillState.CREATED]: { label: '新建', color: 'light-blue' },
  [ServiceBillState.PROCESSING]: { label: '处理中', color: 'amber' },
  [ServiceBillState.PROCESSED]: { label: '处理完成', color: 'light-green' },
  [ServiceBillState.FINISHED]: { label: '完成', color: 'green' },
}

onMounted(async () => {
  // 链接查看
  if (route.params.id) {
    const bill = await ServiceBillApi.getById(parseInt(route.params.id as string))
    if (bill) {
      serviceBill.value = bill
    } else {
      warning('未找到该单据')
    }
  } else {
    const actionQuery = route.query.action
    switch (actionQuery) {
      // 新建
      case 'create':
        isEditState.value = true
        break
      // 导入
      case 'import':
        const { getData } = useRouterStore()
        const data = getData() as ServiceBill
        serviceBill.value = data
        isEditState.value = true
        break
      default:
        warning('不支持的操作: ' + actionQuery)
    }
  }
})

// 表单验证状态
const valid = ref(false)
// 必填验证
const requiredRule = (v: unknown) => !!v || '必填项'
// 电话号码验证
const phoneRule = (v: string) => /^\d{10,11}$/.test(v) || '请输入有效的电话号码'

// 当前 Tab 页
const tab = ref('details')

// 提交表单
async function save() {
  if (valid.value) {
    const bill = await ServiceBillApi.create(serviceBill.value)

    isEditState.value = false
    serviceBill.value = bill
  }
}

/**
 * 处理动作结果
 */
async function processResult(result: ActionsResult<number, void>) {
  const res = result.results[0]
  if (res.success) {
    success('操作成功')
    serviceBill.value = await ServiceBillApi.getById(serviceBill.value.id!)
  } else {
    warning(`操作失败：${res.message}`)
  }
}

const { process, processed, finish, remove } = useBillActions(processResult)
</script>

<style scoped>
.bottom-empty {
  height: 68px;
}
</style>
