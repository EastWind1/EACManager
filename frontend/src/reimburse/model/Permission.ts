/**
 * 报销单模块动作权限
 */
export const Permission = {
  REIMBURSE_QUERY: 'reimburse:query',
  REIMBURSE_DETAIL: 'reimburse:detail',
  REIMBURSE_CREATE: 'reimburse:create',
  REIMBURSE_UPDATE: 'reimburse:update',
  REIMBURSE_DELETE: 'reimburse:delete',
  REIMBURSE_IMPORT: 'reimburse:import',
  REIMBURSE_EXPORT: 'reimburse:export',
  REIMBURSE_PROCESS: 'reimburse:process',
  REIMBURSE_FINISH: 'reimburse:finish',
  REIMBURSE_CANCEL_PROCESS: 'reimburse:cancel-process',
  REIMBURSE_CANCEL_FINISH: 'reimburse:cancel-finish',
  REIMBURSE_STATISTIC: 'reimburse:statistic',
} as const
