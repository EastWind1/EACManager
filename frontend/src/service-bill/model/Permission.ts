/**
 * 服务单模块动作权限
 */
export const Permission = {
  SERVICE_BILL_QUERY: 'serviceBill:query',
  SERVICE_BILL_DETAIL: 'serviceBill:detail',
  SERVICE_BILL_CREATE: 'serviceBill:create',
  SERVICE_BILL_UPDATE: 'serviceBill:update',
  SERVICE_BILL_DELETE: 'serviceBill:delete',
  SERVICE_BILL_IMPORT: 'serviceBill:import',
  SERVICE_BILL_EXPORT: 'serviceBill:export',
  SERVICE_BILL_PROCESS: 'serviceBill:process',
  SERVICE_BILL_PROCESSED: 'serviceBill:processed',
  SERVICE_BILL_FINISH: 'serviceBill:finish',
  SERVICE_BILL_CANCEL_PROCESS: 'serviceBill:cancel-process',
  SERVICE_BILL_CANCEL_PROCESSED: 'serviceBill:cancel-processed',
  SERVICE_BILL_CANCEL_FINISH: 'serviceBill:cancel-finish',
  SERVICE_BILL_STATISTIC: 'serviceBill:statistic',
} as const
