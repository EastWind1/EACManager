/**
 * 用户
 */
export interface User {
  id?: number
  /**
   * 用户名
   */
  username: string
  /**
   * 密码
   */
  password?: string
  /**
   * 名称
   */
  name?: string
  /**
   * 电话
   */
  phone?: string
  /**
   * 邮箱
   */
  email?: string
  /**
   * 权限
   */
  authority: AuthorityRoleValue
}

/**
 * 权限枚举
 */
export const AuthorityRole = {
  ROLE_ADMIN: { value: 'ROLE_ADMIN', title: '管理员' },
  ROLE_USER: { value: 'ROLE_USER', title: '普通用户' },
  ROLE_GUEST: { value: 'ROLE_GUEST', title: '游客' },
} as const

// 权限 value 联合类型，用于类型定义
export type AuthorityRoleValue = (typeof AuthorityRole)[keyof typeof AuthorityRole]['value']
