<template>
  <div class="maint-board">
    <div class="mb-toolbar">
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="grouped">按来源文档分组</el-radio-button>
        <el-radio-button value="all">全部事项聚合</el-radio-button>
      </el-radio-group>
      <el-select v-model="filterStage" placeholder="全部阶段" clearable size="small" style="width: 130px" @change="load">
        <el-option v-for="(t, k) in STAGE_TEXT" :key="k" :label="t" :value="k" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 130px" @change="load">
        <el-option label="未开始" value="PENDING" />
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="待确认" value="CONFIRMING" />
        <el-option label="已完成" value="COMPLETED" />
      </el-select>
      <el-select v-model="filterPriority" placeholder="全部优先级" clearable size="small" style="width: 120px" @change="load">
        <el-option label="高" value="HIGH" />
        <el-option label="中" value="MEDIUM" />
        <el-option label="低" value="LOW" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索标题/要点" clearable size="small" style="width: 200px" @keyup.enter="load" />
      <el-button type="primary" size="small" @click="load">查询</el-button>
      <el-button type="success" size="small" @click="openCreate">新增事项</el-button>
    </div>

    <!-- 草稿确认区 -->
    <div v-if="draftGroups.length" class="draft-zone">
      <div class="zone-title">待确认草稿（解析产物）</div>
      <div v-for="g in draftGroups" :key="g.documentId" class="draft-group">
        <div class="draft-doc">{{ g.documentTitle }} · 草稿 {{ g.items.length }} 条
          <el-button type="success" size="small" style="margin-left: 12px" :loading="confirming" @click="confirmAll(g)">全部确认发布</el-button>
        </div>
        <div v-if="g.matchStat" class="match-stat">
          自动匹配 {{ g.matchStat.inherited }} 条 / 待确认 {{ g.matchStat.pendingConfirm }} 条 / 新增 {{ g.matchStat.added }} 条 / 停用 {{ g.matchStat.removed }} 条
        </div>
        <el-table :data="g.items" stripe size="small" max-height="260">
          <el-table-column prop="title" label="事项标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="阶段" width="90">
            <template #default="{ row }">{{ stageText(row.stage) }}</template>
          </el-table-column>
          <el-table-column prop="changeType" label="变更类型" width="90" />
          <el-table-column label="待确认匹配" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.pendingConfirm === 1" size="small" type="warning">待确认映射</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button v-if="row.pendingConfirm === 1" link type="primary" size="small" @click="pickMatch(row)">确认匹配到…</el-button>
              <el-button link type="danger" size="small" @click="del(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 已发布事项（分组/聚合） -->
    <div class="zone-title" style="margin-top: 16px">已发布事项</div>
    <template v-if="viewMode === 'grouped'">
      <div v-for="g in publishedGroups" :key="g.documentId" class="pub-group">
        <div class="draft-doc">{{ g.documentTitle }}（{{ g.items.length }} 条）</div>
        <el-table :data="g.items" stripe size="small">
          <el-table-column prop="title" label="事项标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="阶段" width="90">
            <template #default="{ row }">{{ stageText(row.stage) }}</template>
          </el-table-column>
          <el-table-column prop="timeNote" label="时间要求" min-width="110" show-overflow-tooltip />
          <el-table-column prop="dueDate" label="到期" width="100" />
          <el-table-column label="优先级" width="70" align="center">
            <template #default="{ row }">{{ prioText(row.priority) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="全局状态" width="90">
            <template #default="{ row }">{{ statusText(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="updateByName" label="最后更新人" width="100" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="viewAssignees(row)">责任人进度</el-button>
              <el-button link type="primary" size="small" @click="viewHistory(row)">历史</el-button>
              <el-button link type="danger" size="small" @click="del(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
    <template v-else>
      <el-table :data="publishedFlat" stripe size="small">
        <el-table-column prop="title" label="事项标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="documentTitle" label="来源文档" min-width="140" show-overflow-tooltip />
        <el-table-column label="阶段" width="90">
          <template #default="{ row }">{{ stageText(row.stage) }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="70" align="center">
          <template #default="{ row }">{{ prioText(row.priority) }}</template>
        </el-table-column>
        <el-table-column prop="dueDate" label="到期" width="100" />
        <el-table-column label="全局状态" width="90">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="updateByName" label="最后更新人" width="100" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="viewAssignees(row)">责任人进度</el-button>
            <el-button link type="primary" size="small" @click="viewHistory(row)">历史</el-button>
            <el-button link type="danger" size="small" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-empty v-if="!loading && !publishedGroups.length" description="暂无已发布事项" :image-size="70" />

    <!-- 编辑/新增事项弹窗 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑事项' : '新增事项'" width="640px" destroy-on-close>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="事项标题" required><el-input v-model="editForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="要点内容"><el-input v-model="editForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="阶段">
              <el-select v-model="editForm.stage" style="width: 100%">
                <el-option v-for="(t, k) in STAGE_TEXT" :key="k" :label="t" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="editForm.priority" style="width: 100%">
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="时间要求"><el-input v-model="editForm.timeNote" maxlength="200" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日期"><el-date-picker v-model="editForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="全局状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="未开始" value="PENDING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="待确认" value="CONFIRMING" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人">
          <el-select v-model="editForm.assigneeIds" multiple filterable placeholder="选择责任人（可多选）" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="全局备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 责任人进度（只读） -->
    <el-dialog v-model="assigneeVisible" title="责任人个人进度（只读）" width="520px" destroy-on-close>
      <el-table :data="assigneeRows" stripe size="small">
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column label="个人进度" width="110">
          <template #default="{ row }">{{ progressText(row.progressStatus) }}</template>
        </el-table-column>
        <el-table-column prop="blockReason" label="阻塞原因" min-width="140">
          <template #default="{ row }">{{ row.blockReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160" />
      </el-table>
      <div class="hint">个人备注对维护人不可见（数据隔离）</div>
    </el-dialog>

    <!-- 事项历史 -->
    <el-dialog v-model="historyVisible" title="事项修改历史" width="640px" destroy-on-close>
      <el-timeline v-if="historyRows.length">
        <el-timeline-item v-for="(h, i) in historyRows" :key="i" :timestamp="h.operationTime" placement="top">
          <div><b>{{ h.fieldName }}</b>：{{ h.oldValue || '-' }} → {{ h.newValue || '-' }}<span v-if="h.operatorName">（{{ h.operatorName }}）</span></div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无历史记录" :image-size="60" />
    </el-dialog>

    <!-- 匹配确认 -->
    <el-dialog v-model="matchVisible" title="确认跨版本匹配映射" width="480px" destroy-on-close>
      <div class="hint" style="margin-bottom: 10px">将该事项映射到旧版本同一事项（个人进度将随 item_code 迁移，不丢失）</div>
      <el-select v-model="matchOldItemId" filterable placeholder="选择旧版本事项" style="width: 100%">
        <el-option v-for="o in matchCandidates" :key="o.id" :label="`${o.title}（${o.itemCode || o.id}）`" :value="o.id" />
      </el-select>
      <template #footer>
        <el-button @click="matchVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!matchOldItemId" @click="doConfirmMatch">确认匹配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listComplianceItems, createComplianceItem, updateComplianceItem, deleteComplianceItem,
  confirmComplianceDrafts, confirmComplianceMatch, complianceItemHistory,
  assigneeProgress
} from '../../api/compliance'
import { listProjectExecutors } from '../../api/project'

const STAGE_TEXT = {
  INITIATION: '立项', FINANCE: '财务流程', EXECUTION: '执行',
  VERIFICATION: '会后核销', SPECIAL: '特殊问题', OTHER: '其他'
}

const viewMode = ref('grouped')
const filterStage = ref(null)
const filterStatus = ref(null)
const filterPriority = ref(null)
const keyword = ref('')
const loading = ref(false)
const confirming = ref(false)

const items = ref([])
const userOptions = ref([])

const editVisible = ref(false)
const editForm = ref({})
const saving = ref(false)

const assigneeVisible = ref(false)
const assigneeRows = ref([])
const historyVisible = ref(false)
const historyRows = ref([])
const matchVisible = ref(false)
const matchOldItemId = ref(null)
const matchCandidates = ref([])
const matchRow = ref(null)

const stageText = (s) => STAGE_TEXT[s] || s
const prioText = (p) => ({ HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || p)
const progressText = (s) => ({ UNPROCESSED: '未处理', PROCESSING: '处理中', COMPLETED: '已完成' }[s] || s)
const statusText = (s) => ({ PENDING: '未开始', IN_PROGRESS: '进行中', CONFIRMING: '待确认', COMPLETED: '已完成' }[s] || s)

const published = computed(() => items.value.filter((x) => x.isDraft === 0))
const drafts = computed(() => items.value.filter((x) => x.isDraft === 1))

function groupByDoc(list) {
  const map = {}
  for (const it of list) {
    const key = it.documentId
    if (!map[key]) map[key] = { documentId: key, documentTitle: it.documentTitle || ('文档 #' + key), items: [] }
    map[key].items.push(it)
  }
  return Object.values(map)
}
const publishedGroups = computed(() => groupByDoc(published.value))
const draftGroups = computed(() => groupByDoc(drafts.value))
const publishedFlat = computed(() => published.value)

async function load() {
  loading.value = true
  try {
    const data = await listComplianceItems({
      stage: filterStage.value || undefined,
      status: filterStatus.value || undefined,
      priority: filterPriority.value || undefined,
      keyword: keyword.value || undefined,
      includeDraft: true,
      page: 1, size: 500
    })
    items.value = data?.items || []
  } finally {
    loading.value = false
  }
}

async function confirmAll(g) {
  try {
    await ElMessageBox.confirm(`确认发布「${g.documentTitle}」的全部 ${g.items.length} 条草稿事项？发布后将同步到收藏用户与责任人的待办。`, '批量确认发布', { type: 'warning' })
  } catch (e) { return }
  confirming.value = true
  try {
    const res = await confirmComplianceDrafts({ documentId: g.documentId })
    ElMessage.success(`已发布 ${res?.published ?? 0} 条`)
    load()
  } finally {
    confirming.value = false
  }
}

function openCreate() {
  editForm.value = { title: '', content: '', stage: 'OTHER', priority: 'MEDIUM', timeNote: '', dueDate: null, status: 'PENDING', remark: '', assigneeIds: [] }
  editVisible.value = true
}
function openEdit(row) {
  editForm.value = {
    id: row.id, title: row.title, content: row.content || '', stage: row.stage,
    priority: row.priority, timeNote: row.timeNote || '', dueDate: row.dueDate || null,
    status: row.status, remark: row.remark || '', assigneeIds: [...(row.assigneeIds || [])]
  }
  editVisible.value = true
}
async function saveEdit() {
  if (!editForm.value.title.trim()) return ElMessage.warning('标题必填')
  saving.value = true
  try {
    const f = editForm.value
    if (f.id) await updateComplianceItem(f.id, f)
    else await createComplianceItem({ ...f, documentId: publishedGroups.value[0]?.documentId || drafts.value[0]?.documentId })
    ElMessage.success('已保存')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function del(row) {
  try {
    await ElMessageBox.confirm(`确认删除事项「${row.title}」？个人进度记录将保留。`, '删除事项', { type: 'warning' })
  } catch (e) { return }
  await deleteComplianceItem(row.id)
  ElMessage.success('已删除')
  load()
}
async function pickMatch(row) {
  matchRow.value = row
  matchOldItemId.value = null
  // 候选：同文档已发布旧事项
  try {
    const data = await listComplianceItems({ documentId: row.documentId, page: 1, size: 500 })
    matchCandidates.value = (data?.items || []).filter((x) => x.id !== row.id)
  } catch (e) { matchCandidates.value = [] }
  matchVisible.value = true
}
async function doConfirmMatch() {
  if (!matchRow.value || !matchOldItemId.value) return
  await confirmComplianceMatch(matchRow.value.id, matchOldItemId.value)
  ElMessage.success('匹配已确认（个人进度已迁移）')
  matchVisible.value = false
  load()
}
async function viewAssignees(row) {
  assigneeRows.value = await assigneeProgress(row.id) || []
  assigneeVisible.value = true
}
async function viewHistory(row) {
  historyRows.value = await complianceItemHistory(row.id) || []
  historyVisible.value = true
}

onMounted(async () => {
  load()
  try {
    const users = await request.get('/project/executors')
    userOptions.value = users || []
  } catch (e) { userOptions.value = [] }
})
</script>

<style scoped>
.mb-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.zone-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.draft-zone { border: 1px solid #FAECD8; background: #FDF6EC; border-radius: 8px; padding: 12px; }
.draft-group { margin-bottom: 14px; }
.draft-doc { font-weight: 600; color: #E6A23C; margin-bottom: 8px; }
.match-stat { font-size: 12px; color: #909399; margin-bottom: 6px; }
.pub-group { margin-bottom: 18px; }
.hint { font-size: 12px; color: #909399; }
</style>
