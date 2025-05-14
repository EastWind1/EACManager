import axios, { type AxiosInstance } from 'axios'
import pinia from '@/stores'
import router from '@/router/router.ts'
import { useUIStore } from '@/stores/UIStore.ts'
import { useTokenStore } from '@/stores/TokenStore.ts'

// 实例化缓存
let existInstance: AxiosInstance | undefined = undefined

/**
 * 获取 axios 实例
 */
function useAxios(): AxiosInstance {
  if (existInstance) {
    return existInstance
  }
  const { showLoading, hideLoading, warning } = useUIStore(pinia)
  const { getToken } = useTokenStore(pinia)
  const instance = axios.create({
    // 从 .env 文件读取后端地址
    baseURL: `${import.meta.env.VITE_BACKGROUND_URL}`,
  })
  // 请求前拦截器
  instance.interceptors.request.use(
    // 显示进度条
    (config) => {
      // 设置 token
      const token = getToken()
      if (token) {
        config.headers.Authorization = `Bearer ${getToken()}`
      }
      // 设置加载条
      showLoading()
      return config
    },
  )
  // 响应后拦截器
  instance.interceptors.response.use(
    (res) => {
      // 隐藏进度条
      hideLoading()
      // 直接获取 data
      return res.data
    },
    // 全局异常处理
    (err) => {
      hideLoading()
      if (err.code === 'ERR_NETWORK') {
        warning('网络异常')
      } else {
        switch (err.status) {
          case 401:
            warning('请重新登录')
            router.push('/login').then()
            break
          case 403:
            warning('权限不足')
            break
          case 404:
            warning('请求地址错误')
            break
          case 500:
            warning(err.response.data.message)
            break
          default:
            warning('未处理异常')
        }
      }
      return Promise.reject(err.response?.data)
    },
  )

  existInstance = instance
  return instance
}

export { useAxios }
