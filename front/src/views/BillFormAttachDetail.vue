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
                  <v-icon :icon="getIconByType(getTypeByExt(attach.name))"></v-icon>
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
  <v-dialog v-model="previewDialog">
    <v-card>
      <template #title>
        {{ previewInfo.attachment.name }}
      </template>
      <template #text>
        <!-- 预览图片 -->
        <div v-if="previewInfo.type === 'image'">
          <img
            :src="previewInfo.objectUrl"
            style="width: 100%; display: block"
            :alt="previewInfo.objectUrl"
          />
        </div>
        <!-- 使用 iframe 预览 PDF 文件 -->
        <div v-else-if="previewInfo.type === 'pdf'">
          <iframe
            :src="previewInfo.objectUrl"
            style="width: 100%; height: 600px; border: none"
          ></iframe>
        </div>
      </template>
      <v-card-actions>
        <v-btn @click="download(attach)">下载</v-btn>
        <v-btn @click="previewDialog = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type { Attachment } from '@/model/Attachment.ts'
import { mdiFileImage, mdiFilePdfBox } from '@mdi/js'
import { ref } from 'vue'
import { useGlobalStore } from '@/stores/global.ts'
import { storeToRefs } from 'pinia'
import { FileApi } from '@/api/Api.ts'

const attachments = defineModel<Attachment[]>()
const store = useGlobalStore()
const { warning } = store
const { loading } = storeToRefs(store)
// 是否可编辑
const { readonly = false } = defineProps<{
  readonly: boolean
}>()
// 预览窗口
const previewDialog = ref(false)
// 文件类型
type FileType = 'image' | 'pdf' | 'unknown'
// 预览信息
const previewInfo = ref<{
  // 附件
  attachment: Attachment
  // 对象 URL
  objectUrl: string
  // 文件类型
  type: FileType
}>({
  attachment: { id: 0, name: '', path: '' },
  objectUrl: '',
  type: 'unknown',
})

function getTypeByExt(name: string) {
  const ext = name.split('.').pop()?.toLowerCase()
  switch (ext) {
    case 'jpg':
    case 'jpeg':
    case 'png':
    case 'gif':
      return 'image'
    case 'pdf':
      return 'pdf'
    default:
      return 'unknown'
  }
}

function getIconByType(type: FileType) {
  switch (type) {
    case 'image':
      return mdiFileImage
    case 'pdf':
      return mdiFilePdfBox
    default:
      return 'mdi-file'
  }
}

function download(attach: Attachment) {
  if (attach == null) {
    warning('文件为空')
    return
  }
  FileApi.download(attach.path).then(

  )
}
</script>

<style scoped></style>
