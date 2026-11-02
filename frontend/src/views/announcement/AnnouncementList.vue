<template>
  <div>
    <div class="page-head">
      <span class="page-title">公告</span>
      <span class="page-sub">系统通知与重要事项（点击查看详情，可标记已读）</span>
      <el-switch
        v-if="canManage"
        v-model="manageMode"
        class="manage-mode-switch"
        active-text="管理模式"
        inline-prompt
      />
    </div>

    <div class="toolbar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索标题 / 内容"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-if="showManageBar"
        v-model="filters.status"
        placeholder="全部状态"
        clearable
        style="width: 140px; margin-left: 8px"
        @change="handleSearch"
      >
        <el-option label="上架" value="ONLINE" />
        <el-option label="下架" value="OFFLINE" />
      </el-select>
      <el-button type="primary" style="margin-left: 8px" @click="handleSearch">搜索</el-button>
      <el-button v-if="showManageBar" type="success" style="margin-left: 8px" @click="openCreate">新建公告</el-button>
    </div>

    <!-- 阅读模式：卡片列表（ONLINE，后端权限感知过滤） -->
    <div v-if="!showManageBar" v-loading="loading" class="announcement-list">
      <el-card
        v-for="a in records"
        :key="a.id"
        shadow="hover"
        class="announcement-item"
        @click="goDetail(a.id)"
      >
        <div class="ann-item-main">
          <el-tag v-if="a.isTop === 1" type="danger" size="small" class="ann-top-tag">置顶</el-tag>
          <el-tag v-if="a.expired" type="info" size="small" class="ann-top-tag">已过期</el-tag>
          <span class="ann-title" :class="{ 'ann-title-read': a.read }">{{ a.title }}</span>
          <span v-if="a.read === false" class="ann-dot" title="未读"></span>
        </div>
        <div class="ann-item-meta">
          <span>{{ a.creatorName || '系统' }}</span>
          <span class="ann-time">{{ formatTime(a.createTime) }}</span>
          <span v-if="a.effectiveTime" class="ann-time">生效：{{ formatTime(a.effectiveTime) }}</span>
          <span v-if="a.expireTime" class="ann-time">失效：{{ formatTime(a.expireTime) }}</span>
        </div>
      </el-card>
      <el-empty v-if="!loading && !records.length" description="暂无公告" />
    </div>

    <!-- 管理模式：表格（全部状态 + 已读进度 + 管理工具栏） -->
    <el-table
      v-else
      v-loading="loading"
      :data="records"
      border
      stripe
      style="width: 100%; margin-top: 4px"
    >
      <el-table-column label="标题" min-width="260">
        <template #default="{ row }">
          <span>{{ row.title }}</span>
          <el-tag v-if="row.isTop === 1" type="danger" size="small" style="margin-left: 6px">置顶</el-tag>
          <el-tag v-if="row.expired" type="info" size="small" style="margin-left: 6px">已过期</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'">{{ row.status === 'ONLINE' ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效时间" width="150">
        <template #default="{ row }">{{ formatTime(row.effectiveTime) }}</template>
      </el-table-column>
      <el-table-column label="失效时间" width="150">
        <template #default="{ row }">{{ row.expireTime ? formatTime(row.expireTime) : '永久' }}</template>
      </el-table-column>
      <el-table-column label="已读进度" width="120" align="center">
        <template #default="{ row }">
          <el-button v-if="row.totalCount > 0" link type="primary" @click="openStats(row)">{{ row.readCount }}/{{ row.totalCount }}</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="创建人" width="100">
        <template #default="{ row }">{{ row.creatorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="320" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">查看</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="toggleTop(row)">{{ row.isTop === 1 ? '取消置顶' : '置顶' }}</el-button>
          <el-button link :type="row.status === 'ONLINE' ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'ONLINE' ? '下架' : '上架' }}
          </el-button>
          <el-popconfirm title="确认删除该公告？（已读记录同步清理）" @confirm="handleDelete(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- 新建 / 编辑弹窗（仅管理模式挂载；权限由 canManage + 管理模式开关双重保护） -->
    <AnnouncementFormDialog
      v-if="showManageBar"
      v-model="formVisible"
      :editing="editingRow"
      @saved="loadList"
    />

    <!-- 已读进度与名单 -->
    <AnnouncementReadStatsDialog v-model="statsVisible" :announcement-id="statsId" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../store/auth'
import {
  listAnnouncements,
  deleteAnnouncement,
  changeAnnouncementStatus,
  toggleAnnouncementTop
} from '../../api/announcement'
import AnnouncementFormDialog from './AnnouncementFormDialog.vue'
import AnnouncementReadStatsDialog from './AnnouncementReadStatsDialog.vue'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const records = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: '', page: 1, size: 10 })

// 权限感知：管理模式开关仅 announcement:manage 持有者可见（SUPER_ADMIN 由后端全权返回）
const canManage = computed(() => authStore.hasPermission('announcement:manage'))

// 管理模式会话记忆（localStorage，刷新不丢）
const MANAGE_MODE_KEY = 'announcement_manage_mode'
const manageMode = ref(localStorage.getItem(MANAGE_MODE_KEY) === '1')
watch(manageMode, (v) => {
  localStorage.setItem(MANAGE_MODE_KEY, v ? '1' : '0')
  filters.page = 1
  filters.status = ''
  loadList()
})

const showManageBar = computed(() => canManage.value && manageMode.value)

// 新建 / 编辑弹窗
const formVisible = ref(false)
const editingRow = ref(null)

// 已读统计弹窗
const statsVisible = ref(false)
const statsId = ref(null)

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadList() {
  loading.value = true
  try {
    const data = await listAnnouncements({
      keyword: filters.keyword || undefined,
      // 管理模式才传状态筛选；阅读模式由后端按权限返回（无 manage 仅 ONLINE）
      status: showManageBar.value && filters.status ? filters.status : undefined,
      page: filters.page,
      size: filters.size
    })
    records.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  loadList()
}

function goDetail(id) {
  router.push(`/profile/announcements/${id}`)
}

function openCreate() {
  editingRow.value = null
  formVisible.value = true
}

function openEdit(row) {
  editingRow.value = row
  formVisible.value = true
}

async function toggleTop(row) {
  try {
    await toggleAnnouncementTop(row.id, row.isTop === 1)
    ElMessage.success(row.isTop === 1 ? '已取消置顶' : '已置顶')
    loadList()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

async function toggleStatus(row) {
  const next = row.status === 'ONLINE' ? 'OFFLINE' : 'ONLINE'
  try {
    await changeAnnouncementStatus(row.id, next)
    ElMessage.success(next === 'ONLINE' ? '已上架' : '已下架')
    loadList()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

async function handleDelete(row) {
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('公告已删除')
    loadList()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

function openStats(row) {
  statsId.value = row.id
  statsVisible.value = true
}

onMounted(loadList)
</script>

<style scoped>
.toolbar {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.page-head {
  display: flex;
  align-items: center;
  gap: 16px;
}
.manage-mode-switch {
  margin-left: auto;
}
.announcement-list {
  min-height: 120px;
}
.announcement-item {
  cursor: pointer;
  margin-bottom: 10px;
}
.ann-item-main {
  display: flex;
  align-items: center;
}
.ann-top-tag {
  margin-right: 8px;
}
.ann-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ann-title-read {
  font-weight: 400;
  color: #909399;
}
.ann-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  margin-left: 8px;
  flex-shrink: 0;
}
.ann-item-meta {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
