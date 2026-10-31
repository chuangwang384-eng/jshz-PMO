<template>
  <div>
    <div class="page-head">
      <span class="page-title">项目管理</span>
      <span class="page-sub">展会/会议/论坛项目台账·阶段推进·专家供应商关联·结算合同</span>
    </div>

    <div class="toolbar">
      <div class="toolbar-filters">
        <el-select v-model="filters.status" placeholder="当前阶段" clearable style="width: 140px" @change="handleSearch">
          <el-option v-for="p in phases" :key="p.id" :label="p.name" :value="String(p.id)" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
        <el-date-picker
          v-model="startRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始时间起"
          end-placeholder="开始时间止"
          value-format="YYYY-MM-DD"
          style="width: 230px; margin-left: 8px"
          @change="handleSearch"
        />
        <el-date-picker
          v-model="endRange"
          type="daterange"
          range-separator="至"
          start-placeholder="结束时间起"
          end-placeholder="结束时间止"
          value-format="YYYY-MM-DD"
          style="width: 230px; margin-left: 8px"
          @change="handleSearch"
        />
        <el-select v-model="filters.ownerId" placeholder="负责人" clearable filterable style="width: 140px; margin-left: 8px" @change="handleSearch">
          <el-option v-for="o in owners" :key="o.id" :label="o.username" :value="o.id" />
        </el-select>
        <el-select v-model="filters.category" placeholder="大类" clearable style="width: 110px; margin-left: 8px" @change="handleSearch">
          <el-option label="线上" value="ONLINE" />
          <el-option label="线下" value="OFFLINE" />
        </el-select>
        <el-select v-model="filters.typeId" placeholder="类型" clearable style="width: 130px; margin-left: 8px" @change="handleSearch">
          <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filters.executorId" placeholder="执行人" clearable filterable style="width: 140px; margin-left: 8px" @change="handleSearch">
          <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
        </el-select>
        <el-button type="primary" style="margin-left: 8px" @click="handleSearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'project:manage'" type="success" plain @click="handleExport">导出 xlsx</el-button>
        <el-button v-permission="'project:create'" type="primary" plain @click="openEdit(null)">新建项目</el-button>
        <el-button v-permission="'project:manage'" plain @click="goProjectConfig">项目配置</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="大类" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.category === 'ONLINE' ? 'success' : 'warning'">{{ row.category === 'ONLINE' ? '线上' : '线下' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="typeName" label="类型" width="90" />
        <el-table-column label="计划时间" min-width="170">
          <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="90" />
        <el-table-column prop="executorNames" label="执行人" min-width="120" show-overflow-tooltip />
        <el-table-column label="当前阶段" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'CANCELED'" type="danger">已取消</el-tag>
            <el-tag v-else type="primary">{{ row.phaseName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
            <el-button v-permission="'project:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.canAdvance" link type="warning" @click="openCancel(row)">取消</el-button>
            <el-popconfirm title="确认软删除该项目？数据保留但列表隐藏" @confirm="handleDelete(row)">
              <template #reference>
                <el-button v-permission="'project:manage'" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
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
      :owners="owners"
      :types="types"
      :phases="phases"
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProjectEdit from './ProjectEdit.vue'
import {
  listProjects, exportProjects, deleteProject, cancelProject,
  listProjectTypes, listProjectPhases, listProjectOwners, listProjectExecutors
} from '../../api/project'
import request from '../../api/request'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const phases = ref([])
const types = ref([])
const owners = ref([])
const users = ref([])

const filters = reactive({ status: null, ownerId: null, category: null, typeId: null, executorId: null })
const startRange = ref([])
const endRange = ref([])

const editVisible = ref(false)
const editingRow = ref(null)
const cancelVisible = ref(false)
const cancelReason = ref('')
const cancelRow = ref(null)

async function fetchList() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      status: filters.status || undefined,
      ownerId: filters.ownerId || undefined,
      category: filters.category || undefined,
      typeId: filters.typeId || undefined,
      executorId: filters.executorId || undefined,
      startBegin: startRange.value?.[0] || undefined,
      startEnd: startRange.value?.[1] || undefined,
      endBegin: endRange.value?.[0] || undefined,
      endEnd: endRange.value?.[1] || undefined
    }
    const data = await listProjects(params)
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
  Object.assign(filters, { status: null, ownerId: null, category: null, typeId: null, executorId: null })
  startRange.value = []
  endRange.value = []
  handleSearch()
}

async function handleExport() {
  try {
    await exportProjects({
      status: filters.status || undefined,
      ownerId: filters.ownerId || undefined,
      category: filters.category || undefined,
      typeId: filters.typeId || undefined,
      executorId: filters.executorId || undefined,
      startBegin: startRange.value?.[0] || undefined,
      startEnd: startRange.value?.[1] || undefined,
      endBegin: endRange.value?.[0] || undefined,
      endEnd: endRange.value?.[1] || undefined
    })
    ElMessage.success('导出成功')
  } catch (e) {
    // downloadFile 内部已提示
  }
}

function goDetail(id) {
  router.push(`/project/${id}`)
}

function goProjectConfig() {
  router.push({ name: 'project-config' })
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

async function handleDelete(row) {
  await deleteProject(row.id)
  ElMessage.success('项目已删除（软删除，数据保留）')
  fetchList()
}

onMounted(async () => {
  fetchList()
  phases.value = await listProjectPhases()
  types.value = await listProjectTypes()
  owners.value = await listProjectOwners()
  const userData = await listProjectExecutors()
  users.value = userData || []
})
</script>

<style scoped>
.page-head { margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; }
.page-sub { margin-left: 8px; color: #909399; font-size: 13px; }
.toolbar { margin-bottom: 12px; display: flex; flex-direction: column; gap: 12px; }
.toolbar-filters { display: flex; flex-wrap: wrap; align-items: center; }
.toolbar-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; }
.pagination-wrap { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
