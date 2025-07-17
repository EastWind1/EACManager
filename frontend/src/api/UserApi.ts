import { useAxios } from '@/api/AxiosConfig.ts'
import type { User } from '@/model/User.ts'
import type { AxiosInstance } from 'axios'

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
  login: (username: string, password: string) =>
    getAxios()
      .post(`/token`, {
        username,
        password,
      })
      .then((res) => ({
        token: res.headers['x-auth-token'],
        user: res.data.data as User,
      })),
  /**
   * 获取所有用户
   */
  getAll: () =>
    getAxios()
      .get('')
      .then((res) => res.data as User[]),

  /**
   * 创建用户
   * @param user 用户数据
   */
  create: (user: User) =>
    getAxios()
      .post('', user)
      .then((res) => res.data as User),

  /**
   * 更新用户
   * @param user 用户数据
   */
  update: (user: User) =>
    getAxios()
      .put('', user)
      .then((res) => res.data as User),

  /**
   * 禁用用户（删除）
   * @param id 用户 ID
   */
  disable: (id: number) => getAxios().delete(`/${id}`),
}
export default UserApi
