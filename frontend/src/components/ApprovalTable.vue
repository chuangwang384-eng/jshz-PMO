<template>
  <el-table
    :data="rows"
    border
    stripe
    v-loading="loading"
    row-key="id"
    @expand-change="(row, expandedRows) => emit('expand', row, expandedRows)"
  >
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
    <el-table-column v-if="mode === 'pending'" label="当前审批人" width="120" align="center">
      <template #default="{ row }">
        <el-tag v-if="row.currentAssignee" type="warning" effect="plain">{{ row.currentAssignee }}</el-tag>
        <span v-else class="muted">未指定</span>
      </template>
    </el-table-column>
    <el-table-column label="申请类型" width="130" align="center">
      <template #default="{ row }">
        <el-tag :type="row.requestType === 1 ? 'success' : 'info'">{{ row.requestTypeText || '修改申请' }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="变更内容" min-width="240" show-overflow-tooltip>
      <template #default="{ row }">{{ changeSummary(row.changes) }}</template>
    </el-table-column>
    <el-table-column v-if="mode === 'pending'" prop="reason" label="申请原因" min-width="180" show-overflow-tooltip />
    <el-table-column prop="createTime" label="申请时间" width="170" />
    <template v-if="mode === 'done'">
      <el-table-column label="审批结果" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="approvalStatusTagType(row.status)">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approveComment" label="审批意见" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.approveComment">{{ row.approveComment }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="approveTime" label="审批时间" width="170" />
    </template>
    <el-table-column v-if="mode === 'pending'" label="操作" width="220" align="center" fixed="right">
      <template #default="{ row }">
        <el-button v-permission="'approval:audit'" link type="success" @click="emit('approve', row)">通过</el-button>
        <el-button v-permission="'approval:audit'" link type="danger" @click="emit('reject', row)">驳回</el-button>
        <el-button
          v-if="ownedByMe(row)"
          v-permission="'approval:audit'"
          link
          type="warning"
          @click="emit('transfer', row)"
        >转办</el-button>
        <el-button
          v-if="ownedByMe(row) && !row.extraApprover"
          v-permission="'approval:audit'"
          link
          type="primary"
          @click="emit('addsign', row)"
        >加签</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { approvalStatusTagType, changeSummary } from '../utils/format'

// 审批管理共用表格组件：mode=pending（待审：当前审批人/原因/操作列）| done（已处理：结果/意见/时间列）
// 展开行旧值经 oldValues 注入，展开事件上抛由父组件懒加载专家详情
const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  oldValues: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'pending' },
  currentUsername: { type: String, default: '' }
})

const emit = defineEmits(['expand', 'approve', 'reject', 'transfer', 'addsign'])

function oldValue(expertId, key) {
  return props.oldValues?.[expertId]?.[key] ?? ''
}

// 转办/加签仅对"未指定审批人或当前审批人是本人"的行可见（与后端校验一致）
function ownedByMe(row) {
  return !row.currentAssignee || row.currentAssignee === props.currentUsername
}
</script>

<style scoped>
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
</style>
