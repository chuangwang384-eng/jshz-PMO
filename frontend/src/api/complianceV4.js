import request from './request'
import { listKnowledgeDocs } from './knowledge'
async function allPages(fetch) {
  const rows = []
  for (let page = 1; ; page++) {
    const d = await fetch(page)
    const batch = d.records || d.items || []
    rows.push(...batch)
    if (!batch.length || rows.length >= (d.total || 0)) return rows
  }
}
// 企业合规 v4（/api/compliance/v4/*，REQ/DES-COMPLIANCE-V4）
// 2026-11-16 清理：「我的执行」UI（ExecutionBoard）已按需求规则6于 1.0.37.34 移除，
// 其后端写接口同步关闭，此处一并删除已无调用方的 executions/tasks/模板草稿接口封装。
export const listAllComplianceDocuments = () => allPages(page => listKnowledgeDocs({ category: 'enterprise-compliance', page, pageSize: 100 }))
export const getComplianceMode = () => request.get('/compliance/v4/mode')
export const listLegacyHistory = () => request.get('/compliance/v4/legacy-history')
export function getTemplateDetail(templateVersionId) {
  return request.get(`/compliance/v4/templates/${templateVersionId}`)
}
