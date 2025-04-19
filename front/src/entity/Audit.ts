import type { User } from '@/entity/User.ts'

/**
 * 审计实体
 */
export interface AuditEntity {
  /**
   * 创建时间
   */
  createdDate?: Date

  /**
   * 创建人
   */
  createdBy?: User

  /**
   * 修改时间
   */
  lastModifiedDate?: Date

  /**
   * 修改人
   */
  lastModifiedBy?: User
}
