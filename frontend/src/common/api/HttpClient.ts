import { useUIStore } from "@/common/store/UIStore.ts";
import { useRouter } from "vue-router";

export interface HttpConfig {
  /**
   * 是否显示加载条，默认为 true
   */
  loading?: boolean;
  params?: unknown;
  headers?: HeadersInit;
  data?: unknown;
}

export class HttpClient {
  private readonly baseURL: string;
  private abortMap: Map<string, AbortController>;
  constructor(baseURL: string) {
    this.baseURL = baseURL;
    this.abortMap = new Map();
  }
  async request<T>(
    method: "GET" | "POST" | "PUT" | "DELETE",
    url: string,
    config?: HttpConfig,
  ): Promise<T> {
    const loading = !(config && !config.loading);
    const uiState = useUIStore();
    const router = useRouter();
    if (loading) {
      uiState.showLoading();
    }
    // 解析查询路径
    const queryParams = new URLSearchParams();
    if (config && config.params) {
      Object.entries(config.params).forEach(([key, value]) => {
        queryParams.append(key, String(value));
      });
    }
    const queryUrl = queryParams.toString();
    const finalUrl = queryUrl ? `${this.baseURL}${url}?${queryUrl}` : `${this.baseURL}${url}`;

     // 防抖
    const key = `${method}-${finalUrl}`;
    if (this.abortMap.has(key)) {
      this.abortMap.get(key)?.abort();
    }
    const abort = new AbortController();
    this.abortMap.set(key, abort);

    const reqInit: RequestInit = {
      signal: abort.signal,
      method: method,
      credentials: "same-origin",
    };
    
    if (method !== "GET") {
      if (config && config.data) {
        if (config.data instanceof FormData) {
          reqInit.body = config.data
        } else {
          reqInit.body = JSON.stringify(config.data)
          reqInit.headers = {
            ...config.headers,
            'Content-Type': 'application/json'
          }
        }
      }
    }

    const res = await fetch(finalUrl, reqInit);

    if (loading) {
      uiState.hideLoading();
    }
    const contentType = res.headers.get("Content-Type");
    try {
      switch (res.status) {
        case 404:
          throw new Error("请求地址不存在");
        case 401:
          await router.push({
            path: "/login",
            query: {
              redirect: location.pathname + location.search,
            },
          });
          throw new Error("请求地址不存在");
        case 403:
          throw new Error("权限不足");
        case 500:
          const jsonData = (await res.json()) as { message: string; data: unknown };
          throw new Error(jsonData && jsonData.message ? `${jsonData.message}` : "请求异常");
        default:
          if (res.ok) {
            if (contentType?.includes("json")) {
              const jsonData = (await res.json()) as { message: string; data: T };
              return jsonData.data;
            }
            return (await res.blob()) as T;
          } else {
            throw new Error("请求内部错误");
          }
      }
    } catch (e) {
      uiState.warning((e as Error).message);
      return Promise.reject(e);
    }
  }
  async get<T>(url: string, config?: HttpConfig): Promise<T> {
    return await this.request("GET", url, config);
  }
  async post<T>(url: string, data: unknown, config?: HttpConfig): Promise<T> {
    if (!config) {
      config = {};
    }
    config.data = data;
    return await this.request("POST", url, config);
  }
  async postForm<T>(url: string, data: unknown, config?: HttpConfig): Promise<T> {
    if (!config) {
      config = {};
    }
    config.data = data;
    config.headers = {
      ...config.headers,
      "Content-Type": "multipart/form-data",
    };
    return await this.request("POST", url, config);
  }
  async put<T>(url: string, data: unknown, config?: HttpConfig): Promise<T> {
    if (!config) {
      config = {};
    }
    config.data = data;
    return await this.request("PUT", url, config);
  }
  async delete<T>(url: string, config?: HttpConfig): Promise<T> {
    return await this.request("DELETE", url, config);
  }
}
