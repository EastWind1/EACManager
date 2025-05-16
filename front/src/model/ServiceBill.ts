import type { AuditEntity } from '@/model/Audit.ts'
import type { Attachment } from '@/model/Attachment.ts'
import type { QueryParam } from '@/model/QueryParam.ts'

/**
 * 服务单类型
 */
export const enum ServiceBillType {
  /**
   * 安装单
   */
  INSTALL = 'INSTALL',
  /**
   * 维护单
   */
  FIX = 'FIX',
}

/**
 * 服务单据状态
 */
export const enum ServiceBillState {
  /**
   * 新建
   */
  CREATED = 'CREATED',
  /**
   * 进行中
   */
  PROCESSING = 'PROCESSING',
  /**
   * 处理完成
   */
  PROCESSED = 'PROCESSED',
  /**
   * 完成
   */
  FINISHED = 'FINISHED',
}

/**
 * 服务单实体
 */
export interface ServiceBill extends AuditEntity {
  id?: number
  /**
   * 单号
   */
  number?: string
  /**
   * 单据类型
   */
  type: ServiceBillType
  /**
   * 单据状态
   */
  state: ServiceBillState
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
   * 完工时间
   */
  processedDate?: Date
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
  state?: ServiceBillState[]

  /**
   * 项目名称
   */
  projectName?: string

  /**
   * 创建起始日期
   */
  createdStartDate?: Date

  /**
   * 创建结束日期
   */
  createdEndDate?: Date

  /**
   * 处理完成起始日期
   */
  processedStartDate?: Date

  /**
   * 处理完成结束日期
   */
  processedEndDate?: Date
}
