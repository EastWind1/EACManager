/**
 * 权限 DTO
 */
export interface Permission {
  id: number
  code: string
  module: string
  moduleName: string
  name: string
  sortOrder: number
}

/**
 * 角色 DTO
 */
export interface Role {
  id?: number
  code: string
  name: string
  sortOrder: number
  disabled: boolean
}
