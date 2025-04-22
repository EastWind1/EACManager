import {ref} from 'vue'

/**
 * 状态
 */
const state = ref({
  show: false,
  text: '',
  color: 'info',
  timeout: 2000
})
/**
 * 全局通知组件
 */
export function useNotify() {
  function show(color, text, timeout) {
    state.value.color = color
    state.value.text = text
    state.value.timeout = timeout

    state.value.show = true
  }

  /**
   * 成功通知
   * @param message 消息
   * @param timeout 超时
   */
  function success(message: string, timeout = 2000) {
    show('success', message, timeout)
  }

  /**
   * 普通通知
   * @param message 消息
   * @param timeout 超时
   */
  function info(message: string, timeout = 2000) {
    show('primary', message, timeout)
  }

  /**
   * 警告通知
   * @param message 消息
   * @param timeout 超时
   */
  function warning(message: string, timeout = 4000) {
    show('red', message, timeout)
  }
  return {state, success, info, warning}
}
