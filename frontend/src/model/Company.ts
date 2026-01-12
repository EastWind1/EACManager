/**
 * 公司
 */
export interface Company {
  id?: number
  /**
   * 名称
   */
  name: string

  /**
   * 联系人姓名
   */
  contactName?: string

  /**
   * 联系人电话
   */
  contactPhone?: string
  /**
   * 邮箱
   */
  email?: string
  /**
   * 地址
   */
  address?: string
}
