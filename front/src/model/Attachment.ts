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
    label: '图像',
    icon: mdiImage
  },
  /**
   * PDF
   */
  PDF: {
    value: 'PDF',
    label: 'PDF',
    icon: mdiFilePdfBox
  },
  /**
   * Word
   */
  WORD: {
    value: 'WORD',
    label: 'Word',
    icon: mdiFileWord
  },
  /**
   * Excel
   */
  EXCEL: {
    value: 'EXCEL',
    label: 'Excel',
    icon: mdiFileExcel
  },
  /**
   * 其他
   */
  OTHER: {
    value: 'OTHER',
    label: '其他',
    icon: mdiFile
  }
}
// 附件类型值
export type AttachmentTypeValue = typeof AttachmentType[keyof typeof AttachmentType]['value']
// 附件类型选项
export type AttachmentTypeOption = typeof AttachmentType[keyof typeof AttachmentType]

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
