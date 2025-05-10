<template>
  <v-file-input v-model="file"></v-file-input>
  <v-btn @click="upload">上传</v-btn>
  <v-text-field label="文件名" v-model="fileName"></v-text-field>
  <v-btn @click="download">下载</v-btn>

  <v-dialog v-model="previewDialog" max-width="800">
    <v-card>
      <v-toolbar color="primary" dark>文件预览</v-toolbar>
      <v-card-text class="pa-0">
        <!-- 预览图片 -->
        <div v-if="previewType === 'image'">
          <img :src="previewUrl" style="width: 100%; display: block;"  :alt="previewUrl"/>
        </div>
        <!-- 使用 iframe 预览 PDF 文件 -->
        <div v-else-if="previewType === 'pdf'">
          <iframe :src="previewUrl" style="width: 100%; height: 600px; border: none;"></iframe>
        </div>
        <div v-else>
          <pre>{{ previewText }}</pre>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-btn @click="previewDialog = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { FileApi, ServiceBillApi } from '@/api/Api.ts'

const file = ref<File>()
const fileName = ref<string>()
const previewDialog = ref(false)
const previewUrl = ref('')
const previewType = ref<'image' | 'pdf' | 'text' | null>(null)
const previewText = ref('')

function getPreviewType(fileName: string): 'image' | 'pdf' | 'text' | null {
  const ext = fileName.split('.').pop()?.toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif'].includes(ext || '')) return 'image'
  if (ext === 'pdf') return 'pdf'
  if (['txt', 'html', 'json', 'js', 'css'].includes(ext || '')) return 'text'
  return null
}

function upload() {
  if (file.value) {
    ServiceBillApi.import(file.value).then(res => console.log(res))
  }
}

function download() {
  if (fileName.value) {
    FileApi.download(fileName.value).then(blob => {
      const url = URL.createObjectURL(blob)
      const type = getPreviewType(fileName.value!)

      if (type === 'image' || type === 'pdf' || type === 'text') {
        // 设置预览内容
        previewUrl.value = url
        previewType.value = type

        // 如果是文本，读取 blob 内容为字符串
        if (type === 'text') {
          const reader = new FileReader()
          reader.onload = () => {
            previewText.value = reader.result as string
            previewDialog.value = true
          }
          reader.readAsText(blob)
        } else {
          previewDialog.value = true
        }
      } else {
        // 不支持预览的文件类型，直接下载
        const a = document.createElement('a')
        a.href = url
        a.setAttribute('download', fileName.value!)
        a.click()
        URL.revokeObjectURL(url)
        a.remove()
      }
    })
  }
}
</script>
