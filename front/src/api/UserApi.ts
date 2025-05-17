import { useAxios } from '@/api/AxiosConfig.ts'
import { useUserStore } from '@/store/UserStore.ts'

const UserApi = {
  /**
   * 登录
   * 返回 token
   * @param username 用户名
   * @param password 密码
   */
  login: (username: string, password: string) =>
    useAxios()
      .post('user/token', {
        username,
        password,
      })
      .then((res) => {
        const {setToken} = useUserStore()
        const token = res.headers['x-auth-toke']
        setToken(token)
      }),
}
export default UserApi
