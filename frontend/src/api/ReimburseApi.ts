import type { PageResult } from '@/model/PageResult.ts'
import { useAxios } from '@/api/AxiosConfig.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import type { AxiosInstance } from 'axios'
import type { Reimbursement, ReimburseQueryParam } from '@/model/Reimbursement.ts'

let axiosInstance: AxiosInstance

function getAxios() {
  if (!axiosInstance) {
    axiosInstance = useAxios('/api/reimburse')
  }
  return axiosInstance
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
    const res = await getAxios().post(`/query`, queryParam)
    return res.data as PageResult<Reimbursement>
  },

  /**
   * 根据 id 获取
   * @param id 单据 ID
   */
  async getById(id: number) {
    const res = await getAxios().get(`/${id}`)
    return res.data as Reimbursement
  },

  /**
   * 新建
   * @param reimbursement 单据
   */
  async create(reimbursement: Reimbursement) {
    const res = await getAxios().post('', reimbursement)
    return res.data as Reimbursement
  },

  /**
   * 保存
   * @param reimbursement 单据
   */
  async save(reimbursement: Reimbursement) {
    const res = await getAxios().put('', reimbursement)
    return res.data as Reimbursement
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
   * 完成
   * @param ids 单据 ID 列表
   */
  async finish(ids: number[]) {
    const res = await getAxios().put(`/finish`, ids)
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
export default ReimburseApi
