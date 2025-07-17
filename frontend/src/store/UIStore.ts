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
  const notify = ref({
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
    notify.value.color = color
    notify.value.text = text
    notify.value.timeout = timeout

    notify.value.show = true
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
  const dialogData = ref({
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
    dialogData.value.show = true
    dialogData.value.title = title
    dialogData.value.text = content
    return new Promise<boolean>((resolve) => {
      dialogData.value.confirm = () => {
        dialogData.value.show = false
        resolve(true)
      }
      dialogData.value.cancel = () => {
        dialogData.value.show = false
        resolve(false)
      }
    })
  }

  return {
    loading,
    showLoading,
    hideLoading,
    notify,
    showNotify,
    success,
    info,
    warning,
    dialogData,
    confirm,
  }
})
