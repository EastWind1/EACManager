import type { Company } from '../model/Company.ts'
import type { QueryParam } from '@/common/model/QueryParam.ts'
import type { PageResult } from '@/common/model/PageResult.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

let http: HttpClient

function getHttp() {
  if (!http) {
    http = new HttpClient('/api/company')
  }
  return http
}

/**
 * 公司 API
 */
const CompanyApi = {
  /**
   * 获取所有公司
   */
  async getAll(param: QueryParam) {
    return await getHttp().get<PageResult<Company>>('', {
      params: param,
    })
  },
  /**
   * 创建公司
   * @param company 公司数据
   */
  async create(company: Company) {
    return await getHttp().post<Company>('', company)
  },

  /**
   * 更新公司
   * @param company 公司数据
   */
  async update(company: Company) {
    return await getHttp().put<Company>('', company)
  },

  /**
   * 禁用公司
   * @param id 公司 ID
   */
  async disable(id: number) {
    return getHttp().delete(`/${id}`)
  },
}
export default CompanyApi
