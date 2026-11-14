import { useUIStore } from '@/common/store/UIStore'
import type { ActionsResult } from '@/common/model/ActionsResult'
import ReimburseApi from '../api/ReimburseApi'

type ReimburseAction = 'process' | 'finish' | 'remove' | 'cancelProcess' | 'cancelFinish'

type Options = {
  /**
   * 批量情况下，异常结果展示的第一列取数方法，用于展示是哪一条数据
   */
  getKeyFn?: (id: number) => string
  /**
   * 动作成功后的页面级处理；列表刷新、详情重载、删除后返回都由调用方决定。
   */
  callback?: (action: ReimburseAction) => void | Promise<void>
}

/**
 * 报销单操作
 */
export function useReimburseActions(options: Options = {}) {
  const { getKeyFn, callback } = options
  const { warning, confirm, showBatchResult } = useUIStore()

  async function invokeCallback(
    action: ReimburseAction,
    result: ActionsResult<number, void>,
  ) {
    await showBatchResult(result, getKeyFn)
    if (result.successCount > 0) {
      await callback?.(action)
    }
    return result
  }

  /**
   * 提交
   */
  async function process(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    return invokeCallback('process', await ReimburseApi.process(ids))
  }

  /**
   * 处理完成
   */
  async function finish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    return invokeCallback('finish', await ReimburseApi.finish(ids))
  }

  /**
   * 删除
   */
  async function remove(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm('确认', `确认删除 ${ids.length} 条单据?`))) {
      return
    }
    return invokeCallback('remove', await ReimburseApi.delete(ids))
  }

  /**
   * 取消处理
   */
  async function cancelProcess(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm('确认', `确认取消 ${ids.length} 条单据?`))) {
      return
    }
    return invokeCallback('cancelProcess', await ReimburseApi.cancelProcess(ids))
  }

  /**
   * 取消完成
   */
  async function cancelFinish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm('确认', `确认取消 ${ids.length} 条单据?`))) {
      return
    }
    return invokeCallback('cancelFinish', await ReimburseApi.cancelFinish(ids))
  }

  return {
    process,
    finish,
    remove,
    cancelProcess,
    cancelFinish,
  }
}
