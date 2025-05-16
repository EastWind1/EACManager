import type { Attachment } from '@/model/Attachment.ts'
import { useAxios } from '@/api/AxiosConfig.ts'
const axios = useAxios()
const prefix = 'attachment'
const AttachmentApi = {
  /**
   * 上传文件
   * @param file 文件
   * @param path 路径，必须为相对路径
   */
  upload: (file: File, path: string) =>
    axios
      .postForm(`${prefix}?path=${path}`, {
        file,
      })
      .then((res) => res.data as Attachment),
  /**
   * 上传临时文件
   * @param file 文件
   */
  uploadTemp: (file: File) =>
    axios
      .postForm(`${prefix}/temp`, {
        file,
      })
      .then((res) => res.data as Attachment),
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data, 此处实际返回类型为 Blob, 需要强转
   * @param path 文件路径
   */
  download: (path: string) =>
    axios.get(`${prefix}/${path}`, { responseType: 'blob' }).then((res) => res as never as Blob),
}
export default AttachmentApi
