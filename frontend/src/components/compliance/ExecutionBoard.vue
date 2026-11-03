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
      <el-input v-model="search" placeholder="搜索事项" clearable size="small" style="width: 180px" @keyup.enter="load" />
      <div style="flex: 1"></div>
      <el-button type="primary" size="small" @click="openCreate">创建执行清单</el-button>
    </div>

    <!-- 任务表 -->
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
            <el-tag v-if="row.blockReason" size="small" type="danger">等待跟进</el-tag>
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
    <el-empty v-if="!loading && !rows.length" :description="scope === 'ACTIVE' ? '暂无进行中任务：创建执行清单开始本次使用' : '暂无完成记录'" :image-size="70" />

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
          <div class="hint">创建后清单为草稿，点击「开始执行」才进入待办并建立提醒。</div>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listMyTasks, createExecution, startExecution, patchTask,
  listPublishedTemplates, createTemplateDraft, listMyExecutions
} from '../../api/complianceV4'
import { listKnowledgeDocs, listProjectExecutors } from '../../api/knowledge'
import request from '../../api/request'

const stat = ref({})
const rows = ref([])
const loading = ref(false)
const scope = ref('ACTIVE')
const executionId = ref(null)
const search = ref('')
const executions = ref([])

// 创建向导
const createVisible = ref(false)
const createStep = ref(0)
const createProjectMode = ref('project')
const createForm = reactive({ projectId: null, templateVersionId: null, name: '', dueDate: null })
const createDefaultDue = ref(null)
const createDocId = ref(null)
const templates = ref([])
const complianceDocs = ref([])
const myProjects = ref([])
const isMaintainer = ref(false)
const creating = ref(false)

// 等待跟进
const waitingVisible = ref(false)
const waitingForm = reactive({ taskId: null, reason: '', nextAction: '', reviewDate: null })
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
  loading.value = true
  try {
    const d = await listMyTasks({
      statusScope: scope.value,
      executionId: executionId.value || undefined,
      search: search.value || undefined,
      page: 1, size: 200
    })
    rows.value = d?.items || []
    stat.value = d?.stats || {}
  } finally {
    loading.value = false
  }
}

async function onProgress(row, v) {
  const r = await patchTask(row.id, { progress: v, rowVersion: row.rowVersion })
  const fresh = await listMyTasks({ statusScope: scope.value, executionId: row.executionId, page: 1, size: 200 })
  const updated = (fresh.items || []).find((x) => x.id === row.id)
  if (updated) {
    row.progress = updated.progress
    row.rowVersion = updated.rowVersion
    row.completedAt = updated.completedAt
  }
  ElMessage.success(v === 'COMPLETED' ? '已完成，已移入完成记录' : '进度已更新')
  if (v === 'COMPLETED' && scope.value === 'ACTIVE') load()
}

async function loadExecutions() {
  const d = await listMyExecutions({ page: 1, size: 100 })
  executions.value = d?.items || []
}

async function loadDocs() {
  try {
    const d = await listKnowledgeDocs({ category: 'enterprise-compliance', page: 1, size: 100 })
    complianceDocs.value = d?.records || d?.items || []
  } catch (e) { complianceDocs.value = [] }
}

async function loadTemplates() {
  templates.value = []
  if (!createDocId.value) return
  try {
    templates.value = await listPublishedTemplates(createDocId.value) || []
  } catch (e) { templates.value = [] }
}

async function migrateV3() {
  try {
    const r = await createTemplateDraft(createDocId.value)
    ElMessage.success(`已生成迁移草稿（${r?.nodesCreated ?? 0} 个节点），请在模板维护页签补结构化定义后发布`)
    loadTemplates()
  } catch (e) { /* 拦截器已提示 */ }
}

async function loadMyProjects() {
  try {
    myProjects.value = await request.get('/project/executors').then((x) => x || [])
  } catch (e) { myProjects.value = [] }
}

function openCreate() {
  createStep.value = 0
  createForm.projectId = null
  createForm.templateVersionId = null
  createForm.name = ''
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
    const idem = 'exec-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
    const r = await createExecution({
      templateVersionId: createForm.templateVersionId,
      projectId: createProjectMode.value === 'project' ? createForm.projectId : undefined,
      name: createForm.name.trim(),
      idempotencyKey: idem
    })
    const execId = r?.executionId
    if (r?.alreadyExists) {
      ElMessage.warning('该创建请求已提交过，已返回既有清单')
    } else {
      // 默认到期日批量写入任务
      if (createDefaultDue.value && execId) {
        const detail = await import('../../api/complianceV4').then((m) => m.getExecutionDetail(execId))
        for (const t of detail?.tasks || []) {
          try { await patchTask(t.id, { dueDate: createDefaultDue.value, rowVersion: t.rowVersion }) } catch (e) {}
        }
      }
      ElMessage.success('清单已创建（草稿）')
    }
    createVisible.value = false
    await loadExecutions()
    executionId.value = execId
    if (execId) {
      try {
        await startExecution(execId, {})
        ElMessage.success('已开始执行，任务进入待办')
      } catch (e) { /* 保持草稿 */ }
    }
    load()
  } finally {
    creating.value = false
  }
}

function openWaiting(row) {
  waitingForm.taskId = row.id
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
      rowVersion: undefined
    })
    ElMessage.success('已设置等待跟进')
    waitingVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function clearWaiting() {
  saving.value = true
  try {
    await patchTask(waitingForm.taskId, { waiting: null })
    ElMessage.success('已清除等待标记')
    waitingVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openDetail(row) {
  ElMessage.info('任务详情侧栏迭代中；可在清单内查看规范要求与本次填写')
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
