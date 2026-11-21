import type { PageResult } from '@/common/model/PageResult'
import type { ActionsResult } from '@/common/model/ActionsResult'
import type { Reimburse, ReimburseQueryParam, ReimburseStateValue } from '../model/Reimburse'
import { HttpClient } from '@/common/api/HttpClient'

const http = new HttpClient('/api/reimburse')
const debouncedRequest = http.debounce(http.request)

/**
 * 报销单 API
 */
const ReimburseApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  async getByQueryParam(queryParam: ReimburseQueryParam) {
    return await debouncedRequest<PageResult<Reimburse>>({
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
    return await http.request<Reimburse>({ method: 'GET', url: `/${id}` })
  },

  /**
   * 导入
   */
  async import(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return await http.request<Reimburse>({ method: 'POST', url: `/import`, data: formData })
  },

  /**
   * 新建
   * @param reimbursement 单据
   */
  async create(reimbursement: Reimburse) {
    return await http.request<Reimburse>({ method: 'POST', url: '', data: reimbursement })
  },

  /**
   * 保存
   * @param reimbursement 单据
   */
  async save(reimbursement: Reimburse) {
    return await http.request<Reimburse>({ method: 'PUT', url: '', data: reimbursement })
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
   * 完成
   * @param ids 单据 ID 列表
   */
  async finish(ids: number[]) {
    return await http.request<ActionsResult<number, void>>({
      method: 'PUT',
      url: `/finish`,
      data: ids,
    })
  },

  /**
   * 取消处理状态
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
   * 取消处理完成状态
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
   * 按状态统计报销单数量
   */
  async countByState() {
    return await debouncedRequest<{ [key in ReimburseStateValue]?: number }>({
      method: 'GET',
      url: `/countByState`,
    })
  },

  /**
   * 按月统计报销单金额
   */
  async sumTotalAmountByMonth() {
    return await debouncedRequest<{ month: string; amount: number }[]>({
      method: 'GET',
      url: `/totalAmountGroupByMonth`,
    })
  },
}
export default ReimburseApi
