import request, { downloadFile } from './request'

// 项目管理接口（对应后端 /api/project/*）
// 写操作（创建/编辑/软删/关联专家供应商/资料/合同/日程/参会专家）需 project:manage；
// 列表/详情/时间线/导出为只读，登录即可（project:view 全员默认）；阶段推进为项目级授权（负责人+创建人）
// 2026-11-09 项目流程重塑：项目类型/执行人概念删除，类型流程配置接口后端已 410，相关方法一并移除

// 分页列表（筛选：status/startBegin/startEnd/endBegin/endEnd/ownerId/category/team/cityId/assistantId）
export function listProjects(params) {
  return request.get('/project', { params })
}

// 创建项目
export function createProject(data) {
  return request.post('/project', data)
}

// 项目详情：{project, phases, types, executors, experts, suppliers, docs, contracts, budget, timeline, phaseTemplates, canOperate}
export function getProjectDetail(id) {
  return request.get(`/project/${id}`)
}

// 编辑项目
export function updateProject(id, data) {
  return request.put(`/project/${id}`, data)
}

// 软删除
export function deleteProject(id) {
  return request.delete(`/project/${id}`)
}

// 推进/回退阶段（推进仅相邻+材料校验；回退含跨级，reason 回退必填）
export function changePhase(id, phaseId, reason) {
  return request.put(`/project/${id}/phase`, { phaseId, reason })
}

// 取消（必填原因）
export function cancelProject(id, reason) {
  return request.put(`/project/${id}/cancel`, { reason })
}

// 恢复已取消项目（Owner 2026-11-10：与取消对称，负责人/创建人；阶段保持不变）
export function restoreProject(id) {
  return request.put(`/project/${id}/restore`)
}

// 调派专家
export function addProjectExpert(id, data) {
  return request.post(`/project/${id}/experts`, data)
}

// 移出专家（留痕）
export function removeProjectExpert(id, expertId) {
  return request.delete(`/project/${id}/experts/${expertId}`)
}

// 新建专家卡并自动关联（留痕）
export function createExpertCard(id, data) {
  return request.post(`/project/${id}/experts/new-card`, data)
}

// 关联供应商（用途自动带出）
export function addProjectSupplier(id, data) {
  return request.post(`/project/${id}/suppliers`, data)
}

// 移出供应商（留痕）
export function removeProjectSupplier(id, supplierId) {
  return request.delete(`/project/${id}/suppliers/${supplierId}`)
}

// 登记专家劳务费
export function saveExpertFee(id, expertId, fee) {
  return request.put(`/project/${id}/experts/${expertId}/fee`, { fee })
}

// 填写供应商费用
export function saveSupplierFee(id, supplierId, fee) {
  return request.put(`/project/${id}/suppliers/${supplierId}/fee`, { fee })
}

// 上传分阶段资料
export function uploadProjectDoc(id, phaseId, file) {
  const fd = new FormData()
  fd.append('phaseId', phaseId)
  fd.append('file', file)
  return request.post(`/project/${id}/docs`, fd)
}

// 删除分阶段资料（软删留痕）
export function deleteProjectDoc(id, docId) {
  return request.delete(`/project/${id}/docs/${docId}`)
}

// 保存富文本（自定义文本模板）
export function saveRichText(id, data) {
  return request.put(`/project/${id}/docs/richtext`, data)
}

// 登记预算金额
export function registerBudget(id, amount) {
  return request.put(`/project/${id}/budget`, { amount })
}

// 登记合同（自动带出专家敏感字段）
export function createProjectContract(id, data) {
  return request.post(`/project/${id}/contracts`, data)
}

// 修改合同敏感字段（同步回写 expert_info 留痕）
export function updateContractSensitive(id, contractId, data) {
  return request.put(`/project/${id}/contracts/${contractId}/sensitive`, data)
}

// 上传合同文件
export function uploadContractFile(id, contractId, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/project/${id}/contracts/${contractId}/file`, fd)
}

// 发起合同审批（复用现有审批流）
export function submitContractApproval(id, contractId) {
  return request.post(`/project/${id}/contracts/${contractId}/approval`)
}

// 导出 xlsx（含专家/供应商明细；DOWNLOAD 留痕）
export function exportProjects(params) {
  return downloadFile('/project/export', params)
}

// 负责人候选（PROJECT_OWNER 角色用户 + 超级管理员）
export function listProjectOwners() {
  return request.get('/project/owners')
}

// 用户选项（全部在职用户，仅 id/姓名，登录即可；负责人/协助人筛选与选择，替代已删的 /executors）
export function listProjectUsers() {
  return request.get('/project/user-options')
}

// 阶段选项（激活阶段字典，登录即可；阶段筛选/展示用）
export function listPhaseOptions() {
  return request.get('/project/phase-options')
}

// ==================== 日程上传（2026-11-09 流程重塑第一阶段） ====================

// 上传日程文件（pdf/doc/docx/ppt/pptx/jpg/png，≤50MB；覆盖当前生效份）
export function uploadSchedule(id, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/project/${id}/schedule`, fd, { timeout: 180000 })
}

// 日程与参会专家汇总（当前日程 + 候选 + 正式记录）
export function getScheduleOverview(id) {
  return request.get(`/project/${id}/schedule`)
}

// 触发解析（规则打底 + 本地大模型兜底，结果为候选，须人工确认）
export function parseSchedule(id, scheduleId) {
  return request.post(`/project/${id}/schedule/${scheduleId}/parse`, null, { timeout: 180000 })
}

// 删除日程阶段附件（软删留痕；删生效日程联动清日程记录与候选）
export function deleteScheduleDoc(id, docId) {
  return request.delete(`/project/${id}/schedule/docs/${docId}`)
}

// 确认新增候选，保留已保存的参会记录
export function confirmSpeakers(id, scheduleId, parseRevision, speakers) {
  return request.put(`/project/${id}/speakers`, { speakers }, { params: { scheduleId, parseRevision } })
}

export function applyScheduleComparison(id, scheduleId, token, accepted) {
  return request.post(`/project/${id}/schedule/${scheduleId}/comparison`, { token, accepted })
}

// 手动补录一条参会专家（解析失败/遗漏兜底）
export function addSpeaker(id, data) {
  return request.post(`/project/${id}/speakers`, data)
}

// 删除一条参会专家记录（候选或正式）
export function deleteSpeaker(id, speakerId) {
  return request.delete(`/project/${id}/speakers/${speakerId}`)
}

// 手动建档单条（成员级）：建档并按姓名回绑参会专家
export function archiveManual(id, data, photo) {
  const url = `/project/${id}/speakers/archive-manual`
  if (!photo) return request.post(url, data)
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  form.append('photo', photo)
  return request.post(url, form)
}

// 项目文件流（Owner 2026-11-12 在线预览/下载）：blob 响应；preview=true Office 转 PDF，失败返回 JSON {code,message}
export function fetchProjectFile(fileName, download = false, preview = false) {
  const params = new URLSearchParams()
  if (download) params.set('download', '1')
  if (preview) params.set('preview', 'true')
  const qs = params.toString()
  return request.get(`/project/files/${encodeURIComponent(fileName)}${qs ? '?' + qs : ''}`, { responseType: 'blob' })
}

// 项目文件 Excel 工作簿分页预览（2026-11-17）：后端复用知识星球 ExcelPreviewService，
// 返回结构与 fetchKnowledgeWorkbook 一致（消费方：ProjectDetail 预览抽屉）
export function fetchProjectWorkbook(fileName, params) {
  return request.get(`/project/files/${encodeURIComponent(fileName)}/workbook`, { params })
}

// ==================== 会议预算制作（2026-11-16 新阶段，位于日程上传之前） ====================

// 预算单总览（不存在返回骨架草稿，不落库；含 feeStandards 收费默认值）
export function getProjectBudget(id) {
  return request.get(`/project/${id}/meeting-budget`)
}

// 整单保存（confirm=false 草稿 / true 确认报价）
export function saveProjectBudget(id, data) {
  return request.put(`/project/${id}/meeting-budget`, data)
}

export function listBudgetQuotes(id) {
  return request.get(`/project/${id}/meeting-budget/quotes`)
}

export function generateBudgetQuote(id, { requestKey, clientNote }) {
  return request.post(`/project/${id}/meeting-budget/quotes`, { requestKey, clientNote })
}

export function downloadBudgetQuote(id, versionId) {
  return request.get(`/project/${id}/meeting-budget/quotes/${versionId}/file`, { responseType: 'blob' })
}

export function recordBudgetQuoteConfirmation(id, versionId, data) {
  return request.post(`/project/${id}/meeting-budget/quotes/${versionId}/customer-confirmation`, data)
}

export function restoreBudgetQuote(id, versionId) {
  return request.post(`/project/${id}/meeting-budget/quotes/${versionId}/restore`)
}

// 收费标准（管理员设置-项目配置）
export function listBudgetFeeStandards(scene) {
  return request.get('/project/config/budget-fee-standards', { params: { scene } })
}

export function saveBudgetFeeStandards(rows) {
  return request.put('/project/config/budget-fee-standards', { rows })
}
