import type { Attachment } from '../model/Attachment.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

let http: HttpClient

function getHttp() {
  if (!http) {
    http = new HttpClient('/api/attachment')
  }
  return http
}

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
    return await getHttp().postForm<Attachment[]>(`/temp`, formData)
  },
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data, 此处实际返回类型为 Blob, 需要强转
   * @param attach 文件
   */
  async download(attach: Attachment) {
    return (await getHttp().get<Blob>('/', { params: attach }))
  },
}
export default AttachmentApi
