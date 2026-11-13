<template>
  <div v-permission="'role:manage'">
    <div class="ui-toolbar">
      <div class="spacer" />
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <el-table :data="roles" border stripe v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="角色编码" width="150" />
      <el-table-column prop="name" label="角色名称" width="140" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内置" width="80" align="center">
        <template #default="{ row }">
          {{ row.builtin === 1 ? '是' : '否' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="310" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.code === 'SUPER_ADMIN'" @click="openPerm(row)">绑定权限</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" :disabled="row.code === 'SUPER_ADMIN'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" :disabled="row.builtin === 1" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑角色' : '新增角色'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="form.builtin === 1" placeholder="如 EXPERT_EDITOR（内置角色禁改编码）" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="角色用途说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            :disabled="form.code === 'SUPER_ADMIN'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 绑定权限（菜单 + 接口树形勾选，同码联动） -->
    <el-dialog v-model="permVisible" :title="`绑定权限 - ${permRow?.name || ''}`" width="640px" destroy-on-close>
      <div v-loading="permLoading" style="min-height: 120px">
        <el-tree
          v-if="!permLoading"
          ref="permTreeRef"
          :data="permTree"
          show-checkbox
          node-key="nodeKey"
          default-expand-all
          :props="{ label: 'label' }"
        >
          <template #default="{ data }">
            <span>{{ data.label }}</span>
            <span class="perm-tag">{{ data.type === 1 ? '菜单' : '接口' }}</span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRoles,
  createRole,
  updateRole,
  deleteRole,
  getRolePermissions,
  assignRolePermissions,
  listPermissions
} from '../api/role'

const roles = ref([])
const loading = ref(false)

const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ id: null, code: '', name: '', description: '', status: 1, builtin: 0 })

const permVisible = ref(false)
const permLoading = ref(false)
const permSaving = ref(false)
const permRow = ref(null)
const permTree = ref([])
const permTreeRef = ref()

const formRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

async function fetchRoles() {
  loading.value = true
  try {
    const data = await listRoles()
    roles.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, code: '', name: '', description: '', status: 1, builtin: 0 })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    code: row.code,
    name: row.name,
    description: row.description || '',
    status: row.status,
    builtin: row.builtin
  })
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
    if (form.id) {
      await updateRole(form.id, {
        code: form.code,
        name: form.name,
        description: form.description,
        status: form.status
      })
      ElMessage.success('角色修改成功')
    } else {
      await createRole({
        code: form.code,
        name: form.name,
        description: form.description,
        status: form.status
      })
      ElMessage.success('角色新增成功')
    }
    formVisible.value = false
    fetchRoles()
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确定将角色「${row.name}」${target === 1 ? '启用' : '停用'}吗？`,
      target === 1 ? '启用角色' : '停用角色',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await updateRole(row.id, {
    code: row.code,
    name: row.name,
    description: row.description,
    status: target
  })
  ElMessage.success(target === 1 ? '角色已启用' : '角色已停用')
  fetchRoles()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${row.name}」吗？该角色与用户的绑定关系将一并删除。`,
      '删除角色',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteRole(row.id)
  ElMessage.success('角色已删除')
  fetchRoles()
}

// 构建菜单 + 接口权限树：菜单为父节点，同码接口挂到对应菜单下（菜单与接口同码联动）
function buildPermTree(menus, apis) {
  const topMenus = menus.filter((m) => !m.parentCode)
  return topMenus.map((menu) => {
    const linkedApis = apis.filter((a) => a.code === menu.code)
    return {
      nodeKey: `menu-${menu.id}`,
      label: menu.name,
      type: 1,
      code: menu.code,
      children: linkedApis.length
        ? linkedApis.map((a) => ({
            nodeKey: `api-${a.id}`,
            label: a.name,
            type: 2,
            code: a.code
          }))
        : undefined
    }
  })
}

function collectCodes(nodeKeys) {
  const codeSet = new Set()
  const walk = (nodes) => {
    nodes.forEach((n) => {
      if (nodeKeys.includes(n.nodeKey)) codeSet.add(n.code)
      if (n.children) walk(n.children)
    })
  }
  walk(permTree.value)
  return [...codeSet]
}

async function openPerm(row) {
  permRow.value = row
  permVisible.value = true
  permLoading.value = true
  permTree.value = []
  try {
    const [perms, bound] = await Promise.all([
      listPermissions(),
      getRolePermissions(row.id)
    ])
    permTree.value = buildPermTree(perms?.menus || [], perms?.apis || [])
    // 后端 GET /roles/{id}/permissions 直接返回权限码数组
    const boundCodes = new Set(Array.isArray(bound) ? bound : bound?.permissionCodes || [])
    const checkedKeys = []
    const walk = (nodes) => {
      nodes.forEach((n) => {
        if (boundCodes.has(n.code)) checkedKeys.push(n.nodeKey)
        if (n.children) walk(n.children)
      })
    }
    walk(permTree.value)
    permLoading.value = false
    await nextTick()
    permTreeRef.value?.setCheckedKeys(checkedKeys)
  } catch (e) {
    // 错误提示已由拦截器统一处理
    permLoading.value = false
  }
}

async function savePerm() {
  const tree = permTreeRef.value
  if (!tree) return
  // 勾选 + 半选（父菜单被勾子接口时半选，菜单码一并授予以保证菜单可见）
  const allKeys = [...tree.getCheckedKeys(), ...tree.getHalfCheckedKeys()]
  permSaving.value = true
  try {
    await assignRolePermissions(permRow.value.id, collectCodes(allKeys))
    ElMessage.success('权限绑定成功')
    permVisible.value = false
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    permSaving.value = false
  }
}

onMounted(fetchRoles)
</script>

<style scoped>
.perm-tag {
  margin-left: 8px;
  font-size: 12px;
  color: var(--ui-text-muted);
}
</style>
