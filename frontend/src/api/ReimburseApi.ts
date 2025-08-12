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
  getByQueryParam: (queryParam: ReimburseQueryParam) =>
    getAxios()
      .post(`/query`, queryParam)
      .then((res) => res.data as PageResult<Reimbursement>),
  /**
   * 根据 id 获取
   * @param id 单据 ID
   */
  getById: (id: number) =>
    getAxios()
      .get(`/${id}`)
      .then((res) => res.data as Reimbursement),
  /**
   * 新建
   * @param reimbursement 单据
   */
  create: (reimbursement: Reimbursement) =>
    getAxios()
      .post('', reimbursement)
      .then((res) => res.data as Reimbursement),
  /**
   * 保存
   * @param reimbursement 单据
   */
  save: (reimbursement: Reimbursement) =>
    getAxios()
      .put('', reimbursement)
      .then((res) => res.data as Reimbursement),
  /**
   * 删除
   * @param ids 单据 ID 列表
   */
  delete: (ids: number[]) =>
    getAxios()
      .delete('', { data: ids })
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 处理
   * @param ids 单据 ID 列表
   */
  process: (ids: number[]) =>
    getAxios()
      .put(`/process`, ids)
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 完成
   * @param ids 单据 ID 列表
   */
  finish: (ids: number[]) =>
    getAxios()
      .put(`/finish`, ids)
      .then((res) => res.data as ActionsResult<number, void>),
  /**
   * 导出
   * @param ids 单据 ID 列表
   */
  export: (ids: number[]) =>
    getAxios()
      .post(`/export`, ids, { responseType: 'blob' })
      .then((res) => res as never as Blob),
}
export default ReimburseApi
