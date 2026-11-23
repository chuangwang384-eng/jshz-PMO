export const moduleNames = { knowledge: '知识文档', expert: '专家', project: '项目', customer: '客户', supplier: '供应商', announcement: '公告' }
export const warningNames = { OVERDUE: '已逾期', DUE_TODAY: '今天到期', DUE_SOON: '七天内到期', NONE: '' }
export function safeLocalPath(value, fallback = '/dashboard') {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u0020]/.test(value)) return fallback
  try { const url = new URL(value, 'http://local'); return url.origin === 'http://local' && url.pathname !== '/login' ? value : fallback } catch { return fallback }
}
export function safeTarget(router, target) {
  if (!target || typeof target.path !== 'string' || safeLocalPath(target.path, '') !== target.path) return null
  const allowed = /^(?:\/dashboard|\/experts|\/project(?:\/\d+)?|\/supplier(?:\/\d+)?|\/profile\/(?:overview|my-approvals|approval\/manage|notifications|directory|announcements(?:\/\d+)?)|\/knowledge-planet\/(?:meeting-execution|association-compliance|enterprise-compliance|customer-info|customer-detail\/\d+))$/
  if (!allowed.test(target.path)) return null
  const query = Object.fromEntries(Object.entries(target.query || {}).filter(([key, val]) => ['keyword','searchMode','docId','expertId','applicationId','notificationId','status','favorites','actionable','from'].includes(key) && typeof val === 'string'))
  if (target.path === '/knowledge-planet/enterprise-compliance' && target.query?.tab === 'docs') query.tab = 'docs'
  return router.resolve({ path: target.path, query }).matched.length ? { path: target.path, query } : null
}
export function latestRequest() { let id = 0; return { next: () => ++id, current: value => value === id } }

export function sectionRequests() {
  let sequence=0
  const versions=new Map()
  return {
    begin(keys){const id=++sequence;for(const key of keys)versions.set(key,id);return id},
    current(key,id){return versions.get(key)===id}
  }
}
