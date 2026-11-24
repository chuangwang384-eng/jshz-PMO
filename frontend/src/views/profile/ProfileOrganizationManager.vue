<template>
  <el-dialog :model-value="true" title="组织架构管理" width="min(920px, 94vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving"
    @update:model-value="value => { if (!value) emit('close') }">
    <el-alert v-if="departmentsError" title="部门加载失败" type="error" :closable="false">
      <el-button link type="primary" @click="loadDepartments">重试</el-button>
    </el-alert>
    <el-tabs>
      <el-tab-pane label="部门">
        <el-button type="primary" :disabled="departmentsLoading || departmentsError" @click="editDepartment()">新增部门</el-button>
        <el-table v-loading="departmentsLoading" :data="departmentRows" row-key="id">
          <el-table-column prop="path" label="部门路径" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ row.active ? '启用' : '停用' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button link type="primary" @click="editDepartment(row)">编辑</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="成员与岗位">
        <el-form inline @submit.prevent="searchMembers">
          <el-form-item label="账号姓名"><el-input v-model="memberKeyword" clearable /></el-form-item>
          <el-form-item><el-button native-type="submit" type="primary">查询</el-button></el-form-item>
        </el-form>
        <el-table v-loading="membersLoading" :data="members.items" :empty-text="membersError ? '加载失败，请重新查询' : '暂无成员'">
          <el-table-column prop="username" label="账号姓名" min-width="120" />
          <el-table-column prop="id" label="账号 ID" min-width="100" />
          <el-table-column label="账号状态" width="100">
            <template #default="{ row }">{{ row.active ? '启用' : '停用' }}</template>
          </el-table-column>
          <el-table-column label="所属部门" min-width="140">
            <template #default="{ row }">{{ departmentPaths.get(row.departmentId) || '未分配' }}</template>
          </el-table-column>
          <el-table-column label="岗位" min-width="100">
            <template #default="{ row }">{{ row.position || '未分配' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button link type="primary" :disabled="departmentsError" @click="editMember(row)">设置</el-button></template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="members.total > members.pageSize" v-model:current-page="memberPage"
          :page-size="members.pageSize" :total="members.total" layout="prev, pager, next" @current-change="loadMembers" />
      </el-tab-pane>
    </el-tabs>
  </el-dialog>

  <el-dialog v-model="editor" :title="editorTitle" width="min(460px, 92vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-form :model="form" :disabled="saving" label-width="92px" @submit.prevent="save">
      <template v-if="kind === 'department'">
        <el-form-item label="部门名称" required><el-input v-model.trim="form.name" /></el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="form.parentId" clearable placeholder="顶级部门">
            <el-option v-for="node in parentOptions" :key="node.id" :label="node.path" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.active" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="账号">{{ memberName }}</el-form-item>
        <el-form-item label="主部门">
          <el-select v-model="form.departmentId" clearable placeholder="未分配">
            <el-option v-for="node in activeDepartments" :key="node.id" :label="node.path" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位"><el-input v-model.trim="form.position" :disabled="!form.departmentId" placeholder="例如 总经理" /></el-form-item>
      </template>
      <el-form-item>
        <el-button :disabled="saving" @click="editor = false">取消</el-button>
        <el-button native-type="submit" :loading="saving" type="primary">保存</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDepartments, getMembers, saveDepartment, saveMembership } from '../../api/profileOverview'

const emit = defineEmits(['close', 'changed'])
const departments = ref([])
const departmentsLoading = ref(false)
const departmentsError = ref(false)
const members = ref({ items: [], total: 0, pageSize: 0 })
const membersLoading = ref(false)
const membersError = ref(false)
const memberPage = ref(1)
const memberKeyword = ref('')
const editor = ref(false)
const kind = ref('department')
const editId = ref(null)
const memberName = ref('')
const saving = ref(false)
const form = reactive({ name: '', parentId: null, active: true, departmentId: null, position: '' })
let membersRequest = 0
let appliedKeyword = ''

const departmentById = computed(() => new Map(departments.value.map(node => [node.id, node])))

function ancestry(id) {
  const ancestors = []
  const visited = new Set()
  for (let node = departmentById.value.get(id); node && !visited.has(node.id); node = departmentById.value.get(node.parentId)) {
    visited.add(node.id)
    ancestors.unshift(node)
  }
  return ancestors
}

const departmentRows = computed(() => departments.value.map(node => {
  const ancestors = ancestry(node.id)
  return { ...node, ancestors, path: ancestors.map(item => item.name).join(' / ') }
}))
const departmentPaths = computed(() => new Map(departmentRows.value.map(node => [node.id, node.path])))
const activeDepartments = computed(() => departmentRows.value.filter(node => node.active))
const parentOptions = computed(() => activeDepartments.value.filter(node => !node.ancestors.some(item => item.id === editId.value)))
const editorTitle = computed(() => kind.value === 'member' ? '设置部门与岗位' : editId.value == null ? '新增部门' : '编辑部门')

async function loadDepartments() {
  departmentsLoading.value = true
  departmentsError.value = false
  try {
    departments.value = await getDepartments()
  } catch {
    departmentsError.value = true
  } finally {
    departmentsLoading.value = false
  }
}

async function loadMembers() {
  const request = ++membersRequest
  membersLoading.value = true
  membersError.value = false
  try {
    const result = await getMembers({ page: memberPage.value, keyword: appliedKeyword })
    if (request === membersRequest) members.value = result
  } catch {
    if (request === membersRequest) {
      membersError.value = true
      members.value = { items: [], total: 0, pageSize: 0 }
    }
  } finally {
    if (request === membersRequest) membersLoading.value = false
  }
}

function searchMembers() {
  appliedKeyword = memberKeyword.value.trim()
  memberPage.value = 1
  loadMembers()
}

function editDepartment(row) {
  kind.value = 'department'
  editId.value = row?.id ?? null
  Object.assign(form, { name: row?.name || '', parentId: row?.parentId ?? null, active: row?.active ?? true })
  editor.value = true
}

function editMember(row) {
  kind.value = 'member'
  editId.value = row.id
  memberName.value = row.username
  Object.assign(form, { departmentId: row.departmentId ?? null, position: row.position || '' })
  editor.value = true
}

async function save() {
  if (saving.value) return
  if (kind.value === 'department' && !form.name.trim()) {
    ElMessage.warning('请输入部门名称')
    return
  }
  saving.value = true
  try {
    if (kind.value === 'department') {
      await saveDepartment(editId.value, { name: form.name, parentId: form.parentId || null, active: form.active })
      await loadDepartments()
    } else {
      await saveMembership(editId.value, { departmentId: form.departmentId || null, position: form.position })
      await loadMembers()
    }
    editor.value = false
    emit('changed')
    ElMessage.success('已保存')
  } catch {
    // 请求拦截器显示服务端校验，保留输入以便修正。
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadDepartments()
  loadMembers()
})
</script>
