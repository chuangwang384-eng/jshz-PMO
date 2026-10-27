// 审批/申请通用展示纯函数（MyApproval 与 ApprovalManage 共用，防两处漂移）

// 变更摘要：字段: 新值 分号连接
export function changeSummary(changes) {
  if (!changes || !Object.keys(changes).length) return '-'
  return Object.entries(changes)
    .map(([k, v]) => `${k}: ${v ?? ''}`)
    .join('；')
}

// 审批状态 -> Tag 颜色：1已通过 success / 2已驳回 danger / 3已撤回 info / 0待审批 warning
export function approvalStatusTagType(status) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  if (status === 3) return 'info'
  return 'warning'
}
