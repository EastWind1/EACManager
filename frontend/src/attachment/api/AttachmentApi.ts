import type { Attachment } from '../model/Attachment.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

const http = new HttpClient('/api/attachment')

/**
 * 附件 API
 */
const AttachmentApi = {
  /**
   * 上传临时文件
   * @param files 文件
   */
  async uploadTemp(files: File[]) {
    if (!files || !files.length) {
      return Promise.resolve([])
    }
    const formData = new FormData()
    for (const file of files) {
      formData.append('files', file)
    }
    return await http.postForm<Attachment[]>(`/temp`, formData)
  },
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data
   * @param attach 文件
   */
  async download(attach: Attachment) {
    return await http.get<Blob>('/', { params: attach })
  },
}
export default AttachmentApi
