<template>
  <div class="exec-board">
    <!-- 统计卡（本人活跃任务） -->
    <el-row :gutter="12" class="exec-stats">
      <el-col :span="6"><div class="stat ui-card"><div class="stat-label">活跃任务</div><div class="stat-value">{{ stat.todoCount ?? 0 }}</div></div></el-col>
      <el-col :span="6"><div class="stat ui-card"><div class="stat-label">近 3 天到期</div><div class="stat-value stat-warn">{{ stat.nearDueCount ?? 0 }}</div></div></el-col>
      <el-col :span="6"><div class="stat ui-card"><div class="stat-label">已逾期</div><div class="stat-value stat-danger">{{ stat.overdueCount ?? 0 }}</div></div></el-col>
      <el-col :span="6"><div class="stat ui-card"><div class="stat-label">待排期</div><div class="stat-value">{{ stat.unscheduledCount ?? 0 }}</div></div></el-col>
    </el-row>

    <!-- 工具栏 -->
    <div class="exec-toolbar">
      <el-radio-group v-model="scope" size="small" @change="load">
        <el-radio-button value="ACTIVE">待处理</el-radio-button>
        <el-radio-button value="COMPLETED">已完成记录</el-radio-button>
      </el-radio-group>
      <el-select v-model="executionId" placeholder="全部清单" clearable filterable size="small" style="width: 220px" @change="load">
        <el-option v-for="e in executions" :key="e.id" :label="e.name" :value="e.id" />
      </el-select>
      <el-input v-model="search" placeholder="搜索事项" clearable size="small" style="width: 180px" @keyup.enter="load" @clear="load" />
      <div style="flex: 1"></div>
      <el-button type="primary" size="small" @click="openCreate">创建执行清单</el-button>
    </div>

    <!-- 任务表 -->
    <el-alert v-if="loadError" title="加载失败，请重试" type="error" :closable="false"><el-button @click="load">重试</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" stripe size="small" class="task-table">
      <el-table-column label="完成" width="60" align="center">
        <template #default="{ row }">
          <el-checkbox
            :model-value="row.progress === 'COMPLETED'"
            @change="(v) => onProgress(row, v ? 'COMPLETED' : 'PROCESSING')"
          />
        </template>
      </el-table-column>
      <el-table-column label="事项与本次选择" min-width="220">
        <template #default="{ row }">
          <div class="task-title">{{ snapTitle(row) }}
            <el-tag v-if="row.waitingReason || row.nextAction" size="small" type="warning">等待跟进</el-tag>
          </div>
          <div class="task-sub" v-if="snapInstruction(row)">{{ snapInstruction(row) }}</div>
          <div class="task-note" v-if="row.note">备注 · {{ row.note.slice(0, 40) }}{{ row.note.length > 40 ? '…' : '' }}</div>
          <div class="task-note muted" v-else>添加备注</div>
        </template>
      </el-table-column>
      <el-table-column prop="executionName" label="清单" min-width="140" show-overflow-tooltip />
      <el-table-column label="计划到期" width="110">
        <template #default="{ row }">
          <span v-if="!row.dueDate" class="muted">待排期</span>
          <span v-else :class="{ 'due-over': isOverdue(row) }">{{ row.dueDate }}</span>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="80" align="center">
        <template #default="{ row }">{{ prioText(row.priority) }}</template>
      </el-table-column>
      <el-table-column label="本次进度" width="110">
        <template #default="{ row }">
          <el-select :model-value="row.progress" size="small" style="width: 100px" @change="(v) => onProgress(row, v)">
            <el-option label="未开始" value="UNPROCESSED" />
            <el-option label="进行中" value="PROCESSING" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          <el-button link type="warning" size="small" @click="openWaiting(row)">等待跟进</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !loadError && !rows.length" :description="scope === 'ACTIVE' ? '暂无进行中任务：创建执行清单开始本次使用' : '暂无完成记录'" :image-size="70" />
    <el-pagination v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="load" />
    <div class="exec-toolbar" v-if="selectedExecution">
      <span>{{ selectedExecution.name }} · {{ selectedExecution.state }}</span>
      <el-button v-if="selectedExecution.state === 'DRAFT'" :loading="saving" @click="startSelected">开始执行</el-button>
      <el-button @click="openExecution">查看清单全部事项</el-button>
      <el-button @click="openReuse">再次使用</el-button>
    </div>
    <el-dialog v-model="executionVisible" title="清单全部事项" width="720px" destroy-on-close>
      <el-alert v-if="executionDetail?.sourceNotice" :title="executionDetail.sourceNotice" type="warning" :closable="false" />
      <el-table :data="executionDetail?.tasks || []">
        <el-table-column label="事项"><template #default="{ row }">{{ snapTitle(row) }}</template></el-table-column>
        <el-table-column prop="progress" label="进度" />
        <el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
    </el-dialog>
    <el-dialog v-model="reuseVisible" title="再次使用" width="500px">
      <el-form label-width="100px">
        <el-form-item label="新清单名称"><el-input v-model="reuseForm.name" maxlength="200" /></el-form-item>
        <el-form-item label="关联项目"><el-select v-model="reuseForm.projectId" clearable placeholder="不选则为个人清单"><el-option v-for="p in myProjects" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="沿用选择"><el-checkbox v-model="reuseForm.copyAnswers">仅复制答案</el-checkbox></el-form-item>
        <el-alert title="新清单为草稿，日期、进度、备注和提醒记录均重新开始。" :closable="false" />
      </el-form>
      <template #footer><el-button :loading="saving" type="primary" :disabled="!reuseForm.name.trim()" @click="doReuse">创建新清单</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="任务详情" width="650px" destroy-on-close>
      <template v-if="detailRow">
        <h3>{{ snapTitle(detailRow) }}</h3>
        <p>{{ snapInstruction(detailRow) }}</p>
        <p v-if="snapOf(detailRow).materials">所需材料：{{ snapOf(detailRow).materials }}</p>
        <p v-if="snapOf(detailRow).acceptance">完成标准：{{ snapOf(detailRow).acceptance }}</p>
        <p v-if="snapOf(detailRow).timeRule">规范时限：{{ snapOf(detailRow).timeRule }}</p>
        <el-form label-width="100px">
          <el-form-item label="本次选择">
            <el-select v-if="['SINGLE', 'MULTI'].includes(snapOf(detailRow).inputType)" v-model="detailForm.selection" :multiple="snapOf(detailRow).inputType === 'MULTI'" clearable aria-label="本次选择">
              <el-option v-for="o in snapOf(detailRow).options || []" :key="o.optionCode" :label="o.label" :value="o.optionCode" />
            </el-select>
            <el-input v-else v-model="detailForm.text" type="textarea" :placeholder="snapOf(detailRow).inputType === 'CHECK' ? '填写本次确认结果' : '填写本次内容'" aria-label="本次填写" />
          </el-form-item>
          <el-form-item label="计划到期日"><el-date-picker v-model="detailForm.dueDate" type="date" value-format="YYYY-MM-DD" aria-label="计划到期日" /></el-form-item>
          <el-form-item label="优先级"><el-select v-model="detailForm.priority" aria-label="优先级"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select></el-form-item>
          <el-form-item label="个人备注"><el-input v-model="detailForm.note" type="textarea" maxlength="2000" show-word-limit aria-label="个人备注" /></el-form-item>
        </el-form>
        <el-alert title="修改仅影响本人本次执行，不改动规范模板或其他清单。" :closable="false" />
      </template>
      <template #footer><el-button @click="detailVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDetail">保存</el-button></template>
    </el-dialog>

    <!-- 创建清单向导 -->
    <el-dialog v-model="createVisible" title="创建执行清单" width="640px" destroy-on-close>
      <el-steps :active="createStep" simple style="margin-bottom: 14px">
        <el-step title="选择项目" />
        <el-step title="选择模板" />
        <el-step title="确认开始" />
      </el-steps>
      <template v-if="createStep === 0">
        <el-form label-width="100px">
          <el-form-item label="用途">
            <el-radio-group v-model="createProjectMode">
              <el-radio-button value="project">关联系统项目</el-radio-button>
              <el-radio-button value="personal">个人用途</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="createProjectMode === 'project'" label="选择项目" required>
            <el-select v-model="createForm.projectId" filterable placeholder="仅显示本人有权访问的项目" style="width: 100%">
              <el-option v-for="p in myProjects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>
      <template v-else-if="createStep === 1">
        <el-form label-width="100px">
          <el-form-item label="来源文档" required>
            <el-select v-model="createDocId" filterable placeholder="选择合规文档" style="width: 100%" @change="loadTemplates">
              <el-option v-for="d in complianceDocs" :key="d.id" :label="d.title" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="模板版本" required>
            <el-select v-model="createForm.templateVersionId" placeholder="选择已发布模板" style="width: 100%">
              <el-option v-for="t in templates" :key="t.id" :label="`v${t.revision} · ${t.title}（${t.nodeCount} 项）`" :value="t.id" />
            </el-select>
          </el-form-item>
          <div v-if="!templates.length && createDocId" class="hint">该文档暂无已发布模板；可在「模板维护」页签发布模板，或使用「迁移草稿」辅助</div>
          <el-button v-if="createDocId && isMaintainer" size="small" plain @click="migrateV3">从该文档旧事项生成模板草稿</el-button>
        </el-form>
      </template>
      <template v-else>
        <el-form label-width="100px">
          <el-form-item label="清单名称" required>
            <el-input v-model="createForm.name" maxlength="200" placeholder="如：君实·6月项目·协会与材料" />
          </el-form-item>
          <el-form-item label="默认到期日">
            <el-date-picker v-model="createDefaultDue" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="可为空，创建后逐项排期" />
          </el-form-item>
          <div class="hint">创建后先保存为草稿，可填写各项配置，再显式开始执行。</div>
        </el-form>
      </template>
      <template #footer>
        <el-button v-if="createStep > 0" @click="createStep -= 1">上一步</el-button>
        <el-button v-if="createStep < 2" type="primary" :disabled="createStep === 0 && createProjectMode === 'project' && !createForm.projectId" @click="createStep += 1">下一步</el-button>
        <el-button v-if="createStep === 2" type="primary" :loading="creating" :disabled="!createForm.templateVersionId || !createForm.name.trim()" @click="doCreate">创建清单</el-button>
      </template>
    </el-dialog>

    <!-- 等待跟进弹窗 -->
    <el-dialog v-model="waitingVisible" title="设置等待跟进（进度保持进行中）" width="460px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="在等什么" required><el-input v-model="waitingForm.reason" maxlength="200" placeholder="如：等待客户确认函件" /></el-form-item>
        <el-form-item label="下一步行动"><el-input v-model="waitingForm.nextAction" maxlength="200" placeholder="如：发函催复 / 周一电话跟进" /></el-form-item>
        <el-form-item label="跟进日期"><el-date-picker v-model="waitingForm.reviewDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="waitingVisible = false">取消</el-button>
        <el-button type="warning" :disabled="!waitingForm.reason && !waitingForm.nextAction" :loading="saving" @click="saveWaiting">设置等待</el-button>
        <el-button type="info" plain :loading="saving" @click="clearWaiting">清除等待</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listMyTasks, createExecution, startExecution, patchTask,
  listPublishedTemplates, createDraftFromV3, listMyExecutions, getExecutionDetail, reuseExecution
} from '../../api/complianceV4'
import { listKnowledgeDocs } from '../../api/knowledge'
import { listProjects } from '../../api/project'

const stat = ref({})
const rows = ref([])
const loading = ref(false)
const loadError = ref(false)
let loadSequence = 0
const scope = ref('ACTIVE')
const executionId = ref(null)
const search = ref('')
const executions = ref([])
const page = ref(1), total = ref(0), pageSize = 50
const selectedExecution = computed(() => executions.value.find(e => e.id === executionId.value))
const executionVisible = ref(false), executionDetail = ref(null)
const reuseVisible = ref(false), reuseForm = reactive({ name: '', projectId: null, copyAnswers: false, key: '' })
const detailVisible = ref(false), detailRow = ref(null)
const detailForm = reactive({ selection: null, text: '', dueDate: null, priority: 'MEDIUM', note: '' })
watch([scope, executionId, search], () => { page.value = 1 })

// 创建向导
const createVisible = ref(false)
const createStep = ref(0)
const createProjectMode = ref('project')
const createForm = reactive({ projectId: null, templateVersionId: null, name: '', key: '' })
const createdExecutionId = ref(null)
const createDefaultDue = ref(null)
const createDocId = ref(null)
const templates = ref([])
const complianceDocs = ref([])
const myProjects = ref([])
const isMaintainer = ref(false)
const creating = ref(false)

// 等待跟进
const waitingVisible = ref(false)
const waitingForm = reactive({ taskId: null, rowVersion: null, reason: '', nextAction: '', reviewDate: null })
const saving = ref(false)

function snapOf(row) {
  return row.snapshot && typeof row.snapshot === 'object' ? row.snapshot : {}
}
function snapTitle(row) {
  return snapOf(row).title || row.sourceNodeCode
}
function snapInstruction(row) {
  return snapOf(row).instruction || ''
}
function isOverdue(row) {
  return row.dueDate && new Date(row.dueDate) < new Date(new Date().toDateString())
}
function prioText(p) {
  return { HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || p
}

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = false
  try {
    const d = await listMyTasks({
      statusScope: scope.value,
      executionId: executionId.value || undefined,
      search: search.value || undefined,
      page: page.value, size: pageSize
    })
    if (sequence !== loadSequence) return
    rows.value = d?.items || []
    total.value = d?.total || 0
    stat.value = d?.stats || {}
  } catch {
    if (sequence === loadSequence) loadError.value = true
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function onProgress(row, v) {
  if (saving.value) return
  saving.value = true
  try {
    await patchTask(row.id, { progress: v, rowVersion: row.rowVersion })
    ElMessage.success(v === 'COMPLETED' ? '已完成，已移入完成记录' : '进度已更新')
    await load()
  } catch { /* 请求拦截器提示，保留原行以便重试 */ } finally { saving.value = false }
}

async function loadExecutions() {
  try {
    const items = []
    let current = 1, count = 0
    do {
      const d = await listMyExecutions({ page: current++, size: 200 })
      count = d?.total || 0
      if (!d?.items?.length) break
      items.push(...d.items)
    } while (items.length < count)
    executions.value = items
  } catch { /* 拦截器提示，保留已有清单 */ }
}

async function loadDocs() {
  try {
    const d = await listKnowledgeDocs({ category: 'enterprise-compliance', page: 1, pageSize: 100 })
    complianceDocs.value = d?.records || d?.items || []
  } catch (e) { complianceDocs.value = [] }
}

async function loadTemplates() {
  templates.value = []
  createForm.templateVersionId = null
  if (!createDocId.value) return
  try {
    templates.value = await listPublishedTemplates(createDocId.value) || []
  } catch (e) { templates.value = [] }
}

async function migrateV3() {
  try {
    const r = await createDraftFromV3(createDocId.value)
    ElMessage.success(`已生成迁移草稿（${r?.nodesCreated ?? 0} 个节点），请在模板维护页签补结构化定义后发布`)
    loadTemplates()
  } catch (e) { /* 拦截器已提示 */ }
}

async function loadMyProjects() {
  try {
    const d = await listProjects({ page: 1, size: 100 })
    myProjects.value = d?.records || []
  } catch (e) { myProjects.value = [] }
}

function openCreate() {
  createStep.value = 0
  createProjectMode.value = 'project'
  createForm.projectId = null
  createForm.templateVersionId = null
  createForm.name = ''
  createForm.key = newKey()
  createdExecutionId.value = null
  createDocId.value = null
  templates.value = []
  createDefaultDue.value = null
  createVisible.value = true
  loadMyProjects()
  loadDocs()
}

async function doCreate() {
  creating.value = true
  try {
    const r = createdExecutionId.value ? { executionId: createdExecutionId.value } : await createExecution({
      templateVersionId: createForm.templateVersionId,
      projectId: createProjectMode.value === 'project' ? createForm.projectId : undefined,
      name: createForm.name.trim(),
      idempotencyKey: createForm.key
    })
    const execId = r?.executionId
    createdExecutionId.value = execId
    if (r?.alreadyExists) {
      ElMessage.warning('该创建请求已提交过，已返回既有清单')
    }
    // 部分失败时保留既有草稿和幂等键；重试已成功的日期更新是幂等的。
    if (createDefaultDue.value && execId) {
      const detail = await getExecutionDetail(execId)
      for (const t of detail?.tasks || []) {
        await patchTask(t.id, { dueDate: createDefaultDue.value, rowVersion: t.rowVersion })
      }
    }
    ElMessage.success('清单已创建（草稿）')
    createVisible.value = false
    await loadExecutions()
    executionId.value = execId
    scope.value = 'ACTIVE'
    load()
  } catch { /* 保持向导与同一幂等键；部分失败重试既有草稿 */
  } finally {
    creating.value = false
  }
}

function openWaiting(row) {
  waitingForm.taskId = row.id
  waitingForm.rowVersion = row.rowVersion
  waitingForm.reason = row.waitingReason || ''
  waitingForm.nextAction = row.nextAction || ''
  waitingForm.reviewDate = row.reviewDate || null
  waitingVisible.value = true
}
async function saveWaiting() {
  saving.value = true
  try {
    await patchTask(waitingForm.taskId, {
      waiting: { reason: waitingForm.reason, nextAction: waitingForm.nextAction, reviewDate: waitingForm.reviewDate || null },
      rowVersion: waitingForm.rowVersion
    })
    ElMessage.success('已设置等待跟进')
    waitingVisible.value = false
    load()
  } catch { /* 拦截器提示，保留输入 */ } finally {
    saving.value = false
  }
}
async function clearWaiting() {
  saving.value = true
  try {
    await patchTask(waitingForm.taskId, { waiting: null, rowVersion: waitingForm.rowVersion })
    ElMessage.success('已清除等待标记')
    waitingVisible.value = false
    load()
  } catch { /* 拦截器提示，保留输入 */ } finally {
    saving.value = false
  }
}

function openDetail(row) {
  detailRow.value = row
  detailForm.selection = snapOf(row).inputType === 'MULTI' ? [...(row.answer?.optionCodes || [])] : row.answer?.optionCode || null
  detailForm.text = row.answer?.text || ''
  detailForm.dueDate = row.dueDate || null
  detailForm.priority = row.priority || 'MEDIUM'
  detailForm.note = row.note || ''
  detailVisible.value = true
}
const newKey = () => 'exec-' + Date.now() + '-' + Math.random().toString(36).slice(2)
async function saveDetail() {
  saving.value = true
  try {
    const type = snapOf(detailRow.value).inputType
    const answer = type === 'SINGLE' ? (detailForm.selection ? { optionCode: detailForm.selection } : null)
      : type === 'MULTI' ? (detailForm.selection?.length ? { optionCodes: detailForm.selection } : null)
      : detailForm.text.trim() ? { text: detailForm.text } : null
    const updated = await patchTask(detailRow.value.id, { answer, dueDate: detailForm.dueDate || null, priority: detailForm.priority, note: detailForm.note || null, rowVersion: detailRow.value.rowVersion })
    Object.assign(detailRow.value, updated)
    detailVisible.value = false
    ElMessage.success('已保存本次配置')
    await load()
  } catch { /* 保留输入供比较重试 */ } finally { saving.value = false }
}
async function openExecution() { executionDetail.value = await getExecutionDetail(executionId.value); executionVisible.value = true }
async function startSelected() {
  saving.value = true
  try { await startExecution(executionId.value, {}); scope.value = 'ACTIVE'; await loadExecutions(); await load(); ElMessage.success('已开始执行') }
  catch { /* 拦截器提示 */ } finally { saving.value = false }
}
function openReuse() { reuseForm.name = selectedExecution.value.name + ' · 再次使用'; reuseForm.projectId = null; reuseForm.copyAnswers = false; reuseForm.key = newKey(); reuseVisible.value = true; loadMyProjects() }
async function doReuse() {
  saving.value = true
  try { const r = await reuseExecution(executionId.value, { name: reuseForm.name.trim(), projectId: reuseForm.projectId, copyAnswers: reuseForm.copyAnswers, idempotencyKey: reuseForm.key }); await loadExecutions(); executionId.value = r.executionId; reuseVisible.value = false; await load() }
  catch { /* 同一幂等键重试 */ } finally { saving.value = false }
}

onMounted(async () => {
  load()
  loadExecutions()
  try {
    const me = await fetch('/api/auth/me', { credentials: 'include' }).then((x) => x.json())
    isMaintainer.value = !!(me.data && (me.data.isAdmin || (me.data.permissions || []).includes('knowledge:maintain')))
  } catch (e) { isMaintainer.value = false }
})
</script>

<style scoped>
.exec-stats { margin-bottom: 12px; }
.stat { padding: 12px 16px; text-align: center; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 22px; font-weight: 700; color: #303133; margin-top: 4px; }
.stat-warn { color: #E6A23C; }
.stat-danger { color: #F56C6C; }
.exec-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.task-title { font-weight: 600; color: #303133; display: flex; align-items: center; gap: 6px; }
.task-sub { font-size: 12px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-note { font-size: 12px; color: #67C23A; }
.task-note.muted { color: #C0C4CC; }
.muted { color: #909399; }
.due-over { color: #F56C6C; font-weight: 600; }
.hint { font-size: 12px; color: #909399; }
</style>
