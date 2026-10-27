// 审批/申请通用展示纯函数（MyApproval 与 ApprovalManage 共用，防两处漂移）

// 照片字段：后端 changes 中已统一为「已更换新照片」文案，旧值为文件名不参与摘要对比
const PHOTO_KEYS = ['照片', '身份证正面', '身份证反面']

// 变更摘要：字段: 新值 分号连接；传入 oldData（旧值快照）且有旧值时升级为「字段: 旧值 -> 新值」
export function changeSummary(changes, oldData) {
  if (!changes || !Object.keys(changes).length) return '-'
  return Object.entries(changes)
    .map(([k, v]) => {
      if (PHOTO_KEYS.includes(k)) return `${k}: ${v ?? ''}`
      const old = oldData?.[k]
      if (old !== undefined && old !== null && String(old).trim() !== '') {
        return `${k}: ${old} → ${v ?? ''}`
      }
      return `${k}: ${v ?? ''}`
    })
    .join('；')
}

// 审批状态 -> Tag 颜色：1已通过 success / 2已驳回 danger / 3已撤回 info / 0待审批 warning
export function approvalStatusTagType(status) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  if (status === 3) return 'info'
  return 'warning'
}
