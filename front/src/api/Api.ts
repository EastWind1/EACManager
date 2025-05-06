import { useAxios } from '@/api/AxiosConfig.ts'
import type { ServiceBill } from '@/entity/ServiceBill.ts'

const axios = useAxios()

export const ServiceBillApi = {
  /**
   * 获取所有订单
   */
  getAll: () => axios.get('serviceBill').then((res) => res.data as ServiceBill[]),
  /**
   * 保存
   * @param serviceBill 订单
   */
  save: (serviceBill: ServiceBill) =>
    axios.post('serviceBill', serviceBill).then((res) => res.data as ServiceBill),
  /**
   * 删除
   * @param id 订单 ID
   */
  delete: (id: number) => axios.delete(`serviceBill/${id}}`),
}

export const UserApi = {
  /**
   * 登录
   * 返回 token
   * @param username 用户名
   * @param password 密码
   */
  login: (username: string, password: string) =>
    axios
      .post('user/token', {
        username,
        password,
      })
      .then((res) => res.data as string),
}

export const FileApi = {
  /**
   * 上传文件
   * @param file 文件
   */
  upload: (file: File) =>
    axios
      .postForm('file', {
        file,
      })
      .then((res) => res.data as string),
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data, 此处实际返回类型为 Blob, 需要强转
   * @param fileName 文件名
   */
  download: (fileName: string) =>
    axios.get(`file/${fileName}`, { responseType: 'blob' }).then(res => res as never as Blob),
}
