<template>
  <v-container>
    <v-form ref="form" v-model="valid" lazy-validation>
      <!-- 单据头部 -->
      <v-card>
        <template #text>
          <v-row justify="space-between">
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <h3>单号: {{ serviceBill.number }}</h3>
            </v-col>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
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
        </template>
      </v-card>
      <!-- 基本信息 -->
      <v-card class="mt-5">
        <template #title>基本信息</template>
        <template #text>
          <v-row>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.number"
                label="单号"
                placeholder="保存后自动生成"
                persistent-placeholder
                readonly
              ></v-text-field>
            </v-col>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-select
                v-model="serviceBill.type"
                :items="serviceBillTypes"
                item-title="label"
                item-value="value"
                label="单据类型"
              ></v-select>
            </v-col>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.projectName"
                label="项目名称"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.projectAddress"
                label="项目地址"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.projectContact"
                label="项目联系人"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
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
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.onSiteContact"
                label="现场联系人"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.onSitePhone"
                label="现场联系人电话"
                :rules="[phoneRule]"
              ></v-text-field>
            </v-col>
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
            <v-tab
              value="processors"
              class="v-card-title"
              v-if="serviceBill.state != ServiceBillState.CREATED"
              >处理人
            </v-tab>
          </v-tabs>
        </template>
        <template #text>
          <v-tabs-window v-model="tab">
            <!-- 服务单明细 -->
            <v-tabs-window-item value="details">
              <OrderFormDetail v-model="serviceBill.details"></OrderFormDetail>
            </v-tabs-window-item>
            <!-- 处理人明细 -->
            <v-tabs-window-item
              value="processors"
              v-if="serviceBill.state != ServiceBillState.CREATED"
            >
              <div
                v-for="(detail, index) in serviceBill.processDetails"
                :key="index"
              >
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
        <template #title> 其它信息</template>
        <template #text>
          <v-row>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.totalAmount"
                label="总金额"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <v-col cols="12" sm="12" md="6" lg="4" xl="3">
              <v-text-field
                v-model="serviceBill.processedDate"
                label="完工时间"
                type="date"
                :rules="[requiredRule]"
              ></v-text-field>
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="serviceBill.remark"
                label="备注"
              ></v-textarea>
            </v-col>
          </v-row>
        </template>
      </v-card>
      <!-- 提交按钮 -->
      <v-btn color="primary" @click="submitForm">提交</v-btn>
    </v-form>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from "vue";
import {
  type ServiceBill,
  ServiceBillState,
  ServiceBillType,
} from "@/entity/ServiceBill.ts";
import OrderFormDetail from "@/views/OrderFormDetail.vue";

// 表单验证状态
const valid = ref(false);

// 初始化表单数据
const serviceBill = ref<ServiceBill>({
  id: undefined,
  number: undefined,
  type: ServiceBillType.INSTALL,
  state: ServiceBillState.CREATED,
  projectName: "",
  projectAddress: "",
  projectContact: "",
  projectContactPhone: "",
  onSiteContact: "",
  onSitePhone: "",
  cargoSenderPhone: "",
  elevatorInfo: "",
  processDetails: [],
  details: [],
  totalAmount: "",
  processedDate: "",
  remark: "",
  createDate: "",
});

// 枚举值映射
const serviceBillTypes = [
  {
    value: ServiceBillType.INSTALL,
    label: "安装单",
  },
  {
    value: ServiceBillType.FIX,
    label: "维修单",
  },
];

// 状态映射
const serviceBillStates = [
  {
    label: "新建",
    color: "light-blue",
  },
  {
    label: "处理中",
    color: "amber",
  },
  {
    label: "处理完成",
    color: "light-green",
  },
  {
    label: "回款完成",
    color: "green",
  },
];

// 验证规则
const requiredRule = (v: unknown) => !!v || "此项为必填项";
const phoneRule = (v: string) =>
  /^\d{10,11}$/.test(v) || "请输入有效的电话号码";

// 提交表单
const submitForm = () => {
  if (valid.value) {
    console.log("提交的服务单数据:", serviceBill.value);
  }
};

const tab = ref("details");
</script>

<style scoped>
h2 {
  margin-top: 20px;
}
</style>
