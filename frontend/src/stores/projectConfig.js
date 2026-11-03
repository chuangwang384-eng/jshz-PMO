import { defineStore } from 'pinia'
import {
  listProjectTypes, createProjectType, updateProjectType, deleteProjectType,
  listProjectPhases, createProjectPhase, updateProjectPhase, deleteProjectPhase,
  listProjectTemplates, createProjectTemplate, updateProjectTemplate, deleteProjectTemplate,
  listPhaseTemplates, savePhaseTemplates,
  listTypePhases, saveTypePhases, listTypePhaseTemplates, saveTypePhaseTemplates
} from '../api/project'

/**
 * 项目配置板块图形化改造 —— 统一数据源（设计文档 §6）
 * 图形视图与表格视图共享同一份数据与保存逻辑；后端零改动，复用 /api/project/config/**。
 *
 * 数据模型：
 * - types: project_type 列表
 * - phases: project_phase 列表
 * - templates: project_template 列表
 * - typePhaseRels: { [typeId]: phaseId[] }（type_phase_rel 按 sort 升序）
 * - phaseTemplateRels: { '${scope}_${phaseId}': binds[] }（scope=0 全局兜底；scope=typeId 类型专属）
 *   binds 元素: { templateId, required, sort, fallback }
 */
export const useProjectConfigStore = defineStore('projectConfig', {
  state: () => ({
    types: [],
    phases: [],
    templates: [],
    allTemplates: [],
    typePhaseRels: {},
    phaseTemplateRels: {},
    typeFlowCache: {}, // 兼容旧逻辑：typeId -> [{phaseId, phaseName}]
    loading: false,
    activeView: 'graph' // 'graph' | 'table'
  }),

  getters: {
    // 启用阶段（按 sort 升序）
    activePhases: (state) => state.phases
      .filter((p) => p.isActive === 1)
      .sort((a, b) => a.sort - b.sort),

    // 未启用阶段
    inactivePhases: (state) => state.phases.filter((p) => p.isActive !== 1),

    // 类型排序后的列表
    sortedTypes: (state) => [...state.types].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.id - b.id),

    // 类型流程阶段数（未配置 → null）
    typePhaseCount: (state) => {
      const map = {}
      for (const t of state.types) {
        const flow = state.typePhaseRels[t.id]
        map[t.id] = flow ? flow.length : null
      }
      return map
    }
  },

  actions: {
    // ==================== 加载 ====================
    async loadAll() {
      this.loading = true
      try {
        await Promise.allSettled([this.loadTypes(), this.loadPhases(), this.loadTemplates()])
      } finally {
        this.loading = false
      }
    },

    async loadTypes() {
      this.types = await listProjectTypes()
      // 并行拉取各类型流程，失败不影响类型列表
      await Promise.allSettled(this.types.map((t) => this.loadTypeFlow(t.id)))
    },

    // 拉取单个类型的阶段序列（type_phase_rel）
    async loadTypeFlow(typeId) {
      const flow = await listTypePhases(typeId)
      this.typeFlowCache[typeId] = flow
      this.typePhaseRels[typeId] = flow.map((f) => f.phaseId)
      return flow
    },

    async loadPhases() {
      this.phases = await listProjectPhases()
    },

    async loadTemplates() {
      this.templates = await listProjectTemplates()
      this.allTemplates = this.templates.map((t) => ({ ...t, _bound: false, _required: 0, _sort: 1 }))
    },

    // 拉取某范围某阶段的模板绑定（scope=0 全局；scope=typeId 类型专属，无专属回退全局并标 fallback）
    async loadPhaseTemplateRels(scope, phaseId) {
      const key = `${scope}_${phaseId}`
      let binds
      if (scope > 0) binds = await listTypePhaseTemplates(scope, phaseId)
      else binds = await listPhaseTemplates(phaseId)
      this.phaseTemplateRels[key] = binds
      return binds
    },

    // 确保全量绑定已加载：所有类型流程阶段的专属绑定 + 所有启用阶段的全局兜底绑定（供总览图/缩略图使用）
    async ensureAllBindings() {
      const typeIds = this.types.map((t) => t.id)
      const phaseIds = new Set()
      for (const t of this.types) {
        const flow = this.typePhaseRels[t.id]
        if (flow) flow.forEach((pid) => phaseIds.add(pid))
      }
      // 未配置流程的类型使用全部启用阶段 → 这些阶段也可能回退全局
      this.activePhases.forEach((p) => phaseIds.add(p.id))
      const jobs = []
      for (const pid of phaseIds) jobs.push(this.loadPhaseTemplateRels(0, pid))
      for (const tid of typeIds) {
        const flow = this.typePhaseRels[tid]
        if (flow) flow.forEach((pid) => jobs.push(this.loadPhaseTemplateRels(tid, pid)))
      }
      await Promise.allSettled(jobs)
    },

    // 计算某类型某阶段的绑定结果（专属优先，无则回退全局；返回 { list, fallback }）
    bindsFor(typeId, phaseId) {
      const ownKey = `${typeId}_${phaseId}`
      const own = this.phaseTemplateRels[ownKey]
      if (own && own.length && !own.some((b) => b.fallback)) return { list: own, fallback: false }
      const gKey = `0_${phaseId}`
      const g = this.phaseTemplateRels[gKey]
      return { list: g || own || [], fallback: true }
    },

    // 某全局阶段绑定被多少类型"回退使用"（无专属覆盖的类型；未配置流程的类型使用全部启用阶段，同样计入）
    globalFallbackTypeCount(phaseId) {
      const ids = []
      for (const t of this.types) {
        const flow = this.typePhaseRels[t.id]
        const hasPhase = flow?.length
          ? flow.includes(phaseId)
          : this.activePhases.some((p) => p.id === phaseId)
        if (!hasPhase) continue
        const ownKey = `${t.id}_${phaseId}`
        const own = this.phaseTemplateRels[ownKey]
        if (!own || !own.length || own.some((b) => b.fallback)) ids.push(t.id)
      }
      return ids
    },

    // ==================== 类型 ====================
    // 新增/更新类型基础信息；返回保存后的类型
    async saveType(payload) {
      if (payload.id) {
        await updateProjectType(payload.id, { name: payload.name, sort: payload.sort })
        const t = this.types.find((x) => x.id === payload.id)
        if (t) { t.name = payload.name; t.sort = payload.sort }
        return t
      }
      const created = await createProjectType({ name: payload.name, sort: payload.sort })
      this.types.push(created)
      return created
    },

    // 全量保存类型阶段序列
    async saveTypePhases(typeId, phaseIds) {
      await saveTypePhases(typeId, phaseIds)
      this.typePhaseRels[typeId] = phaseIds.slice()
      // 同步刷新流程缓存（阶段名由 phases 映射）
      this.typeFlowCache[typeId] = phaseIds
        .map((pid) => {
          const p = this.phases.find((x) => x.id === pid)
          return p ? { phaseId: pid, phaseName: p.name } : null
        })
        .filter(Boolean)
    },

    // 删除类型（后端有引用项目时拒删；成功则清理本地缓存）
    async removeType(typeId) {
      await deleteProjectType(typeId)
      this.types = this.types.filter((x) => x.id !== typeId)
      delete this.typePhaseRels[typeId]
      delete this.typeFlowCache[typeId]
      // 清理该类型专属绑定缓存
      for (const key of Object.keys(this.phaseTemplateRels)) {
        if (key.startsWith(`${typeId}_`)) delete this.phaseTemplateRels[key]
      }
    },

    // ==================== 阶段 ====================
    async addPhase(data) {
      const created = await createProjectPhase({ name: data.name, sort: data.sort })
      this.phases.push(created)
      return created
    },
    async updatePhase(id, data) {
      await updateProjectPhase(id, { name: data.name, sort: data.sort, isActive: data.isActive })
      const p = this.phases.find((x) => x.id === id)
      if (p) Object.assign(p, { name: data.name, sort: data.sort, isActive: data.isActive })
    },
    async removePhase(id) {
      await deleteProjectPhase(id)
      this.phases = this.phases.filter((x) => x.id !== id)
    },

    // ==================== 模板 ====================
    async addTemplate(data) {
      const created = await createProjectTemplate({ name: data.name, templateType: data.templateType })
      this.templates.push(created)
      return created
    },
    async updateTemplate(id, data) {
      await updateProjectTemplate(id, { name: data.name, templateType: data.templateType })
      const t = this.templates.find((x) => x.id === id)
      if (t) Object.assign(t, { name: data.name, templateType: data.templateType })
    },
    async removeTemplate(id) {
      await deleteProjectTemplate(id)
      this.templates = this.templates.filter((x) => x.id !== id)
    },

    // ==================== 阶段-模板绑定 ====================
    // scope=0 全局兜底；scope=typeId 类型专属
    async saveScopePhaseTemplates(scope, phaseId, binds) {
      if (scope > 0) await saveTypePhaseTemplates(scope, phaseId, binds)
      else await savePhaseTemplates(phaseId, binds)
      const key = `${scope}_${phaseId}`
      this.phaseTemplateRels[key] = binds.map((b) => ({ ...b }))
    },

    // ==================== 视图 ====================
    toggleView(view) {
      this.activeView = view
    }
  }
})
