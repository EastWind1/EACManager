import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 用户 token 存储
 */
export const useUserStore = defineStore('userStore', () => {
  // 用户 token
  const token = ref(localStorage.getItem('token'))

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
   * 清除 token
   */
  function removeToken() {
    token.value = null
    localStorage.removeItem('token')
  }

  return {
    setToken,
    getToken,
    removeToken
  }
})
