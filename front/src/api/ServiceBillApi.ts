import { useAxios } from "@/api/AxiosConfig.ts";
import type { ServiceBill } from "@/entity/ServiceBill.ts";

const axios = useAxios()

export const ServiceBillApi = {
  /**
   * 获取所有订单
   */
  getAll: () => axios.get("serviceBill")
    .then(res =>
      res.data as ServiceBill[]
    ),
  /**
   * 保存
   * @param serviceBill 订单
   */
  save: (serviceBill: ServiceBill) => axios.post("serviceBill", serviceBill)
    .then(res =>
      res.data as ServiceBill
    ),
  /**
   * 删除
   * @param id 订单 ID
   */
  delete: (id: number) => axios.delete(`serviceBill/${id}}`)
}
