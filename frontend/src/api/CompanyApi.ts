import type { AxiosInstance } from 'axios'
import { useAxios } from '@/api/AxiosConfig.ts'
import type { Company } from '@/model/Company.ts'

let axiosInstance: AxiosInstance

function getAxios() {
  if (!axiosInstance) {
    axiosInstance = useAxios('/api/company')
  }
  return axiosInstance
}

/**
 * 公司 API
 */
const CompanyApi = {
  /**
   * 获取所有公司
   */
  async getAll() {
    const res = await getAxios().get('')
    return res.data as Company[]
  },
  /**
   * 创建公司
   * @param company 公司数据
   */
  async create(company: Company) {
    const res = await getAxios().post('', company)
    return res.data as Company
  },

  /**
   * 更新公司
   * @param company 公司数据
   */
  async update(company: Company) {
    const res = await getAxios().put('', company)
    return res.data as Company
  },

  /**
   * 禁用公司
   * @param id 公司 ID
   */
  async disable(id: number) {
    return getAxios().delete(`/${id}`)
  }
}
export default CompanyApi
