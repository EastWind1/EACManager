import type {
  Assignee,
  ServiceBill,
  ServiceBillQueryParam,
  ServiceBillStateValue,
} from '../model/ServiceBill'
import type { PageResult } from '@/common/model/PageResult'
import type { ActionsResult } from '@/common/model/ActionsResult'
import { HttpClient } from '@/common/api/HttpClient'

const http = new HttpClient('/api/serviceBill')
const debouncedRequest = http.debounce(http.request)

/**
 * 服务单 API
 */
const ServiceBillApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  async getByQueryParam(queryParam: ServiceBillQueryParam) {
    return await debouncedRequest<PageResult<ServiceBill>>({
      method: 'POST',
      url: `/query`,
      data: queryParam,
    })
  },
  /**
   * 根据 id 获取
   * @param id 单据 ID
   */
  async getById(id: number) {
    return await http.request<ServiceBill>({ method: 'GET', url: `/${id}` })
  },
  /**
   * 指派候选人（启用用户）
   */
  async getAssignees() {
    return await http.request<Assignee[]>({ method: 'GET', url: `/assignees` })
  },
  /**
   * 指派/改派负责人
   * @param ids 单据 ID 列表
   * @param assigneeIds 负责人 ID 列表，空数组表示取消指派
   */
  async assign(ids: number[], assigneeIds: number[]) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/assign`,
      data: { ids, assigneeIds },
    })
  },
  /**
   * 导入
   */
  async import(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return await http.request<ServiceBill>({ method: 'POST', url: `/import`, data: formData })
  },
  /**
   * 新建
   * @param serviceBill 单据
   */
  async create(serviceBill: ServiceBill) {
    return await http.request<ServiceBill>({ method: 'POST', url: '', data: serviceBill })
  },
  /**
   * 保存
   * @param serviceBill 单据
   */
  async save(serviceBill: ServiceBill) {
    return await http.request<ServiceBill>({ method: 'PUT', url: '', data: serviceBill })
  },
  /**
   * 删除
   * @param ids 单据 ID 列表
   */
  async delete(ids: number[]) {
    return await http.request<ActionsResult<number, void>>({
      method: 'DELETE',
      url: '',
      data: ids,
    })
  },
  /**
   * 处理
   * @param ids 单据 ID 列表
   */
  async process(ids: number[]) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/process`,
      data: ids,
    })
  },
  /**
   * 处理完成
   * @param ids 单据 ID 列表
   * @param processedDate 处理完成时间
   */
  async processed(ids: number[], processedDate: Date) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/processed`,
      data: { ids, processedDate },
    })
  },
  /**
   * 完成
   * @param ids 单据 ID 列表
   * @param finishedDate 完成时间
   */
  async finish(ids: number[], finishedDate: Date) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/finish`,
      data: { ids, finishedDate },
    })
  },
  /**
   * 导出
   * @param ids 单据 ID 列表
   */
  async export(ids: number[], onProgress?: (e: { loaded?: number; total?: number }) => void) {
    return await http.request<Blob>({
      method: 'POST',
      url: `/export`,
      data: ids,
      responseType: 'blob',
      onDownloadProgress: onProgress,
    })
  },
  /**
   * 取消处理
   * @param ids 单据 ID 列表
   */
  async cancelProcess(ids: number[]) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/cancel-process`,
      data: ids,
    })
  },
  /**
   * 取消处理完成
   * @param ids 单据 ID 列表
   */
  async cancelProcessed(ids: number[]) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/cancel-processed`,
      data: ids,
    })
  },
  /**
   * 取消完成
   * @param ids 单据 ID 列表
   */
  async cancelFinish(ids: number[]) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/cancel-finish`,
      data: ids,
    })
  },
  /**
   * 按状态统计服务单数量
   */
  async countByState() {
    return await debouncedRequest<{ [key in ServiceBillStateValue]?: number }>({
      method: 'GET',
      url: `/countByState`,
    })
  },
  /**
   * 按月统计服务单金额
   */
  async sumTotalAmountByMonth() {
    return await debouncedRequest<{ month: string; amount: number }[]>({
      method: 'GET',
      url: `/totalAmountGroupByMonth`,
    })
  },
}
export default ServiceBillApi
