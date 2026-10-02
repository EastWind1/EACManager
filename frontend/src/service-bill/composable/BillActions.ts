import { useUIStore } from '@/common/store/UIStore'
import ServiceBillApi from '../api/ServiceBillApi'

/**
 * 单据操作
 */
export function useBillActions(getKeyFn?: (id: number) => string) {
  const { warning, confirm, showBatchResult } = useUIStore()

  /**
   * 开始处理
   */
  async function process(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    return showBatchResult(await ServiceBillApi.process(ids), getKeyFn)
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
    return showBatchResult(await ServiceBillApi.processed(ids, date), getKeyFn)
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
    return showBatchResult(await ServiceBillApi.finish(ids, date), getKeyFn)
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
    return showBatchResult(await ServiceBillApi.delete(ids), getKeyFn)
  }

  /**
   * 取消处理
   */
  async function cancelProcess(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm(`确认`, `确认取消处理 ${ids.length} 条单据?`))) {
      return
    }
    return showBatchResult(await ServiceBillApi.cancelProcess(ids), getKeyFn)
  }

  /**
   * 取消处理完成
   */
  async function cancelProcessed(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm(`确认`, `确认取消处理完成 ${ids.length} 条单据?`))) {
      return
    }
    return showBatchResult(await ServiceBillApi.cancelProcessed(ids), getKeyFn)
  }

  /**
   * 取消完成
   */
  async function cancelFinish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    if (!(await confirm(`确认`, `确认取消完成 ${ids.length} 条单据?`))) {
      return
    }
    return showBatchResult(await ServiceBillApi.cancelFinish(ids), getKeyFn)
  }

  return {
    process,
    processed,
    finish,
    remove,
    cancelProcess,
    cancelProcessed,
    cancelFinish,
  }
}
