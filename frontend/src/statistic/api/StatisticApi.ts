
import { type ServiceBillStateValue } from '@/service-bill/model/ServiceBill.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

let http: HttpClient

function getHttp() {
  if (!http) {
    http = new HttpClient('/api/statistic')
  }
  return http
}

/**
 * 统计 API
 */
export const StatisticApi = {
  /**
   * 统计服务单状态数量
   */
  async countBillsByState() {
    return await getHttp().get<{[key in ServiceBillStateValue]?: number}>(`/billCountByState`)
  },
  /**
   * 按月统计服务单收入总金额
   */
  async sumTotalAmountByMonth() {
    return await getHttp().get<{month: string, amount: number}[]>(`/billTotalAmountGroupByMonth`)
  },
}