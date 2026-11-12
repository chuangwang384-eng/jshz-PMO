<template>
  <div class="project-list-page">
    <section class="project-filters" aria-label="项目筛选">
      <div class="project-list-head">
        <h1>项目列表</h1>
        <div class="project-actions">
          <el-button v-permission="'project:manage'" plain @click="handleExport">导出 Excel</el-button>
          <el-button v-permission="'project:create'" type="primary" @click="openEdit(null)">新建项目</el-button>
        </div>
      </div>
      <div class="primary-filters">
        <el-input v-model="filters.keyword" class="keyword-filter" placeholder="会议名称、客户单位或姓名"
          aria-label="搜索项目" clearable @clear="handleSearch" @keyup.enter="handleSearch" />
        <el-select v-model="filters.team" placeholder="团队" aria-label="团队" clearable @change="handleSearch">
          <el-option label="项目一部" value="DEPT1" />
          <el-option label="项目二部" value="DEPT2" />
          <el-option label="项目三部" value="DEPT3" />
        </el-select>
        <el-select v-model="filters.category" placeholder="会议类型" aria-label="会议类型" clearable @change="handleSearch">
          <el-option label="线上" value="ONLINE" />
          <el-option label="线下" value="OFFLINE" />
        </el-select>
        <el-select v-model="filters.status" placeholder="当前阶段" aria-label="当前阶段" clearable @change="handleSearch">
          <el-option v-for="p in phases" :key="p.id" :label="p.name" :value="String(p.id)" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
        <div class="filter-actions">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button aria-controls="project-extra-filters" :aria-expanded="advancedOpen" @click="advancedOpen = !advancedOpen">
            {{ advancedOpen ? '收起筛选' : '更多筛选' }}{{ advancedCount ? `（${advancedCount}）` : '' }}
          </el-button>
          <el-button text @click="resetSearch">重置</el-button>
        </div>
      </div>
      <div v-show="advancedOpen" id="project-extra-filters" class="extra-filters">
        <label class="filter-field">
          <span>会议城市</span>
          <el-select v-model="filters.cityId" placeholder="全部城市" clearable filterable @change="handleSearch">
            <el-option-group v-for="g in cityGroups" :key="g.province" :label="g.province">
              <el-option v-for="c in g.cities" :key="c.id" :label="c.name" :value="c.id" />
            </el-option-group>
          </el-select>
        </label>
        <label class="filter-field">
          <span>总负责人</span>
          <el-select v-model="filters.ownerId" placeholder="全部人员" clearable filterable @change="handleSearch">
            <el-option v-for="o in users" :key="o.id" :label="o.username" :value="o.id" />
          </el-select>
        </label>
        <label class="filter-field">
          <span>协助人</span>
          <el-select v-model="filters.assistantId" placeholder="全部人员" clearable filterable @change="handleSearch">
            <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </label>
        <label class="filter-field date-filter">
          <span>项目开始日期</span>
          <el-date-picker v-model="startRange" type="daterange" range-separator="至"
            start-placeholder="起始日期" end-placeholder="截止日期" value-format="YYYY-MM-DD" @change="handleSearch" />
        </label>
        <label class="filter-field date-filter">
          <span>项目结束日期</span>
          <el-date-picker v-model="endRange" type="daterange" range-separator="至"
            start-placeholder="起始日期" end-placeholder="截止日期" value-format="YYYY-MM-DD" @change="handleSearch" />
        </label>
      </div>
    </section>

    <el-card shadow="never" class="project-table-card">
      <el-table v-loading="loading" :data="records" @row-click="goDetail">
        <el-table-column label="会议名称" min-width="210">
          <template #default="{ row }">
            <button type="button" class="project-name" @click.stop="goDetail(row.id)">{{ row.name }}</button>
            <div class="cell-meta">{{ row.category === 'ONLINE' ? '线上' : '线下' }}<template v-if="row.cityName"> · {{ row.cityName }}</template></div>
          </template>
        </el-table-column>
        <el-table-column label="当前阶段" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'CANCELED'" type="danger">已取消</el-tag>
            <el-tag v-else-if="row.speakerConfirmedCnt > 0" type="success">{{ row.phaseName }}·已完成</el-tag>
            <el-tag v-else-if="row.scheduleStatus === 'DONE'" type="warning">{{ row.phaseName }}·待确认</el-tag>
            <el-tag v-else-if="row.scheduleStatus === 'PARSING'" type="info">{{ row.phaseName }}·解析中</el-tag>
            <el-tag v-else type="primary">{{ row.phaseName }}{{ row.scheduleStatus === 'FAILED' ? '·解析失败' : '·待上传' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间安排" min-width="220">
          <template #default="{ row }">
            <div>举办 {{ meetingText(row) }}</div>
            <div class="cell-meta">项目 {{ row.startDate || '—' }} 至 {{ row.endDate || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="团队与人员" min-width="170">
          <template #default="{ row }">
            <div>{{ row.teamLabel || '—' }}<template v-if="row.ownerName"> · {{ row.ownerName }}</template></div>
            <div v-if="row.assistantNames?.length" class="cell-meta">协助 {{ row.assistantNames.join('、') }}</div>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }"><span class="cell-meta">{{ row.updateTime?.replace('T', ' ') || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="132">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
              <el-dropdown v-if="auth.hasPermission('project:manage') || row.canAdvance" trigger="click" @command="handleRowAction($event, row)">
                <el-button link aria-label="更多项目操作">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-permission="'project:manage'" command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item v-if="row.canAdvance" :command="row.status === 'CANCELED' ? 'restore' : 'cancel'">
                      {{ row.status === 'CANCELED' ? '恢复项目' : '取消项目' }}
                    </el-dropdown-item>
                    <el-dropdown-item v-permission="'project:manage'" command="delete" divided>删除项目</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <project-edit
      v-model="editVisible"
      :project="editingRow"
      :users="users"
      @saved="onSaved"
    />

    <!-- 取消原因 -->
    <el-dialog v-model="cancelVisible" title="取消项目" width="460px" destroy-on-close>
      <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="500" placeholder="取消原因（必填，写入时间线）" />
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :disabled="!cancelReason.trim()" @click="doCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认（MessageBox，避免 popconfirm 嵌套点击失效） -->
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProjectEdit from './ProjectEdit.vue'
import { useAuthStore } from '../../store/auth'
import {
  listProjects, exportProjects, deleteProject, cancelProject, restoreProject,
  listPhaseOptions, listProjectUsers
} from '../../api/project'
import { listSupplierCities } from '../../api/supplier'

const router = useRouter()
const auth = useAuthStore()
const route = useRoute()
const loading = ref(false)
const records = ref([])
const total = ref(0)

// ---------- 查询状态与 URL 同步（返回/刷新可还原视图） ----------
function queryParam(key) {
  const raw = route.query[key]
  const v = Array.isArray(raw) ? raw[0] : raw
  return v == null ? '' : String(v)
}
function initFilters() {
  return {
    keyword: queryParam('keyword') || null,
    team: ['DEPT1', 'DEPT2', 'DEPT3'].includes(queryParam('team')) ? queryParam('team') : null,
    cityId: queryParam('cityId') ? Number(queryParam('cityId')) : null,
    status: queryParam('status') || null,
    ownerId: queryParam('ownerId') ? Number(queryParam('ownerId')) : null,
    assistantId: queryParam('assistantId') ? Number(queryParam('assistantId')) : null,
    category: ['ONLINE', 'OFFLINE'].includes(queryParam('category')) ? queryParam('category') : null
  }
}
const filters = reactive(initFilters())
const startRange = ref(queryParam('startBegin') && queryParam('startEnd') ? [queryParam('startBegin'), queryParam('startEnd')] : [])
const endRange = ref(queryParam('endBegin') && queryParam('endEnd') ? [queryParam('endBegin'), queryParam('endEnd')] : [])
const advancedCount = computed(() => [
  filters.cityId, filters.ownerId, filters.assistantId, startRange.value?.length, endRange.value?.length
].filter(Boolean).length)
const advancedOpen = ref(advancedCount.value > 0)

function handleRowAction(command, row) {
  const actions = { edit: openEdit, restore: doRestore, cancel: openCancel, delete: confirmDelete }
  actions[command]?.(row)
}
const page = ref(queryParam('page') ? Math.max(1, Number(queryParam('page')) || 1) : 1)
const size = ref([10, 20, 50].includes(Number(queryParam('pageSize'))) ? Number(queryParam('pageSize')) : 10)

/** 查询状态写回 URL：只 replace 不 push，默认值不写入，保持地址干净 */
function syncRouteQuery() {
  const next = {
    keyword: filters.keyword || undefined,
    team: filters.team || undefined,
    cityId: filters.cityId || undefined,
    status: filters.status || undefined,
    ownerId: filters.ownerId || undefined,
    assistantId: filters.assistantId || undefined,
    category: filters.category || undefined,
    startBegin: startRange.value?.[0] || undefined,
    startEnd: startRange.value?.[1] || undefined,
    endBegin: endRange.value?.[0] || undefined,
    endEnd: endRange.value?.[1] || undefined,
    page: page.value > 1 ? String(page.value) : undefined,
    pageSize: size.value !== 10 ? String(size.value) : undefined
  }
  const same = Object.keys(next).every((k) => {
    const cur = route.query[k]
    const curStr = Array.isArray(cur) ? cur[0] || '' : cur || ''
    return String(curStr) === String(next[k] || '')
  })
  if (!same) router.replace({ query: next })
}

const phases = ref([])
const users = ref([])
const cities = ref([])
const cityGroups = computed(() => {
  const groups = []
  const byProvince = new Map()
  for (const c of cities.value) {
    if (!byProvince.has(c.province)) {
      const g = { province: c.province, cities: [] }
      byProvince.set(c.province, g)
      groups.push(g)
    }
    byProvince.get(c.province).cities.push(c)
  }
  return groups
})

const editVisible = ref(false)
const editingRow = ref(null)
const cancelVisible = ref(false)
const cancelReason = ref('')
const cancelRow = ref(null)

function buildParams() {
  return {
    page: page.value,
    size: size.value,
    keyword: filters.keyword || undefined,
    team: filters.team || undefined,
    cityId: filters.cityId || undefined,
    status: filters.status || undefined,
    ownerId: filters.ownerId || undefined,
    assistantId: filters.assistantId || undefined,
    category: filters.category || undefined,
    startBegin: startRange.value?.[0] || undefined,
    startEnd: startRange.value?.[1] || undefined,
    endBegin: endRange.value?.[0] || undefined,
    endEnd: endRange.value?.[1] || undefined
  }
}

async function fetchList() {
  loading.value = true
  try {
    syncRouteQuery()
    const data = await listProjects(buildParams())
    records.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function resetSearch() {
  Object.assign(filters, { keyword: null, team: null, cityId: null, status: null, ownerId: null, assistantId: null, category: null })
  startRange.value = []
  endRange.value = []
  handleSearch()
}

function meetingText(row) {
  if (!row.meetingStartDate && !row.meetingEndDate) return '—'
  if (row.meetingStartDate === row.meetingEndDate) return row.meetingStartDate
  return `${row.meetingStartDate || '-'} ~ ${row.meetingEndDate || '-'}`
}

async function handleExport() {
  try {
    await exportProjects(buildParams())
    ElMessage.success('导出成功')
  } catch (e) {
    // downloadFile 内部已提示
  }
}

function goDetail(id) {
  router.push(`/project/${typeof id === 'object' ? id.id : id}`)
}

function openEdit(row) {
  editingRow.value = row
  editVisible.value = true
}

function onSaved() {
  handleSearch()
}

function openCancel(row) {
  cancelRow.value = row
  cancelReason.value = ''
  cancelVisible.value = true
}

async function doCancel() {
  await cancelProject(cancelRow.value.id, cancelReason.value.trim())
  ElMessage.success('项目已取消')
  cancelVisible.value = false
  fetchList()
}

// 恢复已取消项目（Owner 2026-11-10：与取消对称，负责人/创建人）
async function doRestore(row) {
  await ElMessageBox.confirm('确认恢复项目「' + row.name + '」？恢复后回到 ACTIVE 状态，阶段保持不变。', '恢复项目', { type: 'warning' })
  await restoreProject(row.id)
  ElMessage.success('项目已恢复')
  fetchList()
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm('确认软删除该项目？数据保留但列表隐藏', '删除项目', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '再想想' })
  } catch (e) {
    return
  }
  await deleteProject(row.id)
  ElMessage.success('项目已删除（软删除，数据保留）')
  fetchList()
}

onMounted(async () => {
  fetchList()
  listPhaseOptions().then((data) => { phases.value = data || [] }).catch(() => {})
  listProjectUsers().then((data) => { users.value = data || [] }).catch(() => {})
  listSupplierCities().then((data) => { cities.value = data || [] }).catch(() => {})
})
</script>

<style scoped>
.project-list-page { min-width: 0; display: grid; gap: 16px; }
.project-filters { min-width: 0; background: #fff; border: 1px solid #dfe5ee; border-radius: 12px; padding: 18px 20px; }
.project-list-head, .primary-filters, .project-actions, .filter-actions, .row-actions {
  display: flex; align-items: center; flex-wrap: wrap; gap: 10px;
}
.project-list-head { justify-content: space-between; margin-bottom: 14px; }
.project-list-head h1 { margin: 0; font-size: 19px; color: #172842; }
.primary-filters > .el-input { flex: 1 1 260px; min-width: 0; }
.primary-filters > .el-select { width: 132px; }
.project-actions :deep(.el-button), .filter-actions :deep(.el-button), .row-actions :deep(.el-button) { margin-left: 0; }
.extra-filters {
  display: flex; flex-wrap: wrap; gap: 12px; margin-top: 14px; padding-top: 14px; border-top: 1px solid #e8edf4;
}
.filter-field { flex: 1 1 140px; min-width: 0; display: grid; gap: 6px; color: #52627a; font-size: 13px; }
.date-filter { flex-basis: 245px; }
.filter-field :deep(.el-select), .filter-field :deep(.el-date-editor) { width: 100%; min-width: 0; }
.project-table-card { min-width: 0; border-radius: 12px; }
.project-table-card :deep(.el-card__body) { padding: 0; }
.project-table-card :deep(.el-table) { border-radius: 12px 12px 0 0; }
.project-table-card :deep(.el-table th.el-table__cell) { background: #f7f9fc; color: #43536a; }
.project-table-card :deep(.el-table td.el-table__cell) { padding-block: 12px; }
.project-name { background: none; border: 0; padding: 0; text-align: left; font: inherit; font-weight: 650; color: #1f5086; cursor: pointer; overflow-wrap: anywhere; }
.project-name:hover { text-decoration: underline; }
.cell-meta { font-size: 12px; color: #596a82; overflow-wrap: anywhere; }
.row-actions { gap: 8px; flex-wrap: nowrap; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 14px 16px; overflow-x: auto; }
@media (max-width: 680px) {
  .project-filters { padding: 14px; }
  .primary-filters > .el-input, .filter-actions { flex-basis: 100%; }
  .primary-filters > .el-select { flex: 1 1 92px; min-width: 0; }
  .pagination-wrap { justify-content: flex-start; }
  .pagination-wrap :deep(.el-pagination__jump) { display: none; }
}
</style>
