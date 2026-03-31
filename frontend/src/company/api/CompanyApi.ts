import type { Company } from '../model/Company.ts'
import type { QueryParam } from '@/common/model/QueryParam.ts'
import type { PageResult } from '@/common/model/PageResult.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

const http = new HttpClient('/api/company')

/**
 * 公司 API
 */
const CompanyApi = {
  /**
   * 获取所有公司
   */
  async getAll(param: QueryParam) {
    return await http.get<PageResult<Company>>('', {
      params: param,
    })
  },
  /**
   * 创建公司
   * @param company 公司数据
   */
  async create(company: Company) {
    return await http.post<Company>('', company)
  },

  /**
   * 更新公司
   * @param company 公司数据
   */
  async update(company: Company) {
    return await http.put<Company>('', company)
  },

  /**
   * 禁用公司
   * @param id 公司 ID
   */
  async disable(id: number) {
    return http.delete(`/${id}`)
  },
}
export default CompanyApi
