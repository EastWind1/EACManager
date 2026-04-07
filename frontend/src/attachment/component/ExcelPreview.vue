<template>
    <v-table density="compact" striped="even">
      <thead>
        <tr>
          <th
            v-for="(header, ci) in headers"
            :key="`header-${ci}`"
            :colspan="header.colspan"
            :rowspan="header.rowspan"
          >
            {{ header.value }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, ri) in body" :key="`row-${ri}`">
          <td
            v-for="(cell, colIndex) in row"
            v-show="!cell.hidden"
            :key="`cell-${colIndex}`"
            :colspan="cell.colspan"
            :rowspan="cell.rowspan"
          >
            {{ cell.value }}
          </td>
        </tr>
      </tbody>
    </v-table>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { read, utils } from 'xlsx'
import { useUIStore } from '@/common/store/UIStore.ts'

interface Cell {
  value: string
  colspan: number
  rowspan: number
  hidden: boolean
}

const props = defineProps<{
  /**
   * 文件路径
   */
  src: string
}>()

// 表格数据
const body = ref<Cell[][]>([])
const headers = ref<Cell[]>([])

const { warning } = useUIStore()

async function parseExcel() {
  const response = await fetch(props.src)
  const arrayBuffer = await response.arrayBuffer()
  const workbook = read(arrayBuffer, { type: 'array' })
  const sheetName = workbook.SheetNames[0] ?? ''
  const worksheet = workbook.Sheets[sheetName]
  if (!worksheet) {
    warning('工作表为空')
    headers.value = []
    body.value = []
    return
  }

  const jsonData = utils.sheet_to_json<string[]>(worksheet, { header: 1 })
  const cells: Cell[][] = []
  for (let r = 0; r < jsonData.length; r++) {
    const row = jsonData[r] ?? []
    const newRow: Cell[] = []
    for (let c = 0; c < row.length; c++) {
      newRow.push({
        value: row[c] ?? '',
        colspan: 1,
        rowspan: 1,
        hidden: false,
      })
    }
    cells.push(newRow)
  }

  // 处理合并单元格
  const merges = worksheet['!merges'] || []
  merges.forEach((merge) => {
    const sr = merge.s.r
    const er = merge.e.r
    const sc = merge.s.c
    const ec = merge.e.c

    if (sr < cells.length && cells[sr] && sc < cells[sr].length) {
      const cell = cells[sr][sc]
      if (cell) {
        cell.colspan = ec - sc + 1
        cell.rowspan = er - sr + 1
        cell.hidden = false
      }
    }

    // 其他被合并的单元格标记为隐藏
    for (let r = sr; r <= er; r++) {
      for (let c = sc; c <= ec; c++) {
        if ((r !== sr || c !== sc) && r < cells.length) {
          const row = cells[r]
          if (row && c < row.length) {
            const cell = row[c]
            if (cell) {
              cell.hidden = true
              cell.colspan = 1
              cell.rowspan = 1
            }
          }
        }
      }
    }
  })

  if (cells.length > 0) {
    headers.value = cells[0] ?? []
    body.value = cells.slice(1) ?? []
  } else {
    headers.value = []
    body.value = []
  }
}

onMounted(() => {
  if (props.src) {
    parseExcel()
  }
})
</script>

<style scoped></style>
