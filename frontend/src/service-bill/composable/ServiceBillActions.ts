import { useUIStore } from '@/common/store/UIStore'
import type { ActionsResult } from '@/common/model/ActionsResult'
import ServiceBillApi from '../api/ServiceBillApi'

type ServiceBillAction =
  | 'assign'
  | 'process'
  | 'processed'
  | 'finish'
  | 'remove'
  | 'cancelProcess'
  | 'cancelProcessed'
  | 'cancelFinish'

type Options = {
  /**
   * 批量情况下，异常结果展示的第一列取数方法，用于展示是哪一条数据
   */
  getKeyFn?: (id: number) => string
  /**
   * 动作成功后的页面级处理；列表刷新、详情重载、删除后返回都由调用方决定。
   */
  callback?: (action: ServiceBillAction) => void | Promise<void>
}

/**
 * 单据操作
 */
export function useServiceBillActions(options: Options = {}) {
  const { getKeyFn, callback } = options
  const { warning, confirm, showBatchResult } = useUIStore()

  async function invokeCallback(
    action: ServiceBillAction,
    result: ActionsResult<number, void>,
  ) {
    await showBatchResult(result, getKeyFn)
    if (result.successCount > 0) {
      await callback?.(action)
    }
    return result
  }

  /**
   * 开始处理
   */
  async function process(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    return invokeCallback('process', await ServiceBillApi.process(ids))
  }

  /**
   * 处理完成
   */
  async function processed(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    const date = await useUIStore().selectDate('请选择处理完成日期')
    if (!date) {
      return
    }
    return invokeCallback('processed', await ServiceBillApi.processed(ids, date))
  }

  /**
   * 回款完成
   */
  async function finish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    const date = await useUIStore().selectDate('请选择处理完成日期')
    if (!date) {
      return
    }
    return invokeCallback('finish', await ServiceBillApi.finish(ids, date))
  }

  /**
   * 指派/改派负责人；确认交互由调用方负责
   */
  async function assign(ids: number[], assigneeIds: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    return invokeCallback('assign', await ServiceBillApi.assign(ids, assigneeIds))
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
    return invokeCallback('remove', await ServiceBillApi.delete(ids))
  }

  /**
   * 取消处理
   */
  async function cancelProcess(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm('确认', `确认取消处理 ${ids.length} 条单据?`))) {
      return
    }
    return invokeCallback('cancelProcess', await ServiceBillApi.cancelProcess(ids))
  }

  /**
   * 取消处理完成
   */
  async function cancelProcessed(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm('确认', `确认取消处理完成 ${ids.length} 条单据?`))) {
      return
    }
    return invokeCallback('cancelProcessed', await ServiceBillApi.cancelProcessed(ids))
  }

  /**
   * 取消完成
   */
  async function cancelFinish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm('确认', `确认取消完成 ${ids.length} 条单据?`))) {
      return
    }
    return invokeCallback('cancelFinish', await ServiceBillApi.cancelFinish(ids))
  }

  return {
    assign,
    process,
    processed,
    finish,
    remove,
    cancelProcess,
    cancelProcessed,
    cancelFinish,
  }
}
