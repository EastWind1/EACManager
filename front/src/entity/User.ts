/**
 * 用户
 */
export interface User {
  id: number;
  /**
   * 用户名
   */
  username: string;
  /**
   * 名称
   */
  name: string;
  /**
   * 电话
   */
  phone: string;
  /**
   * 邮箱
   */
  email: string;
  /**
   * 权限
   */
  authorities: Authority[];
}

/**
 * 权限
 */
interface Authority {
  id: number;
  /**
   * 权限类型
   */
  name: string;
}
