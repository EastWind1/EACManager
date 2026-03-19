<template>
  <div class="excel-preview">
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
            :key="`cell-${colIndex}`"
            v-show="!cell.hidden"
            :colspan="cell.colspan"
            :rowspan="cell.rowspan"
          >
            {{ cell.value }}
          </td>
        </tr>
      </tbody>
    </v-table>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { read, utils } from 'xlsx'

interface Cell {
  value: string;
  colspan: number;
  rowspan: number;
  hidden: boolean;
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

async function parseExcel() {
  try {
    const response = await fetch(props.src)
    const arrayBuffer = await response.arrayBuffer()
    const workbook = read(arrayBuffer, { type: 'array' })
    const sheetName = workbook.SheetNames[0]
    const worksheet = workbook.Sheets[sheetName]

    const jsonData = utils.sheet_to_json(worksheet, { header: 1}) as string[][]
    const cells: Cell[][] = []
    for (let r = 0; r < jsonData.length; r++) {
      const row = jsonData[r]
      const newRow: Cell[] = []
      for (let c = 0; c < row.length; c++) {
        newRow.push({
          value: row[c],
          colspan: 1,
          rowspan: 1,
          hidden: false
        })
      }
      cells.push(newRow)
    }

    // 处理合并单元格
    const merges = worksheet['!merges'] || []
    merges.forEach(merge => {
      const sr = merge.s.r
      const er = merge.e.r
      const sc = merge.s.c
      const ec = merge.e.c

      if (sr < cells.length && sc < cells[sr].length) {
        cells[sr][sc].colspan = ec - sc + 1
        cells[sr][sc].rowspan = er - sr + 1
        cells[sr][sc].hidden = false
      }

      // 其他被合并的单元格标记为隐藏
      for (let r = sr; r <= er; r++) {
        for (let c = sc; c <= ec; c++) {
          if (r !== sr || c !== sc) {
            if (r < cells.length && c < cells[r].length) {
              cells[r][c].hidden = true
              // 被合并的单元格跨度设为1
              cells[r][c].colspan = 1
              cells[r][c].rowspan = 1
            }
          }
        }
      }
    })

    if (cells.length > 0) {
      headers.value = cells[0] || []
      body.value = cells.slice(1) || []
    } else {
      headers.value = []
      body.value = []
    }
  } catch (error) {
    console.error('解析Excel文件失败:', error)
  }
}

onMounted(() => {
  if (props.src) {
    parseExcel()
  }
})
</script>

<style scoped>
</style>
