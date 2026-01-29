<!-- 附件明细 -->
<template>
  <v-container>
    <v-row
      class="overflow-auto"
      :class="{ 'border-xl': !readonly && isDragging }"
      @dragenter.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @dragover.prevent
      @drop.prevent="!readonly && drop($event)"
    >
      <v-col v-for="attach in attachments" :key="attach.name" cols="12" lg="3" md="4" sm="6">
        <v-hover v-slot="{ isHovering, props }">
          <v-card v-bind="props">
            <template v-if="!isHovering" #text>
              <div class="d-flex justify-center ga-2">
                <div>
                  <v-icon :icon="AttachmentType[attach.type].icon"></v-icon>
                </div>
                <div>{{ attach.name }}</div>
              </div>
            </template>
            <template v-if="isHovering" #actions>
              <v-btn @click="preview(attach)">预览</v-btn>
              <v-btn @click="download(attach)">下载</v-btn>
              <v-btn :disabled="readonly" color="red" @click="deleteAttach(attach)">删除</v-btn>
            </template>
          </v-card>
        </v-hover>
      </v-col>
      <v-col v-if="!readonly" cols="1">
        <v-card width="53">
          <template #text>
            <v-icon :icon="mdiPlus" @click="upload"></v-icon>
          </template>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
  <v-dialog v-model="previewDialog" height="90vh" min-width="50vw" width="auto">
    <v-card>
      <template #title>
        {{ previewInfo.attachment.name }}
      </template>
      <v-card-text class="overflow-auto d-flex justify-center">
        <embed
          v-if="previewInfo.attachment.type === AttachmentType.PDF.value"
          :src="previewInfo.objectUrl"
          class="w-100 h-100"
        />
        <img
          v-if="previewInfo.attachment.type === AttachmentType.IMAGE.value"
          :alt="previewInfo.attachment.name"
          :src="previewInfo.objectUrl"
          style="object-fit: contain; max-width: 100%"
        />
      </v-card-text>
      <v-card-actions>
        <v-btn @click="download(previewInfo.attachment)">下载</v-btn>
        <v-btn @click="previewDialog = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import { type Attachment, AttachmentType } from '../model/Attachment.ts'
import { mdiPlus } from '@mdi/js'
import { onUnmounted, ref, toRefs } from 'vue'
import AttachmentApi from '../api/AttachmentApi.ts'
import { useUIStore } from '@/common/store/UIStore.ts'
import { useFileSelector } from '../composable/FileSelector.ts'

const attachments = defineModel<Attachment[]>()
const { warning } = useUIStore()
// 是否可编辑
const props = defineProps<{
  readonly: boolean
}>()
const { readonly } = toRefs(props)
// 预览窗口
const previewDialog = ref(false)
// 是否有拖拽
const isDragging = ref(false)
// 预览信息
const previewInfo = ref<{
  // 附件
  attachment: Attachment
  // 对象 URL
  objectUrl: string
}>({
  attachment: { id: 0, name: '', relativePath: '', type: AttachmentType.OTHER.value },
  objectUrl: '',
})
// 文件缓存，避免多次从服务器获取同一文件
const fileCache = new Map<string, string>()
// 销毁时释放缓存
onUnmounted(() => {
  fileCache.forEach((value) => {
    URL.revokeObjectURL(value)
  })
  fileCache.clear()
})

/**
 * 下载附件
 */
async function download(attach: Attachment) {
  if (attach == null) {
    warning('文件为空')
    return
  }

  const a = document.createElement('a')
  if (fileCache.has(attach.relativePath)) {
    a.href = fileCache.get(attach.relativePath)!
  } else {
    const data = await AttachmentApi.download(attach)
    const url = URL.createObjectURL(data)
    fileCache.set(attach.relativePath, url)
    a.href = url
  }

  a.download = attach.name
  a.click()
  a.remove()
}

/**
 * 预览附件
 */
async function preview(attach: Attachment) {
  if (attach == null) {
    warning('文件为空')
    return
  }
  if (attach.type !== AttachmentType.IMAGE.value && attach.type !== AttachmentType.PDF.value) {
    warning('暂不支持预览该文件, 请直接下载')
    return
  }

  if (fileCache.has(attach.relativePath)) {
    previewInfo.value.objectUrl = fileCache.get(attach.relativePath)!
  } else {
    const data = await AttachmentApi.download(attach)
    const url = URL.createObjectURL(data)
    fileCache.set(attach.relativePath, url)
    previewInfo.value.objectUrl = url
  }
  previewInfo.value.attachment = attach
  previewDialog.value = true
}

/**
 * 上传附件
 */
async function upload() {
  const fileList = await useFileSelector('.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx,.txt', true)
  // 上传至临时目录
  const attach = await AttachmentApi.uploadTemp(Array.from(fileList))
  attachments.value?.push(...attach)
}

/**
 * 拖拽上传
 */
async function drop(e: DragEvent) {
  isDragging.value = false
  const fileList = Array.from(e.dataTransfer!.files)
  const validType = new Set<string>([
    '.pdf',
    '.jpg',
    '.jpeg',
    '.png',
    '.doc',
    '.docx',
    '.xls',
    '.xlsx',
    '.txt',
  ])
  const getFileType = (file: File) => {
    const name = file.name
    return name.substring(name.lastIndexOf('.'))
  }
  if (!fileList.every((file) => validType.has(getFileType(file)))) {
    warning('请上传文档或图片')
    return
  }
  // 上传至临时目录
  const attach = await AttachmentApi.uploadTemp(fileList)
  attachments.value?.push(...attach)
}

/**
 * 删除附件
 */
function deleteAttach(attach: Attachment) {
  attachments.value?.splice(
    attachments.value.findIndex((i) => i === attach),
    1,
  )
}
</script>

<style scoped></style>
