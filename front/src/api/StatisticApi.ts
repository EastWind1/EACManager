import { useAxios } from '@/api/AxiosConfig.ts'
import { ServiceBillState } from '@/model/ServiceBill.ts'

const prefix = '/statistic'

/**
 * 统计 API
 */
export const StatisticApi = {
  /**
   * 统计服务单状态数量
   */
  countBillsByState: () =>
    useAxios().get(`${prefix}/billCountByState`).then((res) => res.data as  { [key in ServiceBillState]?: number }),
  /**
   * 按月统计服务单收入总金额
   */
  sumTotalAmountByMonth: () =>
    useAxios().get(`${prefix}/billTotalAmountGroupByMonth`).then((res) => res.data as {month: string, amount: number}[])
}
