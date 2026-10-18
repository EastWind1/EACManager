import type { Attach } from '../model/Attach'
import { HttpClient } from '@/common/api/HttpClient'
import { useUIStore } from '@/common/store/UIStore'

const http = new HttpClient('/api/attachment')
/**
 * 附件 API
 */
const AttachApi = {
  /**
   * 上传附件
   *
   * 附件以 namespace/唯一id 为 key 落盘，key 一经生成不再变更。
   * @param namespace 附件命名空间，由业务模块决定，需与后端常量一致
   * @param files 文件
   */
  async upload(namespace: string, files: File[]) {
    if (!files || !files.length) {
      return Promise.resolve([])
    }
    const formData = new FormData()
    formData.append('namespace', namespace)

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
    return await http.postForm<Attach[]>(`/upload`, formData)
  },
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data
   * @param attach 文件
   * @param onProgress 下载进度回调
   */
  async download(
    attach: Attach,
    onProgress?: (e: { loaded?: number; total?: number }) => void,
  ) {
    return await http.get<Blob>('/', {
      params: attach,
      responseType: 'blob',
      onDownloadProgress: onProgress,
    })
  },
}
export default AttachApi
