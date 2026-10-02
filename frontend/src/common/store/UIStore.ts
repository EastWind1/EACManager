import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { ActionsResult } from '@/common/model/ActionsResult.ts'

/**
 * 通知类型
 */
type NotifyType = 'primary' | 'success' | 'info' | 'warning' | 'error'

/**
 * 组件实现钩子
 */
type UIHook = {
  showLoading?: (progress?: number) => void
  hideLoading?: () => void
  notify?: (text: string, type: NotifyType, timeout: number) => void
  confirm?: (title: string, text: string) => Promise<boolean>
  selectDate?: (title?: string, minDate?: Date, maxDate?: Date) => Promise<Date | undefined>
  showBatchResult?: (
    result: ActionsResult<number, void>,
    getKeyFn?: (id: number) => string,
  ) => Promise<void>
}

/**
 * 全局 UI
 */
export const useUIStore = defineStore('uiStore', () => {
  // 是否加载状态
  const loading = ref(false)

  let api: UIHook = {}

  /**
   * 组件未注册回退
   * @param name
   */
  function fallback(name: string) {
    console.warn(`${name} 组件尚未挂载`)
  }

  /**
   * 注册 UI 回调
   * @param handlers
   */
  function registerUI(handlers: UIHook) {
    api = handlers
  }

  /**
   * 显示全局加载条
   * @param progress 进度
   */
  function showLoading(progress?: number) {
    if (!api.showLoading) {
      fallback('showLoading')
      return
    }
    api.showLoading(progress)
    loading.value = true
  }

  /**
   * 隐藏全局加载条
   */
  function hideLoading() {
    if (!api.hideLoading) {
      fallback('hideLoading')
      return
    }
    api.hideLoading()
    loading.value = false
  }

  /**
   * 通知
   * @param text 文本
   * @param color 颜色
   * @param timeout 超时
   */
  function notify(text: string, color: NotifyType, timeout?: number) {
    if (!api.notify) {
      fallback('notify')
      return
    }
    api.notify(text, color, timeout ?? 2000)
  }

  /**
   * 成功通知
   * @param text 文本
   * @param timeout 超时
   */
  function success(text: string, timeout = 2000) {
    notify(text, 'success', timeout)
  }

  /**
   * 普通通知
   * @param text 文本
   * @param timeout 超时
   */
  function info(text: string, timeout = 2000) {
    notify(text, 'primary', timeout)
  }

  /**
   * 警告通知
   * @param text 文本
   * @param timeout 超时
   */
  function warning(text: string, timeout = 4000) {
    notify(text, 'warning', timeout)
  }

  /**
   * 确认弹窗
   * @param title 标题
   * @param content 内容
   */
  function confirm(title: string, content: string): Promise<boolean> {
    if (!api.confirm) {
      fallback('confirm')
      return Promise.resolve(false)
    }
    return api.confirm(title, content)
  }

  /**
   * 选择日期弹窗
   * @param title 标题
   * @param minDate 最小日期
   * @param maxDate 最大日期
   */
  function selectDate(title?: string, minDate?: Date, maxDate?: Date): Promise<Date | undefined> {
    if (!api.selectDate) {
      fallback('selectDate')
      return Promise.resolve(undefined)
    }
    return api.selectDate(title, minDate, maxDate)
  }

  /**
   * 处理动作结果：返回结果 ≤ 1 条只提示，多条存在失败时弹窗
   *
   * @param result 结果
   * @param getKeyFn 获取 key 回调
   */
  function showBatchResult(
    result: ActionsResult<number, void>,
    getKeyFn?: (id: number) => string,
  ): Promise<void> {
    if (!api.showBatchResult) {
      fallback('showBatchResult')
      return Promise.resolve()
    }
    return api.showBatchResult(result, getKeyFn)
  }

  return {
    loading,
    showLoading,
    hideLoading,
    success,
    info,
    warning,
    confirm,
    selectDate,
    showBatchResult,
    registerUI,
  }
})
