import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 全局 UI
 * 包含加载条、通知
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

  // 通知状态
  const notifyData = ref({
    show: false,
    text: '',
    color: 'info',
    timeout: 2000,
  })

  /**
   * 显示通知
   * @param color 颜色
   * @param text 内容
   * @param timeout 超时
   */
  function showNotify(color: string, text: string, timeout: number) {
    notifyData.value.color = color
    notifyData.value.text = text
    notifyData.value.timeout = timeout

    notifyData.value.show = true
  }

  /**
   * 成功通知
   * @param message 消息
   * @param timeout 超时
   */
  function success(message: string, timeout = 2000) {
    showNotify('success', message, timeout)
  }

  /**
   * 普通通知
   * @param message 消息
   * @param timeout 超时
   */
  function info(message: string, timeout = 2000) {
    showNotify('primary', message, timeout)
  }

  /**
   * 警告通知
   * @param message 消息
   * @param timeout 超时
   */
  function warning(message: string, timeout = 4000) {
    showNotify('red', message, timeout)
  }

  /**
   * 确认框属性
   */
  const confirmData = ref({
    /**
     * 是否显示
     */
    show: false,
    /**
     * 标题
     */
    title: '',
    /**
     * 内容
     */
    text: '',
    /**
     * 确认回调
     */
    confirm: () => {},
    /**
     * 取消回调
     */
    cancel: () => {},
  })

  /**
   * 显示确认框
   * @param title 标题
   * @param content 内容
   * @return Promise<boolean> 是否确认
   */
  function confirm(title: string, content: string): Promise<boolean> {
    confirmData.value.show = true
    confirmData.value.title = title
    confirmData.value.text = content
    return new Promise<boolean>((resolve) => {
      confirmData.value.confirm = () => {
        confirmData.value.show = false
        resolve(true)
      }
      confirmData.value.cancel = () => {
        confirmData.value.show = false
        resolve(false)
      }
    })
  }

  /**
   * 日期选择框
   */
  const dataPickerData = ref<{
    show: boolean
    title: string
    minDate: Date | undefined
    maxDate: Date | undefined
    confirm: (date: Date) => void
    cancel: () => void
  }>({
    /**
     * 是否显示
     */
    show: false,
    /**
     * 标题
     */
    title: '选择日期',
    /**
     * 最小值
     */
    minDate: undefined,
    /**
     * 最大值
     */
    maxDate: undefined,
    /**
     * 确认回调
     */
    confirm: () => {},
    /**
     * 取消回调
     */
    cancel: () => {},
  })
  /**
   * 选择日期
   */
  function selectDate(title?: string, minDate?: Date, maxDate?: Date):Promise<Date | undefined> {
    dataPickerData.value.minDate = minDate ?? undefined
    dataPickerData.value.maxDate = maxDate ?? undefined
    dataPickerData.value.title = title ?? "选择日期"
    dataPickerData.value.show = true
    return new Promise((resolve, reject) => {
      dataPickerData.value.confirm = (date: Date) => {
        dataPickerData.value.show = false
        resolve(date)
      }
      dataPickerData.value.cancel = () => {
        dataPickerData.value.show = false
        reject()
      }
    })
  }
  return {
    loading,
    showLoading,
    hideLoading,
    notifyData,
    success,
    info,
    warning,
    confirmData,
    confirm,
    dataPickerData,
    selectDate
  }
})
