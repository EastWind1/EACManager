import { useUIStore } from '@/store/UIStore.ts'
import ServiceBillApi from '@/api/ServiceBillApi.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import pinia from '@/store'

/**
 * 单据操作
 * @param processResult 处理结果回调
 */
export function useBillActions(processResult: (result: ActionsResult<number, void>) => void) {
  const {warning, showConfirmDialog } = useUIStore(pinia)
  /**
   * 开始处理
   */
  async function process(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    processResult(await ServiceBillApi.process(ids))
  }

  /**
   * 处理完成
   */
  async function processed(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    processResult(await ServiceBillApi.processed(ids))
  }

  /**
   * 回款完成
   */
  async function finish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    processResult(await ServiceBillApi.finish(ids))
  }
  /**
   * 删除
   */
  async function remove(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    const confirmResult = await showConfirmDialog(
      '确认删除',
      `确认删除 ${ids.length} 条单据？`,
    )
    if (!confirmResult) {
      return
    }
    processResult(await ServiceBillApi.delete(ids))
  }

  return {
    process,
    processed,
    finish,
    remove
  }
}
