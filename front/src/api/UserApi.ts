import { useAxios } from '@/api/AxiosConfig.ts'

const axios = useAxios()
const UserApi = {
  /**
   * 登录
   * 返回 token
   * @param username 用户名
   * @param password 密码
   */
  login: (username: string, password: string) =>
    axios
      .post('user/token', {
        username,
        password,
      })
      .then((res) => res.data as string),
}
export default UserApi
