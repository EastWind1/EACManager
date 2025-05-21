import axios, { type AxiosInstance } from 'axios'
import router from '@/router/router.ts'
import { useUIStore } from '@/store/UIStore.ts'
import { useUserStore } from '@/store/UserStore.ts'

// 实例化缓存
let existInstance: AxiosInstance | undefined = undefined

/**
 * 获取 axios 实例
 */
function useAxios(): AxiosInstance {
  if (existInstance) {
    return existInstance
  }
  const { showLoading, hideLoading, warning } = useUIStore()
  const { getToken } = useUserStore()
  const instance = axios.create({
    baseURL: '/api',
  })

  const requestMap = new Map<string, AbortController>()
  // 请求前拦截器
  instance.interceptors.request.use(
    // 显示进度条
    (config) => {
      // 请求防抖
      const key = config.url! + config.method!
      if (requestMap.has(key)) {
        requestMap.get(key)?.abort()
      }
      const abortController = new AbortController()
      config.signal = abortController.signal
      requestMap.set(key, abortController)
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
      // 获取 Token 的请求不处理响应
      if (res.headers['x-auth-token']) {
        return res
      }
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
            if (err.response.data instanceof Blob) {
              const reader = new FileReader()
              reader.onload = () => {
                const json = JSON.parse(reader.result as string)
                warning(json.message? json.message: err.response.statusText)
              }
              reader.readAsText(err.response.data)
            } else {
              warning(err.response.data ? err.response.data.message : err.response.statusText)
            }
            break
          default:
            warning('未处理异常')
        }
      }
      return Promise.reject(err)
    },
  )

  existInstance = instance
  return instance
}

export { useAxios }
