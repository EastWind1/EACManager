<template>
  <v-dialog v-model="show" persistent width="auto">
    <v-card>
      <template #title>
        {{ curTitle }}
      </template>
      <template #text>
        {{ curText }}
      </template>
      <template #actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text @click="confirm">确定</v-btn>
        <v-btn text @click="cancel">取消</v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import { ref } from 'vue'

const show = ref(false)
const curTitle = ref('')
const curText = ref('')
let resolveRef: (value: (boolean | PromiseLike<boolean>)) => void

function open(title: string, text: string): Promise<boolean> {
  curTitle.value = title
  curText.value = text
  show.value = true
  return new Promise<boolean>((resolve) => {
    resolveRef = resolve
  })
}

function confirm() {
  show.value = false
  resolveRef(true)
}

function cancel() {
  show.value = false
  resolveRef(false)
}

defineExpose({ open })
</script>
