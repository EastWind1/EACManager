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
   * 上传文件
   * @param file 文件
   * @param path 路径，必须为相对路径
   */
  upload: (file: File, path: string) =>
    getAxios()
      .postForm(`?path=${path}`, {
        file,
      })
      .then((res) => res.data as Attachment),
  /**
   * 上传临时文件
   * @param file 文件
   */
  uploadTemp: (file: File) =>
    getAxios()
      .postForm(`/temp`, {
        file,
      })
      .then((res) => res.data as Attachment),
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data, 此处实际返回类型为 Blob, 需要强转
   * @param path 文件路径
   */
  download: (path: string) =>
    getAxios().get(`/${path}`, { responseType: 'blob' }).then((res) => res as never as Blob),
}
export default AttachmentApi
