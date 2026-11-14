import {computed, ref} from 'vue'
import {useUIStore} from '@/common/store/UIStore'
import ServiceBillApi from '../api/ServiceBillApi'
import {type Assignee, type ServiceBill, isAssignableState} from '../model/ServiceBill'
import type {ActionsResult} from '@/common/model/ActionsResult'

/**
 * 指派弹窗与候选人加载的公共逻辑。
 * 列表和详情页共用同一套状态校验、候选人缓存和提交行为。
 */
type AssignAction = (ids: number[], assigneeIds: number[]) => Promise<ActionsResult<number, void> | undefined>

export function useServiceBillAssign(assign: AssignAction) {
  const {warning} = useUIStore()

  const assignDialog = ref(false)
  const assignTargetIds = ref<number[]>([])
  const assigneeOptions = ref<Assignee[]>([])
  const assignIds = ref<number[]>([])

  const assignCount = computed(() => assignIds.value.length)

  async function loadAssignees(force = false) {
    if (!force && assigneeOptions.value.length > 0) {
      return
    }
    assigneeOptions.value = await ServiceBillApi.getAssignees()
  }

  async function openAssign(bills: ServiceBill[]) {
    if (bills.length === 0) {
      warning('请选择要操作的单据')
      return
    }
    const assignable = bills.filter(
      (bill): bill is ServiceBill & { id: number } =>
        bill.id !== undefined && isAssignableState(bill.state),
    )
    if (assignable.length !== bills.length) {
      warning('只有新建或处理中的单据才能指派')
    }
    if (assignable.length === 0) {
      return
    }
    assignIds.value = assignable.map((bill) => bill.id)
    assignTargetIds.value = []
    assignDialog.value = true
    await loadAssignees()
  }

  async function confirmAssign() {
    const ids = [...assignIds.value]
    const targetIds = [...assignTargetIds.value]
    assignDialog.value = false
    if (ids.length === 0) {
      return
    }
    await assign(ids, targetIds)
  }

  return {
    assignDialog,
    assignTargetIds,
    assigneeOptions,
    assignCount,
    loadAssignees,
    openAssign,
    confirmAssign,
  }
}
