/**
 * 分页查询结果
 */
export interface PageResult<T> {
  /**
   * 内容列表
   */
  items: T[]
  /**
   * 总数
   */
  totalCount: number
  /**
   * 总页数
   */
  totalPages: number
  /**
   * 页大小
   */
  pageSize: number
  /**
   * 页索引
   */
  pageIndex: number
}
