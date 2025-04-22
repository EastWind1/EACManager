import axios from 'axios'
import { useNotify } from "@/utils/notify.ts";

// 实例化缓存
let existInstance = undefined

/**
 * 获取 axios 实例
 */
function useAxios() {
  if (existInstance) {
    return existInstance
  }

  const {warning} = useNotify()

  const instance = axios.create({
    // 从 .env 文件读取后端地址
    baseURL: `${import.meta.env.VITE_BACKGROUND_URL}`,
  })
  instance.interceptors.response.use(
    // 直接获取 data
    res => res.data,
    // 全局异常处理
    err => {
      switch (err.code) {
        case 'ERR_NETWORK':
          warning('网络异常');
          break
        case 'ERR_BAD_RESPONSE':
          warning(err.response.data.message)
          break
        default:
          warning('未处理异常' + err.code)
      }
      return Promise.reject(err.response.data)
    }
  )

  existInstance = instance
  return instance
}
export { useAxios }
