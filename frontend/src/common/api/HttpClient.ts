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
  /**
   * 取消信号
   */
  signal?: AbortSignal,
  /**
   * 是否内部处理异常
   */
  handleError?: boolean
}

export interface JSONResponse<T> {
  data: T
  message: string
}

/**
 * 发起一次请求的方法签名
 */
export type RequestMethod = <T>(cfg?: HttpConfig) => Promise<T>

/**
 * 防抖包装后的请求方法签名；被后续同 key 请求取代时返回 undefined
 */
export type DebouncedRequestMethod = <T>(cfg: HttpConfig) => Promise<T | undefined>

/**
 * 全局 axios 实例，请求地址由 HttpClient 拼接为完整路径
 */
const client = axios.create()

/**
 * 并发请求计数。
 */
let loadingRequests = 0

function beginLoading() {
  loadingRequests++
  if (loadingRequests === 1) {
    useUIStore().showLoading()
  }
}

function endLoading() {
  loadingRequests = Math.max(0, loadingRequests - 1)
  if (loadingRequests === 0) {
    useUIStore().hideLoading()
  }
}

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
  private readonly pending = new Map<string, AbortController>()

  constructor(baseURL: string) {
    this.baseURL = baseURL
  }

  request = async <T>(cfg?: HttpConfig): Promise<T> => {
    const uiState = useUIStore()
    const router = useRouter()
    const useLoad = cfg?.useLoad ?? true
    const handleError = cfg?.handleError?? true
    if (useLoad) {
      beginLoading()
    }
    const method = cfg?.method ?? 'GET'

    try {
      return await client
        .request({
          baseURL: this.baseURL,
          url: cfg?.url,
          method,
          params: cfg?.params,
          data: cfg?.data,
          headers: cfg?.headers,
          signal: cfg?.signal,
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
          // 不处理异常、网络异常、请求被取消等没有响应体，直接抛出
          if (!handleError || !axios.isAxiosError(err) || !err.response) {
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
            case 401: {
              msg = '未登录或登陆失效'
              await router.push({
                path: '/login',
                query: {
                  redirect: location.pathname + location.search,
                },
              })
              uiState.warning(msg)
              break
            }
            case 403:
              msg = '权限不足'
              uiState.warning(msg)
              break
            case 400:
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
      if (useLoad) {
        endLoading()
      }
    }
  };

  /**
   * 防抖包装：同 key 只保留最后一次请求，被取代时返回 undefined。
   */
  debounce(requestMethod: RequestMethod, key?: string): DebouncedRequestMethod {
    return async <T>(cfg: HttpConfig): Promise<T | undefined> => {
      const requestKey = key ?? `${cfg.method ?? 'GET'} ${this.baseURL}${cfg.url}`

      this.pending.get(requestKey)?.abort()
      const controller = new AbortController()
      this.pending.set(requestKey, controller)
      try {
        return await requestMethod({...cfg, signal: controller.signal})
      } catch (error) {
        if (axios.isCancel(error)) {
          return undefined
        }
        throw error
      } finally {
        if (this.pending.get(requestKey) === controller) {
          this.pending.delete(requestKey)
        }
      }
    }
  }
}
