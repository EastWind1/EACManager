<template>
  <v-container fluid>
    <!-- 卡片布局 -->
    <v-row>
      <!-- 待处理单据与处理中单据 -->
      <v-col cols="12" md="6">
        <v-card>
          <template #title>单据统计</template>
          <template #text>
            <v-row>
              <v-col cols="6">
                <div class="text-h6">待处理单据</div>
                <div class="text-h4">{{ pendingCount }}</div>
              </v-col>
              <v-col cols="6">
                <div class="text-h6">处理中单据</div>
                <div class="text-h4">{{ processingCount }}</div>
              </v-col>
            </v-row>
            <v-row>
              <v-col cols="6">
                <div class="text-h6">处理完成单据</div>
                <div class="text-h4">{{ processedCount }}</div>
              </v-col>
            </v-row>
          </template>
        </v-card>
      </v-col>

      <!-- 当年收入趋势 -->
      <v-col cols="12" md="6">
        <v-card>
          <template #title>当年收入趋势</template>
          <template #text>
            <v-sparkline
              :model-value="incomeData"
              color="blue"
              line-width="2"
              padding="8"
              smooth
              auto-draw
            >
              <template #label="item">
                ￥{{ item.value }}
              </template>
            </v-sparkline>
          </template>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

// 响应式数据
const pendingCount = ref<number>(10) // 待处理单据数量
const processingCount = ref<number>(5) // 处理中单据数量
const processedCount = ref<number>(20)
const incomeData = ref<number[]>([
  12000, 19000, 3000, 5000, 20000, 30000, 45000, 25000, 30000, 40000, 50000, 60000,
])

// 模拟从API获取数据
const fetchData = () => {
  pendingCount.value = 15
  processingCount.value = 7
  processedCount.value = 25
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.text-h4,
.text-h6 {
  text-align: center;
}
</style>
