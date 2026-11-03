<template>
  <div class="my-todo">
    <!-- 统计卡 -->
    <el-row :gutter="12" class="todo-stats">
      <el-col :span="8">
        <div class="stat ui-card"><div class="stat-label">待办</div><div class="stat-value">{{ stat.todoCount ?? 0 }}</div></div>
      </el-col>
      <el-col :span="8">
        <div class="stat ui-card"><div class="stat-label">近 3 天到期</div><div class="stat-value stat-warn">{{ stat.nearDueCount ?? 0 }}</div></div>
      </el-col>
      <el-col :span="8">
        <div class="stat ui-card"><div class="stat-label">已过期</div><div class="stat-value stat-danger">{{ stat.overdueCount ?? 0 }}</div></div>
      </el-col>
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="rows" stripe size="small" class="todo-table">
      <el-table-column label="事项" min-width="200">
        <template #default="{ row }">
          <div class="todo-title">
            {{ row.title }}
            <el-tag v-if="row.source && row.source.includes('责任人')" size="small" type="warning" effect="plain">责任人</el-tag>
            <el-tag v-if="row.blockReason" size="small" type="danger">阻塞</el-tag>
          </div>
          <div class="todo-sub" :title="row.content">{{ row.content || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="documentTitle" label="来源文档" min-width="140" show-overflow-tooltip />
      <el-table-column label="阶段" width="90">
        <template #default="{ row }">{{ stageText(row.stage) }}</template>
      </el-table-column>
      <el-table-column label="优先级" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.priority === 'HIGH' ? 'danger' : row.priority === 'MEDIUM' ? 'warning' : 'info'">
            {{ prioText(row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="timeNote" label="时间要求" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.timeNote || '-' }}</template>
      </el-table-column>
      <el-table-column label="到期" width="110">
        <template #default="{ row }">
          <span :class="{ 'due-over': row.overdue, 'due-near': row.nearDue }">{{ row.dueDate || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="我的进度" width="130">
        <template #default="{ row }">
          <el-select
            :model-value="row.myProgress"
            size="small"
            style="width: 110px"
            @change="(v) => $emit('change-progress', row, v)"
          >
            <el-option label="未处理" value="UNPROCESSED" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="$emit('edit-remark', row)">备注</el-button>
          <el-button link type="warning" size="small" @click="$emit('set-reminder', row)">提醒</el-button>
          <el-button v-if="row.blockReason" link type="danger" size="small" @click="$emit('set-block', row)">阻塞</el-button>
          <el-button v-else link type="danger" size="small" @click="$emit('set-block', row)">阻塞?</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无待办：收藏企业合规文档或被设为事项责任人后，待办将自动出现在这里" :image-size="70" />
  </div>
</template>

<script setup>
defineProps({
  stat: { type: Object, default: () => ({}) },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
defineEmits(['change-progress', 'edit-remark', 'set-reminder', 'set-block'])

function stageText(s) {
  return {
    INITIATION: '立项', FINANCE: '财务流程', EXECUTION: '执行',
    VERIFICATION: '会后核销', SPECIAL: '特殊问题', OTHER: '其他'
  }[s] || s
}
function prioText(p) {
  return { HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || p
}
</script>

<style scoped>
.todo-stats { margin-bottom: 12px; }
.stat { padding: 12px 16px; text-align: center; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 22px; font-weight: 700; color: #303133; margin-top: 4px; }
.stat-warn { color: #E6A23C; }
.stat-danger { color: #F56C6C; }
.todo-title { font-weight: 600; color: #303133; display: flex; align-items: center; gap: 6px; }
.todo-sub { font-size: 12px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.due-over { color: #F56C6C; font-weight: 600; }
.due-near { color: #E6A23C; }
</style>
