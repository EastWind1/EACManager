import type {ActionsResult} from '@/model/ActionsResult.ts'
import ReimburseApi from '@/api/ReimburseApi.ts'

/**
 * 报销单操作组合函数
 * @param callback 回调函数
 */
export function useReimburseActions(callback: (result: ActionsResult<number, void>) => void) {
  /**
   * 处理
   * @param ids 单据ID列表
   */
  async function process(ids: number[]) {
    const result = await ReimburseApi.process(ids)
    callback(result)
  }

  /**
   * 完成
   * @param ids 单据ID列表
   */
  async function finish(ids: number[]) {
    const result = await ReimburseApi.finish(ids)
    callback(result)
  }

  /**
   * 删除
   * @param ids 单据ID列表
   */
  async function remove(ids: number[]) {
    const result = await ReimburseApi.delete(ids)
    callback(result)
  }

  return {
    process,
    finish,
    remove,
  }
}
