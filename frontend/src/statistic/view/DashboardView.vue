<template>
  <v-container>
    <!-- 服务单统计 -->
    <v-row>
      <!-- 待处理单据与处理中单据 -->
      <v-col cols="12" md="6">
        <v-card class="h-100">
          <template #title>
            <div class="d-flex align-center">
              <v-icon :icon="mdiMonitorDashboard" color="primary"></v-icon>
              <span>服务单统计</span>
            </div>
          </template>
          <template #text>
            <v-row>
              <v-col cols="6" @click="billStateClick(ServiceBillState.CREATED.value)">
                <div class="text-subtitle-2 text-center">待处理单据</div>
                <div class="text-headline-large text-center">
                  {{ countByState?.CREATED ?? 0 }}
                </div>
              </v-col>
              <v-col cols="6" @click="billStateClick(ServiceBillState.PROCESSING.value)">
                <div class="text-subtitle-2 text-center">处理中单据</div>
                <div class="text-headline-large text-center">
                  {{ countByState?.PROCESSING ?? 0 }}
                </div>
              </v-col>
              <v-col cols="6" @click="billStateClick(ServiceBillState.PROCESSED.value)">
                <div class="text-subtitle-2 text-center">处理完成单据</div>
                <div class="text-headline-large text-center">
                  {{ countByState?.PROCESSED ?? 0 }}
                </div>
              </v-col>
            </v-row>
          </template>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card class="h-100">
          <template #title>
            <div class="d-flex align-center">
              <v-icon :icon="mdiCash" color="primary"></v-icon>
              <span>近一年收入</span>
            </div>
          </template>
          <template #text>
            <v-sparkline
              :labels="amountLabel"
              :model-value="amountValue"
              color="primary"
              line-width="2"
              smooth
            ></v-sparkline>
          </template>
        </v-card>
      </v-col>
    </v-row>

    <!-- 报销单统计 -->
    <v-row>
      <v-col cols="12" md="6">
        <v-card class="h-100">
          <template #title>
            <div class="d-flex align-center">
              <v-icon :icon="mdiReceipt" color="primary"></v-icon>
              <span>报销单统计</span>
            </div>
          </template>
          <template #text>
            <v-row>
              <v-col cols="6" @click="reimburseStateClick(ReimburseState.CREATED.value)">
                <div class="text-subtitle-2 text-center">待提交报销</div>
                <div class="text-headline-large text-center">
                  {{ reimburseCountByState?.CREATED ?? 0 }}
                </div>
              </v-col>
              <v-col cols="6" @click="reimburseStateClick(ReimburseState.PROCESSING.value)">
                <div class="text-subtitle-2 text-center">处理中报销</div>
                <div class="text-headline-large text-center">
                  {{ reimburseCountByState?.PROCESSING ?? 0 }}
                </div>
              </v-col>
              <v-col cols="6" @click="reimburseStateClick(ReimburseState.FINISHED.value)">
                <div class="text-subtitle-2 text-center">已完成报销</div>
                <div class="text-headline-large text-center">
                  {{ reimburseCountByState?.FINISHED ?? 0 }}
                </div>
              </v-col>
            </v-row>
          </template>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card class="h-100">
          <template #title>
            <div class="d-flex align-center">
              <v-icon :icon="mdiCurrencyUsd" color="primary"></v-icon>
              <span>近一年报销金额</span>
            </div>
          </template>
          <template #text>
            <v-sparkline
              :labels="reimburseAmountLabel"
              :model-value="reimburseAmountValue"
              color="primary"
              line-width="2"
              smooth
            ></v-sparkline>
          </template>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { ServiceBillState, type ServiceBillStateValue } from '@/service-bill/model/ServiceBill'
import { ReimburseState, type ReimburseStateValue } from '@/reimburse/model/Reimbursement'
import { StatisticApi } from '../api/StatisticApi'
import { useRouter } from 'vue-router'
import { mdiCash, mdiCurrencyUsd, mdiMonitorDashboard, mdiReceipt } from '@mdi/js'

const router = useRouter()
// 服务单数量
const countByState = ref<{ [key in ServiceBillStateValue]?: number }>({
  CREATED: 0,
  PROCESSING: 0,
  PROCESSED: 0,
})
// 服务单金额统计
const amountGroupByMonth = ref<{ month: string; amount: number }[]>([])
// 报销单数量
const reimburseCountByState = ref<{ [key in ReimburseStateValue]?: number }>({
  CREATED: 0,
  PROCESSING: 0,
  FINISHED: 0,
})
// 报销单金额统计
const reimburseAmountGroupByMonth = ref<{ month: string; amount: number }[]>([])

// 服务单图表标签
const amountLabel = computed(() => {
  const values = amountGroupByMonth.value
  return values ? values.map((item) => item.month) : []
})
// 服务单图表值
const amountValue = computed(() => {
  const values = amountGroupByMonth.value
  return values ? values.map((item) => item.amount) : []
})
// 报销单图表标签
const reimburseAmountLabel = computed(() => {
  const values = reimburseAmountGroupByMonth.value
  return values ? values.map((item) => item.month) : []
})
// 报销单图表值
const reimburseAmountValue = computed(() => {
  const values = reimburseAmountGroupByMonth.value
  return values ? values.map((item) => item.amount) : []
})

// 服务单统计数量点击跳转
async function billStateClick(state: ServiceBillStateValue) {
  const queryParam = JSON.stringify({
    states: [state],
  })
  await router.push(`/services?query=${queryParam}`)
}

// 报销单统计数量点击跳转
async function reimburseStateClick(state: ReimburseStateValue) {
  const queryParam = JSON.stringify({
    states: [state],
  })
  await router.push(`/reimburses?query=${queryParam}`)
}

// 初始化
async function init() {
  StatisticApi.countBillsByState().then((res) => {
    countByState.value = res
  })
  StatisticApi.sumTotalAmountByMonth().then((res) => {
    amountGroupByMonth.value = res
  })
  StatisticApi.countReimbursesByState().then((res) => {
    reimburseCountByState.value = res
  })
  StatisticApi.sumReimburseTotalAmountByMonth().then((res) => {
    reimburseAmountGroupByMonth.value = res
  })
}

init()
</script>

<style scoped></style>
