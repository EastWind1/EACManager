import type { Attachment } from '@/model/Attachment.ts'
import { useAxios } from '@/api/AxiosConfig.ts'
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
  uploadTemp: (files: File[]) => {
    if (!files || !files.length) {
      return Promise.resolve([])
    }
    const formData = new FormData()
    for (const file of files) {
      formData.append('files', file)
    }
    return getAxios()
      .post(
        `/temp`,
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        },
      )
      .then((res) => res.data as Attachment[])
  },
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data, 此处实际返回类型为 Blob, 需要强转
   * @param path 文件路径
   */
  download: (path: string) =>
    getAxios()
      .get(`/${path}`, { responseType: 'blob' })
      .then((res) => res as never as Blob),
}
export default AttachmentApi
