import { useAxios } from '@/api/AxiosConfig.ts'
import { type ServiceBillStateValue } from '@/model/ServiceBill.ts'
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
  countBillsByState: () =>
    getAxios().get(`/billCountByState`).then((res) => res.data as  { [key in ServiceBillStateValue]?: number }),
  /**
   * 按月统计服务单收入总金额
   */
  sumTotalAmountByMonth: () =>
    getAxios().get(`/billTotalAmountGroupByMonth`).then((res) => res.data as {month: string, amount: number}[])
}
