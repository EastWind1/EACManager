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
                  <v-text-field
                    v-if="serviceBill.state === ServiceBillState.CREATED.value"
                    v-model="serviceBill.number"
                    label="单号"
                    placeholder="可生成自动"
                  ></v-text-field>
                  <h3 v-else>单号: {{ serviceBill.number }}</h3>
                </v-col>
                <!-- 单据状态 -->
                <v-col>
                  <h3>
                    状态:
                    <v-badge
                      :color="ServiceBillState[serviceBill.state].color"
                      :content="ServiceBillState[serviceBill.state].title"
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
                        serviceBill.totalAmount ? serviceBill.totalAmount.toFixed(2) : '0.00'
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
                  v-if="serviceBill.id && serviceBill.state !== ServiceBillState.FINISHED.value"
                  :disabled="isEditState"
                  color="primary"
                  @click="isEditState = true"
                  >编辑
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.CREATED.value"
                  :loading="loading"
                  @click="process([serviceBill.id!])"
                  >开始处理
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.PROCESSING.value"
                  :loading="loading"
                  @click="processed([serviceBill.id!])"
                  >处理完成
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.PROCESSED.value"
                  :loading="loading"
                  @click="finish([serviceBill.id!])"
                  >回款完成
                </v-btn>
                <v-btn
                  v-if="!isEditState && serviceBill.state === ServiceBillState.CREATED.value"
                  :loading="loading"
                  @click="remove([serviceBill.id!])"
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
            <!-- 单据类型 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-select
                v-model="serviceBill.type"
                :items="billTypeOption"
                label="单据类型"
              ></v-select>
            </v-col>
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-select
                v-model="serviceBill.productCompany"
                label="产品公司"
                :items="companyData.data"
                item-title="name"
                return-object
                @update:menu="companySelect"
              ></v-select>
            </v-col>
            <!-- 项目名称 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field
                v-model="serviceBill.projectName"
                :rules="[requiredRule]"
                label="项目名称"
              ></v-text-field>
            </v-col>
            <!-- 项目地址 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field
                v-model="serviceBill.projectAddress"
                :rules="[requiredRule]"
                label="项目地址"
              >
                <template #append>
                  <v-icon :icon="mdiNavigation" @click="openMap(serviceBill.projectAddress)">
                  </v-icon>
                </template>
              </v-text-field>
            </v-col>
            <!-- 项目联系人 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field v-model="serviceBill.projectContact" label="项目联系人"></v-text-field>
            </v-col>
            <!-- 项目联系人电话 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field v-model="serviceBill.projectContactPhone" label="项目联系人电话">
                <template #append v-if="mobile">
                  <v-icon :icon="mdiPhone" @click="callPhone(serviceBill.projectContactPhone)">
                  </v-icon>
                </template>
              </v-text-field>
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
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field v-model="serviceBill.onSiteContact" label="现场联系人"></v-text-field>
            </v-col>
            <!-- 现场联系人电话 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <v-text-field v-model="serviceBill.onSitePhone" label="现场联系人电话">
                <template #append v-if="mobile">
                  <v-icon :icon="mdiPhone" @click="callPhone(serviceBill.onSitePhone)"> </v-icon>
                </template>
              </v-text-field>
            </v-col>
            <!-- 电梯信息 -->
            <v-col cols="12" lg="8" md="12" sm="12" xl="6">
              <v-text-field
                v-model="serviceBill.elevatorInfo"
                label="电梯信息"
                variant="outlined"
              ></v-text-field>
            </v-col>
          </v-row>
        </template>
      </v-card>
      <!-- 明细 -->
      <v-card class="mt-5">
        <template #title>
          <v-tabs v-model="tab">
            <v-tab class="v-card-title" value="detail">明细</v-tab>
            <v-tab class="v-card-title" value="attachment">附件</v-tab>
          </v-tabs>
        </template>
        <template #text>
          <v-tabs-window v-model="tab">
            <!-- 服务单明细 -->
            <v-tabs-window-item value="detail">
              <BillFormDetail v-model="serviceBill" :readonly="!isEditState"></BillFormDetail>
            </v-tabs-window-item>
            <!-- 附件 -->
            <v-tabs-window-item value="attachment">
              <FormAttachDetail
                v-model="serviceBill.attachments"
                :readonly="!isEditState"
              ></FormAttachDetail>
            </v-tabs-window-item>
          </v-tabs-window>
        </template>
      </v-card>
      <!-- 其他字段 -->
      <v-card class="mt-5">
        <template #title>其它信息</template>
        <template #text>
          <v-row>
            <v-col cols="12">
              <v-textarea v-model="serviceBill.remark" label="备注"></v-textarea>
            </v-col>
            <!-- 创建时间 -->
            <v-col cols="12" lg="4" md="6" sm="12" xl="3">
              <label
                v-if="serviceBill.state !== ServiceBillState.CREATED.value"
                class="text-subtitle-1"
                >创建时间
                {{
                  serviceBill.orderDate
                    ? dateUtil.format(serviceBill.orderDate, 'keyboardDate')
                    : ''
                }}</label
              >
              <v-date-input
                v-else
                v-model="serviceBill.orderDate"
                :readonly="!isEditState"
                label="创建时间"
                prepend-icon=""
                prepend-inner-icon="$calendar"
              >
              </v-date-input>
            </v-col>
            <!-- 处理完成时间 -->
            <v-col v-if="serviceBill.processedDate" cols="12" lg="4" md="6" sm="12" xl="3">
              <label class="text-subtitle-1"
                >处理完成时间
                {{ dateUtil.format(serviceBill.processedDate, 'keyboardDate') }}</label
              >
            </v-col>
            <!-- 回款完成时间 -->
            <v-col v-if="serviceBill.finishedDate" cols="12" lg="4" md="6" sm="12" xl="3">
              <label class="text-subtitle-1"
                >回款完成时间 {{ dateUtil.format(serviceBill.finishedDate, 'keyboardDate') }}</label
              >
            </v-col>
          </v-row>
        </template>
      </v-card>
    </v-form>
  </v-container>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { type ServiceBill, ServiceBillState, ServiceBillType } from '../model/ServiceBill.ts'
import BillFormDetail from '../component/BillFormDetail.vue'
import ServiceBillApi from '../api/ServiceBillApi.ts'
import { storeToRefs } from 'pinia'
import FormAttachDetail from '@/attachment/component/FormAttachDetail.vue'
import { useRoute } from 'vue-router'
import { useUIStore } from '@/common/store/UIStore.ts'
import { useRouterStore } from '@/common/store/RouterStore.ts'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'
import { useBillActions } from '../composable/BillActions.ts'
import { VDateInput } from 'vuetify/labs/components'
import { mdiNavigation, mdiPhone } from '@mdi/js'
import { useDate, useDisplay, useHotkey } from 'vuetify/framework'
import type { Company } from '@/company/model/Company.ts'
import CompanyApi from '@/company/api/CompanyApi.ts'

const store = useUIStore()
const { loading } = storeToRefs(store)
const { warning, success } = store
const route = useRoute()
const { mobile } = useDisplay()
const dateUtil = useDate()
// 页面是否编辑状态
const isEditState = ref(false)
// 单据类型选项
const billTypeOption = Object.values(ServiceBillType)

// 表单数据
const serviceBill = ref<ServiceBill>({
  type: ServiceBillType.INSTALL.value,
  state: ServiceBillState.CREATED.value,
  projectName: '',
  projectAddress: '',
  projectContact: '',
  projectContactPhone: '',
  details: [],
  attachments: [],
  totalAmount: 0,
  orderDate: new Date(),
})

// 表单验证状态
const valid = ref(false)
// 必填验证
const requiredRule = (v: unknown) => !!v || '必填项'
// 公司信息,用于下拉框处理
const companyData = ref<{ loaded: boolean; data: Company[] }>({
  loaded: false,
  data: [],
})

// 当前子表 Tab 页
const tab = ref('detail')

/**
 * 公司下拉加载
 */
async function companySelect() {
  if (companyData.value.loaded) {
    return
  }
  companyData.value.data = await CompanyApi.getAll()
  companyData.value.loaded = true
}
// 保存快捷键
useHotkey('ctrl+s', save)
// 提交表单
async function save() {
  if (valid.value) {
    let bill
    // 编辑保存
    if (serviceBill.value.id) {
      bill = await ServiceBillApi.save(serviceBill.value)
    } else {
      // 新增
      bill = await ServiceBillApi.create(serviceBill.value)
    }
    success('保存成功')

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

/**
 * 打开地图
 * @param address 地址
 */
function openMap(address: string) {
  if (!address) {
    return
  }
  if (mobile.value) {
    // 创建隐藏 iframe 避免有空白页
    const iframe = document.createElement('iframe')
    iframe.style.display = 'none'
    iframe.src = `androidamap://keywordNavi?sourceApplication=appname&keyword=${encodeURIComponent(address)}&style=2`
    document.body.appendChild(iframe)
    setTimeout(() => {
      document.body.removeChild(iframe)
    }, 1000)
  } else {
    open(`https://ditu.amap.com/search?query=${encodeURI(address)}`)
  }
}

/**
 * 拨打电话
 * @param phone 电话
 */
function callPhone(phone?: string) {
  if (!phone) {
    return
  }
  const { confirm } = useUIStore()
  confirm('拨打电话', `是否拨打电话 ${phone}`).then((res) => {
    if (!res) {
      return
    }
    open(`tel:${phone}`)
  })
}

const { process, processed, finish, remove } = useBillActions(processResult)

// 初始化
async function init() {
  // 链接查看
  if (route.params.id) {
    const bill = await ServiceBillApi.getById(parseInt(route.params.id as string)).catch(
      () => undefined,
    )
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
        if (!data) {
          warning('未获取到识别结果')
          return
        }
        serviceBill.value = data
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
