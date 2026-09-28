import type { Attachment } from '../model/Attachment'
import { HttpClient } from '@/common/api/HttpClient'
import { useUIStore } from '@/common/store/UIStore'

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

    let totalSize = 0
    for (const file of files) {
      totalSize += file.size
      formData.append('files', file)
    }
    if (totalSize > 5242880) {
      const { warning } = useUIStore()
      warning('文件过大，总大小超过 5MB')
      return Promise.resolve([])
    }
    return await http.postForm<Attachment[]>(`/temp`, formData)
  },
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data
   * @param attach 文件
   * @param onProgress 下载进度回调
   */
  async download(
    attach: Attachment,
    onProgress?: (e: { loaded?: number; total?: number }) => void,
  ) {
    return await http.get<Blob>('/', {
      params: attach,
      responseType: 'blob',
      onDownloadProgress: onProgress,
    })
  },
}
export default AttachmentApi
