import request, { downloadFile } from './request'

// 项目管理接口（对应后端 /api/project/*）
// 写操作（创建/编辑/软删/关联专家供应商/资料/合同/配置）需 project:manage；
// 列表/详情/时间线/导出为只读，登录即可（project:view 全员默认）；阶段推进为项目级授权（负责人+执行人）

// 分页列表（筛选：status/startBegin/startEnd/endBegin/endEnd/ownerId/category/typeId/executorId）
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

// 执行人候选（全部在职用户，仅 id/姓名，登录即可；勿直接调 /users——那需要 user:manage 权限）
export function listProjectExecutors() {
  return request.get('/project/executors')
}

// ==================== 配置管理（project:manage） ====================

export function listProjectTypes() {
  return request.get('/project/config/types')
}

export function createProjectType(data) {
  return request.post('/project/config/types', data)
}

export function updateProjectType(id, data) {
  return request.put(`/project/config/types/${id}`, data)
}

export function deleteProjectType(id) {
  return request.delete(`/project/config/types/${id}`)
}

export function listProjectPhases() {
  return request.get('/project/config/phases')
}

export function createProjectPhase(data) {
  return request.post('/project/config/phases', data)
}

export function updateProjectPhase(id, data) {
  return request.put(`/project/config/phases/${id}`, data)
}

export function deleteProjectPhase(id) {
  return request.delete(`/project/config/phases/${id}`)
}

export function listProjectTemplates() {
  return request.get('/project/config/templates')
}

export function createProjectTemplate(data) {
  return request.post('/project/config/templates', data)
}

export function listPhaseTemplates(phaseId) {
  return request.get(`/project/config/phases/${phaseId}/templates`)
}

export function savePhaseTemplates(phaseId, binds) {
  return request.put(`/project/config/phases/${phaseId}/templates`, binds)
}

// ==================== 配置 v2（20261102）：类型流程 + 类型专属绑定 + 模板编辑删除 ====================

// 类型阶段序列（按 sort 升序）
export function listTypePhases(typeId) {
  return request.get(`/project/config/types/${typeId}/phases`)
}

// 全量保存类型阶段序列（入参为排序后的 phaseId 数组）
export function saveTypePhases(typeId, phaseIds) {
  return request.put(`/project/config/types/${typeId}/phases`, phaseIds)
}

// 类型专属阶段模板绑定（无专属时回退全局兜底，行内 fallback=true 标识）
export function listTypePhaseTemplates(typeId, phaseId) {
  return request.get(`/project/config/types/${typeId}/phases/${phaseId}/templates`)
}

// 全量保存类型专属阶段模板绑定
export function saveTypePhaseTemplates(typeId, phaseId, binds) {
  return request.put(`/project/config/types/${typeId}/phases/${phaseId}/templates`, binds)
}

// 修改模板（名称/模板类型）
export function updateProjectTemplate(id, data) {
  return request.put(`/project/config/templates/${id}`, data)
}

// 删除模板（被绑定引用时后端拦截）
export function deleteProjectTemplate(id) {
  return request.delete(`/project/config/templates/${id}`)
}

// PC-20261104: atomic project configuration definitions.
export const getDefinitionContext = () => request.get('/project/config/definition-context')
export const getTypeDefinition = (id) => request.get(`/project/config/types/${id}/definition`)
export const previewDefinition = (data) => request.post('/project/config/definitions/preview', data)
export const saveTypeDefinition = (id, data) => id ? request.put(`/project/config/types/${id}/definition`, data) : request.post('/project/config/definitions', data)
export const orderProjectTypes = (data) => request.put('/project/config/types/order', data)
export const getProjectConfiguration = (id) => request.get(`/project/${id}/configuration`)
