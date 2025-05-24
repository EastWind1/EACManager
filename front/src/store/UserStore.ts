import { defineStore } from 'pinia'
import { ref } from 'vue'
import CryptoJS from 'crypto-js'

const SECRET_KEY = import.meta.env.VITE_TOKEN_SECRET
/**
 * 用户 token 存储
 */
export const useUserStore = defineStore('userStore', () => {
  // 用户 token
  const secretToken = localStorage.getItem('token')
  const token = ref(secretToken ? CryptoJS.AES.decrypt(secretToken, SECRET_KEY).toString(CryptoJS.enc.Utf8) : null)

  /**
   * 设置用户 token
   * @param value token
   */
  function setToken(value: string) {
    const secretToken = CryptoJS.AES.encrypt(value, SECRET_KEY).toString()
    token.value = value
    localStorage.setItem('token', secretToken)
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
