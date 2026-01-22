/**
 * 基础查询参数
 */
export interface QueryParam {
  /**
   * 页面大小
   */
  pageSize?: number

  /**
   * 页面索引，从 0 开始
   */
  pageIndex?: number

  /**
   * 排序参数列表
   */
  sorts?: SortParam[]
}

/**
 * 排序参数
 */
export interface SortParam {
  /**
   * 排序字段
   */
  field: string

  /**
   * 排序方向（ASC / DESC）
   */
  direction: string
}
