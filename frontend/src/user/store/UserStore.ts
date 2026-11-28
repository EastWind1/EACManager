import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User } from '../model/User'

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
      const cached = JSON.parse(userJSON) as User
      // 旧版本缓存使用的是权限编码数组，清理并强制重新登录以获取新结构
      if (
        Array.isArray(cached.permissions) &&
        cached.permissions.every((permission) => typeof permission === 'object')
      ) {
        user.value = cached
      } else {
        localStorage.removeItem(USER_KEY)
      }
    } catch {
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
   * 是否拥有指定动作权限
   */
  function hasPermission(permission: string) {
    return !!user.value?.permissions?.some((item) => item.code === permission)
  }

  /**
   * 是否拥有任意一个动作权限
   */
  function hasAnyPermission(permissions: string[]) {
    return !!user.value && permissions.some((permission) => hasPermission(permission))
  }

  return {
    setUser,
    getUser,
    removeUser,
    hasPermission,
    hasAnyPermission,
  }
})
