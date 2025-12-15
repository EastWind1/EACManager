<template>
  <v-container fluid>
    <!-- 卡片布局 -->
    <v-row>
      <!-- 待处理单据与处理中单据 -->
      <v-col cols="12" md="6">
        <v-card>
          <template #title>单据统计</template>
          <template #text>
            <v-container>
              <v-row>
                <v-col
                  class="v-card--hover"
                  cols="6"
                  @click="stateClick(ServiceBillState.CREATED.value)"
                >
                  <div class="text-h6 text-center">待处理单据</div>
                  <div class="text-h4 text-center">
                    {{ countByState.CREATED ? countByState.CREATED : 0 }}
                  </div>
                </v-col>
                <v-col
                  class="v-card--hover"
                  cols="6"
                  @click="stateClick(ServiceBillState.PROCESSING.value)"
                >
                  <div class="text-h6 text-center">处理中单据</div>
                  <div class="text-h4 text-center">
                    {{ countByState.PROCESSING ? countByState.PROCESSING : 0 }}
                  </div>
                </v-col>

                <v-col
                  class="v-card--hover"
                  cols="6"
                  @click="stateClick(ServiceBillState.PROCESSED.value)"
                >
                  <div class="text-h6 text-center">处理完成单据</div>
                  <div class="text-h4 text-center">
                    {{ countByState.PROCESSED ? countByState.PROCESSED : 0 }}
                  </div>
                </v-col>
              </v-row>
            </v-container>
          </template>
        </v-card>
      </v-col>

      <!-- 当年收入趋势 -->
      <v-col cols="12" md="6">
        <v-card>
          <template #title> 近一年收入 </template>
          <template #text>
            <v-sparkline
              :labels="amountLabel"
              :model-value="amountValue"
              color="blue"
              line-width="2"
              padding="8"
              smooth
            >
            </v-sparkline>
          </template>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { ServiceBillState, type ServiceBillStateValue } from '@/model/ServiceBill.ts'
import { StatisticApi } from '@/api/StatisticApi.ts'
import { useRouter } from 'vue-router'

const router = useRouter()
// 单据数量
const countByState = ref<{ [key in ServiceBillStateValue]?: number }>({})
// 金额统计
const amountGroupByMonth = ref<{ month: string; amount: number }[]>([])

// 图表标签
const amountLabel = computed(() => {
  const values = amountGroupByMonth.value
  return values ? values.map((item) => item.month) : []
})
// 图标值
const amountValue = computed(() => {
  const values = amountGroupByMonth.value
  return values ? values.map((item) => item.amount) : []
})

// 统计数量点击跳转
async function stateClick(state: ServiceBillStateValue) {
  const queryParam = JSON.stringify({
    states: [state],
  })
  await router.push(`/services?query=${queryParam}`)
}

// 初始化
async function init() {
  StatisticApi.countBillsByState().then((res) => {
    countByState.value = res
  })
  StatisticApi.sumTotalAmountByMonth().then((res) => {
    amountGroupByMonth.value = res
  })
}

init()
</script>

<style scoped></style>
