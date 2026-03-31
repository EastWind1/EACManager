import type { User } from '../model/User.ts'
import type { QueryParam } from '@/common/model/QueryParam.ts'
import type { PageResult } from '@/common/model/PageResult.ts'
import { HttpClient } from '@/common/api/HttpClient.ts'

const http = new HttpClient('/api/user')

/**
 * 用户 API
 */
const UserApi = {
  /**
   * 登录
   * 返回 token
   * @param username 用户名
   * @param password 密码
   */
  async login(username: string, password: string) {
    return await http.post<User>(`/token`, {
      username,
      password,
    })
  },

  /**
   * 获取所有用户
   */
  async getAll(param: QueryParam) {
    return await http.get<PageResult<User>>('', {
      params: param,
    })
  },

  /**
   * 创建用户
   * @param user 用户数据
   */
  async create(user: User) {
    return await http.post<User>('', user)
  },

  /**
   * 更新用户
   * @param user 用户数据
   */
  async update(user: User) {
    return await http.put<User>('', user)
  },

  /**
   * 禁用用户（删除）
   * @param username 用户名
   */
  async disable(username: string) {
    return await http.delete(`/${username}`)
  },
}
export default UserApi
