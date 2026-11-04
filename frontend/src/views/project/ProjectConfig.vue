<template>
  <div>
    <div class="page-head">
      <span class="page-title">项目配置</span>
      <span class="page-sub">类型字典（含流程配置） · 流程阶段 · 模板管理 · 阶段-模板绑定（需 project:manage）</span>
    </div>

    <!-- 视图切换（默认图形视图） -->
    <div class="view-switch">
      <el-radio-group v-model="activeView" @change="onViewChange">
        <el-radio-button value="graph">图形视图</el-radio-button>
        <el-radio-button value="table">表格视图</el-radio-button>
      </el-radio-group>
    </div>

    <el-button class="order-entry" @click="openOrder">调整类型顺序</el-button>
    <el-dialog v-model="orderVisible" title="调整类型展示顺序" width="600px" :close-on-click-modal="false">
      <p>拖动或上下移动，保存后自动排为连续且唯一的 1～N；不改变项目内阶段顺序。</p>
      <div v-for="(t,i) in orderTypes" :key="t.id" class="order-row" draggable="true" @dragstart="orderDrag=i" @dragover.prevent @drop.prevent="dropOrder(i)">
        <span>{{ i+1 }}. {{ t.name }}</span><el-button :disabled="i===0" @click="moveOrder(i,-1)">上移</el-button><el-button :disabled="i===orderTypes.length-1" @click="moveOrder(i,1)">下移</el-button>
      </div>
      <template #footer><el-button @click="orderVisible=false">取消</el-button><el-button type="primary" :loading="orderSaving" @click="saveOrder">保存顺序</el-button></template>
    </el-dialog>
    <step-wizard v-model:visible="wizardVisible" :type="wizardType" @saved="onWizardSaved" />
    <!-- ==================== 图形视图 ==================== -->
    <div v-if="activeView === 'graph'" class="graph-view">
      <el-alert v-if="typesError" :title="typesError" type="error" :closable="false"/>
      <el-card shadow="never">
        <div class="graph-toolbar">
          <div>
            <div class="graph-title">项目类型流程总览</div>
            <div class="graph-sub">一屏展示全部类型及各类型流程阶段连线图，阶段节点下挂模板卡片（绿=单独配置 / 蓝=沿用通用）</div>
          </div>
          <el-button type="primary" @click="openWizard(null)">新增类型</el-button>
        </div>
        <flow-overview @edit-type="openWizard" />
      </el-card>

      <el-card shadow="never" style="margin-top: 14px">
        <div class="graph-toolbar">
          <div>
            <div class="graph-title">类型列表 · 迷你流程缩略图</div>
            <div class="graph-sub">鼠标悬停可查看完整流程；点击「编辑」进入三步向导</div>
          </div>
        </div>
        <el-table :data="store.sortedTypes" stripe>
          <el-table-column prop="name" label="类型名称" min-width="120" />
          <el-table-column label="流程缩略图" min-width="180">
            <template #default="{ row }">
              <mini-flow-thumbnail :phases="typeFlowOf(row.id)" :label="row.name" />
            </template>
          </el-table-column>
          <el-table-column label="阶段数" width="90">
            <template #default="{ row }">{{ store.typePhaseCount[row.id] ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openWizard(row)">编辑</el-button>
              <el-button link type="danger" @click="openDeleteType(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 三步向导 -->


      <!-- 删除类型：影响面提示 -->
      <el-dialog v-model="deleteTypeVisible" title="删除类型 · 影响面确认" width="680px" destroy-on-close>
        <impact-preview
          v-if="deleteTarget"
          :type="deleteTarget"
          :flow-count="deleteFlowCount"
          :own-bind-count="deleteOwnBindCount"
        />
        <template #footer>
          <el-button @click="deleteTypeVisible = false">取消</el-button>
          <el-button type="danger" :loading="deletingType" @click="confirmDeleteType">确认删除</el-button>
        </template>
      </el-dialog>
    </div>

    <!-- ==================== 表格视图（保留原有交互） ==================== -->
    <el-tabs v-else v-model="tab">
      <!-- 类型字典（含流程配置） -->
      <el-tab-pane name="types">
        <template #label>项目类型<span v-if="typesError" class="err-dot">!</span></template>
        <el-card shadow="never">
          <el-alert v-if="typesError" type="error" :closable="false" style="margin-bottom: 10px">
            类型列表加载失败：{{ typesError }}
            <el-button link type="primary" @click="loadTypes">重试</el-button>
          </el-alert>
          <div style="margin-bottom: 10px">
            <el-button type="primary" style="margin-left: 8px" @click="openWizard(null)">新增类型</el-button>
          </div>
          <el-table :data="store.types" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="流程阶段数" width="100">
              <template #default="{ row }">{{ store.typePhaseCount[row.id] ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openWizard(row)">编辑</el-button>
                <el-popconfirm title="确认删除该类型？（有项目引用时将拒绝；无引用将级联清理其流程与专属绑定）" width="280" @confirm="delType(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

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
          <el-alert title="新增公共阶段不会自动加入其他类型。停用仅限制新配置选择，已有项目保持原流程；历史引用会阻止删除。" type="info" :closable="false" style="margin-bottom:12px"/>
          <div style="margin-bottom: 10px">
            <el-input v-model="phaseForm.name" placeholder="新阶段名称（如 筹备）" style="width: 200px" />
            <el-input-number v-model="phaseForm.sort" :min="1" placeholder="排序" style="width: 120px; margin-left: 8px" />
            <el-button type="primary" style="margin-left: 8px" :disabled="!phaseForm.name.trim()" @click="addPhase">新增阶段</el-button>
          </div>
          <el-table :data="store.phases" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="阶段名称" />
            <el-table-column prop="sort" label="公共库顺序" width="110" />
            <el-table-column label="引用（类型 / 项目）" width="150"><template #default="{row}">{{ usageOf(row.id).typeCount }} / {{ usageOf(row.id).projectCount }}</template></el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isActive === 1 ? 'success' : 'info'">{{ row.isActive === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="startEditPhase(row)">编辑</el-button>
                <el-button link type="danger" @click="openDeletePhase(row)">删除</el-button>
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

          <!-- 删除阶段：影响面提示（FR8） -->
          <el-dialog v-model="deletePhaseVisible" title="删除阶段 · 影响面确认" width="680px" destroy-on-close>
            <impact-preview
              v-if="deletePhaseTarget"
              scene="phase"
              :phase="deletePhaseTarget"
              :ref-type-count="deletePhaseRefTypeCount"
              :global-fallback-count="deletePhaseGlobalFallbackCount"
            />
            <template #footer>
              <el-button @click="deletePhaseVisible = false">取消</el-button>
              <el-button type="danger" :loading="deletingPhase" @click="confirmDeletePhase">确认删除</el-button>
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
          <el-table :data="store.templates" stripe>
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
                <el-button link type="danger" @click="openDeleteTemplate(row)">删除</el-button>
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

          <!-- 删除模板：影响面提示（FR8） -->
          <el-dialog v-model="deleteTemplateVisible" title="删除模板 · 影响面确认" width="680px" destroy-on-close>
            <impact-preview
              v-if="deleteTemplateTarget"
              scene="template"
              :template="deleteTemplateTarget"
              :global-bind-refs="deleteTemplateGlobalRefs"
              :own-bind-refs="deleteTemplateOwnRefs"
            />
            <template #footer>
              <el-button @click="deleteTemplateVisible = false">取消</el-button>
              <el-button type="danger" :loading="deletingTemplate" @click="confirmDeleteTemplate">确认删除</el-button>
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
              <el-option label="通用材料要求" :value="0" />

            </el-select>
            <el-select v-model="bindPhaseId" placeholder="选择阶段" style="width: 220px; margin-left: 8px" @change="loadBindings">
              <el-option v-for="p in bindPhaseOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-button type="primary" style="margin-left: 8px" :disabled="bindScope === null || !bindPhaseId" @click="openGlobalBindConfirm">保存绑定</el-button>
          </div>
          <div v-if="bindScope > 0 && bindFallback" class="hint-text" style="margin-bottom: 8px">
            当前展示的是全局兜底绑定（该类型此阶段尚无专属绑定），保存后即成为「{{ bindScopeName }}」专属绑定
          </div>
          <div v-if="bindScope === 0 && bindPhaseId" class="hint-text" style="margin-bottom: 8px">
            <el-alert type="warning" :closable="false" show-icon style="margin-top: 6px">
              通用要求影响：{{ store.globalFallbackTypeCount(bindPhaseId).length }} 个沿用通用要求的类型，仅以后新建项目生效
            </el-alert>
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
                <el-checkbox v-model="row._bound" @change="markDirty" />
              </template>
            </el-table-column>
            <el-table-column label="必填/选填" width="120" align="center">
              <template #default="{ row }">
                <el-select v-model="row._required" :disabled="!row._bound" size="small" style="width: 90px" @change="markDirty">
                  <el-option label="必填" :value="1" />
                  <el-option label="选填" :value="0" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="排序" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row._sort" :min="1" size="small" :disabled="!row._bound" @change="markDirty" />
              </template>
            </el-table-column>
          </el-table>
          <div v-if="bindScope === null || !bindPhaseId" class="hint-text">请先选择配置范围与阶段以配置绑定关系</div>
        </el-card>
      </el-tab-pane>

      <!-- 修改全局绑定：影响面确认（FR8） -->
      <el-dialog v-model="globalBindConfirmVisible" title="修改全局兜底绑定 · 影响面确认" width="680px" destroy-on-close>
        <impact-preview
          v-if="bindScope === 0 && bindPhaseId"
          scene="globalBind"
          :affected-types="globalBindAffectedTypes"
          :affected-count="globalBindAffectedTypes.length"
        />
        <template #footer>
          <el-button @click="globalBindConfirmVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingBindings" @click="confirmSaveBindings">确认保存</el-button>
        </template>
      </el-dialog>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import FlowOverview from './components/FlowOverview.vue'
import MiniFlowThumbnail from './components/MiniFlowThumbnail.vue'
import StepWizard from './components/StepWizard.vue'
import ImpactPreview from './components/ImpactPreview.vue'
import { getDefinitionContext, orderProjectTypes } from '../../api/project'
import { useProjectConfigStore } from '../../stores/projectConfig'

const store = useProjectConfigStore()
const orderVisible=ref(false), orderSaving=ref(false), orderTypes=ref([]), orderEpoch=ref(0), orderDrag=ref(null), phaseUsage=ref([])
const usageOf=(id)=>phaseUsage.value.find(p=>p.phaseId===id)||{typeCount:'-',projectCount:'-'}
async function openOrder(){try{const ctx=await getDefinitionContext();orderTypes.value=ctx.types;orderEpoch.value=ctx.expectedEpoch;orderVisible.value=true}catch{}}
function moveOrder(i,d){const [t]=orderTypes.value.splice(i,1);orderTypes.value.splice(i+d,0,t)}
function dropOrder(i){if(orderDrag.value!==null){const [t]=orderTypes.value.splice(orderDrag.value,1);orderTypes.value.splice(i,0,t);orderDrag.value=null}}
async function saveOrder(){orderSaving.value=true;try{await orderProjectTypes({ids:orderTypes.value.map(t=>t.id),expectedEpoch:orderEpoch.value});orderVisible.value=false;await loadTypes();ElMessage.success('类型顺序已更新')}catch(e){ElMessage.error('顺序未保存，请保留草稿并刷新核对：'+e.message)}finally{orderSaving.value=false}}

const { activeView, allTemplates } = storeToRefs(store)

// ==================== 未保存状态检测（D3） ====================
const dirty = ref(false)
const markDirty = () => { dirty.value = true }
const clearDirty = () => { dirty.value = false }

const tab = ref('types')
// 各 Tab 独立加载容错（任一失败不影响其他 Tab）
const typesError = ref('')
const phasesError = ref('')
const templatesError = ref('')
const bindError = ref('')


const phaseForm = reactive({ name: '', sort: 1 })
const editPhaseVisible = ref(false)
const editPhaseForm = reactive({ id: null, name: '', sort: 1, isActive: 1 })

const templateForm = reactive({ name: '', templateType: 'RICH_TEXT' })
const editTemplateVisible = ref(false)
const editTemplateForm = reactive({ id: null, name: '', templateType: 'FILE' })

const bindScope = ref(0)
const bindPhaseId = ref(null)
const bindFallback = ref(false)
const bindScopeName = ref('')

const wizardVisible = ref(false)
const wizardType = ref(null)

// 编辑会话内表单变化 → 置脏（弹窗可见期间 deep 监听）
watch(editPhaseForm, () => { if (editPhaseVisible.value) markDirty() }, { deep: true })
watch(editTemplateForm, () => { if (editTemplateVisible.value) markDirty() }, { deep: true })
watch(() => phaseForm.name, (v) => { if (v) markDirty() })
watch(() => phaseForm.sort, (v) => { if (v > 1) markDirty() })
watch(() => templateForm.name, (v) => { if (v) markDirty() })
watch(() => templateForm.templateType, (v) => { if (v) markDirty() })
watch(wizardVisible, (v) => { if (v) markDirty() })
// 所有编辑会话关闭（保存/取消/放弃）→ 清脏
watch([editPhaseVisible, editTemplateVisible, wizardVisible], (arr) => {
  if (arr.every((v) => !v)) clearDirty()
})

// ==================== 图形视图状态 ====================
const deleteTypeVisible = ref(false)
const deleteTarget = ref(null)
const deletingType = ref(false)

// ==================== FR8：删除阶段 / 删除模板 / 修改全局绑定 影响面状态 ====================
const deletePhaseVisible = ref(false)
const deletePhaseTarget = ref(null)
const deletingPhase = ref(false)
const deleteTemplateVisible = ref(false)
const deleteTemplateTarget = ref(null)
const deletingTemplate = ref(false)
const globalBindConfirmVisible = ref(false)
const savingBindings = ref(false)


function typeText(t) {
  return { FILE: '资料附件', EXPERT: '专家', SUPPLIER: '供应商', BUDGET: '预算', RICH_TEXT: '自定义文本' }[t] || t
}

// 类型流程（供迷你缩略图）
function typeFlowOf(typeId) {
  const cache = store.typeFlowCache[typeId]
  if (cache && cache.length) {
    return cache.map((f) => ({ phaseId: f.phaseId, phaseName: f.phaseName, active: true }))
  }
  // 未配置：展示全部启用阶段（实际语义）
  return []
}

// ==================== 视图切换（D3：切换前检测未保存状态） ====================
function onViewChange() {
  if (dirty.value) {
    ElMessageBox.confirm('当前视图存在未保存的修改，切换视图将丢弃这些修改。是否继续切换？', '未保存提示', {
      confirmButtonText: '继续切换',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      clearDirty()
      afterViewChange()
    }).catch(() => {
      // 取消 → 回弹到原视图
      activeView.value = activeView.value === 'graph' ? 'table' : 'graph'
    })
    return
  }
  afterViewChange()
}
function afterViewChange() {
  // 图形视图需要全量绑定数据；表格视图按需加载
  if (activeView.value === 'graph' && !Object.keys(store.phaseTemplateRels).length) {
    store.ensureAllBindings().catch(() => {})
  }
}

// ==================== 独立加载 ====================
async function loadTypes() {
  typesError.value = ''
  try {
    await store.loadTypes()
  } catch (e) {
    typesError.value = e?.message || String(e)
  }
}
async function loadPhases() {
  phasesError.value = ''
  try {
    await store.loadPhases()
    phaseUsage.value=(await getDefinitionContext()).phaseUsage
  } catch (e) {
    phasesError.value = e?.message || String(e)
  }
}
async function loadTemplates() {
  templatesError.value = ''
  try {
    await store.loadTemplates()
  } catch (e) {
    templatesError.value = e?.message || String(e)
  }
}
async function fetchAll() {
  await Promise.allSettled([loadTypes(), loadPhases(), loadTemplates()])
  if (activeView.value === 'graph') {
    await store.ensureAllBindings().catch(() => {})
  }
}

// ==================== 类型（表格视图，保留原交互） ====================
async function delType(row) {
  try {
    await store.removeType(row.id)
    ElMessage.success('类型已删除')
    await loadTypes()
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e))
  }
}

// ==================== 流程阶段 ====================
async function addPhase() {
  await store.addPhase({ name: phaseForm.name.trim(), sort: phaseForm.sort })
  ElMessage.success('阶段已新增')
  phaseForm.name = ''
  phaseForm.sort = 1
  clearDirty()
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
  await store.updatePhase(editPhaseForm.id, {
    name: editPhaseForm.name.trim(),
    sort: editPhaseForm.sort,
    isActive: editPhaseForm.isActive
  })
  ElMessage.success('阶段已更新')
  editPhaseVisible.value = false
  loadPhases()
}
function openDeletePhase(row) {
  deletePhaseTarget.value = row
  deletePhaseVisible.value = true
}
const deletePhaseRefTypeCount = computed(() => {
  if (!deletePhaseTarget.value) return 0
  return Object.values(store.typeFlowCache).filter((flow) =>
    flow.some((f) => f.phaseId === deletePhaseTarget.value.id)
  ).length
})
const deletePhaseGlobalFallbackCount = computed(() => {
  if (!deletePhaseTarget.value) return 0
  return store.globalFallbackTypeCount(deletePhaseTarget.value.id).length
})
async function confirmDeletePhase() {
  deletingPhase.value = true
  try {
    await store.removePhase(deletePhaseTarget.value.id)
    ElMessage.success('阶段已删除')
    deletePhaseVisible.value = false
    clearDirty()
    loadPhases()
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e))
  } finally {
    deletingPhase.value = false
  }
}

// ==================== 模板管理 ====================
async function addTemplate() {
  await store.addTemplate({ name: templateForm.name.trim(), templateType: templateForm.templateType })
  ElMessage.success('模板已新建')
  templateForm.name = ''
  clearDirty()
  loadTemplates()
}
function startEditTemplate(row) {
  editTemplateForm.id = row.id
  editTemplateForm.name = row.name
  editTemplateForm.templateType = row.templateType
  editTemplateVisible.value = true
}
async function saveTemplate() {
  await store.updateTemplate(editTemplateForm.id, { name: editTemplateForm.name.trim(), templateType: editTemplateForm.templateType })
  ElMessage.success('模板已更新')
  editTemplateVisible.value = false
  loadTemplates()
}
function openDeleteTemplate(row) {
  deleteTemplateTarget.value = row
  deleteTemplateVisible.value = true
}
const deleteTemplateGlobalRefs = computed(() => {
  if (!deleteTemplateTarget.value) return 0
  let n = 0
  for (const key of Object.keys(store.phaseTemplateRels)) {
    if (!key.startsWith('0_')) continue
    for (const b of store.phaseTemplateRels[key] || []) {
      if (b.templateId === deleteTemplateTarget.value.id) n++
    }
  }
  return n
})
const deleteTemplateOwnRefs = computed(() => {
  if (!deleteTemplateTarget.value) return 0
  let n = 0
  for (const key of Object.keys(store.phaseTemplateRels)) {
    if (key.startsWith('0_')) continue
    for (const b of store.phaseTemplateRels[key] || []) {
      if (b.templateId === deleteTemplateTarget.value.id) n++
    }
  }
  return n
})
async function confirmDeleteTemplate() {
  deletingTemplate.value = true
  try {
    await store.removeTemplate(deleteTemplateTarget.value.id)
    ElMessage.success('模板已删除')
    deleteTemplateVisible.value = false
    clearDirty()
    loadTemplates()
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e))
  } finally {
    deletingTemplate.value = false
  }
}

// ==================== 阶段-模板绑定（范围：全局兜底/类型专属） ====================
function onScopeChange() {
  bindPhaseId.value = null
  bindFallback.value = false
  allTemplates.value = store.templates.map((t) => ({ ...t, _bound: false, _required: 0, _sort: 1 }))
}
async function loadBindings() {
  if (bindScope.value === null || !bindPhaseId.value) return
  bindError.value = ''
  try {
    const binds = await store.loadPhaseTemplateRels(bindScope.value, bindPhaseId.value)
    bindFallback.value = binds.some((b) => b.fallback)
    const scope = store.types.find((t) => t.id === bindScope.value)
    bindScopeName.value = scope ? scope.name : ''
    const map = {}
    binds.forEach((b) => { map[String(b.templateId)] = b })
    const base = store.templates.length ? store.templates : await listProjectTemplatesLazy()
    allTemplates.value = base.map((t) => {
      const b = map[String(t.id)]
      return { ...t, _bound: !!b, _required: b ? b.required : 0, _sort: b ? b.sort : 1 }
    })
  } catch (e) {
    bindError.value = e?.message || String(e)
  }
}
const globalBindAffectedTypes = computed(() => {
  if (bindScope.value !== 0 || bindPhaseId.value === null) return []
  return store.globalFallbackTypeCount(bindPhaseId.value).map((id) => ({
    id,
    name: store.types.find((t) => t.id === id)?.name || String(id)
  }))
})
function openGlobalBindConfirm() {
  // 仅全局兜底（type_id=0）需要影响面确认；类型专属直接保存
  if (bindScope.value === 0) {
    globalBindConfirmVisible.value = true
    return
  }
  doSaveBindings()
}
async function confirmSaveBindings() {
  savingBindings.value = true
  try {
    await doSaveBindings()
    globalBindConfirmVisible.value = false
  } finally {
    savingBindings.value = false
  }
}
async function doSaveBindings() {
  const binds = allTemplates.value
    .filter((t) => t._bound)
    .map((t) => ({ templateId: t.id, required: t._required, sort: t._sort }))
  await store.saveScopePhaseTemplates(bindScope.value, bindPhaseId.value, binds)
  ElMessage.success('绑定已保存')
  clearDirty()
  loadBindings()
}

// ==================== 图形视图操作 ====================
function openWizard(type) {
  wizardType.value = type || null
  wizardVisible.value = true
}
async function onWizardSaved() {
  try {
    await store.loadPhases()
    await store.loadTypes()
    store.phaseTemplateRels = {}
    await store.ensureAllBindings()
    phaseUsage.value=(await getDefinitionContext()).phaseUsage
  } catch(e) {typesError.value='保存已成功，但总览刷新失败，请刷新页面：'+e.message}
}

function openDeleteType(row) {
  deleteTarget.value = row
  deleteTypeVisible.value = true
}
const deleteFlowCount = computed(() => {
  if (!deleteTarget.value) return 0
  return store.typeFlowCache[deleteTarget.value.id]?.length || 0
})
const deleteOwnBindCount = computed(() => {
  if (!deleteTarget.value) return 0
  let n = 0
  for (const key of Object.keys(store.phaseTemplateRels)) {
    if (key.startsWith(`${deleteTarget.value.id}_`)) n++
  }
  return n
})
async function confirmDeleteType() {
  deletingType.value = true
  try {
    await store.removeType(deleteTarget.value.id)
    ElMessage.success('类型已删除')
    deleteTypeVisible.value = false
    await loadTypes()
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e))
  } finally {
    deletingType.value = false
  }
}

// 惰性加载模板（复用 store.loadTemplates）
async function listProjectTemplatesLazy() {
  if (!store.templates.length) await store.loadTemplates()
  return store.templates
}

onMounted(fetchAll)
</script>

<style scoped>
.order-entry{margin-bottom:12px}.order-row{display:flex;gap:10px;align-items:center;padding:12px;border:1px solid #dcdfe6;margin:8px 0}.order-row span{flex:1}
.page-head { margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; }
.page-sub { margin-left: 8px; color: #909399; font-size: 13px; }
.hint-text { color: #909399; font-size: 13px; }
.err-dot { color: #f56c6c; margin-left: 4px; font-weight: 700; }
.view-switch { margin-bottom: 14px; }
.graph-toolbar { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; gap: 10px; flex-wrap: wrap; }
.graph-title { font-size: 16px; font-weight: 600; }
.graph-sub { color: #909399; font-size: 12px; margin-top: 3px; }
</style>
