import { type ServiceBillStateValue } from '@/service-bill/model/ServiceBill'
import { type ReimburseStateValue } from '@/reimburse/model/Reimbursement'
import { HttpClient } from '@/common/api/HttpClient'

const serviceBillHttp = new HttpClient('/api/serviceBill')
const reimburseHttp = new HttpClient('/api/reimburse')

/**
 * 统计 API
 */
export const StatisticApi = {
  /**
   * 统计服务单状态数量
   */
  async countBillsByState() {
    return await serviceBillHttp.get<{ [key in ServiceBillStateValue]?: number }>(`/countByState`)
  },
  /**
   * 按月统计服务单收入总金额
   */
  async sumTotalAmountByMonth() {
    return await serviceBillHttp.get<{ month: string; amount: number }[]>(`/totalAmountGroupByMonth`)
  },
  /**
   * 统计报销单状态数量
   */
  async countReimbursesByState() {
    return await reimburseHttp.get<{ [key in ReimburseStateValue]?: number }>(`/countByState`)
  },
  /**
   * 按月统计报销单金额总和
   */
  async sumReimburseTotalAmountByMonth() {
    return await reimburseHttp.get<{ month: string; amount: number }[]>(`/totalAmountGroupByMonth`)
  },
}
