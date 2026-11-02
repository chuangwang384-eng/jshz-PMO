<template>
  <div v-permission="'audit:view'">
    <div class="page-head">
      <span class="page-title">用户操作日志</span>
      <span class="page-sub">下载导出、专家/文档变更、登录与权限等敏感操作留痕（含历史下载记录）</span>
    </div>

    <div class="toolbar">
      <el-input
        v-model="filters.username"
        placeholder="用户名（模糊）"
        clearable
        style="width: 180px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="filters.actionType"
        placeholder="操作类型"
        clearable
        filterable
        style="width: 180px; margin-left: 8px"
        @change="handleSearch"
      >
        <el-option v-for="(label, type) in actionTypeMap" :key="type" :label="label" :value="type" />
      </el-select>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="操作开始时间"
        end-placeholder="操作结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="margin-left: 8px"
      />
      <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <el-table
      :data="records"
      border
      stripe
      v-loading="loading"
      style="margin-top: 16px"
    >
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column label="操作类型" width="150" align="center">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.actionType)">{{ actionTypeMap[row.actionType] || row.actionType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="actionDesc" label="操作对象 / 描述" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">{{ row.actionDesc || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="操作时间" width="180" />
      <el-table-column prop="ip" label="IP" width="160">
        <template #default="{ row }">{{ row.ip || '-' }}</template>
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listOperations } from '../api/audit'

// 操作类型枚举（与后端 OperationLogService.ACTION_* 常量一致）
const actionTypeMap = {
  DOWNLOAD: '下载 / 导出',
  LOGIN: '登录',
  LOGOUT: '登出',
  CHANGE_PASSWORD: '修改密码',
  RESET_PASSWORD: '重置密码',
  CREATE_USER: '新增用户',
  UPDATE_USER: '修改用户',
  DELETE_USER: '删除用户',
  ASSIGN_ROLES: '分配角色',
  ROLE_PERMISSION: '角色权限变更',
  CREATE_EXPERT: '新增专家',
  UPDATE_EXPERT: '修改专家',
  DELETE_EXPERT: '删除专家',
  IMPORT_EXPERT: '导入专家',
  CREATE_DOC: '上传文档',
  UPDATE_DOC: '更新文档',
  OFF_SHELF_DOC: '下架文档',
  DELETE_DOC: '永久删除文档',
  RELIST_DOC: '文档重新上架',
  WECOM_SYNC: '企微同步',
  WECOM_BIND: '企微绑定',
  // 项目管理
  CREATE_PROJECT: '创建项目',
  UPDATE_PROJECT: '修改项目',
  DELETE_PROJECT: '删除项目',
  CANCEL_PROJECT: '取消项目',
  CHANGE_PHASE: '阶段变更',
  ASSIGN_EXPERT: '调派专家',
  REMOVE_EXPERT: '移出专家',
  ASSIGN_SUPPLIER: '关联供应商',
  REMOVE_SUPPLIER: '移出供应商',
  UPLOAD_DOC: '上传资料',
  CREATE_CONTRACT: '创建合同',
  UPDATE_CONTRACT: '更新合同',
  SYNC_EXPERT_SENSITIVE: '专家敏感信息同步',
  CREATE_TEMPLATE: '创建模板',
  REGISTER_BUDGET: '登记预算',
  SUBMIT_CONTRACT_APPROVAL: '提交合同审批',
  // 公告
  CREATE_ANNOUNCEMENT: '发布公告',
  UPDATE_ANNOUNCEMENT: '修改公告',
  DELETE_ANNOUNCEMENT: '删除公告',
  ONLINE_ANNOUNCEMENT: '公告上架',
  OFFLINE_ANNOUNCEMENT: '公告下架',
  TOP_ANNOUNCEMENT: '公告置顶',
  // 供应商（AOP 注解埋点）
  SUPPLIER_CREATE: '新增供应商',
  SUPPLIER_UPDATE: '修改供应商',
  SUPPLIER_DELETE: '删除供应商',
  SAVE_SUPPLIER_PRICE: '保存行价',
  BIND_SUPPLIER: '绑定供应商',
  UNBIND_SUPPLIER: '解绑供应商'
}

function actionTagType(actionType) {
  if (actionType === 'LOGIN' || actionType === 'CREATE_USER' || actionType === 'CREATE_EXPERT' || actionType === 'CREATE_DOC'
    || actionType === 'RELIST_DOC' || actionType === 'CREATE_PROJECT' || actionType === 'CREATE_CONTRACT'
    || actionType === 'CREATE_TEMPLATE' || actionType === 'CREATE_ANNOUNCEMENT' || actionType === 'ONLINE_ANNOUNCEMENT'
    || actionType === 'SUPPLIER_CREATE' || actionType === 'BIND_SUPPLIER' || actionType === 'ASSIGN_EXPERT'
    || actionType === 'ASSIGN_SUPPLIER') {
    return 'success'
  }
  if (actionType === 'DELETE_USER' || actionType === 'DELETE_EXPERT' || actionType === 'DELETE_DOC'
    || actionType === 'ROLE_PERMISSION' || actionType === 'DELETE_PROJECT' || actionType === 'CANCEL_PROJECT'
    || actionType === 'REMOVE_EXPERT' || actionType === 'REMOVE_SUPPLIER' || actionType === 'DELETE_ANNOUNCEMENT'
    || actionType === 'SUPPLIER_DELETE' || actionType === 'UNBIND_SUPPLIER') {
    return 'danger'
  }
  if (actionType === 'DOWNLOAD' || actionType === 'LOGOUT' || actionType === 'WECOM_SYNC' || actionType === 'WECOM_BIND'
    || actionType === 'CHANGE_PHASE' || actionType === 'SUBMIT_CONTRACT_APPROVAL' || actionType === 'SYNC_EXPERT_SENSITIVE'
    || actionType === 'OFFLINE_ANNOUNCEMENT') {
    return 'info'
  }
  return 'warning'
}

const filters = ref({
  username: '',
  actionType: ''
})
const timeRange = ref(null)
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await listOperations({
      username: filters.value.username || undefined,
      actionType: filters.value.actionType || undefined,
      startTime: timeRange.value?.[0] || undefined,
      endTime: timeRange.value?.[1] || undefined,
      page: page.value,
      size: size.value
    })
    records.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function resetSearch() {
  filters.value.username = ''
  filters.value.actionType = ''
  timeRange.value = null
  page.value = 1
  fetchList()
}

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
.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 0;
  margin-top: 12px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
