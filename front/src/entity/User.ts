/**
 * 用户
 */
export interface User {
  id: number;
  username: string;
  name: string;
  phone: string;
  email: string;
  authorities: Authority[];
}

/**
 * 权限
 */
interface Authority {
  id: number;
  name: string;
}
