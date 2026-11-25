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

    <div class="toolbar ui-toolbar">
      <el-select
        v-model="typeFilter"
        placeholder="全部类型"
        clearable
        filterable
        style="width: 170px"
        @change="handleFilterChange"
      >
        <!-- 分组取自 utils/notificationTypes.js（需求 B）：组内顺序即展示顺序 -->
        <el-option-group v-for="group in TYPE_GROUPS" :key="group.label" :label="group.label">
          <el-option v-for="t in group.types" :key="t" :label="TYPE_LABELS[t]" :value="t" />
        </el-option-group>
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
      <el-button link type="primary" @click="openMuteDialog">通知偏好</el-button>
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
      <el-table-column label="类型" width="150" align="center">
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
      <el-table-column label="时间" width="140">
        <template #default="{ row }">
          <!-- 需求 B：相对时间展示，悬停看绝对时间 -->
          <span :title="row.createTime">{{ formatRelativeTime(row.createTime) }}</span>
        </template>
      </el-table-column>
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

    <!-- 免打扰偏好（需求 D，2026-11-25）：勾选的类型不再接收站内通知；password_reset 强制送达不可关闭 -->
    <el-dialog v-model="muteVisible" title="通知偏好（免打扰）" width="520px" :close-on-click-modal="false">
      <p class="mute-tip">
        勾选的类型将不再接收站内通知（已有的未读消息不受影响，只是之后不再产生新通知）。
        「密码重置」属账号安全提醒，始终送达，不可关闭。
      </p>
      <div v-loading="muteLoading" class="mute-groups">
        <div v-for="group in MUTABLE_GROUPS" :key="group.label" class="mute-group">
          <div class="mute-group-label">{{ group.label }}</div>
          <el-checkbox-group v-model="muteSelection" class="mute-checkboxes">
            <el-checkbox v-for="t in group.types" :key="t" :value="t" :label="TYPE_LABELS[t]" />
          </el-checkbox-group>
        </div>
      </div>
      <template #footer>
        <el-button @click="muteVisible = false">取消</el-button>
        <el-button type="primary" :loading="muteSaving" @click="saveMutePreferences">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { watchDashboardFocus } from '../utils/dashboardFocus'
import { safeTarget } from '../utils/dashboard'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchNotifications, fetchMutedTypes, saveMutedTypes } from '../api/notification'
import { useNotificationStore } from '../store/notification'
import {
  TYPE_LABELS,
  TYPE_GROUPS,
  MUTABLE_TYPES,
  typeTagType,
  formatRelativeTime
} from '../utils/notificationTypes'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()

// 免打扰偏好弹窗的分组：不含「账号安全」组里的 password_reset（不可关闭），其余组原样展示
const MUTABLE_GROUPS = TYPE_GROUPS
  .map((g) => ({ label: g.label, types: g.types.filter((t) => MUTABLE_TYPES.includes(t)) }))
  .filter((g) => g.types.length)

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

// 请求序号：仅最新一次请求可写状态，避免快速切 tab/类型时慢响应覆盖新筛选结果（P3-5）
let requestSeq = 0

async function fetchList() {
  const seq = ++requestSeq
  loading.value = true
  try {
    const data = await fetchNotifications({
      page: page.value,
      pageSize: size.value,
      isRead: readTab.value === 'all' ? undefined : readTab.value === 'unread' ? 0 : 1,
      type: typeFilter.value || undefined
    })
    if (seq !== requestSeq) return
    records.value = data?.items || []
    total.value = data?.total || 0
    // 页码越界（删除/筛选后当前页为空但总数非 0）：回到最后一页重取，避免"共 N 条却空白"（P2-3）
    const maxPage = Math.max(1, Math.ceil(total.value / size.value))
    if (!records.value.length && total.value > 0 && page.value > maxPage) {
      page.value = maxPage
      fetchList()
    }
  } finally {
    if (seq === requestSeq) loading.value = false
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

// 点击行 = 标记已读（未读时）+ 按类型跳转关联页面；点复选框列不触发。
// 已读状态本地更新即可（P2-4：此前额外 fetchList() 全量刷新，多一次请求且丢勾选状态）
async function handleRowClick(row, column) {
  if (column && column.type === 'selection') return
  try {
    if (row.isRead === 0) {
      await notificationStore.markRead(row.id)
      row.isRead = 1
      notificationStore.loadUnread()
    }
    if (row.jumpPath) {
      const resolved = router.resolve(row.jumpPath)
      const target = safeTarget(router, { path: resolved.path, query: resolved.query })
      if(target) router.push(target)
      else ElMessage.warning('关联目标不可用')
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

// ==================== 免打扰偏好（需求 D） ====================
const muteVisible = ref(false)
const muteLoading = ref(false)
const muteSaving = ref(false)
const muteSelection = ref([])

async function openMuteDialog() {
  muteVisible.value = true
  muteLoading.value = true
  try {
    const muted = (await fetchMutedTypes()) || []
    muteSelection.value = Array.isArray(muted) ? muted : []
  } catch (e) {
    // 拦截器已提示
  } finally {
    muteLoading.value = false
  }
}

async function saveMutePreferences() {
  muteSaving.value = true
  try {
    await saveMutedTypes([...muteSelection.value])
    ElMessage.success(muteSelection.value.length
      ? `已设置 ${muteSelection.value.length} 类消息免打扰`
      : '已恢复接收全部类型通知')
    muteVisible.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    muteSaving.value = false
  }
}

onMounted(() => {
  // 未读数走全局 store（与铃铛红点同源同步）；列表按当前筛选拉取
  notificationStore.loadUnread()
  fetchList()
})
// 工作台/消息深链：?notificationId=xxx 弹内容并跳关联事项；打开后从 URL 移除该参数，
// 否则筛选/刷新时会再次触发（P3-6）
watchDashboardFocus(route, 'notificationId', 'notification', async row => {
  if(row.isRead === 0) { await notificationStore.markRead(row.id); row.isRead=1; await fetchList() }
  let confirmed=false
  try { await ElMessageBox.alert(row.content || row.title, row.title, { confirmButtonText:'查看关联事项', dangerouslyUseHTMLString:false }); confirmed=true } catch {}
  const query = { ...route.query }
  delete query.notificationId
  router.replace({ query }).catch(() => {})
  if(confirmed) await handleRowClick(row)
}, message => ElMessage.warning(message))
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.toolbar {
  flex-wrap: wrap;
  gap: 8px;
}
.toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.tab-badge {
  margin-left: 6px;
  transform: translateY(-2px);
}
.row-title {
  color: var(--ui-text-sub);
}
.row-title.is-unread {
  color: var(--ui-text-main);
  font-weight: 600;
}
.unread-dot {
  margin-right: 6px;
  vertical-align: middle;
}
.mute-tip {
  font-size: 13px;
  color: var(--ui-text-sub);
  margin: 0 0 12px;
}
.mute-group {
  margin-bottom: 12px;
}
.mute-group-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-text-main);
  margin-bottom: 6px;
}
.mute-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}
</style>
