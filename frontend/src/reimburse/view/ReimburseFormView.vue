<template>
  <v-form ref="form" v-model="valid" :readonly="!isEditState" @submit.prevent="save">
    <!-- 单据头部 -->
    <v-sheet>
      <v-container>
        <v-row>
          <!-- 左侧：单号和状态信息 -->
          <!-- 单号显示 -->
          <v-col
            v-if="reimbursement.state !== ReimburseState.CREATED.value"
            cols="6"
            sm="4"
            md="3"
            xl="2"
          >
            <div class="text-caption text-grey-darken-1">单号</div>
            <div class="text-h6 text-no-wrap">{{ reimbursement.number }}</div>
          </v-col>
          <!-- 状态标签 -->
          <v-col cols="3" md="2" xl="1">
            <div class="text-caption text-grey-darken-1">状态</div>
            <v-chip
              :color="ReimburseState[reimbursement.state].color"
              size="small"
              class="font-weight-bold mt-1"
            >
              {{ ReimburseState[reimbursement.state].title }}
            </v-chip>
          </v-col>
          <!-- 总金额 -->
          <v-col cols="3" md="2" xl="1">
            <div class="text-caption text-grey-darken-1">总金额</div>
            <div class="text-h6 font-weight-bold text-primary">
              ￥{{ reimbursement.totalAmount ? reimbursement.totalAmount.toFixed(2) : '0.00' }}
            </div>
          </v-col>
          <v-spacer></v-spacer>
          <v-col class="d-flex justify-end align-center">
            <v-btn
              v-if="
                reimbursement.id &&
                (reimbursement.state !== ReimburseState.FINISHED.value ||
                  userStore.hasAnyRole([AuthorityRole.ROLE_ADMIN.value]))
              "
              v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
              :disabled="isEditState"
              color="primary"
              @click="isEditState = true"
              >编辑
            </v-btn>
            <v-btn
              v-if="!isEditState && reimbursement.state === ReimburseState.CREATED.value"
              v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
              :loading="loading"
              color="info"
              @click="process([reimbursement.id!])"
              >提交
            </v-btn>
            <v-btn
              v-if="!isEditState && reimbursement.state === ReimburseState.PROCESSING.value"
              v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
              :loading="loading"
              color="success"
              @click="finish([reimbursement.id!])"
              >处理完成
            </v-btn>
            <v-btn
              v-if="!isEditState && reimbursement.state === ReimburseState.CREATED.value"
              v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
              :loading="loading"
              color="error"
              @click="removeAndBack(reimbursement.id!)"
              >删除
            </v-btn>
            <v-btn
              v-if="isEditState"
              v-role="[AuthorityRole.ROLE_ADMIN.value, AuthorityRole.ROLE_USER.value]"
              :loading="loading"
              color="primary"
              type="submit"
              >保存
            </v-btn>
            <v-btn v-if="isEditState" :loading="loading" color="warning" @click="cancel"
              >取消
            </v-btn>
          </v-col>
        </v-row>
      </v-container>
    </v-sheet>
    <v-card class="mt-4">
      <template #title>
        <v-icon :icon="mdiFileDocument" class="me-2"></v-icon>
        基本信息
      </template>
      <template #text>
        <v-container>
          <v-row>
            <v-col
              cols="12"
              sm="6"
              md="4"
              xl="3"
              v-if="reimbursement.state === ReimburseState.CREATED.value"
            >
              <v-text-field
                v-model="reimbursement.number"
                label="单号"
                placeholder="可生成自动"
              ></v-text-field>
            </v-col>
            <!-- 摘要 -->
            <v-col cols="12" sm="6" md="4" xl="3">
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
            <v-col cols="12" sm="6" md="4" xl="3">
              <div
                v-if="reimbursement.state !== ReimburseState.CREATED.value"
                class="text-subtitle-2 mb-1"
              >
                报销日期
                {{
                  reimbursement.reimburseDate
                    ? dateUtil.format(reimbursement.reimburseDate, 'keyboardDate')
                    : ''
                }}
              </div>
              <v-date-input
                v-else
                v-model="reimbursement.reimburseDate"
                :readonly="!isEditState"
                density="compact"
                label="报销日期"
                variant="outlined"
              ></v-date-input>
            </v-col>
          </v-row>
        </v-container>
      </template>
    </v-card>
    <v-card class="mt-4">
      <template #title>
        <v-tabs v-model="tab">
          <v-tab value="detail">
            <v-icon :icon="mdiListBox" class="me-2"></v-icon>
            明细
          </v-tab>
          <v-tab value="attachment">
            <v-icon :icon="mdiPaperclip" class="me-2"></v-icon>
            附件
          </v-tab>
        </v-tabs>
      </template>
      <template #text>
        <v-tabs-window v-model="tab">
          <!-- 报销单明细 -->
          <v-tabs-window-item value="detail">
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
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { type Reimbursement, ReimburseState } from '../model/Reimbursement.ts'
import ReimburseDetail from '../component/ReimburseDetail.vue'
import ReimburseApi from '../api/ReimburseApi.ts'
import { storeToRefs } from 'pinia'
import FormAttachDetail from '@/attachment/component/FormAttachDetail.vue'
import { useRoute, useRouter } from 'vue-router'
import { useUIStore } from '@/common/store/UIStore.ts'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'
import { useReimburseActions } from '../composable/ReimburseActions.ts'
import { VDateInput } from 'vuetify/labs/components'
import { AuthorityRole } from '@/user/model/User.ts'
import { useDate } from 'vuetify/framework'
import { mdiFileDocument, mdiListBox, mdiPaperclip } from '@mdi/js'
import { useUserStore } from '@/user/store/UserStore.ts'

const store = useUIStore()
const { loading } = storeToRefs(store)
const { warning, success } = store
const route = useRoute()
const router = useRouter()
const dateUtil = useDate()
const userStore = useUserStore()
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
const tab = ref('detail')

/**
 * 保存
 */
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
 * 取消编辑
 */
async function cancel() {
  // 二次确认
  const confirmed = await store.confirm('取消', '是否取消编辑？')
  if (!confirmed) {
    return
  }
  // 已有单据重新加载
  if (reimbursement.value.id) {
    reimbursement.value = await ReimburseApi.getById(reimbursement.value.id)
    isEditState.value = false
  } else {
    // 新增单据跳转回列表
    await router.push('/serviceBill')
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

async function removeAndBack(id: number) {
  await remove([id])
  router.back()
}

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
