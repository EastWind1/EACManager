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
    return await http.get<Record<string, Permission[]>>('/permissions')
  },

  /**
   * 查询全部角色
   */
  async getRoles() {
    return await http.get<Role[]>('/roles')
  },

  /**
   * 创建角色
   */
  async createRole(role: Role) {
    return await http.post<Role>('/role', role)
  },

  /**
   * 修改角色
   */
  async updateRole(role: Role) {
    return await http.put<Role>('/role', role)
  },

  /**
   * 删除角色
   */
  async deleteRole(id: number) {
    return await http.delete(`/role/${id}`)
  },

  /**
   * 查询角色权限
   */
  async getRolePermissions(id: number) {
    return await http.get<Permission[]>(`/role/${id}/permissions`)
  },

  /**
   * 更新角色权限
   */
  async updateRolePermissions(id: number, permissionIds: number[]) {
    return await http.put<Permission[]>(`/role/${id}/permissions`, permissionIds)
  },
}

export default SecurityApi
