import { mdiFile, mdiFileExcel, mdiFilePdfBox, mdiFileWord, mdiImage } from '@mdi/js'

/**
 * 服务单类型
 */
export const AttachmentType = {
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
export type AttachmentTypeValue = (typeof AttachmentType)[keyof typeof AttachmentType]['value']

export interface Attachment {
  id?: number
  /**
   * 文件名
   */
  name: string
  /**
   * 文件相对路径
   */
  relativePath: string
  /**
   * 文件类型
   */
  type: AttachmentTypeValue
}
