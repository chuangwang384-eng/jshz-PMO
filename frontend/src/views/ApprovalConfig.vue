<template>
  <div>
    <div class="page-head">
      <span class="page-sub">配置各审批流的启用状态、流程名称与审批人</span>
    </div>

    <el-tabs v-model="activeFlowKey" type="card" class="flow-tabs" @tab-change="onFlowChange">
      <el-tab-pane
        v-for="f in flowList"
        :key="f.flowKey"
        :label="f.flowName || f.flowKey"
        :name="f.flowKey"
      />
    </el-tabs>

    <el-card v-loading="loading" style="max-width: 640px; margin-top: 16px">
      <el-form label-width="110px">
        <el-form-item label="流程标识">
          <span class="flow-key">{{ current?.flowKey || '-' }}</span>
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="form.flowName" placeholder="请输入流程名称" maxlength="32" />
        </el-form-item>
        <el-form-item label="启用审批流">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          <div class="form-tip">停用后员工将无法提交该流程的申请</div>
        </el-form-item>
        <el-form-item label="审批人类型">
          <el-radio-group v-model="form.approverType">
            <el-radio label="ROLE">指定角色</el-radio>
            <el-radio label="USER">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.approverType === 'ROLE'">
          <el-form-item label="审批人角色">
            <el-select v-model="form.approverRole" style="width: 100%" placeholder="请选择审批人角色">
              <el-option
                v-for="r in roleOptions"
                :key="r.code"
                :label="`${r.name}（${r.code}）`"
                :value="r.code"
              />
            </el-select>
            <div class="form-tip">仅拥有该角色的用户可审批该流程的申请</div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="具体审批人">
            <el-select
              v-model="form.approverUsernames"
              multiple
              filterable
              style="width: 100%"
              placeholder="请选择审批人"
            >
              <el-option
                v-for="u in assignableUsers"
                :key="u.username"
                :label="`${u.name || u.username}（${u.username}）`"
                :value="u.username"
              />
            </el-select>
            <div class="form-tip">任一选中的用户均可审批该流程的申请（或签）</div>
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getApprovalConfigList,
  getApprovalConfig,
  updateApprovalConfig,
  getConfigUsers,
  saveConfigUsers,
  getAssignableUsers
} from '../api/approval'
import { listRoles } from '../api/role'

// 流程列表（后端多审批流配置，如 EXPERT / CONTRACT）
const flowList = ref([])
const activeFlowKey = ref('')
const current = ref(null)

// 当前流程编辑表单
const form = reactive({
  flowKey: '',
  flowName: '',
  enabled: false,
  approverType: 'ROLE',
  approverRole: '',
  approverUsernames: []
})

const roleOptions = ref([])
const assignableUsers = ref([])
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

// 具体审批人候选：可转办/加签用户列表（GET /approval/assignable-users）
async function fetchAssignableUsers() {
  try {
    const users = await getAssignableUsers()
    assignableUsers.value = Array.isArray(users) ? users : []
  } catch (e) {
    assignableUsers.value = []
  }
}

// 加载流程列表并默认选中第一个
async function fetchFlows() {
  loading.value = true
  try {
    const list = await getApprovalConfigList()
    flowList.value = Array.isArray(list) ? list : []
    if (!flowList.value.length) {
      current.value = null
      activeFlowKey.value = ''
      return
    }
    if (!activeFlowKey.value || !flowList.value.some((f) => f.flowKey === activeFlowKey.value)) {
      activeFlowKey.value = flowList.value[0].flowKey
    }
    await loadFlow(activeFlowKey.value)
  } finally {
    loading.value = false
  }
}

// 加载单个流程配置 + 具体审批人回显
async function loadFlow(flowKey) {
  loading.value = true
  try {
    const cfg = await getApprovalConfig(flowKey)
    current.value = cfg
    form.flowKey = cfg?.flowKey || flowKey
    form.flowName = cfg?.flowName || flowKey
    form.enabled = cfg?.enabled === 1
    form.approverType = cfg?.approverType === 'USER' ? 'USER' : 'ROLE'
    form.approverRole = cfg?.approverRole || ''
    form.approverUsernames = []
    if (form.approverType === 'USER') {
      const users = await getConfigUsers(flowKey)
      form.approverUsernames = (Array.isArray(users) ? users : [])
        .map((u) => u.username)
        .filter(Boolean)
    }
  } finally {
    loading.value = false
  }
}

// Tab 切换：加载对应流程配置
function onFlowChange(key) {
  if (key) loadFlow(key)
}

async function save() {
  if (form.approverType === 'USER' && (!form.approverUsernames || !form.approverUsernames.length)) {
    ElMessage.warning('请至少选择一名具体审批人')
    return
  }
  if (form.approverType === 'ROLE' && !form.approverRole) {
    ElMessage.warning('请选择审批人角色')
    return
  }
  saving.value = true
  try {
    await updateApprovalConfig({
      flowKey: form.flowKey,
      flowName: form.flowName,
      enabled: form.enabled ? 1 : 0,
      approverType: form.approverType,
      approverRole: form.approverType === 'ROLE' ? form.approverRole : ''
    })
    if (form.approverType === 'USER') {
      await saveConfigUsers(form.flowKey, form.approverUsernames)
    }
    ElMessage.success('审批流配置已保存')
    await loadFlow(form.flowKey)
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchRoles()
  fetchAssignableUsers()
  fetchFlows()
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.form-tip {
  font-size: 12px;
  color: var(--ui-text-muted);
  line-height: 1.4;
  margin-top: 4px;
}
.flow-tabs {
  margin-top: 12px;
}
.flow-key {
  font-weight: 600;
  color: var(--ui-text-main);
}
</style>
