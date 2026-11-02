<template>
  <div class="customer-info-page">
    <el-card shadow="never">
      <div class="toolbar">
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

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="客户姓名" min-width="120" fixed="left" show-overflow-tooltip />
        <el-table-column prop="workUnit" label="工作单位" min-width="160" show-overflow-tooltip />
        <el-table-column prop="workPosition" label="工作职位" min-width="110" show-overflow-tooltip />
        <el-table-column prop="workLevel" label="工作职级" min-width="100" show-overflow-tooltip />
        <el-table-column prop="currentDuty" label="现任职务" min-width="130" show-overflow-tooltip />
        <el-table-column prop="preferences" label="客户喜好" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createBy" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
            <template v-if="!showDeleted">
              <el-button v-permission="'customer:manage'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-popconfirm
                title="确认删除该客户档案？删除后默认隐藏，可恢复。"
                confirm-button-text="确认删除"
                cancel-button-text="取消"
                @confirm="handleDelete(row)"
              >
                <template #reference>
                  <el-button v-permission="'customer:manage'" link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
            <el-button v-if="showDeleted" v-permission="'customer:manage'" link type="success" size="small" @click="handleRestore(row)">恢复</el-button>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  listCustomers,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  restoreCustomer,
  listRelationTypes
} from '../api/customer'
import CustomerSelectDialog from '../components/CustomerSelectDialog.vue'

const router = useRouter()
const { proxy } = getCurrentInstance()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const showDeleted = ref(false)
const query = reactive({ keyword: '', deleted: 0, page: 1, pageSize: 10 })

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
  const map = { DIRECTED: '单向', BIDIRECTED: '双向', UNDIRECTED: '无向' }
  return `${t.name}（${map[t.direction] || '单向'}）`
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
  loading.value = true
  try {
    const res = await listCustomers({
      keyword: query.keyword || undefined,
      deleted: showDeleted.value ? 1 : 0,
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
  query.page = 1
  loadList()
}

function openCreate() {
  Object.assign(form, emptyForm)
  relationRows.value = []
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm, {
    id: row.id,
    name: row.name,
    workUnit: row.workUnit,
    workPosition: row.workPosition,
    workLevel: row.workLevel,
    currentDuty: row.currentDuty,
    preferences: row.preferences,
    remark: row.remark
  })
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
.toolbar {
  display: flex;
  align-items: center;
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
  color: #909399;
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
