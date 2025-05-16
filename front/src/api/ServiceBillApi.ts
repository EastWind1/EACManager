import type { ServiceBill, ServiceBillQueryParam } from '@/model/ServiceBill.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { useAxios } from '@/api/AxiosConfig.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'

const axios = useAxios()
const prefix = 'serviceBill'
const ServiceBillApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  getByQueryParam: (queryParam: ServiceBillQueryParam) =>
    axios.post(`${prefix}/query`, queryParam).then((res) => res.data as PageResult<ServiceBill>),
  /**
   * 根据 id 获取
   * @param id 订单 ID
   */
  getById: (id: number) => axios.get(`${prefix}/${id}`).then((res) => res.data as ServiceBill),
  /**
   * 导入
   */
  import: (file: File) =>
    axios
      .postForm(`${prefix}/import`, {
        file,
      })
      .then((res) => res.data as ServiceBill),
  /**
   * 新建
   * @param serviceBill 订单
   */
  create: (serviceBill: ServiceBill) =>
    axios.post(`${prefix}`, serviceBill).then((res) => res.data as ServiceBill),
  /**
   * 删除
   * @param id 订单 ID
   */
  /**
   * 保存
   * @param serviceBill 订单
   */
  save: (serviceBill: ServiceBill) =>
    axios.put(`${prefix}`, serviceBill).then((res) => res.data as ServiceBill),
  /**
   * 删除
   * @param ids 订单 ID 列表
   */
  delete: (ids: number[]) =>
    axios
      .delete(`${prefix}`, { data: ids })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 处理
   * @param ids 订单 ID 列表
   */
  process: (ids: number[]) =>
    axios
      .put(`${prefix}/process`, { data: ids })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 处理完成
   * @param ids 订单 ID 列表
   */
  processed: (ids: number[]) =>
    axios
      .put(`${prefix}/processed`, { data: ids })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 完成
   * @param ids 订单 ID 列表
   */
  finish: (ids: number[]) =>
    axios
      .put(`${prefix}/finish`, { data: ids })
      .then((res) => res.data as ActionsResult<number, void>),
}
export default ServiceBillApi
