import type { ServiceBill, ServiceBillQueryParam } from '../model/ServiceBill.ts'
import type { PageResult } from '@/common/model/PageResult.ts'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

const http = new HttpClient('/api/serviceBill')

/**
 * 服务单 API
 */
const ServiceBillApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  async getByQueryParam(queryParam: ServiceBillQueryParam) {
    return await http.post<PageResult<ServiceBill>>(`/query`, queryParam)
  },
  /**
   * 根据 id 获取
   * @param id 单据 ID
   */
  async getById(id: number) {
    return await http.get<ServiceBill>(`/${id}`)
  },
  /**
   * 导入
   */
  async import(file: File) {
    return await http.postForm<ServiceBill>(`/import`, {
      file,
    })
  },
  /**
   * 新建
   * @param serviceBill 单据
   */
  async create(serviceBill: ServiceBill) {
    return await http.post<ServiceBill>('', serviceBill)
  },
  /**
   * 保存
   * @param serviceBill 单据
   */
  async save(serviceBill: ServiceBill) {
    return await http.put<ServiceBill>('', serviceBill)
  },
  /**
   * 删除
   * @param ids 单据 ID 列表
   */
  async delete(ids: number[]) {
    return await http.delete<ActionsResult<number, void>>('', { data: ids })
  },
  /**
   * 处理
   * @param ids 单据 ID 列表
   */
  async process(ids: number[]) {
    return await http.put<ActionsResult<number, void>>(`/process`, ids)
  },
  /**
   * 处理完成
   * @param ids 单据 ID 列表
   * @param processedDate 处理完成时间
   */
  async processed(ids: number[], processedDate: Date) {
    return await http.put<ActionsResult<number, void>>(`/processed`, { ids, processedDate })
  },
  /**
   * 完成
   * @param ids 单据 ID 列表
   * @param finishedDate 完成时间
   */
  async finish(ids: number[], finishedDate: Date) {
    return await http.put<ActionsResult<number, void>>(`/finish`, { ids, finishedDate })
  },
  /**
   * 导出
   * @param ids 单据 ID 列表
   */
  async export(ids: number[]) {
    return await http.post<Blob>(`/export`, ids)
  },
  /**
   * 取消处理
   * @param ids 单据 ID 列表
   */
  async cancelProcess(ids: number[]) {
    return await http.put<ActionsResult<number, void>>(`/cancel-process`, ids)
  },
  /**
   * 取消处理完成
   * @param ids 单据 ID 列表
   */
  async cancelProcessed(ids: number[]) {
    return await http.put<ActionsResult<number, void>>(`/cancel-processed`, ids)
  },
  /**
   * 取消完成
   * @param ids 单据 ID 列表
   */
  async cancelFinish(ids: number[]) {
    return await http.put<ActionsResult<number, void>>(`/cancel-finish`, ids)
  },
}
export default ServiceBillApi