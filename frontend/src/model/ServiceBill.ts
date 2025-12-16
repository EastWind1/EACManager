import type { Attachment } from '@/model/Attachment.ts'
import type { QueryParam } from '@/model/QueryParam.ts'

/**
 * 服务单类型
 */
export const ServiceBillType = {
  /**
   * 安装单
   */
  INSTALL: {
    value: 'INSTALL',
    title: '安装单',
    color: 'blue',
  },
  /**
   * 维护单
   */
  FIX: {
    value: 'FIX',
    title: '维护单',
    color: 'orange',
  },
} as const
// 服务单枚举值
export type ServiceBillTypeValue = (typeof ServiceBillType)[keyof typeof ServiceBillType]['value']
/**
 * 服务单据状态
 */
export const ServiceBillState = {
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
   * 处理完成
   */
  PROCESSED: {
    value: 'PROCESSED',
    title: '处理完成',
    color: 'light-green',
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
export type ServiceBillStateValue =
  (typeof ServiceBillState)[keyof typeof ServiceBillState]['value']

/**
 * 服务单实体
 */
export interface ServiceBill {
  id?: number
  /**
   * 单号
   */
  number?: string
  /**
   * 单据类型
   */
  type: ServiceBillTypeValue
  /**
   * 单据状态
   */
  state: ServiceBillStateValue
  /**
   * 项目名称
   */
  projectName: string
  /**
   * 项目地址
   */
  projectAddress: string
  /**
   * 项目联系人
   */
  projectContact: string
  /**
   * 项目联系人电话
   */
  projectContactPhone: string
  /**
   * 现场联系人
   */
  onSiteContact?: string
  /**
   * 现场联系人电话
   */
  onSitePhone?: string
  /**
   * 电梯信息
   */
  elevatorInfo?: string
  /**
   * 服务明细
   */
  details: ServiceBillDetail[]
  /**
   * 附件
   */
  attachments: Attachment[]
  /**
   * 总金额
   */
  totalAmount: number
  /**
   * 下单时间
   */
  orderDate: Date
  /**
   * 完工时间
   */
  processedDate?: Date
  /**
   * 完成时间
   */
  finishedDate?: Date
  /**
   * 备注
   */
  remark?: string
}

/**
 * 服务项目明细
 */
export interface ServiceBillDetail {
  id?: number
  /**
   * 设备类型
   */
  device: string
  /**
   * 数量
   */
  quantity: number
  /**
   * 单价
   */
  unitPrice: number
  /**
   * 小计
   */
  subtotal: number
  /**
   * 备注
   */
  remark: string
}

/**
 * 服务单查询参数
 */
export interface ServiceBillQueryParam extends QueryParam {
  /**
   * 单号
   */
  number?: string

  /**
   * 状态
   */
  states?: ServiceBillStateValue[]

  /**
   * 项目名称
   */
  projectName?: string

  /**
   * 下单起始日期
   */
  orderStartDate?: Date
  /**
   * 下单结束日期
   */
  orderEndDate?: Date

  /**
   * 处理完成起始日期
   */
  processedStartDate?: Date

  /**
   * 处理完成结束日期
   */
  processedEndDate?: Date
}
