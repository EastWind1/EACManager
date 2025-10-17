import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AuthorityRoleValue, User } from '@/model/User.ts'

/**
 * 用户存储
 */
export const useUserStore = defineStore('userStore', () => {
  const USER_KEY = 'user'
  // 先从缓存读取用户
  const userJSON = localStorage.getItem(USER_KEY)
  const user = ref<User | undefined>()
  if (userJSON) {
    try {
      user.value = JSON.parse(userJSON)
    } catch (e) {
      user.value = undefined
    }
  }

  /**
   * 设置当前用户
   */
  function setUser(value: User) {
    localStorage.setItem(USER_KEY, JSON.stringify(value))
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
    localStorage.removeItem(USER_KEY)
  }

  /**
   * 是否有任何身份
   */
  function hasAnyRole(roles: AuthorityRoleValue[]) {
    return user.value && roles && roles.includes(user.value.authority)
  }

  return {
    setUser,
    getUser,
    removeUser,
    hasAnyRole
  }
})
