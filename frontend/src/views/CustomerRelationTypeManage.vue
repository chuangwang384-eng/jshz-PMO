<template>
  <div class="relation-type-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>客户关系类型管理</span>
          <div class="header-right">
            <el-button @click="goBack">返回列表</el-button>
            <el-button v-permission="'customer:manage'" type="primary" @click="openCreate">新增自定义类型</el-button>
          </div>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon class="tip">
        关系类型采用「预置枚举 + 维护人可扩展」：预置类型（介绍关系、上下级、同事、业务伙伴、亲友等）不可删除；自定义类型可停用，停用后不可用于新建关系，历史关系仍保留展示。
      </el-alert>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="类型名称" min-width="150" />
        <el-table-column prop="code" label="编码" min-width="160" show-overflow-tooltip />
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag :type="row.builtin === 1 ? 'success' : 'info'" size="small">{{ row.builtin === 1 ? '预置' : '自定义' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="110" />
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="canManage">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 1" link type="warning" size="small" @click="handleToggle(row)">停用</el-button>
              <el-button v-else link type="success" size="small" @click="handleToggle(row)">启用</el-button>
            </template>
            <el-tag v-else type="info" size="small">只读</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && rows.length === 0" description="暂无关系类型" />
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑关系类型' : '新增自定义关系类型'" width="460px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="如：供应商关系、战略合作" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import { listRelationTypes, createRelationType, updateRelationType } from '../api/customer'

const router = useRouter()
const { proxy } = getCurrentInstance()
const authStore = useAuthStore()
const canManage = computed(() => authStore.hasPermission('customer:manage'))

const loading = ref(false)
const rows = ref([])

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const emptyForm = { id: null, name: '' }
const form = reactive({ ...emptyForm })
const rules = {
  name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }]
}

async function loadTypes() {
  loading.value = true
  try {
    const res = await listRelationTypes()
    rows.value = res || []
  } catch (e) {
    proxy.$message.error(e?.message || '加载关系类型失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm)
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, name: row.name })
  formVisible.value = true
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateRelationType(form.id, { name: form.name.trim() })
    } else {
      await createRelationType({ name: form.name.trim() })
    }
    ElMessage.success(form.id ? '保存成功' : '新增成功')
    formVisible.value = false
    loadTypes()
  } catch (e) {
    proxy.$message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function handleToggle(row) {
  const target = row.status === 1 ? 0 : 1
  const action = target === 1 ? '启用' : '停用'
  ElMessageBox.confirm(`确认${action}关系类型「${row.name}」？${target === 0 ? '停用后不可用于新建关系，历史关系仍保留展示。' : ''}`, `${action}确认`, {
    confirmButtonText: action,
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      try {
        await updateRelationType(row.id, { status: target })
        ElMessage.success(`已${action}`)
        loadTypes()
      } catch (e) {
        proxy.$message.error(e?.message || '操作失败')
      }
    })
    .catch(() => {})
}

function goBack() {
  router.push('/knowledge-planet/customer-info')
}

onMounted(loadTypes)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  gap: 10px;
}
.tip {
  margin-bottom: 14px;
}
</style>
