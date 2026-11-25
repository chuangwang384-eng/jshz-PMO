<template>
  <div>
    <div class="page-head">
      <span class="page-sub">查看我提交的全部申请及审批进度（专家修改 / 新增等）</span>
    </div>

    <!-- 筛选条（R1 2026-11-24）：状态 / 类型 / 关键词 / 提交日期，条件写回 URL 便于通知跳转与他人分享 -->
    <div class="filter-bar ui-toolbar">
      <el-select v-model="status" placeholder="全部状态" clearable class="filter-status" @change="applyFilters">
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="type" placeholder="全部类型" clearable class="filter-type" @change="applyFilters">
        <el-option v-for="opt in TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="搜索专家姓名 / 申请原因"
        clearable
        class="filter-keyword"
        @keyup.enter="applyFilters"
        @clear="applyFilters"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        class="filter-date"
        @change="applyFilters"
      />
      <el-button type="primary" @click="applyFilters">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <!-- 批量删除（2026-11-25，参照消息中心）：仅「已驳回 / 已撤回」可勾选，口径与单条删除一致 -->
      <el-button type="danger" plain :disabled="!selection.length" @click="handleBatchDelete">
        批量删除{{ selection.length ? `(${selection.length})` : '' }}
      </el-button>
    </div>

    <div v-if="loadError" class="load-error">
      <span>{{ loadError }}</span>
      <el-button link type="primary" @click="fetchList()">重试</el-button>
    </div>

    <!-- 桌面端表格 -->
    <el-table
      v-if="!isMobile"
      ref="tableRef"
      :data="requests"
      row-key="id"
      border
      stripe
      v-loading="loading"
      style="margin-top: 16px"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="46" :selectable="canDelete" />
      <el-table-column label="专家姓名" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.expertName || '-' }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="申请类型" width="150" align="center">
        <template #default="{ row }">
          <el-tag :type="row.requestType === 1 ? 'success' : 'info'">{{ row.requestTypeText || '修改申请' }}</el-tag>
          <!-- 退回修改单的原照片已被清理：提示需重传，避免申请人以为不重传也能生效 -->
          <el-tag v-if="hasExpiredPhoto(row)" type="danger" size="small" class="expired-tag">照片失效</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="approvalStatusTagType(row.status)">{{ approvalStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <!-- R2：当前处理人 + 待审批停留时长（只读派生字段，后端 fillView 计算） -->
      <el-table-column label="当前处理人" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.currentAssigneeName" :class="{ 'assignee-returned': row.status === 4 }">{{ row.currentAssigneeName }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="已等待" width="120" align="center">
        <template #default="{ row }">
          <span v-if="row.status === 0 && row.pendingHours !== null && row.pendingHours !== undefined"
                :class="pendingHoursClass(row.pendingHours)">{{ pendingText(row.pendingHours) }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="审批意见" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="commentText(row)">{{ commentText(row) }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="处理人" width="120" align="center">
        <template #default="{ row }">
          <span v-if="row.approveBy">
            {{ row.approveBy }}<span v-if="row.status === 4" class="muted">（退回）</span>
          </span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="处理时间" width="170">
        <template #default="{ row }">
          <span v-if="row.approveTime">{{ row.approveTime }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 0" link type="danger" @click="handleWithdraw(row)">撤回</el-button>
          <el-button v-if="row.status === 4" link type="warning" @click="handleResubmit(row)">重新编辑提交</el-button>
          <el-button v-if="canDelete(row)" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 移动端卡片视图（R5）：≤768px 时替代宽表格，保留全部行内操作入口 -->
    <div v-else class="card-list" v-loading="loading">
      <div v-for="row in requests" :key="row.id" class="approval-card ui-card">
        <div class="card-head">
          <el-checkbox
            :model-value="isSelected(row)"
            :disabled="!canDelete(row)"
            aria-label="选择该申请"
            @change="toggleSelect(row)"
          />
          <span class="card-title">{{ row.expertName || '-' }}</span>
          <el-tag :type="approvalStatusTagType(row.status)" size="small">{{ approvalStatusText(row.status) }}</el-tag>
        </div>
        <div class="card-row">
          <span class="card-label">类型</span>
          <span>{{ row.requestTypeText || '修改申请' }}</span>
          <el-tag v-if="hasExpiredPhoto(row)" type="danger" size="small">照片失效</el-tag>
        </div>
        <div class="card-row">
          <span class="card-label">申请时间</span>
          <span>{{ row.createTime || '-' }}</span>
        </div>
        <div v-if="row.currentAssigneeName" class="card-row">
          <span class="card-label">当前处理人</span>
          <span :class="{ 'assignee-returned': row.status === 4 }">{{ row.currentAssigneeName }}</span>
        </div>
        <div v-if="row.status === 0 && row.pendingHours !== null && row.pendingHours !== undefined" class="card-row">
          <span class="card-label">已等待</span>
          <span :class="pendingHoursClass(row.pendingHours)">{{ pendingText(row.pendingHours) }}</span>
        </div>
        <div v-if="commentText(row)" class="card-row card-row-block">
          <span class="card-label">意见</span>
          <span class="card-summary">{{ commentText(row) }}</span>
        </div>
        <div class="card-actions">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 0" size="small" type="danger" plain @click="handleWithdraw(row)">撤回</el-button>
          <el-button v-if="row.status === 4" size="small" type="warning" plain @click="handleResubmit(row)">重新编辑</el-button>
          <el-button v-if="canDelete(row)" size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="!loading && !requests.length" description="暂无申请记录" :image-size="80" />
    </div>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="PAGE_SIZES"
        :small="isMobile"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 重新编辑提交：字段 + 照片 + 原因，照片可重传（不上传则保留原申请照片） -->
    <el-dialog v-model="resubmitVisible" title="重新编辑提交" :width="fixedWidth(600)" :close-on-click-modal="false">
      <el-alert v-if="resubmitRow?.returnComment" :title="resubmitRow.returnComment" type="warning" :closable="false" />
      <el-alert
        v-if="resubmitRow?.expiredPhotoFields?.length"
        type="error"
        :closable="false"
        :title="`原申请的${resubmitRow.expiredPhotoFields.join('、')}已失效（文件已被清理），请重新上传，否则本次提交将不包含该照片`"
        style="margin-top: 8px"
      />
      <p class="page-sub dialog-tip">
        修改需要变更的内容后重新提交；清空某个字段表示撤回该字段的变更（专家档案保留原值），值未变化的字段不会覆盖专家档案。
        照片可重新上传，不上传则保留原申请照片；身份证号 / 银行卡号留空表示保留原提交值。
      </p>
      <el-form label-position="top">
        <el-form-item v-for="field in resubmitFields" :key="field" :label="field" :required="CREATE_REQUIRED_FIELDS.includes(field)">
          <el-input
            v-if="!SENSITIVE_FIELDS.includes(field)"
            v-model="resubmitChanges[field]"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            :maxlength="MAX_TEXT_LENGTH"
          />
          <template v-else>
            <el-input
              v-model="resubmitChanges[field]"
              type="password"
              show-password
              :maxlength="MAX_TEXT_LENGTH"
              :placeholder="sensitivePlaceholder(field)"
            />
            <span class="field-current">当前已提交：{{ maskedOf(resubmitRow?.changes?.[field]) }}</span>
          </template>
        </el-form-item>
        <el-form-item v-for="key in resubmitPhotoKeys" :key="key" :label="key">
          <span class="resubmit-photo-state" :class="{ 'photo-expired': isExpiredPhoto(key) }">{{ resubmitPhotoState(key) }}</span>
          <el-upload
            :show-file-list="false"
            :accept="PHOTO_ACCEPT"
            :before-upload="beforeResubmitPhoto"
            :http-request="(opt) => uploadResubmitPhoto(key, opt)"
          >
            <el-button link type="primary" :loading="!!resubmitPhotoUploading[key]">重新上传</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="申请原因">
          <el-input
            v-model="resubmitReason"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            :maxlength="MAX_REASON_LENGTH"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resubmitVisible = false">取消</el-button>
        <el-button type="primary" :loading="resubmitLoading" @click="confirmResubmit">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 申请详情抽屉（2026-11-25）：变更对比 + 审批进度时间线 + 行内操作，合并原「查看进度」「查看对比」两个弹窗 -->
    <ApprovalDiffDialog
      v-model="detailVisible"
      :row="detailRow"
      mode="view"
      variant="drawer"
      :drawer-size="isMobile ? '100%' : 'min(760px, 94vw)'"
    >
      <template #extra>
        <div v-if="detailRow" class="detail-block">
          <div class="detail-title">审批进度</div>
          <el-timeline v-if="detailRow.records?.length">
            <el-timeline-item
              v-for="record in detailRow.records"
              :key="record.id"
              :timestamp="record.createTime"
              :type="timelineType(record.action)"
              placement="top"
            >
              <div class="timeline-node">{{ record.nodeName }}</div>
              <div class="timeline-meta">操作人：{{ record.operator || '-' }}　动作：{{ actionText(record.action) }}</div>
              <div v-if="record.comment" class="timeline-comment">意见：{{ record.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="muted">暂无进度记录</div>
          <div class="detail-actions">
            <el-button v-if="detailRow.status === 0" type="danger" plain @click="handleWithdraw(detailRow)">撤回申请</el-button>
            <el-button v-if="detailRow.status === 4" type="warning" @click="resubmitFromDetail">重新编辑提交</el-button>
            <el-button v-if="canDelete(detailRow)" type="danger" plain @click="handleDelete(detailRow)">删除申请单</el-button>
          </div>
        </div>
      </template>
    </ApprovalDiffDialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMyApplications,
  withdrawRequest,
  resubmitRequest,
  uploadApprovalPhoto,
  deleteApplication,
  deleteApplications
} from '../api/approval'
import { uploadLimits, ensureUploadLimits, toMb } from '../utils/limits'
import { useNotificationStore } from '../store/notification'
import { approvalStatusTagType, approvalStatusText } from '../utils/format'
import {
  TEXT_FIELDS,
  PHOTO_FIELDS,
  SENSITIVE_FIELDS,
  CONTRACT_FIELDS,
  CREATE_REQUIRED_FIELDS,
  NAME_PATTERN,
  MAX_TEXT_LENGTH,
  MAX_REASON_LENGTH,
  PHOTO_ACCEPT,
  STATUS_OPTIONS,
  TYPE_OPTIONS,
  VALID_STATUS,
  PAGE_SIZES
} from '../utils/approvalFields'
import { watchDashboardFocus } from '../utils/dashboardFocus'
import ApprovalDiffDialog from '../components/ApprovalDiffDialog.vue'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()

const requests = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const loadError = ref('')
const tableRef = ref(null)

// ==================== 筛选条件（R1，与 URL query 双向同步） ====================
const FILTER_KEYS = ['status', 'type', 'keyword', 'startDate', 'endDate']
const isValidDate = (v) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v)

/** 从 URL query 归一化筛选条件（非法值一律丢弃，避免 NaN/非法枚举进入请求） */
function readFiltersFromRoute() {
  const q = route.query
  const rawStatus = q.status === undefined || q.status === '' ? undefined : Number(q.status)
  const statusValue = VALID_STATUS.includes(rawStatus) ? rawStatus : undefined
  const typeValue = TYPE_OPTIONS.some((opt) => opt.value === q.type) ? q.type : undefined
  return {
    status: statusValue,
    type: typeValue,
    keyword: typeof q.keyword === 'string' ? q.keyword : '',
    startDate: isValidDate(q.startDate) ? q.startDate : '',
    endDate: isValidDate(q.endDate) ? q.endDate : ''
  }
}

const initialFilters = readFiltersFromRoute()
// 状态筛选：支持从通知中心跳转携带 status（0-4），非法值直接忽略
const status = ref(initialFilters.status)
const type = ref(initialFilters.type)
const keyword = ref(initialFilters.keyword)
const dateRange = ref(
  initialFilters.startDate && initialFilters.endDate ? [initialFilters.startDate, initialFilters.endDate] : null
)

/** 当前筛选条件（统一 trim，空值归一为 undefined，便于与 URL 比较） */
function currentFilters() {
  const range = Array.isArray(dateRange.value) ? dateRange.value : []
  return {
    status: status.value ?? undefined,
    type: type.value || undefined,
    keyword: (keyword.value || '').trim() || undefined,
    startDate: range[0] || undefined,
    endDate: range[1] || undefined
  }
}

function sameFilters(a, b) {
  return (a.status ?? undefined) === (b.status ?? undefined)
    && (a.type || undefined) === (b.type || undefined)
    && (a.keyword || undefined) === (b.keyword || undefined)
    && (a.startDate || undefined) === (b.startDate || undefined)
    && (a.endDate || undefined) === (b.endDate || undefined)
}

/** 应用筛选：重置页码 → 写回 URL → 拉取列表（写 URL 触发的 watch 因条件一致不会重复请求） */
function applyFilters() {
  const filters = currentFilters()
  const query = { ...route.query }
  FILTER_KEYS.forEach((key) => delete query[key])
  if (filters.status !== undefined) query.status = String(filters.status)
  if (filters.type) query.type = filters.type
  if (filters.keyword) query.keyword = filters.keyword
  if (filters.startDate) query.startDate = filters.startDate
  if (filters.endDate) query.endDate = filters.endDate
  page.value = 1
  if (JSON.stringify(query) !== JSON.stringify(route.query)) {
    router.replace({ query }).catch(() => {})
  }
  fetchList()
}

function resetFilters() {
  status.value = undefined
  type.value = undefined
  keyword.value = ''
  dateRange.value = null
  applyFilters()
}

// URL 变化（浏览器前进/后退、通知跳转、他人分享链接）时同步本地筛选。
// 仅在本页路由内响应：离页瞬间 route.query 属于目标页，不得据此拉取列表或改写本页筛选。
watch(
  () => [route.name, route.query.status, route.query.type, route.query.keyword, route.query.startDate, route.query.endDate],
  () => {
    if (route.name !== 'my-approvals') return
    const next = readFiltersFromRoute()
    if (sameFilters(next, currentFilters())) return
    status.value = next.status
    type.value = next.type
    keyword.value = next.keyword
    dateRange.value = next.startDate && next.endDate ? [next.startDate, next.endDate] : null
    page.value = 1
    fetchList()
  }
)

// ==================== 移动端判定（R5） ====================
const isMobile = ref(false)
let mediaQuery = null
function syncMedia(event) {
  isMobile.value = event.matches
}
/** 弹窗宽度：窄屏按百分比自适应（固定 600px 在 375px 屏会溢出），宽屏沿用原像素宽度 */
function fixedWidth(px) {
  return isMobile.value ? '94%' : `${px}px`
}

// ==================== 列表 ====================
// 列表刷新驱动（2026-11-24 修正）：跟随全局消息通知 store 的 30s 轮询（store.lastPollAt），
// 不再在本页自建定时器——此前页面定时器与全局通知轮询并行，且新通知到达时页面不会立即刷新。
watch(
  () => notificationStore.lastPollAt,
  () => {
    // 轮询刷新走静默模式：不置 loading，避免表格/卡片每 30 秒闪一次遮罩
    if (!loading.value) {
      fetchList({ silent: true })
    }
  }
)

// 请求序号：仅最新一次请求可写状态，避免慢返回的旧请求覆盖新筛选结果（Q6）
let requestSeq = 0

async function fetchList(options = {}) {
  const silent = options.silent === true
  const seq = ++requestSeq
  if (!silent) loading.value = true
  loadError.value = ''
  const filters = currentFilters()
  try {
    const data = await getMyApplications({
      page: page.value,
      size: size.value,
      status: filters.status,
      type: filters.type,
      keyword: filters.keyword,
      startDate: filters.startDate,
      endDate: filters.endDate
    })
    if (seq !== requestSeq) return
    const items = data?.items || []
    requests.value = items
    total.value = data?.total || 0
    // 数据刷新后清空勾选：已选行对象可能与新列表不再对应，避免批量删除误带旧选择
    clearSelection()
    // 已打开的详情抽屉按 id 换成最新行数据（单据被处理/转办后抽屉内容随之更新）
    if (detailVisible.value && detailRow.value) {
      const fresh = items.find((item) => item.id === detailRow.value.id)
      if (fresh) detailRow.value = fresh
    }
    // 页码越界（删除/筛选后当前页为空但总数非 0）：回到最后一页重取，避免"共 N 条却空白"
    const maxPage = Math.max(1, Math.ceil(total.value / size.value))
    if (!items.length && total.value > 0 && page.value > maxPage) {
      page.value = maxPage
      fetchList()
    }
  } catch (e) {
    if (seq !== requestSeq) return
    // 失败态必须可见（此前仅全局 toast 一闪，页面停留在旧数据）；静默轮询失败保留旧数据不打扰
    if (!silent) {
      loadError.value = '申请列表加载失败，请稍后重试'
      requests.value = []
      total.value = 0
    }
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function handleSizeChange() {
  // 改变每页条数必须回到第 1 页，否则 MyBatis-Plus 会跳过越界查询返回空列表（Q7）
  page.value = 1
  fetchList()
}

function handlePageChange() {
  fetchList()
}

// ==================== 勾选 / 批量删除 ====================
const selection = ref([])

function handleSelectionChange(rows) {
  selection.value = rows || []
}

function isSelected(row) {
  return selection.value.some((item) => item.id === row.id)
}

/** 移动端卡片没有表格勾选列，手动维护勾选集合 */
function toggleSelect(row) {
  if (selection.value.some((item) => item.id === row.id)) {
    selection.value = selection.value.filter((item) => item.id !== row.id)
  } else {
    selection.value = [...selection.value, row]
  }
}

function clearSelection() {
  selection.value = []
  nextTick(() => tableRef.value?.clearSelection?.())
}

// ==================== 展示辅助 ====================
function timelineType(action) {
  if (action === 'APPROVE') return 'success'
  if (action === 'REJECT') return 'danger'
  if (action === 'WITHDRAW') return 'danger'
  if (action === 'RETURN') return 'warning'
  if (action === 'TRANSFER') return 'warning'
  return 'primary'
}

function actionText(action) {
  if (action === 'APPROVE') return '审批通过'
  if (action === 'REJECT') return '驳回'
  if (action === 'WITHDRAW') return '撤回申请'
  if (action === 'RETURN') return '退回修改'
  if (action === 'RESUBMIT') return '重新提交'
  if (action === 'TRANSFER') return '审批转办'
  if (action === 'ADD_APPROVER') return '审批加签'
  return '提交申请'
}

/** 待审批停留时长文案（R2） */
function pendingText(hours) {
  const value = Number(hours)
  if (!Number.isFinite(value) || value < 0) return '-'
  if (value < 1) return '不足 1 小时'
  if (value < 24) return `${Math.floor(value)} 小时`
  return `${Math.floor(value / 24)} 天 ${Math.floor(value % 24)} 小时`
}

/** 停留时长分级着色：≥72h 危险、≥24h 警告（与超时感知一致） */
function pendingHoursClass(hours) {
  const value = Number(hours)
  if (value >= 72) return 'pending-over'
  if (value >= 24) return 'pending-warn'
  return ''
}

function commentText(row) {
  if (row.status === 4 && row.returnComment) return `退回意见：${row.returnComment}`
  return row.approveComment || ''
}

/** 删除入口：「已通过 / 已驳回 / 已撤回」（与后端 deleteApplication / deleteApplications 口径一致；待审批/退回修改不可删） */
function canDelete(row) {
  return row.status === 1 || row.status === 2 || row.status === 3
}

/** 退回修改单的原照片已被清理任务删除（后端 expiredPhotoFields） */
function hasExpiredPhoto(row) {
  return Array.isArray(row?.expiredPhotoFields) && row.expiredPhotoFields.length > 0
}

function isExpiredPhoto(key) {
  return Array.isArray(resubmitRow.value?.expiredPhotoFields) && resubmitRow.value.expiredPhotoFields.includes(key)
}

function maskedOf(value) {
  const text = value == null ? '' : String(value).trim()
  if (!text) return '未提交'
  if (text.length <= 7) return text
  return `${text.slice(0, 3)}****${text.slice(-4)}`
}

// ==================== 详情抽屉 ====================
const detailVisible = ref(false)
const detailRow = ref(null)

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

/** 抽屉内发起重新编辑：先收起抽屉，避免弹窗与抽屉叠层 */
function resubmitFromDetail() {
  detailVisible.value = false
  handleResubmit(detailRow.value)
}

// ==================== 撤回 / 删除 ====================
async function handleWithdraw(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤回「${row.expertName}」的申请吗？撤回后该申请将不可再审批，临时照片会被清理。`,
      '撤回确认',
      {
        confirmButtonText: '确认撤回',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (e) {
    return
  }
  try {
    await withdrawRequest(row.id)
    ElMessage.success('已撤回')
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.expertName || '-'}」这张${approvalStatusText(row.status)}的申请单吗？\n删除后申请单与审批进度记录不可恢复（删除动作本身会记录在操作日志）；专家档案不受影响。`,
      '删除确认',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch (e) {
    return
  }
  try {
    await deleteApplication(row.id)
    ElMessage.success('申请单已删除')
    // 被删单据的详情抽屉一并关闭
    if (detailRow.value?.id === row.id) detailVisible.value = false
    await fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理（如并发的状态变更会返回 409）
  }
}

/** 批量删除：后端跳过不可删条目并返回实际条数，这里把「未删成」的部分明确提示出来 */
async function handleBatchDelete() {
  const rows = selection.value.filter((row) => canDelete(row))
  if (!rows.length) return
  const ids = rows.map((row) => row.id)
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${ids.length} 张申请单吗？删除后申请单与审批进度记录不可恢复；专家档案与已审批结果不受影响。`,
      '批量删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch (e) {
    return
  }
  try {
    const count = await deleteApplications(ids)
    const removed = Number(count) || 0
    if (removed < ids.length) {
      ElMessage.warning(`已删除 ${removed} 张，另有 ${ids.length - removed} 张因状态已变化未删除`)
    } else {
      ElMessage.success(`已删除 ${removed} 张`)
    }
    if (detailRow.value && ids.includes(detailRow.value.id)) detailVisible.value = false
    await fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

// ==================== 重新编辑提交 ====================
const resubmitVisible = ref(false)
const resubmitLoading = ref(false)
const resubmitRow = ref(null)
const resubmitChanges = ref({})
const resubmitFields = ref([])
const resubmitReason = ref('')
const resubmitPhotoKeys = ref([])
const resubmitPhotos = ref({})
const resubmitPhotoUploading = ref({})

function handleResubmit(row) {
  resubmitRow.value = row
  const contract = row.applicationType === 'CONTRACT'
  resubmitFields.value = contract ? [...CONTRACT_FIELDS] : [...TEXT_FIELDS]
  resubmitChanges.value = Object.fromEntries(
    resubmitFields.value.map((key) => [
      key,
      SENSITIVE_FIELDS.includes(key) ? '' : String(row.changes?.[key] ?? '')
    ])
  )
  // 历史单原因可能超过上限（旧版本提交不限长），预填时截断并提示，避免"不改就提交失败"（Q8）
  const rawReason = row.reason || ''
  resubmitReason.value = rawReason.slice(0, MAX_REASON_LENGTH)
  if (rawReason.length > MAX_REASON_LENGTH) {
    ElMessage.warning(`原申请原因超过 ${MAX_REASON_LENGTH} 字，已截断，请确认后再提交`)
  }
  resubmitPhotoKeys.value = contract ? [] : PHOTO_FIELDS
  resubmitPhotos.value = {}
  resubmitPhotoUploading.value = {}
  resubmitVisible.value = true
}

async function confirmResubmit() {
  const changes = {}
  for (const field of resubmitFields.value) {
    const raw = resubmitChanges.value[field]
    const value = typeof raw === 'string' ? raw.trim() : ''
    const original = String(resubmitRow.value.changes?.[field] ?? '').trim()
    // 敏感字段留空 = 保留原提交值
    if (SENSITIVE_FIELDS.includes(field) && !value) continue
    if (CREATE_REQUIRED_FIELDS.includes(field) && !value) {
      ElMessage.warning(`请填写${field}`)
      return
    }
    if (field === '姓名' && value && !NAME_PATTERN.test(value)) {
      ElMessage.warning('姓名格式不正确（2-20 个汉字，可含间隔号）')
      return
    }
    if (value === original) continue
    // 非必填文本字段清空 = 撤回该字段的变更：由后端从变更中移除，不写入空串
    changes[field] = value
  }
  // 照片：仅提交本次重新上传的字段（未重传的保留原申请照片）
  Object.assign(changes, resubmitPhotos.value)
  resubmitLoading.value = true
  let result
  try {
    result = await resubmitRequest(resubmitRow.value.id, { changes, reason: resubmitReason.value })
  } catch (e) {
    // 错误提示已由拦截器统一处理
    return
  } finally {
    resubmitLoading.value = false
  }
  resubmitVisible.value = false
  // 原照片临时文件已失效时后端会剔除该字段：必须告知申请人，不能静默成功
  const dropped = Array.isArray(result?.droppedPhotoFields) ? result.droppedPhotoFields : []
  if (dropped.length) {
    ElMessage.warning(`已重新提交；原申请的「${dropped.join('、')}」已失效，本次提交不包含该照片`)
  } else {
    ElMessage.success('已重新提交，等待审批')
  }
  await fetchList()
}

function sensitivePlaceholder(field) {
  return `留空则保留当前已提交值（${maskedOf(resubmitRow.value?.changes?.[field])}）`
}

// 重新上传照片：大小限制沿用后端真源 expert.upload（GET /api/meta/limits），与专家表单一致
function beforeResubmitPhoto(raw) {
  if (raw.size > uploadLimits.photoMaxBytes) {
    ElMessage.warning(`图片大小不能超过 ${toMb(uploadLimits.photoMaxBytes)}MB`)
    return false
  }
  return true
}

async function uploadResubmitPhoto(key, { file }) {
  resubmitPhotoUploading.value = { ...resubmitPhotoUploading.value, [key]: true }
  try {
    const res = await uploadApprovalPhoto(file)
    resubmitPhotos.value = { ...resubmitPhotos.value, [key]: 'TMP:' + res.fileName }
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    resubmitPhotoUploading.value = { ...resubmitPhotoUploading.value, [key]: false }
  }
}

function resubmitPhotoState(key) {
  if (resubmitPhotos.value[key]) return '已重新上传，提交后生效'
  if (isExpiredPhoto(key)) return '原照片已失效，请重新上传'
  return resubmitRow.value?.changes?.[key] ? '原申请已更换照片（不重传则保留）' : '未更换（沿用专家现有照片）'
}

// ==================== 生命周期 ====================
onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 768px)')
  isMobile.value = mediaQuery.matches
  mediaQuery.addEventListener('change', syncMedia)
  fetchList()
  ensureUploadLimits()
})

onBeforeUnmount(() => {
  if (mediaQuery) {
    mediaQuery.removeEventListener('change', syncMedia)
    mediaQuery = null
  }
})

// 工作台/通知深链：?applicationId=xxx 直接打开详情或重提弹窗。
// 打开后从 URL 移除该参数：否则筛选/刷新时会再次触发，且分享链接会带着它。
watchDashboardFocus(
  route,
  'applicationId',
  'application',
  async (row) => {
    if (row.status === 4) {
      handleResubmit(row)
    } else {
      openDetail(row)
    }
    const query = { ...route.query }
    delete query.applicationId
    await router.replace({ query }).catch(() => {})
  },
  (message) => ElMessage.warning(message)
)
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
.filter-status,
.filter-type {
  width: 140px;
}
.filter-keyword {
  width: 220px;
}
.filter-date {
  width: 250px;
}
.expired-tag {
  margin-left: 4px;
}
.load-error {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ui-danger);
}
.timeline-node {
  font-size: 14px;
  font-weight: 600;
  color: var(--ui-text-main);
}
.timeline-meta {
  font-size: 12px;
  color: var(--ui-text-muted);
  margin-top: 4px;
}
.timeline-comment {
  font-size: 13px;
  color: var(--ui-text-sub);
  margin-top: 4px;
}
.detail-block {
  margin-top: 20px;
}
.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ui-text-main);
  margin-bottom: 14px;
}
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.resubmit-photo-state {
  font-size: 13px;
  color: var(--ui-text-muted);
  margin-right: 12px;
}
.photo-expired {
  color: var(--ui-danger);
}
.field-current {
  font-size: 12px;
  color: var(--ui-text-muted);
  margin-top: 2px;
}
.assignee-returned {
  color: var(--ui-warning);
}
.pending-warn {
  color: var(--ui-warning);
}
.pending-over {
  color: var(--ui-danger);
  font-weight: 600;
}
.dialog-tip {
  margin: 12px 0;
}
/* 移动端卡片视图（R5） */
.card-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
}
.approval-card {
  padding: 12px 14px;
  border-radius: var(--ui-radius-md);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.card-title {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ui-text-main);
}
.card-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: var(--ui-text-sub);
  margin-bottom: 4px;
}
.card-row-block {
  align-items: flex-start;
}
.card-label {
  color: var(--ui-text-muted);
  flex: 0 0 76px;
}
.card-summary {
  flex: 1;
  min-width: 0;
  word-break: break-all;
}
.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--ui-border-weak);
}
.card-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
@media (max-width: 768px) {
  /* 选择器带 .filter-bar 前缀，特异性高于上面的 .filter-* 定宽规则，无需 !important */
  .filter-bar :deep(.el-select),
  .filter-bar :deep(.el-date-editor) {
    width: 100%;
  }
  .filter-keyword {
    width: 100%;
  }
  .page-head {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
