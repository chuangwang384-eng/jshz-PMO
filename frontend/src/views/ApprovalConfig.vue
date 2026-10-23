<template>
  <div>
    <div class="page-head">
      <span class="page-title">审批流配置</span>
      <span class="page-sub">配置专家信息修改审批流的启用状态与审批人角色</span>
    </div>

    <el-card v-loading="loading" style="max-width: 520px; margin-top: 16px">
      <el-form label-width="110px">
        <el-form-item label="流程名称">
          <span>{{ config?.flowName || '专家信息修改审批' }}</span>
        </el-form-item>
        <el-form-item label="启用审批流">
          <el-switch v-model="enabled" active-text="启用" inactive-text="停用" />
          <div class="form-tip">停用后员工将无法提交专家信息修改申请</div>
        </el-form-item>
        <el-form-item label="审批人角色">
          <el-select v-model="approverRole" style="width: 100%" placeholder="请选择审批人角色">
            <el-option
              v-for="r in roleOptions"
              :key="r.code"
              :label="`${r.name}（${r.code}）`"
              :value="r.code"
            />
          </el-select>
          <div class="form-tip">仅拥有该角色的用户可审批员工提交的修改申请</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getApprovalConfig, updateApprovalConfig } from '../api/approval'
import { listRoles } from '../api/role'

const config = ref(null)
const enabled = ref(false)
const approverRole = ref('ADMIN')
const roleOptions = ref([])
const loading = ref(false)
const saving = ref(false)

// 审批人角色下拉：动态加载角色列表（GET /api/roles），仅启用角色可选
async function fetchRoles() {
  try {
    const roles = await listRoles()
    roleOptions.value = (Array.isArray(roles) ? roles : []).filter(
      (r) => r.status === 1 || r.status == null
    )
  } catch (e) {
    roleOptions.value = []
  }
}

async function fetchConfig() {
  loading.value = true
  try {
    config.value = await getApprovalConfig()
    enabled.value = config.value?.enabled === 1
    approverRole.value = config.value?.approverRole || 'ADMIN'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await updateApprovalConfig({
      enabled: enabled.value ? 1 : 0,
      approverRole: approverRole.value
    })
    ElMessage.success('审批流配置已保存')
    fetchConfig()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchConfig()
  fetchRoles()
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
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
