<!-- 附件明细 -->
<template>
  <v-container>
    <!-- 拖拽上传添加边框 -->
    <v-row
      :class="{ 'border-xl': !readonly && isDragging }"
      class="overflow-auto"
      @dragenter.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @dragover.prevent
      @drop.prevent="!readonly && drop($event)"
    >
      <v-col v-for="attach in attachments" :key="attach.name" cols="12" md="4" sm="6" xl="3">
        <v-hover v-slot="{ isHovering, props }">
          <v-card v-bind="props">
            <template #text>
              <div v-if="downInfo.get(attach.id)?.downloading" class="d-flex justify-center">
                <v-progress-circular
                  :indeterminate="(downInfo.get(attach.id)?.progress ?? 0) <= 0"
                  :model-value="downInfo.get(attach.id)?.progress"
                  color="primary"
                  size="40"
                  width="4"
                >
                </v-progress-circular>
              </div>
              <div v-else-if="isHovering" class="d-flex justify-center ga-2">
                <v-btn @click="preview(attach)">预览</v-btn>
                <v-btn @click="download(attach)">下载</v-btn>
                <v-btn :disabled="readonly" color="error" @click="deleteAttach(attach)">
                  删除
                </v-btn>
              </div>
              <template v-else>
                <div>
                  <v-icon :icon="AttachmentType[attach.type].icon"></v-icon>
                </div>
                <div>{{ attach.name }}</div>
              </template>
            </template>
          </v-card>
        </v-hover>
      </v-col>
      <v-col v-if="!readonly" cols="1">
        <v-hover v-slot="{ isHovering, props }">
          <v-card
            v-bind="props"
            variant="outlined"
            width="53"
            :class="{ 'hover-shadow': isHovering }"
          >
            <template #text>
              <v-icon :icon="mdiPlus" @click="upload"></v-icon>
            </template>
          </v-card>
        </v-hover>
      </v-col>
    </v-row>
  </v-container>
  <v-dialog v-model="previewDialog" height="90vh" min-width="60vw" width="auto">
    <v-card>
      <template #title>
        {{ previewInfo.attachment.name }}
      </template>
      <v-card-text class="overflow-auto d-flex justify-center">
        <PDFPreview
          v-if="previewInfo.attachment.type === AttachmentType.PDF.value"
          :src="previewInfo.objectUrl"
        />
        <img
          v-if="previewInfo.attachment.type === AttachmentType.IMAGE.value"
          :alt="previewInfo.attachment.name"
          :src="previewInfo.objectUrl"
          style="object-fit: contain; max-width: 100%"
        />
        <ExcelPreview
          v-if="previewInfo.attachment.type === AttachmentType.EXCEL.value"
          :src="previewInfo.objectUrl"
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
import { type Attachment, AttachmentType } from '../model/Attachment'
import { mdiPlus } from '@mdi/js'
import { defineAsyncComponent, onUnmounted, ref } from 'vue'
import AttachmentApi from '../api/AttachmentApi'
import { useUIStore } from '@/common/store/UIStore'
import { useFileSelector } from '../composable/FileSelector'

const ExcelPreview = defineAsyncComponent(() => import('./ExcelPreview.vue'))
const PDFPreview = defineAsyncComponent(() => import('./PDFPreview.vue'))

const attachments = defineModel<Attachment[]>()
// 是否可编辑
defineProps<{
  readonly: boolean
}>()

const downInfo = ref<Map<number, { downloading: boolean; progress: number }>>(new Map())
const { warning } = useUIStore()

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
  attachment: { id: 0, name: '', type: AttachmentType.OTHER.value },
  objectUrl: '',
})
// 文件缓存，避免多次从服务器获取同一文件
const fileCache = new Map<number, string>()
// 销毁时释放缓存
onUnmounted(() => {
  fileCache.forEach((value) => {
    URL.revokeObjectURL(value)
  })
  fileCache.clear()
})

/**
 * 获取附件对象 URL，本地没有时从服务器下载，并在附件卡片上显示进度
 */
async function loadFile(attach: Attachment): Promise<string> {
  const cached = fileCache.get(attach.id)
  if (cached) {
    return cached
  }

  // 先登记下载状态，避免请求过快失败时 finally 取不到记录
  downInfo.value.set(attach.id, { downloading: true, progress: 0 })

  return await AttachmentApi.download(attach, (e) => {
    const info = downInfo.value.get(attach.id)
    if (!info) {
      return
    }
    const total = e.total ?? 0
    const cur = e.loaded ?? 0
    if (total > 0) {
      info.progress = (cur * 100) / total
    }
  })
    .then((data) => {
      const url = URL.createObjectURL(data)
      fileCache.set(attach.id, url)
      return url
    })
    .finally(() => {
      const info = downInfo.value.get(attach.id)
      if (info) {
        info.downloading = false
      }
    })
}

/**
 * 下载附件
 */
async function download(attach: Attachment) {
  if (attach == null) {
    warning('文件为空')
    return
  }

  try {
    const url = await loadFile(attach)
    const a = document.createElement('a')
    a.href = url
    a.download = attach.name
    a.click()
    a.remove()
  } catch (err) {
    // 请求错误已由 HttpClient 统一提示
    console.error(err)
  }
}

/**
 * 预览附件
 */
async function preview(attach: Attachment) {
  if (attach == null) {
    warning('文件为空')
    return
  }

  if (attach.type == AttachmentType.WORD.value || attach.type == AttachmentType.OTHER.value) {
    warning('该文件类型暂不支持预览，请直接下载')
    return
  }

  try {
    const url = await loadFile(attach)
    previewInfo.value = { attachment: attach, objectUrl: url }
    previewDialog.value = true
  } catch (err) {
    // 请求错误已由 HttpClient 统一提示
    console.error(err)
  }
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

<style scoped>
.hover-shadow {
  transition: all 0.2s;
  background-color: rgb(from currentColor r g b / 0.04);
}
</style>
