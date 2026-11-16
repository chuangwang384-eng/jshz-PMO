<template>
  <div class="customer-info-page">
    <el-card shadow="never">
      <div class="toolbar ui-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索客户姓名/单位/职位/职级/职务/喜好"
          clearable
          style="width: 320px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-switch
          v-if="canManage"
          v-model="showDeleted"
          class="deleted-switch"
          active-text="已删除视图"
          @change="handleSwitchDeleted"
        />
        <div class="toolbar-right">
          <el-button v-permission="'customer:manage'" type="primary" @click="openCreate">新增客户</el-button>
          <el-button @click="goRelationGraph">关系网络</el-button>
          <el-button v-permission="'customer:manage'" @click="goRelationTypeManage">关系类型管理</el-button>
        </div>
      </div>

      <!-- 列宽按 20% / 38% / 17% / 15% / 10% 比例分配：全部用 min-width 让 el-table 按比例吃满容器 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        highlight-current-row
        class="customer-table"
        @row-click="openDrawer"
      >
        <el-table-column label="客户姓名" min-width="200">
          <template #default="{ row }">
            <span class="customer-avatar">{{ (row.name || '?').slice(0, 1) }}</span>
            <span class="customer-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工作单位 / 职务" min-width="380">
          <template #default="{ row }">
            <div v-if="row.workUnit || row.workLevel" class="unit-cell">
              <div class="unit-main" :title="row.workUnit || '未填写'">{{ row.workUnit || '未填写' }}</div>
              <div class="unit-sub" :title="row.workLevel || '未填写'">{{ row.workLevel || '未填写' }}</div>
            </div>
            <span v-else class="unit-empty">未填写</span>
          </template>
        </el-table-column>
        <el-table-column label="关系" min-width="170">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.relationCount > 0"
              content="查看关联客户"
              placement="top"
              :show-after="200"
            >
              <el-button link type="primary" class="relation-link" @click.stop="openRelationGraph(row)">
                <el-icon class="relation-icon"><Connection /></el-icon>
                {{ row.relationCount }} 条关联
              </el-button>
            </el-tooltip>
            <span v-else class="unit-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatDay(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="100">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDrawer(row)">查看</el-button>
            <el-dropdown
              v-if="canManage"
              trigger="click"
              @command="(cmd) => handleRowCommand(cmd, row)"
            >
              <el-button link type="primary" size="small" class="more-btn" @click.stop>···</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canManage && !showDeleted" command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item v-if="canManage && !showDeleted" command="delete" divided>删除</el-dropdown-item>
                  <el-dropdown-item v-if="showDeleted" command="archive">打开完整档案</el-dropdown-item>
                  <el-dropdown-item v-if="showDeleted && canManage" command="restore" divided>恢复</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 详情抽屉：查看 + 快速编辑 + 关联人物（打开完整档案入口保留） -->
    <el-drawer v-model="drawerVisible" size="520px" :title="drawerCustomer.name || '客户档案'" @closed="clearCurrentRow">
      <div class="drawer-head">
        <span class="customer-avatar drawer-avatar">{{ (drawerCustomer.name || '?').slice(0, 1) }}</span>
        <div>
          <div class="drawer-name">{{ drawerCustomer.name }}</div>
          <div class="drawer-sub">{{ drawerCustomer.workUnit || '—' }}<template v-if="drawerCustomer.currentDuty"> · {{ drawerCustomer.currentDuty }}</template></div>
        </div>
      </div>
      <div class="drawer-section">
        <div class="drawer-section-title">基本资料 <el-button v-if="canManage && !drawerEditing && !showDeleted" link type="primary" size="small" @click="startDrawerEdit">快速编辑</el-button></div>
        <template v-if="!drawerEditing">
          <div class="drawer-grid">
            <div class="drawer-field"><label>工作单位</label><span>{{ drawerCustomer.workUnit || '—' }}</span></div>
            <div class="drawer-field"><label>工作职位</label><span>{{ drawerCustomer.workPosition || '—' }}</span></div>
            <div class="drawer-field"><label>现任职务</label><span>{{ drawerCustomer.currentDuty || '—' }}</span></div>
            <div class="drawer-field"><label>工作职级</label><span>{{ drawerCustomer.workLevel || '—' }}</span></div>
            <div class="drawer-field"><label>客户喜好</label><span>{{ drawerCustomer.preferences || '—' }}</span></div>
            <div class="drawer-field drawer-field-wide"><label>备注</label><span class="pre-wrap">{{ drawerCustomer.remark || '—' }}</span></div>
          </div>
        </template>
        <template v-else>
          <el-form :model="drawerForm" label-width="80px" class="drawer-form">
            <el-form-item label="姓名"><el-input v-model="drawerForm.name" maxlength="50" /></el-form-item>
            <el-form-item label="工作单位"><el-input v-model="drawerForm.workUnit" maxlength="100" /></el-form-item>
            <el-form-item label="工作职位"><el-input v-model="drawerForm.workPosition" maxlength="50" /></el-form-item>
            <el-form-item label="现任职务"><el-input v-model="drawerForm.currentDuty" maxlength="50" /></el-form-item>
            <el-form-item label="工作职级"><el-input v-model="drawerForm.workLevel" maxlength="50" /></el-form-item>
            <el-form-item label="客户喜好"><el-input v-model="drawerForm.preferences" type="textarea" :rows="2" maxlength="500" /></el-form-item>
            <el-form-item label="备注"><el-input v-model="drawerForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
            <div class="drawer-edit-actions">
              <el-button size="small" type="primary" :loading="drawerSaving" @click="saveDrawer">保存</el-button>
              <el-button size="small" :disabled="drawerSaving" @click="drawerEditing = false">取消</el-button>
            </div>
          </el-form>
        </template>
      </div>
      <div class="drawer-section">
        <div class="drawer-section-title">关联人物 <el-button link type="primary" size="small" @click="openDrawerRelationGraph">查看关系网络</el-button></div>
        <div v-if="drawerRelations.length" class="drawer-relations">
          <div v-for="rel in drawerRelations" :key="rel.id" class="drawer-relation-row">
            <span>{{ rel.otherName }}</span>
            <el-tag size="small" type="info">{{ rel.relationTypeName }}</el-tag>
          </div>
        </div>
        <div v-else class="drawer-relations-empty">暂无关联人物</div>
      </div>
      <div class="drawer-meta">
        创建人 {{ drawerCustomer.createBy || '—' }} · 创建于 {{ formatDay(drawerCustomer.createTime) }}<template v-if="drawerCustomer.updateTime"> · 更新于 {{ formatDay(drawerCustomer.updateTime) }}</template>
      </div>
      <div class="drawer-footer">
        <el-button @click="goDetail(drawerCustomer)">打开完整档案</el-button>
        <el-button v-if="showDeleted && canManage" type="success" @click="handleRestore(drawerCustomer); drawerVisible = false">恢复</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑客户档案' : '新增客户档案'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户姓名" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="必填" />
        </el-form-item>
        <el-form-item label="工作单位" prop="workUnit">
          <el-input v-model="form.workUnit" maxlength="100" />
        </el-form-item>
        <el-form-item label="工作职位" prop="workPosition">
          <el-input v-model="form.workPosition" maxlength="50" />
        </el-form-item>
        <el-form-item label="工作职级" prop="workLevel">
          <el-input v-model="form.workLevel" maxlength="50" />
        </el-form-item>
        <el-form-item label="现任职务" prop="currentDuty">
          <el-input v-model="form.currentDuty" maxlength="50" />
        </el-form-item>
        <el-form-item label="客户喜好" prop="preferences">
          <el-input v-model="form.preferences" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <template v-if="!form.id">
          <el-divider content-position="left">关联已有客户（选填，最多 {{ MAX_RELATIONS }} 行）</el-divider>
          <div v-if="relationRows.length === 0" class="relation-empty">
            <el-button type="primary" plain size="small" @click="addRelationRow">+ 关联客户</el-button>
          </div>
          <div v-for="(row, index) in relationRows" :key="index" class="relation-row">
            <div class="relation-row-head">
              <span class="relation-index">关联 {{ index + 1 }}</span>
              <el-button link type="danger" size="small" @click="removeRelationRow(index)">删除此行</el-button>
            </div>
            <el-form-item label="目标客户" class="relation-form-item">
              <div class="target-pick">
                <el-input :model-value="row.targetName || ''" placeholder="未选择" readonly />
                <el-button type="primary" plain size="small" @click="openTargetSelect(index)">选择客户</el-button>
              </div>
            </el-form-item>
            <el-form-item label="关系类型" class="relation-form-item">
              <el-select v-model="row.relationTypeId" placeholder="请选择关系类型" style="width: 100%">
                <el-option v-for="t in enabledRelationTypes" :key="t.id" :label="typeLabel(t)" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="isDirectedType(row.relationTypeId)" label="方向" class="relation-form-item">
              <el-select v-model="row.direction" style="width: 100%">
                <el-option label="新客户 → 已有客户" value="NEW_TO_TARGET" />
                <el-option label="已有客户 → 新客户" value="TARGET_TO_NEW" />
              </el-select>
            </el-form-item>
            <el-form-item v-else-if="relationTypeDirection(row.relationTypeId)" label="方向" class="relation-form-item">
              <el-tag type="info" size="small">{{ relationTypeDirection(row.relationTypeId) === 'BIDIRECTED' ? '双向（双方互见）' : '无向（不分方向）' }}</el-tag>
            </el-form-item>
            <el-form-item label="关系说明" class="relation-form-item">
              <el-input v-model="row.description" maxlength="200" placeholder="选填" />
            </el-form-item>
          </div>
          <div v-if="relationRows.length > 0 && relationRows.length < MAX_RELATIONS" class="relation-add">
            <el-button type="primary" plain size="small" @click="addRelationRow">+ 再关联一位</el-button>
          </div>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <CustomerSelectDialog
      v-model="targetSelectVisible"
      :customer-id="null"
      :exclude-ids="pickedTargetIds"
      @select="onTargetSelect"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import {
  listCustomers,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  restoreCustomer,
  listRelationTypes,
  getCenterGraph
} from '../api/customer'
import CustomerSelectDialog from '../components/CustomerSelectDialog.vue'
import { useAuthStore } from '../store/auth'
import { relationDirectionLabel } from '../utils/dict'

const router = useRouter()
const route = useRoute()
const { proxy } = getCurrentInstance()
const tableRef = ref(null)

/** 抽屉关闭后取消行选中高亮 */
function clearCurrentRow() {
  tableRef.value?.setCurrentRow()
}

const PAGE_SIZES = [10, 20, 50]

/** 从 URL query 安全取值（非法值回落默认，避免分享/刷新后状态漂移） */
function queryParam(name) {
  const v = route.query[name]
  const s = Array.isArray(v) ? v[0] : v
  return typeof s === 'string' ? s : ''
}

function initListQuery() {
  const page = Number(queryParam('page'))
  const size = Number(queryParam('pageSize'))
  return {
    keyword: queryParam('keyword'),
    page: Number.isInteger(page) && page > 0 ? page : 1,
    pageSize: PAGE_SIZES.includes(size) ? size : 10
  }
}

const authStore = useAuthStore()
// 客户模块管理权限（customer:manage）= ADMIN / 知识星球维护人（KNOWLEDGE_MAINTAINER）
const canManage = computed(() => authStore.hasPermission('customer:manage'))

const loading = ref(false)
const rows = ref([])
const total = ref(0)
// 已删除视图仅 canManage 可见；无权限账号即使 URL 带 deleted=1 也忽略，避免触发 403
const showDeleted = ref(canManage.value && queryParam('deleted') === '1')
const query = reactive({ ...initListQuery(), deleted: 0 })

/** 列表查询状态写回 URL：只 replace 不 push，返回列表时可还原搜索词/页码/视图 */
function syncRouteQuery() {
  const next = {
    keyword: query.keyword || undefined,
    page: query.page > 1 ? String(query.page) : undefined,
    pageSize: query.pageSize !== 10 ? String(query.pageSize) : undefined,
    deleted: canManage.value && showDeleted.value ? '1' : undefined
  }
  const same = ['keyword', 'page', 'pageSize', 'deleted'].every((k) => {
    const cur = route.query[k]
    const curStr = Array.isArray(cur) ? cur[0] || '' : cur || ''
    return String(curStr) === String(next[k] || '')
  })
  if (!same) router.replace({ query: next })
}

// 详情抽屉（Owner 2026-11-09）：查看 + 快速编辑 + 关联人物；打开完整档案入口保留
const drawerVisible = ref(false)
const drawerCustomer = ref({})
const drawerRelations = ref([])
const drawerEditing = ref(false)
const drawerSaving = ref(false)
const drawerForm = reactive({ name: '', workUnit: '', workPosition: '', currentDuty: '', workLevel: '', preferences: '', remark: '' })

function formatDay(value) {
  if (!value) return '—'
  const m = String(value).match(/(\d{4})-(\d{2})-(\d{2})/)
  return m ? `${m[1]}-${m[2]}-${m[3]}` : String(value).slice(0, 10)
}

function openDrawer(row) {
  drawerCustomer.value = { ...row }
  drawerRelations.value = []
  drawerEditing.value = false
  drawerVisible.value = true
  // 行点击即视为选中：表格给该行蓝色高亮
  tableRef.value?.setCurrentRow(row)
  getCenterGraph(row.id).then((graph) => {
    const nodeNames = {}
    for (const n of graph?.nodes || []) nodeNames[n.id] = n.name
    drawerRelations.value = (graph?.links || []).map((link) => ({
      id: link.id,
      otherName: nodeNames[link.source] === drawerCustomer.value.name || link.source === drawerCustomer.value.id
        ? (nodeNames[link.target] || '—')
        : (nodeNames[link.source] || '—'),
      relationTypeName: link.relationTypeName || '—'
    }))
  }).catch(() => {})
}

function startDrawerEdit() {
  Object.assign(drawerForm, {
    name: drawerCustomer.value.name || '',
    workUnit: drawerCustomer.value.workUnit || '',
    workPosition: drawerCustomer.value.workPosition || '',
    currentDuty: drawerCustomer.value.currentDuty || '',
    workLevel: drawerCustomer.value.workLevel || '',
    preferences: drawerCustomer.value.preferences || '',
    remark: drawerCustomer.value.remark || ''
  })
  drawerEditing.value = true
}

async function saveDrawer() {
  if (!String(drawerForm.name || '').trim()) {
    ElMessage.warning('客户姓名不能为空')
    return
  }
  drawerSaving.value = true
  try {
    const payload = {
      name: drawerForm.name.trim(),
      workUnit: drawerForm.workUnit?.trim() || '',
      workPosition: drawerForm.workPosition?.trim() || '',
      currentDuty: drawerForm.currentDuty?.trim() || '',
      workLevel: drawerForm.workLevel?.trim() || '',
      preferences: drawerForm.preferences?.trim() || '',
      remark: drawerForm.remark?.trim() || ''
    }
    await updateCustomer(drawerCustomer.value.id, payload)
    ElMessage.success('保存成功')
    drawerCustomer.value = { ...drawerCustomer.value, ...payload }
    drawerEditing.value = false
    loadList()
  } catch (e) {
    proxy.$message.error(e?.message || '保存失败')
  } finally {
    drawerSaving.value = false
  }
}

function handleRowCommand(cmd, row) {
  if (cmd === 'edit') {
    openDrawer(row)
    startDrawerEdit()
    return
  }
  if (cmd === 'archive') {
    goDetail(row)
    return
  }
  if (cmd === 'restore') {
    handleRestore(row)
    return
  }
  if (cmd === 'delete') {
    ElMessageBox.confirm(
      `确认删除客户「${row.name}」的档案？删除后默认隐藏，可在已删除视图恢复。`,
      '删除客户',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
      .then(() => handleDelete(row))
      .catch(() => {})
  }
}

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const emptyForm = { id: null, name: '', workUnit: '', workPosition: '', workLevel: '', currentDuty: '', preferences: '', remark: '' }
const form = reactive({ ...emptyForm })
const rules = {
  name: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }]
}

// 关联已有客户（仅新建）
const MAX_RELATIONS = 10
const relationRows = ref([])
const relationTypes = ref([])
const targetSelectVisible = ref(false)
const activeRelationIndex = ref(-1)
const enabledRelationTypes = computed(() => relationTypes.value.filter((t) => t.status === 1))
const pickedTargetIds = computed(() => relationRows.value.map((r) => r.targetCustomerId).filter(Boolean))

function typeLabel(t) {
  return `${t.name}（${relationDirectionLabel(t.direction)}）`
}

function relationTypeDirection(typeId) {
  return relationTypes.value.find((x) => x.id === typeId)?.direction || ''
}

function isDirectedType(typeId) {
  return relationTypeDirection(typeId) === 'DIRECTED'
}

function addRelationRow() {
  if (relationRows.value.length >= MAX_RELATIONS) {
    ElMessage.warning(`最多关联 ${MAX_RELATIONS} 位客户`)
    return
  }
  relationRows.value.push({ targetCustomerId: null, targetName: '', relationTypeId: null, direction: 'NEW_TO_TARGET', description: '' })
}

function removeRelationRow(index) {
  relationRows.value.splice(index, 1)
}

function openTargetSelect(index) {
  activeRelationIndex.value = index
  targetSelectVisible.value = true
}

function onTargetSelect(customer) {
  if (activeRelationIndex.value < 0) return
  const row = relationRows.value[activeRelationIndex.value]
  row.targetCustomerId = customer.id
  row.targetName = customer.name
}

async function loadList() {
  syncRouteQuery()
  loading.value = true
  try {
    const res = await listCustomers({
      keyword: query.keyword || undefined,
      deleted: canManage.value && showDeleted.value ? 1 : 0,
      page: query.page,
      pageSize: query.pageSize
    })
    rows.value = res?.items || []
    total.value = res?.total || 0
  } catch (e) {
    proxy.$message.error(e?.message || '加载客户列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function resetQuery() {
  query.keyword = ''
  query.page = 1
  loadList()
}

function handleSwitchDeleted() {
  // 无 customer:manage 时不允许进入已删除视图（后端同样返回 403）
  if (!canManage.value) {
    showDeleted.value = false
    return
  }
  query.page = 1
  loadList()
}

function openCreate() {
  Object.assign(form, emptyForm)
  relationRows.value = []
  formVisible.value = true
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const pendingRelations = relationRows.value.filter((r) => r.targetCustomerId)
  for (const r of pendingRelations) {
    if (!r.relationTypeId) {
      ElMessage.warning(`请为「${r.targetName}」选择关系类型`)
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      workUnit: form.workUnit?.trim() || '',
      workPosition: form.workPosition?.trim() || '',
      workLevel: form.workLevel?.trim() || '',
      currentDuty: form.currentDuty?.trim() || '',
      preferences: form.preferences?.trim() || '',
      remark: form.remark?.trim() || ''
    }
    if (form.id) {
      await updateCustomer(form.id, payload)
    } else {
      if (pendingRelations.length) {
        payload.relations = pendingRelations.map((r) => ({
          targetCustomerId: r.targetCustomerId,
          relationTypeId: r.relationTypeId,
          direction: isDirectedType(r.relationTypeId) ? r.direction : undefined,
          description: r.description?.trim() || ''
        }))
      }
      await createCustomer(payload)
    }
    ElMessage.success(form.id ? '保存成功' : '新增成功')
    formVisible.value = false
    loadList()
  } catch (e) {
    proxy.$message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await deleteCustomer(row.id)
    ElMessage.success('已删除（默认隐藏，可在已删除视图恢复）')
    loadList()
  } catch (e) {
    proxy.$message.error(e?.message || '删除失败')
  }
}

async function handleRestore(row) {
  try {
    await restoreCustomer(row.id)
    ElMessage.success('已恢复')
    loadList()
  } catch (e) {
    proxy.$message.error(e?.message || '恢复失败')
  }
}

function goDetail(row) {
  router.push(`/knowledge-planet/customer-detail/${row.id}`)
}

function goRelationGraph() {
  router.push('/knowledge-planet/customer-relation-graph')
}

/** 进入指定客户的关系网络中心视图（列表关系列 / 抽屉共用入口） */
function goCustomerRelationGraph(customer) {
  if (!customer?.id) return
  router.push({
    path: '/knowledge-planet/customer-relation-graph',
    query: { customerId: String(customer.id), customerName: customer.name || '' }
  })
}

/** 列表「关系」列点击：直接跳该客户自己的关系网络视图 */
function openRelationGraph(row) {
  goCustomerRelationGraph(row)
}

/** 抽屉入口：只看当前客户的关系圈（Owner 2026-11-09） */
function openDrawerRelationGraph() {
  goCustomerRelationGraph(drawerCustomer.value)
}

function goRelationTypeManage() {
  router.push('/knowledge-planet/customer-relation-type-manage')
}

onMounted(() => {
  loadList()
  listRelationTypes()
    .then((res) => {
      relationTypes.value = res || []
    })
    .catch(() => {})
})
</script>

<style scoped>
/* 详情抽屉（Owner 2026-11-09 规范） */
.customer-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #ecf5ff;
  color: var(--ui-primary);
  font-size: 13px;
  margin-right: 8px;
  flex-shrink: 0;
}
.customer-name {
  vertical-align: middle;
  font-weight: 600;
  color: var(--ui-text-main);
}

/* 列表视觉（Owner 2026-11-09）：白底 + 浅横向分隔线，去掉斑马纹与纵向边框 */
.customer-table {
  --el-table-border-color: #eef1f5;
  --el-table-tr-bg-color: #ffffff;
  --el-table-row-hover-bg-color: #f4f7fc;
  --el-table-header-bg-color: #fafbfc;
  --el-table-header-text-color: #6b7280;
}
.customer-table :deep(th.el-table__cell) {
  font-weight: 600;
  font-size: 13px;
  padding: 10px 0;
  background-color: #fafbfc;
}
/* 行高压缩：两行文字 38px + 上下 11px ≈ 60–62px（原方案约 80px） */
.customer-table :deep(td.el-table__cell) {
  padding: 11px 0;
  border-bottom: 1px solid #eef1f5;
  color: #4b5563;
}
.customer-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: #f4f7fc;
}
/* 选中行（点开抽屉的那一行）才给明显的蓝色高亮 */
.customer-table :deep(.el-table__body tr.current-row > td.el-table__cell) {
  background-color: var(--el-color-primary-light-8);
}
/* 链接按钮收紧内边距，保证 10% 宽的「操作」列（查看 + ···）不换行撑高行高 */
.customer-table :deep(.cell .el-button.is-link) {
  padding: 0 4px;
}
.drawer-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid #ebeef5;
}
.drawer-avatar {
  width: 44px;
  height: 44px;
  font-size: 18px;
}
.drawer-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--ui-text-main);
}
.drawer-sub {
  font-size: 12px;
  color: var(--ui-text-muted);
  margin-top: 2px;
}
.drawer-section {
  margin-top: 16px;
  padding-bottom: 6px;
}
.drawer-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-text-main);
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.drawer-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}
.drawer-field label {
  display: block;
  font-size: 11px;
  color: var(--ui-text-muted);
  margin-bottom: 2px;
}
.drawer-field span {
  font-size: 13px;
  color: var(--ui-text-main);
  overflow-wrap: anywhere;
}
.drawer-field-wide {
  grid-column: 1 / -1;
}
.pre-wrap {
  white-space: pre-wrap;
}
.drawer-form {
  margin-top: 4px;
}
.drawer-edit-actions {
  display: flex;
  gap: 8px;
}
.drawer-relations {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.drawer-relation-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: var(--ui-text-main);
}
.drawer-relations-empty {
  font-size: 12px;
  color: var(--ui-text-muted);
}
.drawer-meta {
  margin-top: 16px;
  font-size: 11px;
  color: var(--ui-text-muted);
}
.drawer-footer {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
/* 列表新方案（Owner 2026-11-09） */
.unit-cell {
  min-width: 0;
}
.unit-main {
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-text-main);
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.unit-sub {
  font-size: 12px;
  color: var(--ui-text-muted);
  line-height: 16px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.unit-empty {
  color: var(--ui-info);
  font-size: 12px;
}
.relation-link {
  font-size: 13px;
}
.relation-icon {
  margin-right: 4px;
  font-size: 14px;
}
.more-btn {
  font-size: 16px;
  letter-spacing: 1px;
}
.toolbar {
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 10px;
}
.deleted-switch {
  margin-left: 10px;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.relation-empty {
  margin-bottom: 10px;
}
.relation-row {
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 8px 12px 2px;
  margin-bottom: 12px;
}
.relation-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.relation-index {
  font-size: 13px;
  color: var(--ui-text-muted);
}
.relation-form-item {
  margin-bottom: 12px;
}
.target-pick {
  display: flex;
  gap: 8px;
  width: 100%;
}
.target-pick .el-input {
  flex: 1;
}
.relation-add {
  margin-top: 4px;
}
</style>
