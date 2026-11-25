// 消息通知类型字典（前端唯一副本，2026-11-25 收口）——对应后端 NotificationService.KNOWN_TYPES（16 类）
// 与 MUTABLE_TYPES（password_reset 不可免打扰）。
//
// 消费方：views/MessageCenter.vue、components/MessageDrawerBell.vue
// 约定（AGENTS 17.7 / 19.2）：后端新增通知类型（TYPE_* 常量 + KNOWN_TYPES）时，必须在同一次提交内同步本文件，
// 不得在页面/组件里再写第二份字面量副本。

/** 类型 -> 中文文案（与后端 KNOWN_TYPES 一一对应） */
export const TYPE_LABELS = {
  approval_result: '审批结果',
  approval_todo: '待审批',
  approval_withdrawn: '申请撤回',
  approval_transferred: '审批转办',
  approval_assigned: '审批加签',
  role_changed: '角色调整',
  password_reset: '密码重置',
  doc_off_shelf: '文档下架',
  doc_deleted: '文档删除',
  doc_updated: '文档更新',
  doc_relisted: '文档上架',
  compliance_published: '合规事项发布',
  compliance_updated: '合规事项更新',
  compliance_due: '合规到期提醒',
  compliance_assigned: '合规责任人指派',
  compliance_done_notify: '合规责任人完成提醒'
}

/** 类型 -> el-tag 颜色 */
export function typeTagType(type) {
  if (type === 'approval_result') return 'success'
  if (type === 'approval_todo' || type === 'approval_transferred' || type === 'approval_assigned') return 'warning'
  if (type === 'role_changed' || type === 'password_reset') return 'danger'
  if (type && type.startsWith('compliance_')) return type === 'compliance_due' ? 'warning' : 'primary'
  return 'info'
}

/** 筛选与偏好设置的分组展示顺序（组内顺序即展示顺序；不含 password_reset 的组供免打扰偏好复用） */
export const TYPE_GROUPS = [
  { label: '审批通知', types: ['approval_result', 'approval_todo', 'approval_withdrawn', 'approval_transferred', 'approval_assigned'] },
  { label: '账号安全', types: ['role_changed', 'password_reset'] },
  { label: '知识星球', types: ['doc_updated', 'doc_relisted', 'doc_off_shelf', 'doc_deleted'] },
  { label: '企业合规', types: ['compliance_published', 'compliance_updated', 'compliance_due', 'compliance_assigned', 'compliance_done_notify'] }
]

/** 允许免打扰的类型（后端 MUTABLE_TYPES 展示层副本：password_reset 强制送达不可关闭） */
export const MUTABLE_TYPES = TYPE_GROUPS.flatMap((g) => g.types).filter((t) => t !== 'password_reset')

/**
 * 相对时间文案（需求 B，2026-11-25）：1 小时内按分钟、当天按小时、7 天内按天，更早回退绝对时间。
 * 入参为后端 'yyyy-MM-dd HH:mm:ss'（或 ISO）字符串；空值返回 ''。
 */
export function formatRelativeTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ').slice(0, 19)
  const then = new Date(text.replace(/-/g, '/'))
  if (Number.isNaN(then.getTime())) return text
  const diff = Date.now() - then.getTime()
  if (diff < 0) return text
  const minute = 60 * 1000
  if (diff < minute) return '刚刚'
  if (diff < 60 * minute) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < 24 * 60 * minute) return `${Math.floor(diff / (60 * minute))} 小时前`
  if (diff < 7 * 24 * 60 * minute) return `${Math.floor(diff / (24 * 60 * minute))} 天前`
  return text
}
