import { mdiFile, mdiFileExcel, mdiFilePdfBox, mdiFileWord, mdiImage } from '@mdi/js'

/**
 * 服务单类型
 */
export const AttachType = {
  /**
   * 图像
   */
  IMAGE: {
    value: 'IMAGE',
    title: '图像',
    icon: mdiImage,
  },
  /**
   * PDF
   */
  PDF: {
    value: 'PDF',
    title: 'PDF',
    icon: mdiFilePdfBox,
  },
  /**
   * Word
   */
  WORD: {
    value: 'WORD',
    title: 'Word',
    icon: mdiFileWord,
  },
  /**
   * Excel
   */
  EXCEL: {
    value: 'EXCEL',
    title: 'Excel',
    icon: mdiFileExcel,
  },
  /**
   * 其他
   */
  OTHER: {
    value: 'OTHER',
    title: '其他',
    icon: mdiFile,
  },
} as const
// 附件类型值
export type AttachTypeValue = (typeof AttachType)[keyof typeof AttachType]['value']

export interface Attach {
  id: number
  /**
   * 文件名
   */
  name: string
  /**
   * 文件类型
   */
  type: AttachTypeValue
}
