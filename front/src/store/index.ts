import { createPinia } from 'pinia'

/**
 * 导出以供 composable 方法使用，保证实例唯一
 */
const pinia = createPinia()
export default pinia
