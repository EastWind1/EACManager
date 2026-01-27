import { useAxios } from '@/common/api/AxiosConfig.ts'
import type { User } from '../model/User.ts'
import type { AxiosInstance } from 'axios'
import type { QueryParam } from '@/common/model/QueryParam.ts'
import type { PageResult } from '@/common/model/PageResult.ts'

let axiosInstance: AxiosInstance

function getAxios() {
  if (!axiosInstance) {
    axiosInstance = useAxios('/api/user')
  }
  return axiosInstance
}

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
    const res = await getAxios().post(`/token`, {
      username,
      password,
    })
    return res.data as User
  },

  /**
   * 获取所有用户
   */
  async getAll(param: QueryParam) {
    const res = await getAxios().get('', {
      params: param
    })
    return res.data as PageResult<User>
  },

  /**
   * 创建用户
   * @param user 用户数据
   */
  async create(user: User) {
    const res = await getAxios().post('', user)
    return res.data as User
  },

  /**
   * 更新用户
   * @param user 用户数据
   */
  async update(user: User) {
    const res = await getAxios().put('', user)
    return res.data as User
  },

  /**
   * 禁用用户（删除）
   * @param username 用户名
   */
  async disable(username: string) {
    const res = await getAxios().delete(`/${username}`)
    return res.data
  },
}
export default UserApi
