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

// 时间线（operation_log 同源，倒序）
export function getProjectTimeline(id) {
  return request.get(`/project/${id}/timeline`)
}

// 专家名单
export function listProjectExperts(id) {
  return request.get(`/project/${id}/experts`)
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

// 供应商名单
export function listProjectSuppliers(id) {
  return request.get(`/project/${id}/suppliers`)
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

// 分阶段资料列表
export function listProjectDocs(id, params) {
  return request.get(`/project/${id}/docs`, { params })
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

// 合同列表
export function listProjectContracts(id) {
  return request.get(`/project/${id}/contracts`)
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

// 上传日程文件（pdf/doc/docx/ppt/pptx/jpg/png，≤20MB；覆盖当前生效份）
export function uploadSchedule(id, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/project/${id}/schedule`, fd)
}

// 日程与参会专家汇总（当前日程 + 候选 + 正式记录）
export function getScheduleOverview(id) {
  return request.get(`/project/${id}/schedule`)
}

// 触发解析（规则打底 + 本地大模型兜底，结果为候选，须人工确认）
export function parseSchedule(id, scheduleId) {
  return request.post(`/project/${id}/schedule/${scheduleId}/parse`)
}

// 人工确认保存参会专家（整表覆盖式提交正式记录）
export function confirmSpeakers(id, speakers) {
  return request.put(`/project/${id}/speakers`, { speakers })
}

// 手动补录一条参会专家（解析失败/遗漏兜底）
export function addSpeaker(id, data) {
  return request.post(`/project/${id}/speakers`, data)
}

// 删除一条参会专家记录（候选或正式）
export function deleteSpeaker(id, speakerId) {
  return request.delete(`/project/${id}/speakers/${speakerId}`)
}
