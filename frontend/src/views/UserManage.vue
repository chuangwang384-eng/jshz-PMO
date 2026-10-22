<template>
  <div>
    <div class="toolbar">
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
      <el-table-column label="是否管理员" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isAdmin ? 'danger' : 'info'">
            {{ row.isAdmin ? '管理员' : '员工' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.isAdmin"
            link
            type="primary"
            @click="handleSetAdmin(row, true)"
          >
            设为管理员
          </el-button>
          <el-button
            v-if="row.isAdmin"
            link
            type="warning"
            @click="handleSetAdmin(row, false)"
          >
            取消管理员
          </el-button>
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
        <el-form-item label="管理员">
          <el-switch v-model="form.isAdmin" active-text="是" inactive-text="否" />
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, updateUser, resetPassword, deleteUser } from '../api/user'

const keyword = ref('')
const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ username: '', isAdmin: false, status: 1 })

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

function openCreate() {
  form.username = ''
  form.isAdmin = false
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
    await createUser({
      username: form.username.trim(),
      isAdmin: form.isAdmin ? 1 : 0,
      status: form.status
    })
    ElMessage.success('新增用户成功，初始密码为 111111')
    formVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleSetAdmin(row, isAdmin) {
  const action = isAdmin ? '设为管理员' : '取消管理员'
  try {
    await ElMessageBox.confirm(
      `确定将「${row.username}」${action}吗？`,
      action,
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await updateUser(row.id, {
    username: row.username,
    isAdmin: isAdmin ? 1 : 0,
    status: row.status
  })
  ElMessage.success(`${action}成功`)
  fetchList()
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

onMounted(fetchList)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
}
.spacer {
  flex: 1;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
