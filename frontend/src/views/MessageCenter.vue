<template>
  <div>
    <div class="page-head">
      <span class="page-sub">集中查看本人全部通知：审批 / 文档 / 账号安全等提醒，支持筛选、批量已读与删除</span>
    </div>

    <el-tabs v-model="readTab" style="margin-top: 4px" @tab-change="handleFilterChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane name="unread">
        <template #label>
          未读<el-badge v-if="notificationStore.unread" :value="notificationStore.unread" :max="99" class="tab-badge" />
        </template>
      </el-tab-pane>
      <el-tab-pane label="已读" name="read" />
    </el-tabs>

    <div class="toolbar">
      <el-select
        v-model="typeFilter"
        placeholder="全部类型"
        clearable
        filterable
        style="width: 170px"
        @change="handleFilterChange"
      >
        <el-option v-for="(label, t) in TYPE_LABELS" :key="t" :label="label" :value="t" />
      </el-select>
      <el-button
        style="margin-left: 8px"
        :disabled="!selectedUnreadIds.length"
        @click="handleBatchRead"
      >批量已读{{ selectedUnreadIds.length ? `(${selectedUnreadIds.length})` : '' }}</el-button>
      <el-button :disabled="!notificationStore.unread" @click="handleMarkAllRead">全部已读</el-button>
      <el-button
        type="danger"
        plain
        :disabled="!selection.length"
        @click="handleBatchDelete"
      >批量删除{{ selection.length ? `(${selection.length})` : '' }}</el-button>
    </div>

    <el-table
      :data="records"
      border
      stripe
      v-loading="loading"
      style="margin-top: 12px"
      row-key="id"
      @selection-change="handleSelectionChange"
      @row-click="handleRowClick"
    >
      <el-table-column type="selection" width="46" :selectable="(row) => true" />
      <el-table-column label="类型" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ TYPE_LABELS[row.type] || row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="row-title" :class="{ 'is-unread': row.isRead === 0 }">
            <span v-if="row.isRead === 0" class="unread-dot"></span>{{ row.title }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容摘要" min-width="280" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click.stop="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleFilterChange"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchNotifications } from '../api/notification'
import { useNotificationStore } from '../store/notification'

const router = useRouter()
const notificationStore = useNotificationStore()

// 类型枚举（与后端 NotificationService.TYPE_* 一致，共 10 类）
const TYPE_LABELS = {
  approval_result: '审批结果',
  approval_todo: '待审批',
  approval_withdrawn: '申请撤回',
  approval_transferred: '审批转办',
  approval_assigned: '审批加签',
  role_changed: '角色调整',
  password_reset: '密码重置',
  doc_off_shelf: '文档下架',
  doc_deleted: '文档删除',
  doc_updated: '文档更新'
}

function typeTagType(type) {
  if (type === 'approval_result') return 'success'
  if (type === 'approval_todo' || type === 'approval_transferred' || type === 'approval_assigned') return 'warning'
  if (type === 'role_changed' || type === 'password_reset') return 'danger'
  return 'info'
}

const readTab = ref('all')
const typeFilter = ref('')
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const selection = ref([])

// 勾选中仍为未读的消息 id（批量已读只对未读有效）
const selectedUnreadIds = computed(() =>
  selection.value.filter((r) => r.isRead === 0).map((r) => r.id)
)

async function fetchList() {
  loading.value = true
  try {
    const data = await fetchNotifications({
      page: page.value,
      pageSize: size.value,
      isRead: readTab.value === 'all' ? undefined : readTab.value === 'unread' ? 0 : 1,
      type: typeFilter.value || undefined
    })
    records.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

// Tab / 类型 / 每页条数变化：回到第 1 页重新查询（不重置筛选本身）
function handleFilterChange() {
  page.value = 1
  fetchList()
}

function handleSelectionChange(rows) {
  selection.value = rows || []
}

// 点击行 = 标记已读（未读时）+ 按类型跳转关联页面；点复选框列不触发
async function handleRowClick(row, column) {
  if (column && column.type === 'selection') return
  try {
    if (row.isRead === 0) {
      await notificationStore.markRead(row.id)
      row.isRead = 1
      fetchList()
    }
    if (row.jumpPath) {
      router.push(row.jumpPath)
    }
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

async function handleBatchRead() {
  const ids = selectedUnreadIds.value
  if (!ids.length) return
  try {
    const count = await notificationStore.readBatch(ids)
    ElMessage.success(`已读 ${count} 条`)
    fetchList()
  } catch (e) {
    // 拦截器已提示
  }
}

async function handleMarkAllRead() {
  try {
    await ElMessageBox.confirm('确定将本人全部未读消息标记为已读吗？', '全部已读', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch (e) {
    return
  }
  try {
    await notificationStore.markAllRead()
    ElMessage.success('已全部标记为已读')
    fetchList()
  } catch (e) {
    // 拦截器已提示
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除消息「${row.title}」吗？删除后不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await notificationStore.removeOne(row.id)
    ElMessage.success('已删除')
    fetchList()
  } catch (e) {
    // 拦截器已提示
  }
}

async function handleBatchDelete() {
  const ids = selection.value.map((r) => r.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 条消息吗？删除后不可恢复。`, '批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    const count = await notificationStore.removeBatch(ids)
    ElMessage.success(`已删除 ${count} 条`)
    fetchList()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(() => {
  // 未读数走全局 store（与铃铛红点同源同步）；列表按当前筛选拉取
  notificationStore.loadUnread()
  fetchList()
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.page-title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}
.page-sub {
  font-size: 13px;
  color: #909399;
}
.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.tab-badge {
  margin-left: 6px;
  transform: translateY(-2px);
}
.row-title {
  color: #606266;
}
.row-title.is-unread {
  color: #303133;
  font-weight: 600;
}
.unread-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f56c6c;
  margin-right: 6px;
  vertical-align: middle;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
