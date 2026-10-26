import { useUserStore } from '@/user/store/UserStore'
import type { Directive } from 'vue'
export type PermissionDirective = Directive<HTMLElement, string | string[]>
declare module 'vue' {
  export interface GlobalDirectives {
    // 使用 v 作为前缀 (v-permission)
    vPermission: PermissionDirective
  }
}
/**
 * 根据当前用户的动作权限显示/隐藏元素
 */
function applyPermission(el: HTMLElement, value?: string | string[]) {
  if (!value) {
    return
  }
  const userStore = useUserStore()
  const permissions = value instanceof Array ? value : [value]
  el.style.display = userStore.hasAnyPermission(permissions) ? '' : 'none'
}
export default {
  mounted: (el, binding) => applyPermission(el, binding.value),
  updated: (el, binding) => applyPermission(el, binding.value),
} satisfies PermissionDirective
