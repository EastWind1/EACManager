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
  getAll: () =>
    getAxios()
      .get('')
      .then((res) => res.data as Company[]),

  /**
   * 创建公司
   * @param company 公司数据
   */
  create: (company: Company) =>
    getAxios()
      .post('', company)
      .then((res) => res.data as Company),

  /**
   * 更新公司
   * @param company 公司数据
   */
  update: (company: Company) =>
    getAxios()
      .put('', company)
      .then((res) => res.data as Company),

  /**
   * 禁用公司
   * @param id 公司 ID
   */
  disable: (id: number) => getAxios().delete(`/${id}`),
}
export default CompanyApi
