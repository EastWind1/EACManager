import { useAxios } from '@/api/AxiosConfig.ts'

const axios = useAxios()

export const ServiceBillApi = {
  getAll: () => {
    return axios.get('serviceBill')
  }
}
