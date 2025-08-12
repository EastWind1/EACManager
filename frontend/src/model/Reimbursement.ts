import type { Attachment } from '@/model/Attachment.ts'
import type { QueryParam } from '@/model/QueryParam.ts'

/**
 * 报销单据状态
 */
export const ReimburseState = {
  /**
   * 新建
   */
  CREATED: {
    value: 'CREATED',
    title: '新建',
    color: 'blue',
  },
  /**
   * 进行中
   */
  PROCESSING: {
    value: 'PROCESSING',
    title: '进行中',
    color: 'orange',
  },
  /**
   * 完成
   */
  FINISHED: {
    value: 'FINISHED',
    title: '完成',
    color: 'green',
  },
} as const
// 服务单状态枚举
export type ReimburseStateValue =
  (typeof ReimburseState)[keyof typeof ReimburseState]['value']

/**
 * 服务单实体
 */
export interface Reimbursement {
  id?: number
  /**
   * 单号
   */
  number?: string
  /**
   * 单据状态
   */
  state: ReimburseStateValue
  /**
   * 摘要
   */
  summary?: string
  /**
   * 金额
   */
  totalAmount?: number
  /**
   * 创建时间
   */
  reimburseDate?: Date
  /**
   * 备注
   */
  remark?: string
  /**
   * 明细
   */
  details: ReimburseDetail[]
  /**
   * 附件
   */
  attachments: Attachment[]
}

/**
 * 报销单明细
 */
export interface ReimburseDetail {
  id?: number
  /**
   * 明细名称
   */
  name?: string;
  /**
   * 金额
   */
  amount?: number;
}

/**
 * 服务单查询参数
 */
export interface ReimburseQueryParam extends QueryParam {
  /**
   * 单号
   */
  number?: string

  /**
   * 状态
   */
  state?: ReimburseStateValue[]


  /**
   * 报销起始日期
   */
  reimburseStartDate?: Date
  /**
   * 报销结束日期
   */
  reimburseEndDate?: Date
}
