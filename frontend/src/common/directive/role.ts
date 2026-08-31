import type { AuthorityRoleValue } from '@/user/model/User'
import { useUserStore } from '@/user/store/UserStore'
import type { Directive } from 'vue'

export type RoleDirective = Directive<HTMLElement, AuthorityRoleValue | AuthorityRoleValue[]>

declare module 'vue' {
  export interface GlobalDirectives {
    // 使用 v 作为前缀 (v-highlight)
    vHighlight: RoleDirective
  }
}

export default {
  mounted: (el, binding) => {
    const userStore = useUserStore()
    if (!binding.value) {
      return
    }
    const args = binding.value instanceof Array ? binding.value : [binding.value]
    if (!userStore.hasAnyRole(args)) {
      el.style.display = 'none'
    } else {
      el.style.display = ''
    }
  }
} satisfies RoleDirective
