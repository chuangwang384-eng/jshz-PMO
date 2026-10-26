<template>
  <div>
    <div class="page-head">
      <span class="page-title">审批模板管理</span>
      <span class="page-sub">预设常用申请模板，员工提交申请时选择模板仅填写模板字段</span>
    </div>

    <el-card v-loading="loading" style="margin-top: 16px">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">新建模板</el-button>
        <span class="toolbar-tip">模板字段仅可从专家可编辑字段中勾选；停用后员工不可再用于新申请，历史申请不受影响</span>
      </div>

      <el-table :data="templates" border stripe>
        <el-table-column prop="templateName" label="模板名称" min-width="140" />
        <el-table-column label="模板字段" min-width="320">
          <template #default="{ row }">
            <el-tag
              v-for="f in row.fields"
              :key="f"
              :type="isPhotoField(f) ? 'warning' : 'info'"
              size="small"
              class="field-tag"
            >
              {{ f }}
            </el-tag>
            <el-tag v-if="coversCreateRequired(row)" type="success" size="small" class="field-tag">
              覆盖专家新增必填
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled === 1 ? 'warning' : 'success'" @click="toggleEnabled(row)">
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑模板弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模板' : '新建模板'" width="640px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="模板名称">
          <el-input v-model="form.templateName" maxlength="100" show-word-limit placeholder="如：联系方式变更" />
        </el-form-item>
        <el-form-item label="模板字段">
          <div class="field-group">
            <div class="field-group-title">文本字段</div>
            <el-checkbox-group v-model="form.fields">
              <el-checkbox v-for="f in catalog.textFields" :key="f" :value="f">{{ f }}</el-checkbox>
            </el-checkbox-group>
          </div>
          <div class="field-group">
            <div class="field-group-title">照片字段</div>
            <el-checkbox-group v-model="form.fields">
              <el-checkbox v-for="f in catalog.photoFields" :key="f" :value="f">{{ f }}</el-checkbox>
            </el-checkbox-group>
          </div>
          <div class="form-tip">
            所选字段已覆盖「专家新增」必填项（{{ catalog.createRequiredFields.join('、') }}）时，
            该模板可用于新增专家申请
            <el-tag v-if="selectedCoversCreateRequired" type="success" size="small" style="margin-left: 6px">
              当前已覆盖
            </el-tag>
          </div>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="formEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAllApprovalTemplates,
  getApprovalFieldCatalog,
  createApprovalTemplate,
  updateApprovalTemplate,
  deleteApprovalTemplate
} from '../api/approval'

const loading = ref(false)
const saving = ref(false)
const templates = ref([])
const catalog = reactive({ textFields: [], photoFields: [], createRequiredFields: [] })

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ templateName: '', fields: [] })
const formEnabled = ref(true)

const selectedCoversCreateRequired = computed(() => coversCreateRequired({ fields: form.fields }))

function isPhotoField(f) {
  return catalog.photoFields.includes(f)
}

function coversCreateRequired(row) {
  return (
    Array.isArray(catalog.createRequiredFields) &&
    catalog.createRequiredFields.length > 0 &&
    catalog.createRequiredFields.every((f) => (row.fields || []).includes(f))
  )
}

async function fetchCatalog() {
  try {
    const data = await getApprovalFieldCatalog()
    catalog.textFields = data?.textFields || []
    catalog.photoFields = data?.photoFields || []
    catalog.createRequiredFields = data?.createRequiredFields || []
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

async function fetchList() {
  loading.value = true
  try {
    templates.value = (await getAllApprovalTemplates()) || []
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.templateName = ''
  form.fields = []
  formEnabled.value = true
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.templateName = row.templateName
  form.fields = [...(row.fields || [])]
  formEnabled.value = row.enabled === 1
  dialogVisible.value = true
}

async function save() {
  if (!form.templateName.trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  if (!form.fields.length) {
    ElMessage.warning('请至少勾选一个模板字段')
    return
  }
  saving.value = true
  try {
    const body = {
      templateName: form.templateName.trim(),
      fields: [...form.fields],
      enabled: formEnabled.value ? 1 : 0
    }
    if (editingId.value) {
      await updateApprovalTemplate(editingId.value, body)
      ElMessage.success('模板已更新')
    } else {
      await createApprovalTemplate(body)
      ElMessage.success('模板已创建')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row) {
  const action = row.enabled === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}模板「${row.templateName}」吗？${row.enabled === 1 ? '停用后员工提交申请时将无法再选择该模板。' : ''}`,
      `${action}模板`,
      { confirmButtonText: `确认${action}`, cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await updateApprovalTemplate(row.id, {
      templateName: row.templateName,
      fields: row.fields,
      enabled: row.enabled === 1 ? 0 : 1
    })
    ElMessage.success(`已${action}`)
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除模板「${row.templateName}」吗？仅删除模板本身，历史申请单的来源记录不受影响。`,
      '删除模板',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteApprovalTemplate(row.id)
    ElMessage.success('模板已删除')
    fetchList()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

onMounted(() => {
  fetchCatalog()
  fetchList()
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
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.toolbar-tip {
  font-size: 12px;
  color: #909399;
}
.field-tag {
  margin: 0 6px 4px 0;
}
.field-group {
  margin-bottom: 8px;
}
.field-group-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
