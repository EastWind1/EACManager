import type { User } from '../model/User'
import type { QueryParam } from '@/common/model/QueryParam'
import type { PageResult } from '@/common/model/PageResult'
import { HttpClient } from '@/common/api/HttpClient'

const http = new HttpClient('/api/user')
const debouncedRequest = http.debounce(http.request)

/**
 * 用户 API
 */
const UserApi = {
  /**
   * 登录；token 由服务端写入 httpOnly cookie
   * @param username 用户名
   * @param password 密码
   */
  async login(username: string, password: string) {
    return await http.request<User>({
      method: 'POST',
      url: `/token`,
      data: { username, password },
    })
  },
  /**
   * 查询是否需要初始化管理员
   */
  async getInitializationStatus() {
    return await http.request<{ required: boolean }>({
      method: 'GET',
      url: '/initialization',
      useLoad: false,
    })
  },

  /**
   * 初始化首个管理员
   */
  async initialize(user: {
    username: string
    password: string
    name: string
    phone?: string
    email?: string
  }) {
    return await http.request<User>({
      method: 'POST',
      url: '/initialization',
      data: user,
      useLoad: false,
    })
  },

  /**
   * 登出
   */
  async logout() {
    return await http.request({ method: 'PUT', url: `/logout`, data: {} })
  },

  /**
   * 获取所有用户
   */
  async getAll(param: QueryParam) {
    return await debouncedRequest<PageResult<User>>({ method: 'GET', url: '', params: param })
  },

  /**
   * 创建用户
   * @param user 用户数据
   */
  async create(user: User) {
    return await http.request<User>({ method: 'POST', url: '', data: user })
  },

  /**
   * 更新用户
   * @param user 用户数据
   */
  async update(user: User) {
    return await http.request<User>({ method: 'PUT', url: '', data: user })
  },

  /**
   * 禁用用户（删除）
   * @param id 用户 ID
   */
  async disable(id: number) {
    return await http.request({ method: 'DELETE', url: `/${id}` })
  },
}
export default UserApi
