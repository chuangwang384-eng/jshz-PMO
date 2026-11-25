<template>
  <div v-permission="'approval:audit'">
    <div class="page-head">
      <span class="page-sub">处理员工提交的专家信息修改申请</span>
    </div>

    <!-- 筛选条（2026-11-25 审批筛选增强）：关键词 / 类型两 Tab 共用；状态筛选仅「已处理」Tab；条件写回 URL 便于分享与深链 -->
    <div class="filter-bar ui-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索专家姓名 / 申请人"
        clearable
        class="filter-keyword"
        @keyup.enter="applyFilters"
        @clear="applyFilters"
      />
      <el-select v-model="type" placeholder="全部类型" clearable class="filter-type" @change="applyFilters">
        <el-option v-for="opt in TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select
        v-if="activeTab === 'done'"
        v-model="doneStatus"
        placeholder="全部状态"
        clearable
        class="filter-status"
        @change="applyFilters"
      >
        <el-option v-for="opt in DONE_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button type="primary" @click="applyFilters">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <div v-if="loadError" class="load-error">
      <span>{{ loadError }}</span>
      <el-button link type="primary" @click="refresh()">重试</el-button>
    </div>

    <el-tabs v-model="activeTab" style="margin-top: 12px" @tab-change="handleTabChange">
      <!-- 待审批 -->
      <el-tab-pane label="待审批" name="pending">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="加签采用或签语义：您与被加签人任一人审批（通过/驳回）即生效；转办后仅被转办人可审批。退回修改会保留临时照片，申请人可修改后重新提交。"
          style="margin-bottom: 12px"
        />
        <el-button v-if="pendingTotal > 3" type="primary" :loading="quickLoading"
          style="margin-bottom: 12px" @click="openQuickApproval">快速审批</el-button>
        <ApprovalTable
          mode="pending"
          :rows="pendingList"
          :loading="pendingLoading"
          :current-username="authStore.username"
          @diff="openDiff"
          @approve="(row) => openDiff(row, 'approve')"
          @reject="(row) => openDiff(row, 'reject')"
          @return="(row) => openDiff(row, 'return')"
          @transfer="openTransfer"
          @addsign="openAddSign"
        />
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="pendingPage"
            v-model:page-size="pendingSize"
            :total="pendingTotal"
            :page-sizes="PAGE_SIZES"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handlePendingSizeChange"
            @current-change="fetchPending"
          />
        </div>
      </el-tab-pane>

      <!-- 已处理 -->
      <el-tab-pane label="已处理" name="done">
        <ApprovalTable
          mode="done"
          :rows="doneList"
          :loading="doneLoading"
          :current-username="authStore.username"
          @diff="openDiff"
        />
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="donePage"
            v-model:page-size="doneSize"
            :total="doneTotal"
            :page-sizes="PAGE_SIZES"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleDoneSizeChange"
            @current-change="fetchDone"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 变更前后对比弹窗（查看/通过/驳回/退回修改四合一，审批意见在弹窗底部） -->
    <ApprovalDiffDialog
      v-model="diffVisible"
      :row="diffRow"
      :mode="diffMode"
      :queue-index="quickIndex"
      :queue-size="quickQueue.length"
      @navigate="navigateQuick"
      @success="handleDiffSuccess"
      @stale="handleDiffStale"
    />

    <!-- 转办弹窗：选人（仅列出有审批权限的用户，排除本人） -->
    <el-dialog v-model="transferVisible" title="审批转办" width="480px" destroy-on-close>
      <div class="dialog-expert">专家：{{ actionRow?.expertName }}（申请人：{{ actionRow?.applicant }}）</div>
      <div class="dialog-summary">转办后仅被转办人可审批该申请单，请在下方选择接收人。</div>
      <el-select
        v-model="transferTarget"
        filterable
        placeholder="选择被转办人（仅列出有审批权限的用户）"
        style="width: 100%"
      >
        <el-option v-for="u in assignableOptions" :key="u.id" :label="u.username" :value="u.username" />
      </el-select>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="warning" :loading="actionSubmitting" @click="confirmTransfer">确认转办</el-button>
      </template>
    </el-dialog>

    <!-- 加签弹窗（或签：任一人审批即生效） -->
    <el-dialog v-model="addSignVisible" title="审批加签" width="480px" destroy-on-close>
      <div class="dialog-expert">专家：{{ actionRow?.expertName }}（申请人：{{ actionRow?.applicant }}）</div>
      <div class="dialog-summary">
        加签采用或签语义：您与被加签人任一人审批（通过/驳回）即生效，无需两人都审。
      </div>
      <el-select
        v-model="addSignTarget"
        filterable
        placeholder="选择被加签人（仅列出有审批权限的用户）"
        style="width: 100%; margin-bottom: 12px"
      >
        <el-option v-for="u in assignableOptions" :key="u.id" :label="u.username" :value="u.username" />
      </el-select>
      <el-input
        v-model="addSignComment"
        type="textarea"
        :rows="2"
        maxlength="200"
        placeholder="加签说明（可空）"
      />
      <template #footer>
        <el-button @click="addSignVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionSubmitting" @click="confirmAddSign">确认加签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getApprovalRequests,
  transferRequest,
  addApproverRequest,
  getAssignableUsers
} from '../api/approval'
import { useAuthStore } from '../store/auth'
import { useNotificationStore } from '../store/notification'
import { TYPE_OPTIONS, PAGE_SIZES } from '../utils/approvalFields'
import { watchDashboardFocus } from '../utils/dashboardFocus'
import ApprovalTable from '../components/ApprovalTable.vue'
import ApprovalDiffDialog from '../components/ApprovalDiffDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()

const activeTab = ref('pending')

// 已处理 Tab 的状态筛选（待审批 Tab 恒为 status=0，无需筛选）
const DONE_STATUS_OPTIONS = [
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已撤回' },
  { value: 4, label: '退回修改' }
]

// ==================== 筛选条件（与 URL query 双向同步） ====================
const FILTER_KEYS = ['keyword', 'type', 'status']
const keyword = ref('')
const type = ref('')
const doneStatus = ref(undefined)

function readFiltersFromRoute() {
  const q = route.query
  const rawStatus = q.status === undefined || q.status === '' ? undefined : Number(q.status)
  return {
    keyword: typeof q.keyword === 'string' ? q.keyword : '',
    type: TYPE_OPTIONS.some((opt) => opt.value === q.type) ? q.type : '',
    doneStatus: DONE_STATUS_OPTIONS.some((opt) => opt.value === rawStatus) ? rawStatus : undefined
  }
}

function currentFilters() {
  return {
    keyword: (keyword.value || '').trim() || undefined,
    type: type.value || undefined,
    doneStatus: doneStatus.value ?? undefined
  }
}

function applyFilters() {
  const filters = currentFilters()
  const query = { ...route.query }
  FILTER_KEYS.forEach((key) => delete query[key])
  if (filters.keyword) query.keyword = filters.keyword
  if (filters.type) query.type = filters.type
  if (filters.doneStatus !== undefined) query.status = String(filters.doneStatus)
  pendingPage.value = 1
  donePage.value = 1
  if (JSON.stringify(query) !== JSON.stringify(route.query)) {
    router.replace({ query }).catch(() => {})
  }
  fetchPending()
  fetchDone()
}

function resetFilters() {
  keyword.value = ''
  type.value = ''
  doneStatus.value = undefined
  applyFilters()
}

// URL 变化（前进/后退、深链、分享链接）时同步筛选；仅本页路由内响应
watch(
  () => [route.name, route.query.keyword, route.query.type, route.query.status],
  () => {
    if (route.name !== 'approval-manage') return
    const next = readFiltersFromRoute()
    if (next.keyword === (keyword.value || '').trim()
      && next.type === (type.value || '')
      && next.doneStatus === doneStatus.value) return
    keyword.value = next.keyword
    type.value = next.type
    doneStatus.value = next.doneStatus
    pendingPage.value = 1
    donePage.value = 1
    fetchPending()
    fetchDone()
  }
)

// ==================== 列表 ====================
const pendingList = ref([])
const pendingTotal = ref(0)
const pendingPage = ref(1)
const pendingSize = ref(20)
const pendingLoading = ref(false)

const doneList = ref([])
const doneTotal = ref(0)
const donePage = ref(1)
const doneSize = ref(20)
const doneLoading = ref(false)

const loadError = ref('')
// 请求序号：仅最新一次请求可写状态，避免快速切换筛选时慢响应覆盖新结果
let pendingSeq = 0
let doneSeq = 0

async function fetchPending(options = {}) {
  const silent = options.silent === true
  const seq = ++pendingSeq
  if (!silent) pendingLoading.value = true
  loadError.value = ''
  const filters = currentFilters()
  try {
    const data = await getApprovalRequests({
      status: 0,
      keyword: filters.keyword,
      type: filters.type,
      page: pendingPage.value,
      size: pendingSize.value
    })
    if (seq !== pendingSeq) return
    pendingList.value = data?.items || []
    pendingTotal.value = data?.total || 0
    const maxPage = Math.max(1, Math.ceil(pendingTotal.value / pendingSize.value))
    if (!pendingList.value.length && pendingTotal.value > 0 && pendingPage.value > maxPage) {
      pendingPage.value = maxPage
      fetchPending()
    }
  } catch (e) {
    if (seq !== pendingSeq) return
    if (!silent) {
      loadError.value = '待审批列表加载失败，请稍后重试'
      pendingList.value = []
      pendingTotal.value = 0
    }
  } finally {
    if (seq === pendingSeq) pendingLoading.value = false
  }
}

// 已处理 = 已通过(1) + 已驳回(2) + 已撤回(3) + 已退回修改(4)，后端 statuses 多状态统一分页；
// 状态筛选选中时只查该状态
async function fetchDone(options = {}) {
  const silent = options.silent === true
  const seq = ++doneSeq
  if (!silent) doneLoading.value = true
  loadError.value = ''
  const filters = currentFilters()
  try {
    const data = await getApprovalRequests({
      statuses: filters.doneStatus !== undefined ? String(filters.doneStatus) : '1,2,3,4',
      keyword: filters.keyword,
      type: filters.type,
      page: donePage.value,
      size: doneSize.value
    })
    if (seq !== doneSeq) return
    doneList.value = data?.items || []
    doneTotal.value = data?.total || 0
    const maxPage = Math.max(1, Math.ceil(doneTotal.value / doneSize.value))
    if (!doneList.value.length && doneTotal.value > 0 && donePage.value > maxPage) {
      donePage.value = maxPage
      fetchDone()
    }
  } catch (e) {
    if (seq !== doneSeq) return
    if (!silent) {
      loadError.value = '已处理列表加载失败，请稍后重试'
      doneList.value = []
      doneTotal.value = 0
    }
  } finally {
    if (seq === doneSeq) doneLoading.value = false
  }
}

function handlePendingSizeChange() {
  pendingPage.value = 1
  fetchPending()
}

function handleDoneSizeChange() {
  donePage.value = 1
  fetchDone()
}

// 跟随全局通知轮询静默刷新当前 Tab（新申请提交后无需手动刷新即可出现）
watch(
  () => notificationStore.lastPollAt,
  () => {
    if (activeTab.value === 'pending') {
      if (!pendingLoading.value) fetchPending({ silent: true })
    } else if (!doneLoading.value) {
      fetchDone({ silent: true })
    }
  }
)

// ==================== 对比弹窗 / 快速审批 ====================
const diffVisible = ref(false)
const diffRow = ref(null)
const diffMode = ref('view')
const quickQueue = ref([])
const quickIndex = ref(0)
const quickLoading = ref(false)

function openDiff(row, mode = 'view') {
  diffRow.value = row
  diffMode.value = mode
  diffVisible.value = true
}

async function openQuickApproval() {
  if (quickLoading.value) return
  quickLoading.value = true
  const size = Math.max(...PAGE_SIZES)
  const username = authStore.username
  const filters = currentFilters()
  const queue = new Map()
  try {
    let page = 1
    let total
    do {
      const result = await getApprovalRequests({
        status: 0,
        keyword: filters.keyword,
        type: filters.type,
        page,
        size
      })
      total = result?.total || 0
      for (const row of result?.items || []) {
        if (row.status === 0 && (!row.currentAssignee || row.currentAssignee === username || row.extraApprover === username)) {
          queue.set(row.id, row)
        }
      }
    } while (page++ * size < total)
    if (activeTab.value !== 'pending') return
    if (total <= 3) {
      ElMessage.info('待审批超过 3 条时可使用快速审批')
      refresh()
      return
    }
    if (!queue.size) {
      ElMessage.info('没有分给您的待审批（其余单据已转办给他人或不在您的审批人快照内）')
      refresh()
      return
    }
    quickQueue.value = [...queue.values()]
    quickIndex.value = 0
    openDiff(quickQueue.value[0], 'quick')
  } catch {
    // 接口拦截器已提示加载错误。
  } finally {
    quickLoading.value = false
  }
}

function navigateQuick(offset) {
  const nextIndex = quickIndex.value + offset
  if (!quickQueue.value[nextIndex]) return
  quickIndex.value = nextIndex
  diffRow.value = quickQueue.value[nextIndex]
}

function handleDiffSuccess(id) {
  if (diffMode.value === 'quick') {
    quickQueue.value = quickQueue.value.filter(row => row.id !== id)
    if (quickQueue.value.length) {
      if (quickIndex.value >= quickQueue.value.length) quickIndex.value = 0
      diffRow.value = quickQueue.value[quickIndex.value]
    } else {
      diffVisible.value = false
      ElMessage.info('本轮待审批已处理完毕')
    }
  }
  refresh()
}

// 并发抢占（409）：该单已被其他审批人处理——从快速审批队列移除并自动推进，不再停留报错
function handleDiffStale(id) {
  if (diffMode.value === 'quick') {
    quickQueue.value = quickQueue.value.filter(row => row.id !== id)
    if (quickQueue.value.length) {
      if (quickIndex.value >= quickQueue.value.length) quickIndex.value = 0
      diffRow.value = quickQueue.value[quickIndex.value]
      ElMessage.info('该单已被其他审批人处理，已切换到下一条')
    } else {
      diffVisible.value = false
      ElMessage.info('本轮待审批已处理完毕（部分单据已被他人处理）')
    }
  }
  refresh()
}

// ==================== 转办 / 加签（B2） ====================
const transferVisible = ref(false)
const transferTarget = ref('')
const addSignVisible = ref(false)
const addSignTarget = ref('')
const addSignComment = ref('')
const assignableUsers = ref([])
const actionRow = ref(null)
const actionSubmitting = ref(false)

// 下拉排除本人（后端拒绝转办/加签给自己，前端提前拦截避免无效提交）
const assignableOptions = computed(() =>
  assignableUsers.value.filter((u) => u.username !== authStore.username)
)

async function fetchAssignableUsers() {
  try {
    const data = await getAssignableUsers()
    assignableUsers.value = data?.items || data || []
  } catch (e) {
    assignableUsers.value = []
    ElMessage.warning('可转办/加签用户加载失败，请关闭弹窗后重试')
  }
}

function openTransfer(row) {
  actionRow.value = row
  transferTarget.value = ''
  transferVisible.value = true
  if (!assignableUsers.value.length) {
    fetchAssignableUsers()
  }
}

function openAddSign(row) {
  actionRow.value = row
  addSignTarget.value = ''
  addSignComment.value = ''
  addSignVisible.value = true
  if (!assignableUsers.value.length) {
    fetchAssignableUsers()
  }
}

async function confirmTransfer() {
  if (!transferTarget.value) {
    ElMessage.warning('请选择被转办人')
    return
  }
  actionSubmitting.value = true
  try {
    await transferRequest(actionRow.value.id, { targetUser: transferTarget.value })
    ElMessage.success(`已转办给 ${transferTarget.value}`)
    transferVisible.value = false
    refresh()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    actionSubmitting.value = false
  }
}

async function confirmAddSign() {
  if (!addSignTarget.value) {
    ElMessage.warning('请选择被加签人')
    return
  }
  actionSubmitting.value = true
  try {
    await addApproverRequest(actionRow.value.id, {
      targetUser: addSignTarget.value,
      comment: addSignComment.value.trim() || undefined
    })
    ElMessage.success(`已加签 ${addSignTarget.value}，或签生效：任一人审批即终态`)
    addSignVisible.value = false
    refresh()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    actionSubmitting.value = false
  }
}

function refresh() {
  if (activeTab.value === 'pending') {
    fetchPending()
  } else {
    fetchDone()
  }
}

// Tab 切换时按目标 Tab 加载对应数据（el-tabs @tab-change 传入切换后的 tabName）
function handleTabChange(tabName) {
  if (tabName === 'pending') {
    fetchPending()
  } else {
    fetchDone()
  }
}

onMounted(() => {
  const initial = readFiltersFromRoute()
  keyword.value = initial.keyword
  type.value = initial.type
  doneStatus.value = initial.doneStatus
  fetchPending()
  // 预加载已处理列表，保证首次切到"已处理"Tab 时数据已就绪
  fetchDone()
})

// 工作台/通知深链：?applicationId=xxx 直接打开审批弹窗；打开后从 URL 移除该参数，避免刷新重开
watchDashboardFocus(route, 'applicationId', 'approval', async (row) => {
  openDiff(row, 'approve')
  const query = { ...route.query }
  delete query.applicationId
  await router.replace({ query }).catch(() => {})
}, message => ElMessage.warning(message))
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.filter-bar {
  margin-top: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
.filter-keyword {
  width: 220px;
}
.filter-type,
.filter-status {
  width: 140px;
}
.load-error {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ui-danger);
}
.dialog-expert {
  font-size: 15px;
  font-weight: 600;
  color: var(--ui-text-main);
  margin-bottom: 8px;
}
.dialog-summary {
  font-size: 13px;
  color: var(--ui-text-sub);
  margin-bottom: 12px;
}
</style>
