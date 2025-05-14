import { useAxios } from '@/api/AxiosConfig.ts'
import { type ServiceBill, type ServiceBillQueryParam } from '@/model/ServiceBill.ts'
import type { Attachment } from '@/model/Attachment.ts'
import type { PageResult } from '@/model/PageResult.ts'

const axios = useAxios()

export const ServiceBillApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  getByQueryParam: (queryParam: ServiceBillQueryParam) =>
    axios.post('serviceBill/query', queryParam).then((res) => res.data as PageResult<ServiceBill>),
  /**
   * 根据 id 获取
   * @param id 订单 ID
   */
  getById: (id: number) => axios.get(`serviceBill/${id}`).then((res) => res.data as ServiceBill),
  /**
   * 导入
   */
  import: (file: File) =>
    axios
      .postForm('serviceBill/import', {
        file,
      })
      .then((res) => res.data as ServiceBill),
  /**
   * 新建
   * @param serviceBill 订单
   */
  create: (serviceBill: ServiceBill) =>
    axios.post('serviceBill', serviceBill).then((res) => res.data as ServiceBill),
  /**
   * 删除
   * @param id 订单 ID
   */
  /**
   * 保存
   * @param serviceBill 订单
   */
  save: (serviceBill: ServiceBill) =>
    axios.put('serviceBill', serviceBill).then((res) => res.data as ServiceBill),
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
   * @param path 路径，必须为相对路径
   */
  upload: (file: File, path: string) =>
    axios
      .postForm(`attachment?path=${path}`, {
        file,
      })
      .then((res) => res.data as Attachment),
  /**
   * 上传临时文件
   * @param file 文件
   */
  uploadTemp: (file: File) =>
    axios
      .postForm(`attachment/temp`, {
        file,
      })
      .then((res) => res.data as Attachment),
  /**
   * 下载文件
   * 由于已在拦截器中获取了 data, 此处实际返回类型为 Blob, 需要强转
   * @param path 文件路径
   */
  download: (path: string) =>
    axios.get(`attachment/${path}`, { responseType: 'blob' }).then((res) => res as never as Blob),
}
