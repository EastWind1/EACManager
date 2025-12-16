import { useUIStore } from '@/store/UIStore.ts'
import ServiceBillApi from '@/api/ServiceBillApi.ts'
import type { ActionsResult } from '@/model/ActionsResult.ts'
import { h, ref, render } from 'vue'
import { VBtn, VCard, VDatePicker, VDialog } from 'vuetify/components'
import { appContext } from '@/main.ts'

/**
 * 单据操作
 * @param processResult 处理结果回调
 */
export function useBillActions(processResult: (result: ActionsResult<number, void>) => void) {
  const { warning, confirm } = useUIStore()

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
   * 显示日期选择器
   * @param title 标题
   * @param minDate 最小日期
   * @param maxDate 最大日期
   */
  function showDatePicker(
    title: string,
    minDate?: Date,
    maxDate?: Date,
  ): Promise<Date | undefined> {
    return new Promise<Date | undefined>((resolve) => {
      const date = ref<Date>()
      const node = h(
        VDialog,
        {
          modelValue: true,
          persistent: true,
          width: 'auto',
        },
        () =>
          h(VCard, null, {
            title: () => title,
            text: () =>
              h(VDatePicker, {
                modelValue: date.value,
                'onUpdate:modelValue': (value) => (date.value = value as Date),
                min: minDate,
                max: maxDate,
              }),
            actions: () => [
              h(
                VBtn,
                {
                  color: 'primary',
                  text: true,
                  onClick: () => {
                    resolve(date.value)
                    render(null, document.body)
                  },
                },
                () => '确定',
              ),
              h(
                VBtn,
                {
                  text: true,
                  onClick: () => {
                    resolve(undefined)
                    render(null, document.body)
                  },
                },
                () => '取消',
              ),
            ],
          }),
      )

      node.appContext = appContext
      render(node, document.body)
    })
  }

  /**
   * 处理完成
   */
  async function processed(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    const date = await showDatePicker('请选择处理完成日期')
    if (!date) {
      return
    }
    processResult(await ServiceBillApi.processed(ids, date))
  }

  /**
   * 回款完成
   */
  async function finish(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    const date = await showDatePicker('请选择处理完成日期')
    if (!date) {
      return
    }
    processResult(await ServiceBillApi.finish(ids,  date))
  }

  /**
   * 删除
   */
  async function remove(ids: number[]) {
    if (ids.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    const confirmResult = await confirm('确认删除', `确认删除 ${ids.length} 条单据？`)
    if (!confirmResult) {
      return
    }
    processResult(await ServiceBillApi.delete(ids))
  }

  return {
    process,
    processed,
    finish,
    remove,
  }
}
