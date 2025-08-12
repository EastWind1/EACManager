import type { ServiceBill, ServiceBillQueryParam } from '@/model/ServiceBill.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { useAxios } from '@/api/AxiosConfig.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import type { Attachment } from '@/model/Attachment.ts'
import type { AxiosInstance } from 'axios'

let axiosInstance: AxiosInstance

function getAxios() {
  if (!axiosInstance) {
    axiosInstance = useAxios('/api/serviceBill')
  }
  return axiosInstance
}

/**
 * 服务单 API
 */
const ServiceBillApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  getByQueryParam: (queryParam: ServiceBillQueryParam) =>
    getAxios()
      .post(`/query`, queryParam)
      .then((res) => res.data as PageResult<ServiceBill>),
  /**
   * 根据 id 获取
   * @param id 订单 ID
   */
  getById: (id: number) =>
    getAxios()
      .get(`/${id}`)
      .then((res) => res.data as ServiceBill),
  /**
   * 导入
   */
  import: (file: File) =>
    getAxios()
      .postForm(`/import`, {
        file,
      })
      .then((res) => res.data as ServiceBill),
  /**
   * 新建
   * @param serviceBill 订单
   */
  create: (serviceBill: ServiceBill) =>
    getAxios()
      .post('', serviceBill)
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
    getAxios()
      .put('', serviceBill)
      .then((res) => res.data as ServiceBill),
  /**
   * 删除
   * @param ids 订单 ID 列表
   */
  delete: (ids: number[]) =>
    getAxios()
      .delete('', { data: ids })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 处理
   * @param ids 订单 ID 列表
   */
  process: (ids: number[]) =>
    getAxios()
      .put(`/process`, ids)
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 处理完成
   * @param ids 订单 ID 列表
   * @param processedDate 处理完成时间
   */
  processed: (ids: number[], processedDate: Date) =>
    getAxios()
      .put(`/processed`, { ids, processedDate })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 完成
   * @param ids 订单 ID 列表
   */
  finish: (ids: number[]) =>
    getAxios()
      .put(`/finish`, ids)
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 导出
   * @param ids 订单 ID 列表
   */
  export: (ids: number[]) =>
    getAxios()
      .post(`/export`, ids, { responseType: 'blob' })
      .then((res) => res as never as Blob),
}
export default ServiceBillApi
