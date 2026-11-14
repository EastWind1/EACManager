import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'

/**
 * 通知类型
 */
export type NotifyType = 'primary' | 'success' | 'info' | 'warning' | 'error'

export type Notification = {
  text: string
  color: NotifyType
  timeout: number
}

type ConfirmRequest = {
  title: string
  text: string
  resolve: (value: boolean) => void
}

type DateRequest = {
  title?: string
  min?: Date
  max?: Date
  resolve: (value: Date | undefined) => void
}

export type BatchResult = {
  successCount: number
  failedCount: number
  rows: {
    key: string
    message: string
  }[]
}

type BatchRequest = BatchResult & {
  resolve: () => void
}

/**
 * 全局 UI 状态。
 *
 * 同一时刻只存在一个通知、确认、日期选择和批量结果请求；新请求会结束旧请求，
 * 避免调用方永久等待。
 */
export const useUIStore = defineStore('uiStore', () => {
  const loading = ref(false)
  const loadingProgress = ref(0)
  const notification = shallowRef<Notification | null>(null)
  const confirmRequest = shallowRef<ConfirmRequest | null>(null)
  const dateRequest = shallowRef<DateRequest | null>(null)
  const batchRequest = shallowRef<BatchRequest | null>(null)

  function showLoading(progress?: number) {
    loadingProgress.value = progress ?? -1
    loading.value = true
  }

  function hideLoading() {
    loading.value = false
  }

  function notify(text: string, color: NotifyType, timeout = 2000) {
    notification.value = { text, color, timeout }
  }

  function clearNotification() {
    notification.value = null
  }

  function success(text: string, timeout = 2000) {
    notify(text, 'success', timeout)
  }

  function info(text: string, timeout = 2000) {
    notify(text, 'primary', timeout)
  }

  function warning(text: string, timeout = 4000) {
    notify(text, 'warning', timeout)
  }

  function confirm(title: string, text: string): Promise<boolean> {
    confirmRequest.value?.resolve(false)
    return new Promise<boolean>((resolve) => {
      confirmRequest.value = { title, text, resolve }
    })
  }

  function resolveConfirm(confirmed: boolean) {
    const request = confirmRequest.value
    confirmRequest.value = null
    request?.resolve(confirmed)
  }

  function selectDate(title?: string, minDate?: Date, maxDate?: Date): Promise<Date | undefined> {
    dateRequest.value?.resolve(undefined)
    return new Promise<Date | undefined>((resolve) => {
      dateRequest.value = { title, min: minDate, max: maxDate, resolve }
    })
  }

  function resolveDate(value: Date | undefined) {
    const request = dateRequest.value
    dateRequest.value = null
    request?.resolve(value)
  }

  /**
   * 处理动作结果：返回结果 ≤ 1 条只提示，多条存在失败时弹窗
   */
  function showBatchResult(
    result: ActionsResult<number, void>,
    getKeyFn: (id: number) => string = (id) => String(id),
  ): Promise<void> {
    if (result.results.length <= 1) {
      const item = result.results[0]
      if (item) {
        if (item.success) {
          success('操作成功')
        } else {
          warning(`操作失败：${item.message}`)
        }
      }
      return Promise.resolve()
    }
    if (result.failCount) {
      batchRequest.value?.resolve()
      return new Promise<void>((resolve) => {
        batchRequest.value = {
          successCount: result.successCount,
          failedCount: result.failCount,
          rows: result.results
            .filter((item) => !item.success)
            .map((item) => ({
              key: getKeyFn(item.param),
              message: item.message,
            })),
          resolve,
        }
      })
    }
    success(`${result.successCount} 条单据操作成功, 0 条失败`)
    return Promise.resolve()
  }

  function resolveBatchResult() {
    const request = batchRequest.value
    batchRequest.value = null
    request?.resolve()
  }

  /**
   * App 卸载时结束所有等待，避免未完成的 Promise 永久挂起。
   */
  function cancelAll() {
    confirmRequest.value?.resolve(false)
    dateRequest.value?.resolve(undefined)
    batchRequest.value?.resolve()
    confirmRequest.value = null
    dateRequest.value = null
    batchRequest.value = null
    notification.value = null
    loading.value = false
  }

  return {
    loading,
    loadingProgress,
    notification,
    confirmRequest,
    dateRequest,
    batchRequest,
    showLoading,
    hideLoading,
    success,
    info,
    warning,
    clearNotification,
    confirm,
    resolveConfirm,
    selectDate,
    resolveDate,
    showBatchResult,
    resolveBatchResult,
    cancelAll,
  }
})
