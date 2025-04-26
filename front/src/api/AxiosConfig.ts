import axios, { type AxiosInstance } from 'axios'
import { useGlobalStore } from '@/stores/global.ts'
import pinia from '@/stores'

// 实例化缓存
let existInstance: AxiosInstance | undefined = undefined


/**
 * 获取 axios 实例
 */
function useAxios(): AxiosInstance {
  if (existInstance) {
    return existInstance
  }
  // 全局状态
  const { showLoading, hideLoading, warning } = useGlobalStore(pinia)

  const instance = axios.create({
    // 从 .env 文件读取后端地址
    baseURL: `${import.meta.env.VITE_BACKGROUND_URL}`,
  })
  // 请求前拦截器
  instance.interceptors.request.use(
    // 显示进度条
    config => {
      showLoading()
      return config
    }
  )
  // 响应后拦截器
  instance.interceptors.response.use(
    res => {
      // 隐藏进度条
      hideLoading()
      // 直接获取 data
      return res.data
    },
    // 全局异常处理
    err => {
      hideLoading()
      switch (err.code) {
        case 'ERR_NETWORK':
          warning('网络异常');
          break
        case 'ERR_BAD_REQUEST':
          warning(err.response.data.message)
          break
        case 'ERR_BAD_RESPONSE':
          warning(err.response.data.message)
          break
        default:
          warning('未处理异常' + err.code)
      }
      return Promise.reject(err.response?.data)
    }
  )

  existInstance = instance
  return instance
}
export { useAxios }
