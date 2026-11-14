<template>
  <v-dialog v-model="visible" max-width="480">
    <v-card rounded="lg">
      <template #title>
        <v-icon :icon="mdiAccountSwitch" class="me-2"></v-icon>
        指派负责人
      </template>
      <template #text>
        <div class="text-body-2 mb-4">已选择 {{ count }} 条服务单</div>
        <v-select
          v-model="targetIds"
          :items="assigneeOptions"
          chips
          clearable
          closable-chips
          item-title="name"
          item-value="id"
          label="负责人"
          multiple
          placeholder="留空表示取消指派"
        ></v-select>
      </template>
      <template #actions>
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="visible = false">取消</v-btn>
        <v-btn color="primary" :disabled="loading" @click="emit('confirm')">确定</v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import {mdiAccountSwitch} from '@mdi/js'
import type {Assignee} from '../model/ServiceBill'

defineProps<{
  count: number
  assigneeOptions: Assignee[]
  loading?: boolean
}>()

const visible = defineModel<boolean>()
const targetIds = defineModel<number[]>('targetIds')

const emit = defineEmits<{
  confirm: []
}>()

</script>
