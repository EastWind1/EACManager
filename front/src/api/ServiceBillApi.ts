import type { ServiceBill, ServiceBillQueryParam } from '@/model/ServiceBill.ts'
import type { PageResult } from '@/model/PageResult.ts'
import { useAxios } from '@/api/AxiosConfig.ts'
const axios = useAxios()

const ServiceBillApi = {
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
export default ServiceBillApi
