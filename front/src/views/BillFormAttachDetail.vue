<!-- 附件明细 -->
<template>
  <v-container>
    <v-row>
      <v-col cols="3">
        <v-hover v-slot="{ isHovering, props }" v-for="attach in attachments" :key="attach.name">
          <v-card v-bind="props">
            <template #text v-if="!isHovering">
              <div class="d-flex justify-center ga-2">
                <div>
                  <v-icon :icon="getIconByType(attach.name)"></v-icon>
                </div>
                <div>{{ attach.name }}</div>
              </div>
            </template>
            <template #actions v-if="isHovering">
              <v-btn>预览</v-btn>
              <v-btn>下载</v-btn>
              <v-btn color="red" :disabled="readonly">删除</v-btn>
            </template>
          </v-card>
        </v-hover>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import type { Attachment } from '@/entity/Attachment.ts'
import { mdiFileImage, mdiFilePdfBox } from '@mdi/js'

const attachments = defineModel<Attachment[]>()
// 是否可编辑
const { readonly = false } = defineProps<{
  readonly: boolean
}>()

function getIconByType(name: string) {
  const ext = name.split('.').pop()?.toLowerCase()
  switch (ext) {
    case 'jpg':
    case 'jpeg':
    case 'png':
    case 'gif':
      return mdiFileImage
    case 'pdf':
      return mdiFilePdfBox
    default:
      return 'mdi-file'
  }
}
</script>

<style scoped></style>
