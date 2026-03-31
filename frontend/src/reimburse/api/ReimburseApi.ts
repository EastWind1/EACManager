import type { PageResult } from '@/common/model/PageResult.ts'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'
import type { Reimbursement, ReimburseQueryParam } from '../model/Reimbursement.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

let http: HttpClient

function getHttp() {
  if (!http) {
    http = new HttpClient('/api/reimburse')
  }
  return http
}

/**
 * 报销单 API
 */
const ReimburseApi = {
  /**
   * 条件查询
   * @param queryParam 查询参数
   */
  async getByQueryParam(queryParam: ReimburseQueryParam) {
    return await getHttp().post<PageResult<Reimbursement>>(`/query`, queryParam)
  },

  /**
   * 根据 id 获取
   * @param id 单据 ID
   */
  async getById(id: number) {
    return  await getHttp().get<Reimbursement>(`/${id}`)
  },

  /**
   * 新建
   * @param reimbursement 单据
   */
  async create(reimbursement: Reimbursement) {
    return await getHttp().post<Reimbursement>('', reimbursement)
  },

  /**
   * 保存
   * @param reimbursement 单据
   */
  async save(reimbursement: Reimbursement) {
    return await getHttp().put<Reimbursement>('', reimbursement)
  },

  /**
   * 删除
   * @param ids 单据 ID 列表
   */
  async delete(ids: number[]) {
    return await getHttp().delete<ActionsResult<number, void>>('', { data: ids })
  },

  /**
   * 处理
   * @param ids 单据 ID 列表
   */
  async process(ids: number[]) {
    return await getHttp().put<ActionsResult<number, void>>(`/process`, ids)
  },

  /**
   * 完成
   * @param ids 单据 ID 列表
   */
  async finish(ids: number[]) {
    return await getHttp().put<ActionsResult<number, void>>(`/finish`, ids)
  },

  /**
   * 导出
   * @param ids 单据 ID 列表
   */
  async export(ids: number[]) {
    return await getHttp().post<Blob>(`/export`, ids)
  },
}
export default ReimburseApi