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
                  <v-text-field
                    v-if="reimbursement.state === ReimburseState.CREATED.value"
                    v-model="reimbursement.number"
                    label="单号"
                    placeholder="可生成自动"
                  ></v-text-field>
                  <h3 v-else>单号: {{ reimbursement.number }}</h3>
                </v-col>
                <!-- 单据状态 -->
                <v-col>
                  <h3>
                    状态:
                    <v-badge
                      :color="ReimburseState[reimbursement.state].color"
                      :content="ReimburseState[reimbursement.state].title"
                      inline
                    ></v-badge>
                  </h3>
                </v-col>
                <!-- 总金额 -->
                <v-col>
                  <h3>
                    总金额:
                    <span class="text-red"
                      >￥
                      {{
                        reimbursement.totalAmount ? reimbursement.totalAmount.toFixed(2) : '0.00'
                      }}</span
                    >
                  </h3>
                </v-col>
              </v-row>
            </v-col>
            <!-- 右侧按钮区域 -->
            <v-col justify-end>
              <v-row class="ga-2" justify="end">
                <!-- 非完成状态都可以编辑 -->
                <v-btn
                  v-if="reimbursement.id && reimbursement.state !== ReimburseState.FINISHED.value"
                  :disabled="isEditState"
                  color="primary"
                  @click="isEditState = true"
                  >编辑
                </v-btn>
                <v-btn
                  v-if="!isEditState && reimbursement.state === ReimburseState.CREATED.value"
                  :loading="loading"
                  @click="process([reimbursement.id!])"
                  >提交
                </v-btn>
                <v-btn
                  v-if="!isEditState && reimbursement.state === ReimburseState.PROCESSING.value"
                  :loading="loading"
                  @click="finish([reimbursement.id!])"
                  >处理完成
                </v-btn>
                <v-btn
                  v-if="!isEditState && reimbursement.state === ReimburseState.CREATED.value"
                  :loading="loading"
                  @click="remove([reimbursement.id!])"
                  >删除
                </v-btn>
                <v-btn v-if="isEditState" :loading="loading" type="submit">保存</v-btn>
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
            <!-- 摘要 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field
                v-model="reimbursement.summary"
                :rules="[requiredRule]"
                label="摘要"
              ></v-text-field>
            </v-col>
            <!-- 备注 -->
            <v-col cols="12">
              <v-textarea v-model="reimbursement.remark" label="备注"></v-textarea>
            </v-col>
            <!-- 报销日期 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <label
                v-if="reimbursement.state !== ReimburseState.CREATED.value"
                class="text-subtitle-1"
                >报销日期
                {{
                  reimbursement.reimburseDate
                    ? date.format(reimbursement.reimburseDate, 'yyyy-MM-dd')
                    : ''
                }}</label
              >
              <v-date-input
                v-else
                v-model="reimbursement.reimburseDate"
                :readonly="!isEditState"
                label="报销日期"
                prepend-icon=""
                prepend-inner-icon="$calendar"
              >
              </v-date-input>
            </v-col>
          </v-row>
        </template>
      </v-card>
      <!-- 明细 -->
      <v-card class="mt-5">
        <template #title>
          <v-tabs v-model="tab">
            <v-tab class="v-card-title" value="details">明细</v-tab>
            <v-tab class="v-card-title" value="attachment">附件</v-tab>
          </v-tabs>
        </template>
        <template #text>
          <v-tabs-window v-model="tab">
            <!-- 报销单明细 -->
            <v-tabs-window-item value="details">
              <ReimburseDetail v-model="reimbursement" :readonly="!isEditState"></ReimburseDetail>
            </v-tabs-window-item>
            <!-- 附件 -->
            <v-tabs-window-item value="attachment">
              <FormAttachDetail
                v-model="reimbursement.attachments"
                :readonly="!isEditState"
              ></FormAttachDetail>
            </v-tabs-window-item>
          </v-tabs-window>
        </template>
      </v-card>
    </v-form>
  </v-container>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { type Reimbursement, ReimburseState } from '@/model/Reimbursement.ts'
import ReimburseDetail from '@/component/ReimburseDetail.vue'
import * as date from 'date-fns'
import ReimburseApi from '@/api/ReimburseApi.ts'
import { storeToRefs } from 'pinia'
import FormAttachDetail from '@/component/FormAttachDetail.vue'
import { useRoute } from 'vue-router'
import { useUIStore } from '@/store/UIStore.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import { useReimburseActions } from '@/composable/ReimburseActions.ts'
import { VDateInput } from 'vuetify/labs/components'

const store = useUIStore()
const { loading } = storeToRefs(store)
const { warning, success } = store
const route = useRoute()
// 页面是否编辑状态
const isEditState = ref(false)

// 初始化表单数据
const reimbursement = ref<Reimbursement>({
  state: ReimburseState.CREATED.value,
  summary: '',
  details: [],
  attachments: [],
  totalAmount: 0,
  reimburseDate: new Date(),
})

// 表单验证状态
const valid = ref(false)
// 必填验证
const requiredRule = (v: unknown) => !!v || '必填项'

// 当前 Tab 页
const tab = ref('details')

// 提交表单
async function save() {
  if (valid.value) {
    let bill
    // 编辑保存
    if (reimbursement.value.id) {
      bill = await ReimburseApi.save(reimbursement.value)
    } else {
      // 新增
      bill = await ReimburseApi.create(reimbursement.value)
    }
    success('保存成功')

    isEditState.value = false
    reimbursement.value = bill
  }
}

/**
 * 处理动作结果
 */
async function processResult(result: ActionsResult<number, void>) {
  const res = result.results[0]
  if (res.success) {
    success('操作成功')
    reimbursement.value = await ReimburseApi.getById(reimbursement.value.id!)
  } else {
    warning(`操作失败：${res.message}`)
  }
}

const { process, finish, remove } = useReimburseActions(processResult)

// 初始化
async function init() {
  // 链接查看
  if (route.params.id) {
    const bill = await ReimburseApi.getById(parseInt(route.params.id as string)).catch(
      () => undefined,
    )
    if (bill) {
      reimbursement.value = bill
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
      default:
        warning('不支持的操作: ' + actionQuery)
    }
  }
}

init()
</script>

<style scoped></style>
