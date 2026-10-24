<template>
  <div>
    <div class="page-head">
      <span class="page-title">我的审批</span>
      <span class="page-sub">查看我提交的专家信息修改申请及审批进度</span>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="handleStatusChange">
        <el-option label="待审批" :value="0" />
        <el-option label="已通过" :value="1" />
        <el-option label="已驳回" :value="2" />
      </el-select>
    </div>

    <el-table :data="requests" border stripe v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="expertName" label="专家姓名" width="140" />
      <el-table-column label="申请类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.requestType === 1 ? 'success' : 'info'">{{ row.requestTypeText || '修改申请' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="变更摘要" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">{{ changeSummary(row.changes) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approveComment" label="审批意见" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.approveComment">{{ row.approveComment }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openProgress(row)">查看进度</el-button>
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
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>

    <!-- 审批进度：时间线 -->
    <el-dialog v-model="progressVisible" title="审批进度" width="560px" destroy-on-close>
      <div class="progress-expert">专家：{{ progressRow?.expertName }}</div>
      <el-timeline v-if="progressRow?.records?.length">
        <el-timeline-item
          v-for="record in progressRow.records"
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
      <el-empty v-else description="暂无进度记录" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMyApprovalRequests } from '../api/approval'

const route = useRoute()
const router = useRouter()

const requests = ref([])
// 状态筛选：读取 URL query.status（0=待审批 1=已通过 2=已驳回），支持从通知中心跳转携带状态
const status = ref(
  route.query.status !== undefined && route.query.status !== '' ? Number(route.query.status) : undefined
)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const progressVisible = ref(false)
const progressRow = ref(null)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyApprovalRequests({
      page: page.value,
      size: size.value,
      status: status.value !== undefined && status.value !== '' ? status.value : undefined
    })
    requests.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

// 变更摘要：字段: 新值 逗号连接
function changeSummary(changes) {
  if (!changes || !Object.keys(changes).length) return '-'
  return Object.entries(changes)
    .map(([k, v]) => `${k}: ${v ?? ''}`)
    .join('；')
}

function statusTagType(status) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

function timelineType(action) {
  if (action === 'APPROVE') return 'success'
  if (action === 'REJECT') return 'danger'
  return 'primary'
}

function actionText(action) {
  if (action === 'APPROVE') return '审批通过'
  if (action === 'REJECT') return '驳回'
  return '提交申请'
}

function openProgress(row) {
  progressRow.value = row
  progressVisible.value = true
}

function handleStatusChange() {
  const q = { ...route.query }
  if (status.value === undefined || status.value === '') {
    delete q.status
  } else {
    q.status = String(status.value)
  }
  router.replace({ query: q })
  page.value = 1
  fetchList()
}

// URL query.status 变化（浏览器前进/后退或通知跳转）时同步本地筛选
watch(
  () => route.query.status,
  (val) => {
    const next = val === undefined || val === '' ? undefined : Number(val)
    if (next !== status.value) {
      status.value = next
      page.value = 1
      fetchList()
    }
  }
)

onMounted(fetchList)
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
.filter-bar {
  margin-top: 12px;
}
.progress-expert {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}
.timeline-node {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.timeline-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.timeline-comment {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
}
</style>
