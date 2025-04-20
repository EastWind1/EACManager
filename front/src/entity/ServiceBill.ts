import type { User } from '@/entity/User'
import type { AuditEntity } from '@/entity/Audit.ts'

/**
 * 服务单类型
 */
export const enum ServiceBillType {
  /**
   * 安装单
   */
  INSTALL,
  /**
   * 维护单
   */
  FIX,
}

/**
 * 服务单据状态
 */
export const enum ServiceBillState {
  /**
   * 新建
   */
  CREATED,
  /**
   * 进行中
   */
  PROCESSING,
  /**
   * 处理完成
   */
  PROCESSED,
  /**
   * 完成
   */
  FINISHED,
}

/**
 * 服务单实体
 */
export interface ServiceBill extends AuditEntity {
  id?: number
  /**
   * 单号
   */
  number?: number
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
  onSiteContact: string
  /**
   * 现场联系人电话
   */
  onSitePhone: string
  /**
   * 送货联系电话
   */
  cargoSenderPhone: string
  /**
   * 电梯信息
   */
  elevatorInfo: string
  /**
   * 处理人明细列表
   */
  processDetails: ServiceBillProcessorDetail[]
  /**
   * 服务明细
   */
  details: ServiceBillDetail[]
  /**
   * 总金额
   */
  totalAmount: number
  /**
   * 完工时间
   */
  processedDate: string
  /**
   * 备注
   */
  remark: string
  /**
   * 创建时间
   */
  createDate: string
}
/**
 * 服务项目明细
 */
export interface ServiceBillDetail extends AuditEntity {
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
 * 处理人明细
 */
interface ServiceBillProcessorDetail extends AuditEntity {
  id: number
  /**
   * 处理人
   */
  processUser: User

  /*
   * 处理数量
   */
  processCount: number

  /**
   * 处理金额
   */
  processedAmount: number
  /**
   * 接受时间
   */
  acceptDate: string

  /**
   * 处理完成时间
   */
  processedDate: string
}
