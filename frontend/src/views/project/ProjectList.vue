<template>
  <div>
    <div class="page-head">
      <span class="page-sub">会议/展会项目台账 · 会议信息 · 团队与人员 · 阶段推进 · 专家供应商关联 · 结算合同</span>
    </div>

    <div class="toolbar">
      <div class="toolbar-filters">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索会议名称 / 客户单位 / 客户姓名"
          clearable
          style="width: 220px"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.team" placeholder="团队" clearable style="width: 110px; margin-left: 8px" @change="handleSearch">
          <el-option label="项目一部" value="DEPT1" />
          <el-option label="项目二部" value="DEPT2" />
          <el-option label="项目三部" value="DEPT3" />
        </el-select>
        <el-select v-model="filters.category" placeholder="会议类型" clearable style="width: 100px; margin-left: 8px" @change="handleSearch">
          <el-option label="线上" value="ONLINE" />
          <el-option label="线下" value="OFFLINE" />
        </el-select>
        <el-select v-model="filters.cityId" placeholder="会议城市" clearable filterable style="width: 130px; margin-left: 8px" @change="handleSearch">
          <el-option-group v-for="g in cityGroups" :key="g.province" :label="g.province">
            <el-option v-for="c in g.cities" :key="c.id" :label="c.name" :value="c.id" />
          </el-option-group>
        </el-select>
        <el-select v-model="filters.status" placeholder="当前阶段" clearable style="width: 120px; margin-left: 8px" @change="handleSearch">
          <el-option v-for="p in phases" :key="p.id" :label="p.name" :value="String(p.id)" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
        <el-select v-model="filters.ownerId" placeholder="总负责人" clearable filterable style="width: 110px; margin-left: 8px" @change="handleSearch">
          <el-option v-for="o in users" :key="o.id" :label="o.username" :value="o.id" />
        </el-select>
        <el-select v-model="filters.assistantId" placeholder="协助人" clearable filterable style="width: 110px; margin-left: 8px" @change="handleSearch">
          <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
        </el-select>
        <el-date-picker
          v-model="startRange"
          type="daterange"
          range-separator="至"
          start-placeholder="项目开始起"
          end-placeholder="项目开始止"
          value-format="YYYY-MM-DD"
          style="width: 220px; margin-left: 8px"
          @change="handleSearch"
        />
        <el-date-picker
          v-model="endRange"
          type="daterange"
          range-separator="至"
          start-placeholder="项目结束起"
          end-placeholder="项目结束止"
          value-format="YYYY-MM-DD"
          style="width: 220px; margin-left: 8px"
          @change="handleSearch"
        />
        <div class="toolbar-actions toolbar-search-actions">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </div>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'project:manage'" type="success" plain @click="handleExport">导出 xlsx</el-button>
        <el-button v-permission="'project:create'" type="primary" plain @click="openEdit(null)">新建项目</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" @row-click="goDetail">
        <el-table-column prop="name" label="会议名称" min-width="200" show-overflow-tooltip class-name="cell-clickable" />
        <el-table-column label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.category === 'ONLINE' ? 'success' : 'warning'">{{ row.category === 'ONLINE' ? '线上' : '线下' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teamLabel" label="团队" width="90" align="center" />
        <el-table-column prop="cityName" label="会议城市" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.cityName || '—' }}</template>
        </el-table-column>
        <el-table-column label="项目起止" min-width="170">
          <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="举办时间" min-width="150">
          <template #default="{ row }">
            {{ meetingText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="总负责人" width="90" />
        <el-table-column label="协助人" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.assistantNames?.length ? row.assistantNames.join('、') : '—' }}</template>
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
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row.id)">详情</el-button>
            <el-button v-permission="'project:manage'" link type="primary" @click.stop="openEdit(row)">编辑</el-button>
            <el-button v-if="row.canAdvance && row.status === 'CANCELED'" link type="success" @click.stop="doRestore(row)">恢复</el-button>
            <el-button v-if="row.canAdvance && row.status !== 'CANCELED'" link type="warning" @click.stop="openCancel(row)">取消</el-button>
            <el-button v-permission="'project:manage'" link type="danger" @click.stop="confirmDelete(row)">删除</el-button>
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
import {
  listProjects, exportProjects, deleteProject, cancelProject, restoreProject,
  listPhaseOptions, listProjectUsers
} from '../../api/project'
import { listSupplierCities } from '../../api/supplier'

const router = useRouter()
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
.page-head { margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; }
.page-sub { margin-left: 8px; color: #909399; font-size: 13px; }
.toolbar { margin-bottom: 12px; display: flex; flex-direction: column; gap: 12px; }
.toolbar-filters { display: flex; flex-wrap: wrap; align-items: center; }
.toolbar-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; }
.toolbar-search-actions { margin-left: auto; }
.pagination-wrap { margin-top: 12px; display: flex; justify-content: flex-end; }
:deep(.cell-clickable) { cursor: pointer; }
:deep(.el-table__row) { cursor: pointer; }
</style>
