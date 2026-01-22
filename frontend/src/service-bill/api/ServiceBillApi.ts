import type { ServiceBill, ServiceBillQueryParam } from '../model/ServiceBill.ts'
import type { PageResult } from '@/common/model/PageResult.ts'
import { useAxios } from '@/common/api/AxiosConfig.ts'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'
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
  async getByQueryParam(queryParam: ServiceBillQueryParam) {
    const res = await getAxios().post(`/query`, queryParam)
    return res.data as PageResult<ServiceBill>
  },
  /**
   * 根据 id 获取
   * @param id 单据 ID
   */
  async getById(id: number) {
    const res = await getAxios().get(`/${id}`)
    return res.data as ServiceBill
  },
  /**
   * 导入
   */
  async import(file: File) {
    const res = await getAxios().postForm(`/import`, {
      file,
    })
    return res.data as ServiceBill
  },
  /**
   * 新建
   * @param serviceBill 单据
   */
  async create(serviceBill: ServiceBill) {
    const res = await getAxios().post('', serviceBill)
    return res.data as ServiceBill
  },
  /**
   * 保存
   * @param serviceBill 单据
   */
  async save(serviceBill: ServiceBill) {
    const res = await getAxios().put('', serviceBill)
    return res.data as ServiceBill
  },
  /**
   * 删除
   * @param ids 单据 ID 列表
   */
  async delete(ids: number[]) {
    const res = await getAxios().delete('', { data: ids })
    return res.data as ActionsResult<number, void>
  },
  /**
   * 处理
   * @param ids 单据 ID 列表
   */
  async process(ids: number[]) {
    const res = await getAxios().put(`/process`, ids)
    return res.data as ActionsResult<number, void>
  },
  /**
   * 处理完成
   * @param ids 单据 ID 列表
   * @param processedDate 处理完成时间
   */
  async processed(ids: number[], processedDate: Date) {
    const res = await getAxios().put(`/processed`, { ids, processedDate })
    return res.data as ActionsResult<number, void>
  },
  /**
   * 完成
   * @param ids 单据 ID 列表
   * @param finishedDate 完成时间
   */
  async finish(ids: number[], finishedDate: Date) {
    const res = await getAxios().put(`/finish`, { ids, finishedDate })
    return res.data as ActionsResult<number, void>
  },
  /**
   * 导出
   * @param ids 单据 ID 列表
   */
  async export(ids: number[]) {
    const res = await getAxios().post(`/export`, ids, { responseType: 'blob' })
    return res as never as Blob
  },
}
export default ServiceBillApi
