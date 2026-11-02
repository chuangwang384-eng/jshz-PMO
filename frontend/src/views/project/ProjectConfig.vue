<template>
  <div>
    <div class="page-head">
      <span class="page-sub">类型字典（含流程配置） · 流程阶段 · 模板管理 · 阶段-模板绑定（需 project:manage）</span>
    </div>

    <el-tabs v-model="tab">
      <!-- 类型字典（含流程配置） -->
      <el-tab-pane name="types">
        <template #label>项目类型<span v-if="typesError" class="err-dot">!</span></template>
        <el-card shadow="never">
          <el-alert v-if="typesError" type="error" :closable="false" style="margin-bottom: 10px">
            类型列表加载失败：{{ typesError }}
            <el-button link type="primary" @click="loadTypes">重试</el-button>
          </el-alert>
          <div style="margin-bottom: 10px">
            <el-input v-model="typeForm.name" placeholder="新类型名称（展会/会议/论坛等）" style="width: 240px" />
            <el-button type="primary" style="margin-left: 8px" :disabled="!typeForm.name.trim()" @click="addType">新增类型</el-button>
          </div>
          <el-table :data="types" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="流程阶段数" width="100">
              <template #default="{ row }">{{ typePhaseCount[row.id] ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="startEditType(row)">编辑</el-button>
                <el-popconfirm title="确认删除该类型？（有项目引用时将拒绝；无引用将级联清理其流程与专属绑定）" width="280" @confirm="delType(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <!-- 编辑类型弹窗：基础信息 + 流程配置（阶段序列）+ 阶段模板绑定 -->
          <el-dialog v-model="editTypeVisible" title="编辑类型（含流程配置）" width="760px" destroy-on-close>
            <el-form label-width="90px">
              <el-form-item label="类型名称" required>
                <el-input v-model="editTypeForm.name" placeholder="类型名称" style="width: 280px" />
              </el-form-item>
              <el-form-item label="排序">
                <el-input-number v-model="editTypeForm.sort" :min="0" />
              </el-form-item>
            </el-form>

            <el-divider content-position="left">流程配置（阶段序列，未配置时该项目类型使用全部启用阶段）</el-divider>
            <div style="margin-bottom: 8px">
              <el-select v-model="flowAddPhaseId" placeholder="添加阶段（仅启用阶段）" style="width: 220px">
                <el-option v-for="p in activePhasesNotInFlow" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-button type="primary" style="margin-left: 8px" :disabled="!flowAddPhaseId" @click="flowAdd">添加</el-button>
            </div>
            <el-table :data="editTypeForm.flow" size="small" max-height="220" stripe>
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="phaseName" label="阶段" />
              <el-table-column label="操作" width="220">
                <template #default="{ $index }">
                  <el-button link :disabled="$index === 0" @click="flowMove($index, -1)">上移</el-button>
                  <el-button link :disabled="$index === editTypeForm.flow.length - 1" @click="flowMove($index, 1)">下移</el-button>
                  <el-button link type="danger" @click="editTypeForm.flow.splice($index, 1)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <template v-if="editTypeForm.flow.length">
              <el-divider content-position="left">阶段模板绑定（该类型专属；未配置的阶段回退全局兜底绑定）</el-divider>
              <el-collapse v-model="expandedPhases">
                <el-collapse-item v-for="fp in editTypeForm.flow" :key="fp.phaseId" :name="fp.phaseId" :title="fp.phaseName">
                  <el-table :data="fp.templates" size="small" max-height="240" stripe>
                    <el-table-column prop="name" label="模板名称" />
                    <el-table-column label="模板类型" width="110">
                      <template #default="{ row }">
                        <el-tag size="small">{{ typeText(row.templateType) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="是否绑定" width="90" align="center">
                      <template #default="{ row }">
                        <el-checkbox v-model="row._bound" />
                      </template>
                    </el-table-column>
                    <el-table-column label="必填/选填" width="110" align="center">
                      <template #default="{ row }">
                        <el-select v-model="row._required" :disabled="!row._bound" size="small" style="width: 90px">
                          <el-option label="必填" :value="1" />
                          <el-option label="选填" :value="0" />
                        </el-select>
                      </template>
                    </el-table-column>
                    <el-table-column label="排序" width="110">
                      <template #default="{ row }">
                        <el-input-number v-model="row._sort" :min="1" size="small" :disabled="!row._bound" />
                      </template>
                    </el-table-column>
                  </el-table>
                  <div v-if="fp.fallback" class="hint-text" style="margin-top: 4px">当前展示的是全局兜底绑定（该类型此阶段尚无专属绑定），保存后即成为类型专属</div>
                </el-collapse-item>
              </el-collapse>
            </template>

            <template #footer>
              <el-button @click="editTypeVisible = false">取消</el-button>
              <el-button type="primary" :loading="savingType" :disabled="!editTypeForm.name.trim()" @click="saveTypeFull">保存</el-button>
            </template>
          </el-dialog>
        </el-card>
      </el-tab-pane>

      <!-- 流程阶段 -->
      <el-tab-pane name="phases">
        <template #label>流程阶段<span v-if="phasesError" class="err-dot">!</span></template>
        <el-card shadow="never">
          <el-alert v-if="phasesError" type="error" :closable="false" style="margin-bottom: 10px">
            阶段列表加载失败：{{ phasesError }}
            <el-button link type="primary" @click="loadPhases">重试</el-button>
          </el-alert>
          <div style="margin-bottom: 10px">
            <el-input v-model="phaseForm.name" placeholder="新阶段名称（如 筹备）" style="width: 200px" />
            <el-input-number v-model="phaseForm.sort" :min="1" placeholder="排序" style="width: 120px; margin-left: 8px" />
            <el-button type="primary" style="margin-left: 8px" :disabled="!phaseForm.name.trim()" @click="addPhase">新增阶段</el-button>
          </div>
          <el-table :data="phases" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="阶段名称" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isActive === 1 ? 'success' : 'info'">{{ row.isActive === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="startEditPhase(row)">编辑</el-button>
                <el-popconfirm title="确认删除该阶段？（被类型流程引用或有项目处于该阶段时将拒绝）" width="280" @confirm="delPhase(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-dialog v-model="editPhaseVisible" title="编辑阶段" width="420px" destroy-on-close>
            <el-input v-model="editPhaseForm.name" placeholder="阶段名称" />
            <el-input-number v-model="editPhaseForm.sort" :min="1" style="margin-top: 10px" />
            <el-switch v-model="editPhaseForm.isActive" active-text="启用" :active-value="1" :inactive-value="0" style="margin-top: 10px" />
            <template #footer>
              <el-button @click="editPhaseVisible = false">取消</el-button>
              <el-button type="primary" :disabled="!editPhaseForm.name.trim()" @click="savePhase">保存</el-button>
            </template>
          </el-dialog>
        </el-card>
      </el-tab-pane>

      <!-- 模板管理（含编辑/删除） -->
      <el-tab-pane name="templates">
        <template #label>模板管理<span v-if="templatesError" class="err-dot">!</span></template>
        <el-card shadow="never">
          <el-alert v-if="templatesError" type="error" :closable="false" style="margin-bottom: 10px">
            模板列表加载失败：{{ templatesError }}
            <el-button link type="primary" @click="loadTemplates">重试</el-button>
          </el-alert>
          <div style="margin-bottom: 10px">
            <el-input v-model="templateForm.name" placeholder="自定义模板名称（如 立项背景）" style="width: 240px" />
            <el-select v-model="templateForm.templateType" placeholder="模板类型" style="width: 160px; margin-left: 8px">
              <el-option label="资料附件" value="FILE" />
              <el-option label="专家" value="EXPERT" />
              <el-option label="供应商" value="SUPPLIER" />
              <el-option label="预算" value="BUDGET" />
              <el-option label="自定义文本" value="RICH_TEXT" />
            </el-select>
            <el-button type="primary" style="margin-left: 8px" :disabled="!templateForm.name.trim() || !templateForm.templateType" @click="addTemplate">新建模板</el-button>
          </div>
          <el-table :data="templates" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="模板名称" />
            <el-table-column label="模板类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ typeText(row.templateType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createBy" label="创建人" width="100" />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button link type="primary" @click="startEditTemplate(row)">编辑</el-button>
                <el-popconfirm title="确认删除该模板？（被绑定引用时将拒绝）" @confirm="delTemplate(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-dialog v-model="editTemplateVisible" title="编辑模板" width="420px" destroy-on-close>
            <el-input v-model="editTemplateForm.name" placeholder="模板名称" />
            <el-select v-model="editTemplateForm.templateType" style="width: 100%; margin-top: 10px">
              <el-option label="资料附件" value="FILE" />
              <el-option label="专家" value="EXPERT" />
              <el-option label="供应商" value="SUPPLIER" />
              <el-option label="预算" value="BUDGET" />
              <el-option label="自定义文本" value="RICH_TEXT" />
            </el-select>
            <template #footer>
              <el-button @click="editTemplateVisible = false">取消</el-button>
              <el-button type="primary" :disabled="!editTemplateForm.name.trim()" @click="saveTemplate">保存</el-button>
            </template>
          </el-dialog>
        </el-card>
      </el-tab-pane>

      <!-- 阶段-模板绑定（配置范围：全局兜底 / 类型专属） -->
      <el-tab-pane label="阶段模板绑定" name="bind">
        <el-card shadow="never">
          <el-alert v-if="bindError" type="error" :closable="false" style="margin-bottom: 10px">
            绑定数据加载失败：{{ bindError }}
            <el-button link type="primary" @click="loadBindings">重试</el-button>
          </el-alert>
          <div style="margin-bottom: 10px">
            <el-select v-model="bindScope" placeholder="配置范围" style="width: 200px" @change="onScopeChange">
              <el-option label="全局兜底（type_id=0）" :value="0" />
              <el-option v-for="t in types" :key="t.id" :label="'类型：' + t.name" :value="t.id" />
            </el-select>
            <el-select v-model="bindPhaseId" placeholder="选择阶段" style="width: 220px; margin-left: 8px" @change="loadBindings">
              <el-option v-for="p in bindPhaseOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-button type="primary" style="margin-left: 8px" :disabled="bindScope === null || !bindPhaseId" @click="saveBindings">保存绑定</el-button>
          </div>
          <div v-if="bindScope > 0 && bindFallback" class="hint-text" style="margin-bottom: 8px">
            当前展示的是全局兜底绑定（该类型此阶段尚无专属绑定），保存后即成为「{{ bindScopeName }}」专属绑定
          </div>
          <el-table v-if="bindScope !== null && bindPhaseId" :data="allTemplates" stripe>
            <el-table-column prop="name" label="模板名称" />
            <el-table-column label="模板类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ typeText(row.templateType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="是否绑定" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row._bound" />
              </template>
            </el-table-column>
            <el-table-column label="必填/选填" width="120" align="center">
              <template #default="{ row }">
                <el-select v-model="row._required" :disabled="!row._bound" size="small" style="width: 90px">
                  <el-option label="必填" :value="1" />
                  <el-option label="选填" :value="0" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="排序" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row._sort" :min="1" size="small" :disabled="!row._bound" />
              </template>
            </el-table-column>
          </el-table>
          <div v-if="bindScope === null || !bindPhaseId" class="hint-text">请先选择配置范围与阶段以配置绑定关系</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listProjectTypes, createProjectType, updateProjectType, deleteProjectType,
  listProjectPhases, createProjectPhase, updateProjectPhase, deleteProjectPhase,
  listProjectTemplates, createProjectTemplate, updateProjectTemplate, deleteProjectTemplate,
  listPhaseTemplates, savePhaseTemplates,
  listTypePhases, saveTypePhases, listTypePhaseTemplates, saveTypePhaseTemplates
} from '../../api/project'

const tab = ref('types')
const types = ref([])
const phases = ref([])
const templates = ref([])
const allTemplates = ref([])

// 各 Tab 独立加载容错（需求一/三：互不影响，可重试）
const typesError = ref('')
const phasesError = ref('')
const templatesError = ref('')
const bindError = ref('')

const typeForm = reactive({ name: '' })
const editTypeVisible = ref(false)
const savingType = ref(false)
const editTypeForm = reactive({ id: null, name: '', sort: 0, flow: [] })
const flowAddPhaseId = ref(null)
const expandedPhases = ref([])
const typePhaseCount = ref({})

const phaseForm = reactive({ name: '', sort: 1 })
const editPhaseVisible = ref(false)
const editPhaseForm = reactive({ id: null, name: '', sort: 1, isActive: 1 })

const templateForm = reactive({ name: '', templateType: 'RICH_TEXT' })
const editTemplateVisible = ref(false)
const editTemplateForm = reactive({ id: null, name: '', templateType: 'FILE' })

const bindScope = ref(null)
const bindPhaseId = ref(null)
const bindFallback = ref(false)
const bindScopeName = ref('')
const typeFlowCache = ref({}) // typeId -> [{phaseId, phaseName}]，选范围后过滤阶段下拉

const activePhasesNotInFlow = computed(() =>
  phases.value.filter((p) => p.isActive === 1 && !editTypeForm.flow.some((f) => f.phaseId === p.id))
)
const bindPhaseOptions = computed(() => {
  if (bindScope.value > 0 && (typeFlowCache.value[bindScope.value] || []).length) {
    const ids = new Set(typeFlowCache.value[bindScope.value].map((f) => f.phaseId))
    return phases.value.filter((p) => ids.has(p.id))
  }
  return phases.value
})

function typeText(t) {
  return { FILE: '资料附件', EXPERT: '专家', SUPPLIER: '供应商', BUDGET: '预算', RICH_TEXT: '自定义文本' }[t] || t
}

// ==================== 独立加载（任一失败不影响其他 Tab） ====================
async function loadTypes() {
  typesError.value = ''
  try {
    types.value = await listProjectTypes()
    for (const t of types.value) {
      try {
        const flow = await listTypePhases(t.id)
        typeFlowCache.value[t.id] = flow
        typePhaseCount.value[t.id] = flow.length
      } catch (e) {
        typePhaseCount.value[t.id] = '-'
      }
    }
  } catch (e) {
    typesError.value = e?.message || String(e)
  }
}
async function loadPhases() {
  phasesError.value = ''
  try {
    phases.value = await listProjectPhases()
  } catch (e) {
    phasesError.value = e?.message || String(e)
  }
}
async function loadTemplates() {
  templatesError.value = ''
  try {
    templates.value = await listProjectTemplates()
  } catch (e) {
    templatesError.value = e?.message || String(e)
  }
}
async function fetchAll() {
  await Promise.allSettled([loadTypes(), loadPhases(), loadTemplates()])
}

// ==================== 类型（含流程配置） ====================
async function addType() {
  await createProjectType({ name: typeForm.name.trim() })
  ElMessage.success('类型已新增')
  typeForm.name = ''
  loadTypes()
}
async function startEditType(row) {
  editTypeForm.id = row.id
  editTypeForm.name = row.name
  editTypeForm.sort = row.sort
  editTypeForm.flow = []
  flowAddPhaseId.value = null
  editTypeVisible.value = true
  // 拉取该类型流程 + 每阶段绑定（无专属回退全局，fallback 标识展示）
  try {
    const flow = await listTypePhases(row.id)
    typeFlowCache.value[row.id] = flow
    typePhaseCount.value[row.id] = flow.length
    editTypeForm.flow = []
    for (const f of flow) {
      const binds = await listTypePhaseTemplates(row.id, f.phaseId)
      const templatesOfPhase = templates.value.length ? templates.value : await listProjectTemplates()
      const map = {}
      binds.forEach((b) => { map[String(b.templateId)] = b })
      editTypeForm.flow.push({
        phaseId: f.phaseId,
        phaseName: f.phaseName,
        fallback: binds.some((b) => b.fallback),
        templates: templatesOfPhase.map((t) => ({
          ...t,
          _bound: !!map[String(t.id)],
          _required: map[String(t.id)] ? map[String(t.id)].required : 0,
          _sort: map[String(t.id)] ? map[String(t.id)].sort : 1
        }))
      })
    }
    expandedPhases.value = []
  } catch (e) {
    ElMessage.error('流程配置加载失败：' + (e?.message || e))
  }
}
function flowAdd() {
  const p = phases.value.find((x) => x.id === flowAddPhaseId.value)
  if (!p) return
  editTypeForm.flow.push({ phaseId: p.id, phaseName: p.name, fallback: false, templates: templates.value.map((t) => ({ ...t, _bound: false, _required: 0, _sort: 1 })) })
  flowAddPhaseId.value = null
}
function flowMove(index, dir) {
  const arr = editTypeForm.flow
  const target = index + dir
  if (target < 0 || target >= arr.length) return
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
}
async function saveTypeFull() {
  savingType.value = true
  try {
    // 统提保存：基础信息 → 阶段序列 → 逐阶段模板绑定；任一步失败弹窗停留并提示环节
    await updateProjectType(editTypeForm.id, { name: editTypeForm.name.trim(), sort: editTypeForm.sort })
    await saveTypePhases(editTypeForm.id, editTypeForm.flow.map((f) => f.phaseId))
    for (const f of editTypeForm.flow) {
      const binds = f.templates.filter((t) => t._bound).map((t) => ({ templateId: t.id, required: t._required, sort: t._sort }))
      await saveTypePhaseTemplates(editTypeForm.id, f.phaseId, binds)
    }
    ElMessage.success('类型（含流程配置）已保存')
    editTypeVisible.value = false
    loadTypes()
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || e))
  } finally {
    savingType.value = false
  }
}
async function delType(row) {
  await deleteProjectType(row.id)
  ElMessage.success('类型已删除')
  loadTypes()
}

// ==================== 流程阶段 ====================
async function addPhase() {
  await createProjectPhase({ name: phaseForm.name.trim(), sort: phaseForm.sort })
  ElMessage.success('阶段已新增')
  phaseForm.name = ''
  phaseForm.sort = 1
  loadPhases()
}
function startEditPhase(row) {
  editPhaseForm.id = row.id
  editPhaseForm.name = row.name
  editPhaseForm.sort = row.sort
  editPhaseForm.isActive = row.isActive
  editPhaseVisible.value = true
}
async function savePhase() {
  await updateProjectPhase(editPhaseForm.id, {
    name: editPhaseForm.name.trim(),
    sort: editPhaseForm.sort,
    isActive: editPhaseForm.isActive
  })
  ElMessage.success('阶段已更新')
  editPhaseVisible.value = false
  loadPhases()
}
async function delPhase(row) {
  await deleteProjectPhase(row.id)
  ElMessage.success('阶段已删除')
  loadPhases()
}

// ==================== 模板管理 ====================
async function addTemplate() {
  await createProjectTemplate({ name: templateForm.name.trim(), templateType: templateForm.templateType })
  ElMessage.success('模板已新建')
  templateForm.name = ''
  loadTemplates()
}
function startEditTemplate(row) {
  editTemplateForm.id = row.id
  editTemplateForm.name = row.name
  editTemplateForm.templateType = row.templateType
  editTemplateVisible.value = true
}
async function saveTemplate() {
  await updateProjectTemplate(editTemplateForm.id, { name: editTemplateForm.name.trim(), templateType: editTemplateForm.templateType })
  ElMessage.success('模板已更新')
  editTemplateVisible.value = false
  loadTemplates()
}
async function delTemplate(row) {
  await deleteProjectTemplate(row.id)
  ElMessage.success('模板已删除')
  loadTemplates()
}

// ==================== 阶段-模板绑定（范围：全局兜底/类型专属） ====================
function onScopeChange() {
  bindPhaseId.value = null
  bindFallback.value = false
  allTemplates.value = templates.value.map((t) => ({ ...t, _bound: false, _required: 0, _sort: 1 }))
}
async function loadBindings() {
  if (bindScope.value === null || !bindPhaseId.value) return
  bindError.value = ''
  try {
    const binds = bindScope.value > 0
      ? await listTypePhaseTemplates(bindScope.value, bindPhaseId.value)
      : await listPhaseTemplates(bindPhaseId.value)
    bindFallback.value = binds.some((b) => b.fallback)
    const scope = types.value.find((t) => t.id === bindScope.value)
    bindScopeName.value = scope ? scope.name : ''
    const map = {}
    binds.forEach((b) => { map[String(b.templateId)] = b })
    const base = templates.value.length ? templates.value : await listProjectTemplates()
    allTemplates.value = base.map((t) => {
      const b = map[String(t.id)]
      return { ...t, _bound: !!b, _required: b ? b.required : 0, _sort: b ? b.sort : 1 }
    })
  } catch (e) {
    bindError.value = e?.message || String(e)
  }
}
async function saveBindings() {
  const binds = allTemplates.value
    .filter((t) => t._bound)
    .map((t) => ({ templateId: t.id, required: t._required, sort: t._sort }))
  if (bindScope.value > 0) {
    await saveTypePhaseTemplates(bindScope.value, bindPhaseId.value, binds)
  } else {
    await savePhaseTemplates(bindPhaseId.value, binds)
  }
  ElMessage.success('绑定已保存')
  loadBindings()
}

onMounted(fetchAll)
</script>

<style scoped>
.page-head { margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; }
.page-sub { margin-left: 8px; color: #909399; font-size: 13px; }
.hint-text { color: #909399; font-size: 13px; }
.err-dot { color: #f56c6c; margin-left: 4px; font-weight: 700; }
</style>
