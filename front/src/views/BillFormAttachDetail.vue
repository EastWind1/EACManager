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
                  <v-icon :icon="typeIcons[attach.type]"></v-icon>
                </div>
                <div>{{ attach.name }}</div>
              </div>
            </template>
            <template #actions v-if="isHovering">
              <v-btn @click="preview(attach)">预览</v-btn>
              <v-btn @click="download(attach)">下载</v-btn>
              <v-btn color="red" :disabled="readonly">删除</v-btn>
            </template>
          </v-card>
        </v-hover>
      </v-col>
    </v-row>
  </v-container>
  <v-dialog v-model="previewDialog" class="w-75 h-75">
    <v-card>
      <template #title>
        {{ previewInfo.attachment.name }}
      </template>
      <template #text>
        <!-- 预览图片 -->
        <div v-if="previewInfo.attachment.type === AttachmentType.IMAGE">
          <img
            :src="previewInfo.objectUrl"
            class="w-100 h-100"
            :alt="previewInfo.objectUrl"
          />
        </div>
        <!-- 使用 iframe 预览 PDF 文件 -->
        <div v-else-if="previewInfo.attachment.type === AttachmentType.PDF">
          <iframe
            :src="previewInfo.objectUrl"
            class="w-100 h-100"
          ></iframe>
        </div>
      </template>
      <v-card-actions>
        <v-btn @click="download(previewInfo.attachment)">下载</v-btn>
        <v-btn @click="previewDialog = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { type Attachment, AttachmentType } from '@/model/Attachment.ts'
import { mdiFile, mdiFileExcel, mdiFileImage, mdiFilePdfBox, mdiFileWord } from '@mdi/js'
import { ref } from 'vue'
import { useGlobalStore } from '@/stores/global.ts'
import { FileApi } from '@/api/Api.ts'

const attachments = defineModel<Attachment[]>()
const store = useGlobalStore()
const { warning } = store
// 是否可编辑
const { readonly = false } = defineProps<{
  readonly: boolean
}>()
// 预览窗口
const previewDialog = ref(false)
// 预览信息
const previewInfo = ref<{
  // 附件
  attachment: Attachment
  // 对象 URL
  objectUrl: string
}>({
  attachment: { id: 0, name: '', relativePath: '', type: AttachmentType.OTHER },
  objectUrl: ''
})

const typeIcons = {
  [AttachmentType.IMAGE]: mdiFileImage,
  [AttachmentType.PDF]: mdiFilePdfBox,
  [AttachmentType.WORD]: mdiFileWord,
  [AttachmentType.EXCEL]: mdiFileExcel,
  [AttachmentType.OTHER]: mdiFile
}

function download(attach: Attachment) {
  if (attach == null) {
    warning('文件为空')
    return
  }
  FileApi.download(attach.relativePath).then(data => {
    const url = URL.createObjectURL(data)
    const a = document.createElement('a');
    a.href = url
    a.download = attach.name
    a.click()
    a.remove();
  })
}

function preview(attach: Attachment) {
  if (attach == null) {
    warning('文件为空')
    return
  }
  if (attach.type !== AttachmentType.IMAGE && attach.type !== AttachmentType.PDF) {
    warning('暂不支持预览该文件')
    return
  }
  FileApi.download(attach.relativePath).then(data => {
    const url = URL.createObjectURL(data)
    previewInfo.value.attachment = attach
    previewInfo.value.objectUrl = url
    previewDialog.value = true
  })
}

</script>

<style scoped></style>
