import type { Permission, Role } from '@/permission/model/Permission'

/**
 * 用户
 */
export interface User {
  id?: number
  username: string
  password?: string
  name?: string
  phone?: string
  email?: string
  /**
   * 用户拥有的角色及每个角色的权限
   */
  roles?: Role[]
  /**
   * 用户去重后的动作权限
   */
  permissions?: Permission[]
}
