import { useUIStore } from '@/common/store/UIStore'
import ReimburseApi from '../api/ReimburseApi'

/**
 * 报销单操作
 */
export function useReimburseActions(getKeyFn?: (id: number) => string) {
  const { warning, confirm, showBatchResult } = useUIStore()

  /**
   * 提交
   */
  async function process(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    return showBatchResult(await ReimburseApi.process(ids), getKeyFn)
  }

  /**
   * 处理完成
   */
  async function finish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    return showBatchResult(await ReimburseApi.finish(ids), getKeyFn)
  }

  /**
   * 删除
   */
  async function remove(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm(`确认`, `确认删除 ${ids.length} 条单据?`))) {
      return
    }
    return showBatchResult(await ReimburseApi.delete(ids), getKeyFn)
  }

  /**
   * 取消处理
   */
  async function cancelProcess(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm(`确认`, `确认取消 ${ids.length} 条单据?`))) {
      return
    }
    return showBatchResult(await ReimburseApi.cancelProcess(ids), getKeyFn)
  }

  /**
   * 取消完成
   */
  async function cancelFinish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm(`确认`, `确认取消 ${ids.length} 条单据?`))) {
      return
    }
    return showBatchResult(await ReimburseApi.cancelFinish(ids), getKeyFn)
  }

  return {
    process,
    finish,
    remove,
    cancelProcess,
    cancelFinish,
  }
}
