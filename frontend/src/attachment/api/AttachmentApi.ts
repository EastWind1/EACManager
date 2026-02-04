import type { Attachment } from '../model/Attachment.ts'
import { useAxios } from '@/common/api/AxiosConfig.ts'
import type { AxiosInstance } from 'axios'

let axiosInstance: AxiosInstance

function getAxios() {
  if (!axiosInstance) {
    axiosInstance = useAxios('/api/attachment')
  }
  return axiosInstance
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
    const res = await getAxios().post(`/temp`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return res.data as Attachment[]
  },
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data, 此处实际返回类型为 Blob, 需要强转
   * @param attach 文件
   */
  async download(attach: Attachment) {

    return await getAxios().get('/', {responseType: 'blob', params: attach }) as Blob
  },
}
export default AttachmentApi
