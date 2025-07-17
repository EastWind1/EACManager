import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 路由数据存储
 */
export const useRouterStore = defineStore('routerStore', () => {
  const data = ref<unknown>()

  /**
   * 设置数据
   * @param value
   */
  function setData(value: unknown) {
    data.value = value
  }

  /**
   * 获取数据
   */
  function getData() {
    return data.value
  }

  return {
    getData,
    setData,
  }
})
