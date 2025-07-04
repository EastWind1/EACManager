/**
 * 批量操作结果
 */
export interface ActionsResult<P, R> {
  /**
   * 结果集
   */
  results: {
    /**
     * 参数
     */
    param: P,
    /**
     * 结果
     */
    result: R,
    /**
     * 是否成功
     */
    success: boolean,
    /**
     * 消息
     */
    message: string
  }[]
  /**
   * 成功数量
   */
  successCount: number
  /**
   * 失败数量
   */
  failCount: number
}
