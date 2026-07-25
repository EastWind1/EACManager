import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 全局 UI
 * 包含加载条、通知、确认框、日期选择器
 * 对话框组件自管理状态，store 仅持有组件引用
 */
export const useUIStore = defineStore('uiStore', () => {
  // 加载条是否显示
  const loading = ref(false)

  /**
   * 显示加载条
   */
  function showLoading() {
    loading.value = true
  }

  /**
   * 隐藏加载条
   */
  function hideLoading() {
    loading.value = false
  }

  const notifyFn = ref<
    (
      msg: string,
      type: 'primary' | 'success' | 'info' | 'warning' | 'error',
      timeout: number,
    ) => void
  >(() => {})

  function registerNotifyFn(
    fn: (
      msg: string,
      type: 'primary' | 'success' | 'info' | 'warning' | 'error',
      timeout: number,
    ) => void,
  ) {
    notifyFn.value = fn
  }
  /**
   * 显示通知
   * @param type 类型
   * @param text 内容
   * @param timeout 超时
   */
  function notify(
    text: string,
    type: 'success' | 'info' | 'warning' | 'primary' | 'error',
    timeout: number,
  ) {
    notifyFn.value(text, type, timeout)
  }

  /**
   * 成功通知
   * @param message 消息
   * @param timeout 超时
   */
  function success(message: string, timeout = 2000) {
    notify(message, 'success', timeout)
  }

  /**
   * 普通通知
   * @param message 消息
   * @param timeout 超时
   */
  function info(message: string, timeout = 2000) {
    notify(message, 'primary', timeout)
  }

  /**
   * 警告通知
   * @param message 消息
   * @param timeout 超时
   */
  function warning(message: string, timeout = 4000) {
    notify(message, 'warning', timeout)
  }

  const confirmFn = ref<(title: string, text: string) => Promise<boolean>>(
    () => new Promise(() => true),
  )

  function registerConfirmFn(fn: (title: string, text: string) => Promise<boolean>) {
    confirmFn.value = fn
  }

  /**
   * 显示确认框
   * @param title 标题
   * @param content 内容
   * @return Promise<boolean> 是否确认
   */
  function confirm(title: string, content: string): Promise<boolean> {
    return confirmFn.value(title, content)
  }

  const datePickerFn = ref<(title?: string, min?: Date, max?: Date) => Promise<Date | undefined>>(
    () => new Promise(() => undefined),
  )

  function registerDatePickerFn(
    fn: (title?: string, min?: Date, max?: Date) => Promise<Date | undefined>,
  ) {
    datePickerFn.value = fn
  }

  /**
   * 选择日期
   */
  function selectDate(title?: string, minDate?: Date, maxDate?: Date): Promise<Date | undefined> {
    return datePickerFn.value(title, minDate, maxDate)
  }

  return {
    loading,
    showLoading,
    hideLoading,
    success,
    info,
    warning,
    confirm,
    registerNotifyFn,
    registerConfirmFn,
    registerDatePickerFn,
    selectDate,
  }
})
