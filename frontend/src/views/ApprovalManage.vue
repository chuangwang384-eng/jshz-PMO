<template>
  <div v-permission="'approval:audit'">
    <div class="page-head">
      <span class="page-title">审批管理</span>
      <span class="page-sub">处理员工提交的专家信息修改申请</span>
    </div>

    <el-tabs v-model="activeTab" style="margin-top: 12px" @tab-change="handleTabChange">
      <!-- 待审批 -->
      <el-tab-pane label="待审批" name="pending">
        <el-table :data="pendingList" border stripe v-loading="pendingLoading" row-key="id">
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="expand-box">
                <div class="expand-head">变更内容（旧值 -> 新值）</div>
                <div v-for="(v, k) in row.changes" :key="k" class="expand-item">
                  <span class="expand-field">{{ k }}</span>
                  <span class="expand-old">{{ oldValue(row.expertId, k) || '-' }}</span>
                  <span class="expand-arrow">→</span>
                  <span class="expand-new">{{ v ?? '' }}</span>
                </div>
                <el-empty v-if="!Object.keys(row.changes || {}).length" description="无变更内容" :image-size="60" />
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="applicant" label="申请人" width="140" />
          <el-table-column prop="expertName" label="专家姓名" width="140" />
          <el-table-column label="申请类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.requestType === 1 ? 'success' : 'info'">{{ row.requestTypeText || '修改申请' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变更内容" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ changeSummary(row.changes) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="申请原因" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createTime" label="申请时间" width="170" />
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'approval:audit'" link type="success" @click="openApprove(row)">通过</el-button>
              <el-button v-permission="'approval:audit'" link type="danger" @click="openReject(row)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
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
        <el-table :data="doneList" border stripe v-loading="doneLoading" row-key="id">
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="expand-box">
                <div class="expand-head">变更内容（旧值 -> 新值）</div>
                <div v-for="(v, k) in row.changes" :key="k" class="expand-item">
                  <span class="expand-field">{{ k }}</span>
                  <span class="expand-old">{{ oldValue(row.expertId, k) || '-' }}</span>
                  <span class="expand-arrow">→</span>
                  <span class="expand-new">{{ v ?? '' }}</span>
                </div>
                <el-empty v-if="!Object.keys(row.changes || {}).length" description="无变更内容" :image-size="60" />
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="applicant" label="申请人" width="140" />
          <el-table-column prop="expertName" label="专家姓名" width="140" />
          <el-table-column label="申请类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.requestType === 1 ? 'success' : 'info'">{{ row.requestTypeText || '修改申请' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变更内容" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ changeSummary(row.changes) }}</template>
          </el-table-column>
          <el-table-column label="审批结果" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : row.status === 3 ? 'info' : 'danger'">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approveComment" label="审批意见" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.approveComment">{{ row.approveComment }}</span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="approveTime" label="审批时间" width="170" />
        </el-table>
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

    <!-- 通过弹窗：意见可空 -->
    <el-dialog v-model="approveVisible" title="审批通过" width="480px" destroy-on-close>
      <div class="dialog-expert">专家：{{ actionRow?.expertName }}（申请人：{{ actionRow?.applicant }}）</div>
      <div class="dialog-summary">{{ changeSummary(actionRow?.changes) }}</div>
      <el-input
        v-model="approveComment"
        type="textarea"
        :rows="3"
        maxlength="200"
        placeholder="审批意见（可空）"
      />
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button v-permission="'approval:audit'" type="success" :loading="actionSubmitting" @click="confirmApprove">确认通过</el-button>
      </template>
    </el-dialog>

    <!-- 驳回弹窗：意见必填 -->
    <el-dialog v-model="rejectVisible" title="审批驳回" width="480px" destroy-on-close>
      <div class="dialog-expert">专家：{{ actionRow?.expertName }}（申请人：{{ actionRow?.applicant }}）</div>
      <div class="dialog-summary">{{ changeSummary(actionRow?.changes) }}</div>
      <el-input
        v-model="rejectComment"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="请填写驳回意见（必填）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button v-permission="'approval:audit'" type="danger" :loading="actionSubmitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getApprovalRequests, approveRequest, rejectRequest } from '../api/approval'
import { getExpert } from '../api/expert'

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

// 展开行时加载的专家旧值缓存：expertId -> {中文键: 旧值}
const oldValuesMap = ref({})

// 审批弹窗
const approveVisible = ref(false)
const rejectVisible = ref(false)
const actionRow = ref(null)
const approveComment = ref('')
const rejectComment = ref('')
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

// 已处理 = 已通过(1) + 已驳回(2) + 已撤回(3)，后端 statuses 多状态统一分页（避免各取一页合并导致总数不稳定）
async function fetchDone() {
  doneLoading.value = true
  try {
    const data = await getApprovalRequests({
      statuses: '1,2,3',
      page: donePage.value,
      size: doneSize.value
    })
    doneList.value = data?.items || []
    doneTotal.value = data?.total || 0
  } finally {
    doneLoading.value = false
  }
}

// 展开行：加载专家详情（sensitive=1 全量），记录旧值
// 新增申请单（expertId 为 null）不存在旧值，跳过加载，避免发出 GET /api/experts/null 无效请求
async function handleExpand(row, expandedRows) {
  const expanded = expandedRows.some((r) => r.id === row.id)
  if (expanded && row.expertId != null && !oldValuesMap.value[row.expertId]) {
    try {
      const expert = await getExpert(row.expertId, { sensitive: 1 })
      oldValuesMap.value = { ...oldValuesMap.value, [row.expertId]: expert || {} }
    } catch (e) {
      oldValuesMap.value = { ...oldValuesMap.value, [row.expertId]: {} }
    }
  }
}

function oldValue(expertId, key) {
  return oldValuesMap.value[expertId]?.[key] ?? ''
}

function changeSummary(changes) {
  if (!changes || !Object.keys(changes).length) return '-'
  return Object.entries(changes)
    .map(([k, v]) => `${k}: ${v ?? ''}`)
    .join('；')
}

function openApprove(row) {
  actionRow.value = row
  approveComment.value = ''
  approveVisible.value = true
}

function openReject(row) {
  actionRow.value = row
  rejectComment.value = ''
  rejectVisible.value = true
}

async function confirmApprove() {
  actionSubmitting.value = true
  try {
    await approveRequest(actionRow.value.id, { comment: approveComment.value.trim() || undefined })
    ElMessage.success('已通过')
    approveVisible.value = false
    refresh()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    actionSubmitting.value = false
  }
}

async function confirmReject() {
  if (!rejectComment.value.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  actionSubmitting.value = true
  try {
    await rejectRequest(actionRow.value.id, { comment: rejectComment.value.trim() })
    ElMessage.success('已驳回')
    rejectVisible.value = false
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
.muted {
  color: #a8abb2;
}
.expand-box {
  padding: 8px 24px;
}
.expand-head {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}
.expand-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
  font-size: 13px;
}
.expand-field {
  width: 120px;
  color: #606266;
  font-weight: 600;
}
.expand-old {
  flex: 1;
  color: #909399;
  text-decoration: line-through;
}
.expand-arrow {
  color: #909399;
}
.expand-new {
  flex: 1;
  color: #303133;
  font-weight: 600;
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
