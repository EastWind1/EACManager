/**
 * 服务单类型
 */
export const enum AttachmentType {
  /**
   * 图像
   */
  IMAGE = 'IMAGE',
  /**
   * PDF
   */
  PDF = 'PDF',
  /**
   * Word
   */
  WORD = 'WORD',
  /**
   * Excel
   */
  EXCEL = 'EXCEL',
  /**
   * 其他
   */
  OTHER = 'OTHER'
}
export interface Attachment {
  id?: number
  /**
   * 文件名
   */
  name: string
  /**
   * 文件路径
   */
  path: string
  /**
   * 文件类型
   */
  type: AttachmentType
}
