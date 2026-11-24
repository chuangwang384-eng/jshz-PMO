<template>
  <el-dialog :model-value="true" :title="leaderMode ? '部门成员' : '组织架构管理'" width="min(960px, 94vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving"
    @update:model-value="value => { if (!value) emit('close') }">
    <el-tabs v-if="!leaderMode">
      <el-tab-pane label="部门">
        <el-button type="primary" :disabled="departmentsLoading || departmentsError" @click="editDepartment()">新增部门</el-button>
        <el-alert v-if="departmentsError" title="部门加载失败" type="error" :closable="false">
          <el-button link type="primary" @click="loadDepartments">重试</el-button>
        </el-alert>
        <el-table v-else v-loading="departmentsLoading" :data="departmentTree" row-key="id" default-expand-all
          :tree-props="{ children: 'children' }">
          <el-table-column prop="name" label="部门" min-width="200" />
          <el-table-column label="负责人" min-width="200">
            <template #default="{ row }">
              <template v-if="row.leaderName">
                <el-tag v-if="row.generalManagerNode || row.generalOfficeNode" size="small" type="danger" effect="light">总经理</el-tag>
                <span>{{ row.leaderName }}</span>
              </template>
              <span v-else class="ui-desc">未任命</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ row.active ? '启用' : '停用' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button link type="primary" @click="editDepartment(row)">编辑</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-form inline @submit.prevent="searchMembers">
      <el-form-item v-if="leaderMode" label="部门">
        <el-select v-model="memberDepartmentId" clearable placeholder="全部负责部门" class="department-filter">
          <el-option v-for="node in ledDepartments" :key="node.id" :label="node.path" :value="node.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="账号姓名"><el-input v-model="memberKeyword" clearable /></el-form-item>
      <el-form-item><el-button native-type="submit" type="primary">查询</el-button></el-form-item>
    </el-form>
    <el-table v-loading="membersLoading" :data="members.items" :empty-text="membersError ? '加载失败，请重新查询' : '暂无成员'">
      <el-table-column label="账号姓名" min-width="180">
        <template #default="{ row }">
          <span>{{ row.username }}</span>
          <el-tag v-if="row.generalManager" size="small" type="danger" effect="light" class="identity-tag">总经理</el-tag>
          <el-tooltip v-if="row.leaderOf.length" :content="row.leaderOf.join('；')" placement="top">
            <el-tag size="small" type="warning" effect="light" class="identity-tag">负责人</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="账号 ID" min-width="100" />
      <el-table-column label="账号状态" width="100">
        <template #default="{ row }">{{ row.active ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="所属部门" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.departmentPath || '未分配' }}</template>
      </el-table-column>
      <el-table-column label="岗位" min-width="100">
        <template #default="{ row }">{{ row.position || '未分配' }}</template>
      </el-table-column>
      <el-table-column v-if="!leaderMode" label="操作" width="90">
        <template #default="{ row }"><el-button link type="primary" :disabled="departmentsError" @click="editMember(row)">设置</el-button></template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="members.total > members.pageSize" v-model:current-page="memberPage"
      :page-size="members.pageSize" :total="members.total" layout="prev, pager, next" @current-change="loadMembers" />
  </el-dialog>

  <el-dialog v-model="editor" :title="editorTitle" width="min(480px, 92vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-form :model="form" :disabled="saving" label-width="92px" @submit.prevent="save">
      <template v-if="kind === 'department'">
        <el-form-item label="部门名称" required><el-input v-model.trim="form.name" /></el-form-item>
        <el-form-item v-if="!form.generalManagerNode" label="上级部门" required>
          <el-select v-model="form.parentId" placeholder="请选择上级部门">
            <el-option v-for="node in parentOptions" :key="node.id" :label="node.path" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.generalManagerNode ? '总经理' : '负责人'">
          <span v-if="form.generalOfficeNode">总经理（固定，无需单独设置）</span>
          <el-select v-else v-model="form.leaderUserId" clearable filterable
            :placeholder="form.generalManagerNode ? '未任命' : '可不设'">
            <el-option v-for="person in userOptions" :key="person.id" :label="personLabel(person)" :value="person.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!form.generalManagerNode" label="启用"><el-switch v-model="form.active" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="账号">{{ memberName }}</el-form-item>
        <el-form-item label="主部门">
          <el-select v-model="form.departmentId" clearable placeholder="未分配">
            <el-option v-for="node in activeDepartments" :key="node.id" :label="node.path" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位"><el-input v-model.trim="form.position" :disabled="!form.departmentId" placeholder="可不填" /></el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDepartments, getMembers, getOrganization, saveDepartment, saveMembership } from '../../api/profileOverview'
import { listUsers } from '../../api/user'

// leaderMode：部门负责人只读视图，仅能看到本人负责部门及其下级的成员。
const props = defineProps({ leaderMode: { type: Boolean, default: false } })
const emit = defineEmits(['close', 'changed'])
const departments = ref([])
const departmentsLoading = ref(false)
const departmentsError = ref(false)
const members = ref({ items: [], total: 0, pageSize: 0 })
const membersLoading = ref(false)
const membersError = ref(false)
const memberPage = ref(1)
const memberKeyword = ref('')
const memberDepartmentId = ref(null)
const ledDepartments = ref([])
const userOptions = ref([])
const editor = ref(false)
const kind = ref('department')
const editId = ref(null)
const memberName = ref('')
const saving = ref(false)
const form = reactive({ name: '', parentId: null, active: true, leaderUserId: null, departmentId: null, position: '' })
let membersRequest = 0
let appliedKeyword = ''
let appliedDepartmentId = null

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
const activeDepartments = computed(() => departmentRows.value.filter(node => node.active))
const parentOptions = computed(() => activeDepartments.value.filter(node => !node.ancestors.some(item => item.id === editId.value)))

// 表格树形展示：按 parent_id 挂接，末级节点不带 children 键。
const departmentTree = computed(() => {
  const byId = new Map(departmentRows.value.map(node => [node.id, { ...node }]))
  const roots = []
  byId.forEach(node => {
    const parent = node.parentId == null ? null : byId.get(node.parentId)
    if (parent) {
      if (!parent.children) parent.children = []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
})
const editorTitle = computed(() => kind.value === 'member' ? '设置部门与岗位'
  : editId.value == null ? '新增部门' : form.generalManagerNode ? '总经理任命' : '编辑部门')

const personLabel = person => `${person.username}（ID ${person.id}）`

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

async function loadUserOptions() {
  try {
    const result = await listUsers({ page: 1, size: 500 })
    userOptions.value = (result?.items || []).filter(person => person.status === 1)
  } catch {
    // 请求拦截器已提示；负责人下拉为空时仍可编辑其他字段。
  }
}

async function loadLedDepartments() {
  try {
    ledDepartments.value = (await getOrganization()).ledDepartments || []
  } catch {
    ledDepartments.value = []
  }
}

async function loadMembers() {
  const request = ++membersRequest
  membersLoading.value = true
  membersError.value = false
  try {
    const params = { page: memberPage.value, keyword: appliedKeyword }
    if (appliedDepartmentId != null) params.departmentId = appliedDepartmentId
    const result = await getMembers(params)
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
  appliedDepartmentId = memberDepartmentId.value ?? null
  memberPage.value = 1
  loadMembers()
}

function editDepartment(row) {
  kind.value = 'department'
  editId.value = row?.id ?? null
  Object.assign(form, {
    name: row?.name || '', parentId: row?.parentId ?? null, active: row?.active ?? true,
    leaderUserId: row?.leaderUserId ?? null, generalManagerNode: !!row?.generalManagerNode,
    generalOfficeNode: !!row?.generalOfficeNode
  })
  editor.value = true
}

function editMember(row) {
  kind.value = 'member'
  editId.value = row.id
  memberName.value = row.username
  Object.assign(form, { departmentId: row.departmentId ?? null, position: row.position || '' })
  editor.value = true
}

// 总经理换人会让前任和新任都需重新登录，保存前明确确认。
function confirmGeneralManagerChange(nextId) {
  if (editId.value == null || !form.generalManagerNode) return Promise.resolve()
  const previous = departmentById.value.get(editId.value)?.leaderName || '未任命'
  const next = nextId == null ? '无人' : personLabel(userOptions.value.find(person => person.id === nextId) || { id: nextId })
  if (previous === '未任命' && nextId != null) return Promise.resolve()
  return ElMessageBox.confirm(`总经理将由「${previous}」变更为「${next}」，相关账号需重新登录后生效。`, '确认总经理变更', { type: 'warning' })
}

async function save() {
  if (saving.value) return
  if (kind.value === 'department' && !form.name.trim()) {
    ElMessage.warning('请输入部门名称')
    return
  }
  if (kind.value === 'department' && !form.generalManagerNode && !form.parentId) {
    ElMessage.warning('请选择上级部门')
    return
  }
  saving.value = true
  try {
    if (kind.value === 'department') {
      const data = {
        name: form.name, parentId: form.generalManagerNode ? null : form.parentId,
        active: form.generalManagerNode ? true : form.active, leaderUserId: form.generalOfficeNode ? null : form.leaderUserId
      }
      await confirmGeneralManagerChange(data.leaderUserId)
      await saveDepartment(editId.value, data)
      await loadDepartments()
    } else {
      await saveMembership(editId.value, { departmentId: form.departmentId || null, position: form.position })
    }
    await loadMembers()
    editor.value = false
    emit('changed')
    ElMessage.success('已保存')
  } catch {
    // 请求拦截器显示服务端校验；取消总经理变更确认时不保存。两种情况都保留弹窗和输入。
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (props.leaderMode) {
    loadLedDepartments()
  } else {
    loadDepartments()
    loadUserOptions()
  }
  loadMembers()
})
</script>

<style scoped>
.department-filter {
  width: 280px;
}

.identity-tag {
  margin-left: 6px;
}
</style>
