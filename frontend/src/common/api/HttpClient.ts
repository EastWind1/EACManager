import axios, { type AxiosProgressEvent } from 'axios'
import { useUIStore } from '@/common/store/UIStore'
import { useRouter } from 'vue-router'

export interface HttpConfig {
  url?: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  params?: unknown
  headers?: Record<string, string>
  data?: unknown
  /**
   * 响应类型，默认自动解析；下载、导出等二进制响应需指定为 'blob'
   */
  responseType?: 'json' | 'blob'
  /**
   * 下载进度回调
   */
  onDownloadProgress?: (progressEvent: AxiosProgressEvent) => void
  /**
   * 上传进度回调
   */
  onUploadProgress?: (progressEvent: AxiosProgressEvent) => void
  /**
   * 是否显示加载条，默认为 true
   */
  useLoad?: boolean
}

export interface JSONResponse<T> {
  data: T
  message: string
}

/**
 * 全局 axios 实例，请求地址由 HttpClient 拼接为完整路径
 */
const client = axios.create()
/**
 * 全局 abort，用于取消重复请求
 */
const abortMap = new Map<string, AbortController>()

/**
 * 从错误响应中提取服务端提示信息
 */
async function extractMessage(data: unknown): Promise<string | undefined> {
  try {
    let payload = data
    if (payload instanceof Blob) {
      payload = JSON.parse(await payload.text())
    }
    const body = payload as JSONResponse<unknown>
    return body.message
  } catch {}
  return undefined
}

/**
 * HTTP 客户端
 *
 * 处理响应体、异常
 */
export class HttpClient {
  private readonly baseURL: string

  constructor(baseURL: string) {
    this.baseURL = baseURL
  }

  async request<T>(cfg?: HttpConfig): Promise<T> {
    const uiState = useUIStore()
    const router = useRouter()
    // 加载条
    const useLoad = cfg?.useLoad ?? true
    if (useLoad) {
      uiState.showLoading()
    }
    const method = cfg?.method ?? 'GET'
    // 防抖：取消同一非 POST 请求中尚未完成的请求
    const abort = new AbortController()
    const key = `${method}-${this.baseURL}${cfg?.url}-${JSON.stringify(cfg?.params)}`
    if (method !== 'POST') {
      abortMap.get(key)?.abort()
      abortMap.set(key, abort)
    }

    try {
      return await client
        .request({
          baseURL: this.baseURL,
          url: cfg?.url,
          method,
          params: cfg?.params,
          data: cfg?.data,
          headers: cfg?.headers,
          signal: abort.signal,
          responseType: cfg?.responseType ?? 'json',
          onDownloadProgress: cfg?.onDownloadProgress,
          onUploadProgress: cfg?.onUploadProgress,
        })
        .then((res) => {
          // 响应处理
          const contentType = (res.headers['content-type'] as string | undefined) ?? ''
          if (cfg?.responseType === 'blob') {
            return res.data as T
          }
          if (contentType.includes('json') || (typeof res.data === 'object' && res.data !== null)) {
            return (res.data as JSONResponse<T>).data
          }
          return res.data as T
        })
        .catch(async (err) => {
          // 网络异常、请求被取消等没有响应体，直接抛出
          if (!axios.isAxiosError(err) || !err.response) {
            throw err
          }
          const res = err.response
          // 异常处理
          let msg = '请求异常'
          switch (res.status) {
            case 404:
              msg = '请求地址不存在'
              uiState.warning(msg)
              break
            case 401:
              msg = '未授权'
              uiState.warning(msg)
              await router.push({
                path: '/login',
                query: {
                  redirect: location.pathname + location.search,
                },
              })
              break
            case 403:
              msg = '权限不足'
              uiState.warning(msg)
              break
            case 500:
              msg = (await extractMessage(res.data)) ?? msg
              uiState.warning(msg)
              break
            default:
              uiState.warning(msg)
              break
          }
          throw err
        })
    } finally {
      abortMap.delete(key)
      if (useLoad) {
        uiState.hideLoading()
      }
    }
  }

  async get<T>(url: string, config?: HttpConfig): Promise<T> {
    return await this.request({ ...config, method: 'GET', url })
  }

  async post<T>(url: string, data: unknown, config?: HttpConfig): Promise<T> {
    if (!config) {
      config = {}
    }
    config.data = data
    return await this.request({ ...config, method: 'POST', url })
  }

  async postForm<T>(url: string, data: FormData, config?: HttpConfig): Promise<T> {
    if (!config) {
      config = {}
    }
    config.data = data
    return await this.request({ ...config, method: 'POST', url })
  }

  async put<T>(url: string, data: unknown, config?: HttpConfig): Promise<T> {
    if (!config) {
      config = {}
    }
    config.data = data
    return await this.request({ ...config, method: 'PUT', url })
  }

  async delete<T>(url: string, config?: HttpConfig): Promise<T> {
    return await this.request({ ...config, method: 'DELETE', url })
  }
}
