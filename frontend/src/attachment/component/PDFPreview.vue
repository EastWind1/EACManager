<template>
  <embed
    v-if="useNative && src"
    :key="src"
    :src="src"
    type="application/pdf"
    class="w-100 h-100"
  />
  <div class="w-100 h-100" v-else>
    <div class=" overflow-auto">
      <canvas ref="canvasRef"></canvas>
    </div>
    <div class="d-flex justify-center align-content-center ga-1">
      <v-btn :icon="mdiMinus" @click="zoomOut" :disabled="scale <= 0.2"></v-btn>
      <span class="align-content-center">{{Math.round(scale * 100)}}%</span>
      <v-btn :icon="mdiPlus" @click="zoomIn" :disabled="scale >= 2.0"></v-btn>
    </div>
  </div>

</template>

<script lang="ts" setup>
import {onMounted, onUnmounted, ref, shallowRef, watch} from 'vue'
import { useUIStore } from '@/common/store/UIStore.ts'
import {mdiPlus, mdiMinus} from "@mdi/js";

const props = defineProps<{
  src: string
  scale?: number
}>()
// 若浏览器支持pdf，则使用内置
const useNative = navigator.pdfViewerEnabled
const { warning } = useUIStore()

const canvasRef = ref<HTMLCanvasElement>()
const pdfjsDoc = shallowRef<import('pdfjs-dist').PDFDocumentProxy | null>(null)
const totalPages = ref(0)
const currentPage = ref(1)
const scale = ref(props.scale ?? 0.5)

async function loadPdfjs() {
  const pdfjsLib = await import('pdfjs-dist')

  pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
    'pdfjs-dist/build/pdf.worker.min.mjs',
    import.meta.url
  ).toString()

  return pdfjsLib
}

async function renderPage(pageNum: number) {
  const doc = pdfjsDoc.value
  if (!doc || !canvasRef.value) return

  try {
    const page = await doc.getPage(pageNum)
    const viewport = page.getViewport({scale: scale.value})

    const canvas = canvasRef.value
    canvas.height = viewport.height
    canvas.width = viewport.width

    await page.render({
      canvas,
      viewport,
    }).promise
  } catch (err) {
    warning('PDF 渲染失败')
    console.error(err)
  }
}

async function loadPdfWithPdfjs() {

  try {
    const pdfjsLib = await loadPdfjs()
    const loadingTask = pdfjsLib.getDocument(props.src)
    const doc = await loadingTask.promise
    pdfjsDoc.value = doc
    totalPages.value = doc.numPages
    currentPage.value = 1

    if (doc.numPages > 0) {
      await renderPage(1)
    }
  } catch (err) {
    warning('PDF 加载失败')
    console.error(err)
  }
}

async function zoomIn() {
  if (scale.value >= 2) {
    return
  }
  scale.value += 0.3
  await renderPage(1)
}

async function zoomOut() {
  if (scale.value <= 0.2) {
    return
  }
  scale.value -= 0.3
  await renderPage(1)
}

watch(
  () => props.src,
  (newSrc) => {
    if (useNative) {
      return
    }
    if (pdfjsDoc.value) {
      pdfjsDoc.value.destroy()
      pdfjsDoc.value = null
    }
    if (newSrc) {
      loadPdfWithPdfjs()
    }
  }
)

onMounted(() => {
  if (!useNative && props.src) {
    loadPdfWithPdfjs()
  }
})

onUnmounted(() => {
  pdfjsDoc.value?.destroy()
  pdfjsDoc.value = null
})
</script>

<style scoped>
</style>
