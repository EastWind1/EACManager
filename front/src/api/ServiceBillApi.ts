import type { ServiceBill, ServiceBillQueryParam } from '@/model/ServiceBill.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { useAxios } from '@/api/AxiosConfig.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import type { Attachment } from '@/model/Attachment.ts'

const prefix = 'serviceBill'
const ServiceBillApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  getByQueryParam: (queryParam: ServiceBillQueryParam) =>
    useAxios()
      .post(`${prefix}/query`, queryParam)
      .then((res) => res.data as PageResult<ServiceBill>),
  /**
   * 根据 id 获取
   * @param id 订单 ID
   */
  getById: (id: number) =>
    useAxios()
      .get(`${prefix}/${id}`)
      .then((res) => res.data as ServiceBill),
  /**
   * 导入
   */
  import: (file: File) =>
    useAxios()
      .postForm(`${prefix}/import`, {
        file,
      })
      .then((res) => res.data as ServiceBill),
  /**
   * 添加附件
   */
  addAttachment: (id: number, file: File) =>
    useAxios()
      .postForm(`${prefix}/${id}/attachment`, {
        file,
      })
      .then((res) => res.data as Attachment),
  /**
   * 新建
   * @param serviceBill 订单
   */
  create: (serviceBill: ServiceBill) =>
    useAxios()
      .post(`${prefix}`, serviceBill)
      .then((res) => res.data as ServiceBill),
  /**
   * 删除
   * @param id 订单 ID
   */
  /**
   * 保存
   * @param serviceBill 订单
   */
  save: (serviceBill: ServiceBill) =>
    useAxios()
      .put(`${prefix}`, serviceBill)
      .then((res) => res.data as ServiceBill),
  /**
   * 删除
   * @param ids 订单 ID 列表
   */
  delete: (ids: number[]) =>
    useAxios()
      .delete(`${prefix}`, { data: ids })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 处理
   * @param ids 订单 ID 列表
   */
  process: (ids: number[]) =>
    useAxios()
      .put(`${prefix}/process`, ids)
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 处理完成
   * @param ids 订单 ID 列表
   * @param processedDate 处理完成时间
   */
  processed: (ids: number[], processedDate: Date) =>
    useAxios()
      .put(`${prefix}/processed`, { ids, processedDate })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 完成
   * @param ids 订单 ID 列表
   */
  finish: (ids: number[]) =>
    useAxios()
      .put(`${prefix}/finish`, ids)
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 导出
   * @param ids 订单 ID 列表
   */
  export: (ids: number[]) =>
    useAxios()
      .post(`${prefix}/export`, ids, { responseType: 'blob' })
      .then((res) => res as never as Blob),
}
export default ServiceBillApi
