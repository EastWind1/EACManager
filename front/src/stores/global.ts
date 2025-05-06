import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useGlobalStore = defineStore('global', () => {
  // 用户 token
  const token = ref(localStorage.getItem('token'))
  // 加载条是否显示
  const loading = ref(false)
  // 通知状态
  const notify = ref({
    show: false,
    text: '',
    color: 'info',
    timeout: 2000,
  })

  /**
   * 设置用户 token
   * @param value token
   */
  function setToken(value: string) {
    token.value = value
    localStorage.setItem('token', value)
  }

  /**
   * 获取 token
   */
  function getToken() {
    return token.value
  }

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

  return {
    loading,
    showLoading,
    hideLoading,
    notify,
    success,
    info,
    warning,
    setToken,
    getToken,
  }
})
