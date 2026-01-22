import { useAxios } from '@/common/api/AxiosConfig.ts'
import { type ServiceBillStateValue } from '@/service-bill/model/ServiceBill.ts'
import type { AxiosInstance } from 'axios'

let axiosInstance: AxiosInstance

function getAxios() {
  if (!axiosInstance) {
    axiosInstance = useAxios('/api/statistic')
  }
  return axiosInstance
}

/**
 * 统计 API
 */
export const StatisticApi = {
  /**
   * 统计服务单状态数量
   */
  async countBillsByState() {
    const res = await getAxios().get(`/billCountByState`)
    return res.data as {
      [key in ServiceBillStateValue]?: number
    }
  },
  /**
   * 按月统计服务单收入总金额
   */
  async sumTotalAmountByMonth() {
    const res = await getAxios().get(`/billTotalAmountGroupByMonth`)
    return res.data as {
      month: string
      amount: number
    }[]
  },
}
