<template>
  <div v-permission="'user:manage'">
    <div class="ui-toolbar">
      <el-input
        v-model="keyword"
        placeholder="按姓名搜索"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <div class="spacer" />
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <el-table
      :data="users"
      border
      stripe
      v-loading="loading"
      style="margin-top: 16px"
    >
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="username" label="姓名" width="180" />
      <el-table-column label="角色" min-width="200">
        <template #default="{ row }">
          <template v-if="row.roles && row.roles.length">
            <el-tag v-for="r in row.roles" :key="r" size="small" style="margin-right: 4px">{{ r }}</el-tag>
          </template>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="手机号" width="150" align="center">
        <template #default="{ row }">
          <span v-if="row.mobile">{{ maskMobile(row.mobile) }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="部门" min-width="150">
        <template #default="{ row }">
          <span v-if="row.departmentName">{{ row.departmentName }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="企微绑定" width="140" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.wecomUserId" type="success" size="small">{{ row.wecomUserId }}</el-tag>
          <el-tag v-else type="info" size="small">未绑定</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAssignRole(row)">分配角色</el-button>
          <el-button link type="danger" @click="handleResetPassword(row)">重置密码</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增用户 -->
    <el-dialog v-model="formVisible" title="新增用户" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="姓名" prop="username">
          <el-input v-model="form.username" placeholder="账号 = 员工姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色（可选）" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="assignVisible" :title="`分配角色 - ${assignRow?.username || ''}`" width="480px" destroy-on-close>
      <el-select v-model="assignRoleIds" multiple placeholder="请选择角色（可多选）" style="width: 100%">
        <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="submitAssignRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, resetPassword, deleteUser, assignUserRoles } from '../api/user'
import { listRoles } from '../api/role'

const keyword = ref('')
const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ username: '', roleIds: [], status: 1 })

const roleOptions = ref([])
const assignVisible = ref(false)
const assignRow = ref(null)
const assignRoleIds = ref([])
const assignSubmitting = ref(false)

const formRules = {
  username: [{ required: true, message: '请输入姓名（账号）', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const data = await listUsers({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value
    })
    users.value = data?.items || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function maskMobile(m) {
  if (!m) return '-'
  const s = String(m)
  return `${s.slice(0, 3)}****${s.slice(-4)}`
}

function openCreate() {
  form.username = ''
  form.roleIds = []
  form.status = 1
  formVisible.value = true
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    const created = await createUser({
      username: form.username.trim(),
      status: form.status
    })
    // 新用户角色分配（多选，RBAC）
    if (form.roleIds.length) {
      await assignUserRoles(created?.id, form.roleIds)
    }
    ElMessage.success('新增用户成功，初始密码为 111111')
    formVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

// 角色下拉：仅启用角色可选（GET /api/roles）。总经理角色只能通过组织架构任命，不在此分配。
async function fetchRoles() {
  try {
    const roles = await listRoles()
    roleOptions.value = (Array.isArray(roles) ? roles : []).filter(
      (r) => r.code !== 'GENERAL_MANAGER' && (r.status === 1 || r.status == null)
    )
  } catch (e) {
    roleOptions.value = []
  }
}

// 分配角色：打开弹窗并回显当前角色（PUT /api/users/{id}/roles）
function openAssignRole(row) {
  assignRow.value = row
  assignRoleIds.value = (row.roleIds || []).slice()
  assignVisible.value = true
}

async function submitAssignRole() {
  assignSubmitting.value = true
  try {
    await assignUserRoles(assignRow.value.id, assignRoleIds.value)
    ElMessage.success('角色分配成功')
    assignVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    assignSubmitting.value = false
  }
}

async function handleResetPassword(row) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.username}」的密码重置为 111111 吗？`,
      '重置密码',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await resetPassword(row.id)
  ElMessage.success('密码已重置为 111111')
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

onMounted(() => {
  fetchList()
  fetchRoles()
})
</script>

<style scoped>
</style>
