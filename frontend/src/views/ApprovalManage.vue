<template>
  <div v-permission="'approval:audit'">
    <div class="page-head">
      <span class="page-sub">处理员工提交的专家信息修改申请</span>
    </div>

    <el-tabs v-model="activeTab" style="margin-top: 12px" @tab-change="handleTabChange">
      <!-- 待审批 -->
      <el-tab-pane label="待审批" name="pending">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="加签采用或签语义：您与被加签人任一人审批（通过/驳回）即生效；转办后仅被转办人可审批。"
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
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchPending"
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
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchDone"
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
    />

    <!-- 转办弹窗：选人（仅列出有审批权限的用户） -->
    <el-dialog v-model="transferVisible" title="审批转办" width="480px" destroy-on-close>
      <div class="dialog-expert">专家：{{ actionRow?.expertName }}（申请人：{{ actionRow?.applicant }}）</div>
      <div class="dialog-summary">转办后仅被转办人可审批该申请单，请在下方选择接收人。</div>
      <el-select
        v-model="transferTarget"
        filterable
        placeholder="选择被转办人（仅列出有审批权限的用户）"
        style="width: 100%"
      >
        <el-option v-for="u in assignableUsers" :key="u.id" :label="u.username" :value="u.username" />
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
        <el-option v-for="u in assignableUsers" :key="u.id" :label="u.username" :value="u.username" />
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
import { useRoute } from 'vue-router'
import { watchDashboardFocus } from '../utils/dashboardFocus'
const route = useRoute()
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getApprovalRequests,
  transferRequest,
  addApproverRequest,
  getAssignableUsers
} from '../api/approval'
import { useAuthStore } from '../store/auth'
import ApprovalTable from '../components/ApprovalTable.vue'
import ApprovalDiffDialog from '../components/ApprovalDiffDialog.vue'

const authStore = useAuthStore()

const activeTab = ref('pending')

// 待审批
const pendingList = ref([])
const pendingTotal = ref(0)
const pendingPage = ref(1)
const pendingSize = ref(20)
const pendingLoading = ref(false)

// 已处理
const doneList = ref([])
const doneTotal = ref(0)
const donePage = ref(1)
const doneSize = ref(20)
const doneLoading = ref(false)

// 变更前后对比弹窗（view/approve/reject/return 四态）
const diffVisible = ref(false)
const diffRow = ref(null)
const diffMode = ref('view')
const quickQueue = ref([])
const quickIndex = ref(0)
const quickLoading = ref(false)

// 转办 / 加签（B2）
const transferVisible = ref(false)
const transferTarget = ref('')
const addSignVisible = ref(false)
const addSignTarget = ref('')
const addSignComment = ref('')
const assignableUsers = ref([])
const actionRow = ref(null)
const actionSubmitting = ref(false)

async function fetchPending() {
  pendingLoading.value = true
  try {
    const data = await getApprovalRequests({
      status: 0,
      page: pendingPage.value,
      size: pendingSize.value
    })
    pendingList.value = data?.items || []
    pendingTotal.value = data?.total || 0
  } finally {
    pendingLoading.value = false
  }
}

// 已处理 = 已通过(1) + 已驳回(2) + 已撤回(3) + 已退回修改(4)，后端 statuses 多状态统一分页（避免各取一页合并导致总数不稳定）
async function fetchDone() {
  doneLoading.value = true
  try {
    const data = await getApprovalRequests({
      statuses: '1,2,3,4',
      page: donePage.value,
      size: doneSize.value
    })
    doneList.value = data?.items || []
    doneTotal.value = data?.total || 0
  } finally {
    doneLoading.value = false
  }
}

// 打开变更前后对比弹窗（默认只读 view；通过/驳回/退回修改经对应 mode 进入）
function openDiff(row, mode = 'view') {
  diffRow.value = row
  diffMode.value = mode
  diffVisible.value = true
}

async function openQuickApproval() {
  if (quickLoading.value) return
  quickLoading.value = true
  const size = 100
  const username = authStore.username
  const queue = new Map()
  try {
    let page = 1
    let total
    do {
      const result = await getApprovalRequests({ status: 0, page, size })
      total = result?.total || 0
      for (const row of result?.items || []) {
        if (row.status === 0 && (!row.currentAssignee || row.currentAssignee === username || row.extraApprover === username)) {
          queue.set(row.id, row)
        }
      }
    } while (page++ * size < total)
    if (activeTab.value !== 'pending') return
    if (total <= 3 || !queue.size) {
      ElMessage.info(total <= 3 ? '待审批超过 3 条时可使用快速审批' : '当前没有可处理的待审批')
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

// ==================== 转办 / 加签（B2） ====================

async function fetchAssignableUsers() {
  try {
    const data = await getAssignableUsers()
    assignableUsers.value = data?.items || data || []
  } catch (e) {
    assignableUsers.value = []
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
  fetchPending()
  // 预加载已处理列表，保证首次切到"已处理"Tab 时数据已就绪
  fetchDone()
})
watchDashboardFocus(route, 'applicationId', 'approval', row => openDiff(row, 'approve'), message => ElMessage.warning(message))
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
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.dialog-expert {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.dialog-summary {
  font-size: 13px;
  color: #606266;
  margin-bottom: 12px;
}
</style>
