import { defineStore } from 'pinia'
import { ref } from 'vue'
import CryptoJS from 'crypto-js'
import type { User } from '@/model/User.ts'

const SECRET_KEY = import.meta.env.VITE_TOKEN_SECRET
/**
 * 用户 token 存储
 */
export const useUserStore = defineStore('userStore', () => {
  // 用户 token
  const secretToken = localStorage.getItem('token')
  // 用户
  const secretUser = localStorage.getItem('user')
  const token = ref(
    secretToken ? CryptoJS.AES.decrypt(secretToken, SECRET_KEY).toString(CryptoJS.enc.Utf8) : null,
  )
  const user = ref<User | undefined>(
    secretUser
      ? JSON.parse(CryptoJS.AES.decrypt(secretUser, SECRET_KEY).toString(CryptoJS.enc.Utf8))
      : undefined,
  )

  /**
   * 设置当前用户
   */
  function setUser(value: User) {
    const json = JSON.stringify(value)
    const secretUser = CryptoJS.AES.encrypt(json, SECRET_KEY).toString()
    localStorage.setItem('user', secretUser)
    user.value = value
  }

  /**
   * 获取当前用户
   */
  function getUser() {
    return user.value
  }

  /**
   * 移除当前用户
   */
  function removeUser() {
    user.value = undefined
    localStorage.removeItem('user')
  }

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
    removeToken,
    setUser,
    getUser,
    removeUser,
  }
})
