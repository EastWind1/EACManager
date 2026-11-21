import { HttpClient } from '@/common/api/HttpClient'
import type { Permission, Role } from '../model/Permission'

const http = new HttpClient('/api/security')

/**
 * 角色与权限 API
 */
const SecurityApi = {
  /**
   * 获取全部可配置权限
   */
  async getPermissions() {
    return await http.request<Record<string, Permission[]>>({
      method: 'GET',
      url: '/permissions',
    })
  },

  /**
   * 查询全部角色
   */
  async getRoles() {
    return await http.request<Role[]>({ method: 'GET', url: '/roles' })
  },

  /**
   * 创建角色
   */
  async createRole(role: Role) {
    return await http.request<Role>({ method: 'POST', url: '/role', data: role })
  },

  /**
   * 修改角色
   */
  async updateRole(role: Role) {
    return await http.request<Role>({ method: 'PUT', url: '/role', data: role })
  },

  /**
   * 删除角色
   */
  async deleteRole(id: number) {
    return await http.request({ method: 'DELETE', url: `/role/${id}` })
  },

  /**
   * 查询角色权限
   */
  async getRolePermissions(id: number) {
    return await http.request<Permission[]>({ method: 'GET', url: `/role/${id}/permissions` })
  },

  /**
   * 更新角色权限
   */
  async updateRolePermissions(id: number, permissionIds: number[]) {
    return await http.request<Permission[]>({
      method: 'PUT',
      url: `/role/${id}/permissions`,
      data: permissionIds,
    })
  },
}

export default SecurityApi
