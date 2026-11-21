import type { Company } from '../model/Company'
import type { QueryParam } from '@/common/model/QueryParam'
import type { PageResult } from '@/common/model/PageResult'
import { HttpClient } from '@/common/api/HttpClient'

const http = new HttpClient('/api/company')
const debouncedRequest = http.debounce(http.request)

/**
 * 公司 API
 */
const CompanyApi = {
  /**
   * 获取所有公司
   */
  async getAll(param: QueryParam) {
    return await debouncedRequest<PageResult<Company>>({ method: 'GET', url: '', params: param })
  },
  /**
   * 创建公司
   * @param company 公司数据
   */
  async create(company: Company) {
    return await http.request<Company>({ method: 'POST', url: '', data: company })
  },

  /**
   * 更新公司
   * @param company 公司数据
   */
  async update(company: Company) {
    return await http.request<Company>({ method: 'PUT', url: '', data: company })
  },

  /**
   * 禁用公司
   * @param id 公司 ID
   */
  async disable(id: number) {
    return await http.request({ method: 'DELETE', url: `/${id}` })
  },
}
export default CompanyApi
