import axios, { type AxiosInstance } from 'axios'
import { useUIStore } from '@/common/store/UIStore.ts'
import router from '@/router.ts'

/**
 * 获取 axios 实例
 */
function useAxios(baseURL: string): AxiosInstance {
  const instance = axios.create({
    baseURL,
  })
  const { showLoading, hideLoading, warning } = useUIStore()
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
    async (err) => {
      hideLoading()
      if (err.code === 'ERR_NETWORK') {
        warning('网络异常')
      } else {
        switch (err.status) {
          case 401:
            warning('未登录或登录过期')
            router
              .push({
                path: '/login',
                query: {
                  redirect: location.pathname + location.search,
                },
              })
              .then()
            break
          case 403:
            warning('权限不足')
            break
          case 404:
            warning('请求地址不存在')
            break
          default:
            // 下载时异常需要单独处理
            if (err.response.data instanceof Blob) {
              const bytes = err.response.data as Blob
              const json = await bytes.text()
              err.response.data = JSON.parse(json)
            }
            warning(err.response.data ? err.response.data.message : err.response.statusText)
            break
        }
      }
      return Promise.reject(err)
    },
  )

  return instance
}

export { useAxios }
