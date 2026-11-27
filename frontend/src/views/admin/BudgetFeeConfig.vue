<template>
  <div class="fee-page">
    <header class="fee-heading">
      <h1>项目配置</h1>
      <p>维护会议预算的收费项、类目与参考单价。</p>
    </header>

    <div class="ui-card fee-card">
      <el-tabs v-model="scene" class="fee-tabs">
        <el-tab-pane name="OFFLINE">
          <template #label><span class="tab-label">线下收费标准</span></template>
          <div class="tab-tip">线下收费项供「会议预算制作」从收费标准库添加。参考单价区间值填「低,高」，预填取低值。类目可选择已有或输入新建。</div>
        </el-tab-pane>
        <el-tab-pane name="ONLINE">
          <template #label><span class="tab-label">线上收费标准</span></template>
          <div class="tab-tip">线上项目在「会议预算制作」阶段可点选这些收费项生成报价单。</div>
        </el-tab-pane>
      </el-tabs>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="fee-error"/>
      <!-- 2026-11-20 收费标准库：171 条模板费用项，支持类目筛选与关键字搜索 -->
      <div style="display:flex; gap:8px; margin-bottom:10px">
        <el-select v-model="catalogFilter" placeholder="全部类目" clearable filterable style="width: 170px">
          <el-option v-for="o in catalogOptions" :key="o.value" :label="o.label" :value="o.value"/>
        </el-select>
        <el-input v-model="keyword" placeholder="搜索名称/详细内容" clearable style="width: 240px"/>
        <span class="hint-text" style="align-self:center">共 {{ filteredRows.length }} 条</span>
      </div>
      <el-table v-loading="loading" :data="pagedRows" row-key="feeKey" size="default" stripe empty-text="暂无收费项">
        <!-- 2026-11-21：类目可编辑，支持选择已有类目或输入新建类目 -->
        <el-table-column label="类目" width="160">
          <template #default="{ row }">
            <el-select v-model="row.catalog" filterable allow-create default-first-option clearable
                       placeholder="类目" size="small" style="width: 140px">
              <el-option v-for="o in catalogOptions" :key="o.value" :label="o.label" :value="o.value"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="收费项名称 / 明细" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="收费项名称" size="small"/>
            <!-- 2026-11-21：收费项明细（详细内容）改为可编辑 -->
            <el-input v-model="row.detail" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }"
                      placeholder="收费项明细（详细内容）" size="small" style="margin-top: 4px"/>
          </template>
        </el-table-column>
        <!-- 2026-11-21：「用途与默认值」改为「参考单价」，去掉用途徽标，直接编辑参考单价 -->
        <el-table-column label="参考单价" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.defaultValue" :placeholder="isOnline ? '如 8000' : '如 500 或 300,400'" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="成本价(元)" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.costPrice" :min="0" :precision="2" :controls="false" size="small" style="width: 100px" placeholder="成本"/>
          </template>
        </el-table-column>
        <el-table-column label="最低利润(元)" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.minProfit" :min="0" :precision="2" :controls="false" size="small" style="width: 100px" placeholder="利润"/>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="110">
          <template #default="{ row }">
            <el-input v-model="row.unit" placeholder="元/场 等" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="备注" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isActive" :active-value="1" :inactive-value="0" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeRow(row)">{{ row._new ? '移除' : '删除' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页（2026-11-21 卡顿修复）：160+ 行全量渲染约 1300 个输入组件导致页面卡死，改为每页 20 行 -->
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="filteredRows.length"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
      />

      <div class="fee-actions">
        <el-button plain @click="openAddDialog">+ 新增收费项</el-button>
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存修改</el-button>
        <span role="status" class="hint-text">{{ dirty ? '有未保存的修改' : '暂无未保存修改' }}</span>
      </div>
    </div>

    <!-- 新增收费项弹窗（2026-11-21 Owner 要求）：表单化录入，确认后置顶插入列表，点「保存修改」入库 -->
    <el-dialog v-model="addVisible" title="新增收费项" width="520px" append-to-body>
      <el-form :model="addForm" label-width="96px">
        <el-form-item label="类目">
          <el-select v-model="addForm.catalog" filterable allow-create default-first-option clearable
                     placeholder="选择已有类目，或输入新类目名称" style="width: 100%">
            <el-option v-for="o in catalogOptions" :key="o.value" :label="o.label" :value="o.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="收费项名称">
          <el-input v-model="addForm.name" placeholder="必填" maxlength="100"/>
        </el-form-item>
        <el-form-item label="收费项明细">
          <el-input v-model="addForm.detail" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }"
                    placeholder="详细内容/规格说明（可空）" maxlength="500"/>
        </el-form-item>
        <el-form-item label="参考单价(元)">
          <el-input v-model="addForm.defaultValue" :placeholder="isOnline ? '如 8000' : '如 500 或 300,400'"/>
        </el-form-item>
        <el-form-item label="成本价(元)">
          <el-input-number v-model="addForm.costPrice" :min="0" :precision="2" :controls="false" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="最低利润(元)">
          <el-input-number v-model="addForm.minProfit" :min="0" :precision="2" :controls="false" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="addForm.unit" placeholder="元/场 等"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" maxlength="200"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAdd">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listBudgetFeeStandards, saveBudgetFeeStandards, deleteBudgetFeeStandard } from '../../api/project'
import { apiErrorMessage } from '../../utils/common'
import { BUDGET_FEE_CATALOGS as CATALOGS } from '../../utils/dict'

const scene = ref('OFFLINE')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rows = ref([])
const original = ref('')

const isOnline = computed(() => scene.value === 'ONLINE')
const dirty = computed(() => JSON.stringify(stripped(rows.value)) !== original.value)

// 收费标准库类目：2026-11-26 收口到 utils/dict.js BUDGET_FEE_CATALOGS（与 ProjectBudgetPanel 共用）
// 2026-11-21：类目选项 = 内置 6 类 + 数据中已出现的自定义类目，支持新建类目（allow-create）
const catalogOptions = computed(() => {
  const opts = Object.entries(CATALOGS).map(([value, label]) => ({ value, label }))
  const known = new Set(Object.keys(CATALOGS))
  for (const r of rows.value) {
    if (r.catalog && !known.has(r.catalog)) {
      known.add(r.catalog)
      opts.push({ value: r.catalog, label: CATALOGS[r.catalog] || r.catalog })
    }
  }
  return opts
})
const catalogFilter = ref('')
const keyword = ref('')
const filteredRows = computed(() => {
  const kw = keyword.value.trim()
  return rows.value.filter((r) => {
    if (catalogFilter.value && r.catalog !== catalogFilter.value) return false
    if (kw && !(String(r.name || '') + String(r.detail || '')).includes(kw)) return false
    return true
  })
})

function stripped(list) {
  return list.map((r) => ({
    feeKey: (r.feeKey || '').trim(),
    name: (r.name || '').trim(),
    scene: r.scene || scene.value,
    catalog: r.catalog || null,
    category: r.category || null,
    detail: r.detail || null,
    spec: r.spec || null,
    defaultValue: (r.defaultValue || '').trim() || null,
    costPrice: r.costPrice == null || r.costPrice === '' ? null : Number(r.costPrice),
    minProfit: r.minProfit == null || r.minProfit === '' ? null : Number(r.minProfit),
    unit: (r.unit || '').trim() || null,
    remark: (r.remark || '').trim() || null,
    isActive: r.isActive
  }))
}

// 分页（2026-11-21 卡顿修复）：160+ 行 × 8 输入组件全量渲染卡死，每页 20 行
const page = ref(1)
const pageSize = 20
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredRows.value.slice(start, start + pageSize)
})
watch([catalogFilter, keyword], () => { page.value = 1 })

// 新增收费项弹窗（2026-11-21 Owner 要求弹窗化）
const addVisible = ref(false)
const addForm = ref({})
function openAddDialog() {
  addForm.value = { catalog: '', name: '', detail: '', defaultValue: isOnline.value ? '' : '500', costPrice: null, minProfit: null, unit: '', remark: '' }
  addVisible.value = true
}
function confirmAdd() {
  const f = addForm.value
  if (!f.name || !f.name.trim()) {
    ElMessage.warning('收费项名称不能为空')
    return
  }
  const key = (isOnline.value ? 'OL_' : 'OFF_') + Date.now().toString(36).toUpperCase() + Math.floor(Math.random() * 90 + 10)
  catalogFilter.value = ''
  keyword.value = ''
  page.value = 1
  rows.value.unshift({
    feeKey: key, name: f.name.trim(), scene: scene.value, catalog: f.catalog || null,
    detail: (f.detail || '').trim() || null,
    defaultValue: (f.defaultValue || '').trim() || null,
    costPrice: f.costPrice == null ? null : Number(f.costPrice),
    minProfit: f.minProfit == null ? null : Number(f.minProfit),
    unit: (f.unit || '').trim() || null, remark: (f.remark || '').trim() || null,
    isActive: 1, _new: true
  })
  addVisible.value = false
  ElMessage.success('已添加到列表顶部，请点击「保存修改」入库')
}

// 删除收费项（2026-11-20）：被预算明细引用时后端拒绝；本地未保存行直接移除。
// 注意 filteredRows 与 rows 下标不同，必须按行对象定位。
async function removeRow(row) {
  if (!row) return
  if (row._new) {
    rows.value = rows.value.filter((r) => r !== row)
    return
  }
  await ElMessageBox.confirm(
    `确认删除收费项「${row.name || row.feeKey}」？删除后不可恢复；已被预算明细引用的收费项无法删除。`,
    '删除收费项', { type: 'warning', confirmButtonText: '确认删除' }
  )
  try {
    await deleteBudgetFeeStandard(row.feeKey)
    ElMessage.success('收费项已删除')
    await load()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '删除失败'))
  }
}

async function load() {
  loading.value = true
  error.value = ''
  page.value = 1
  try {
    const data = await listBudgetFeeStandards(scene.value)
    rows.value = (Array.isArray(data) ? data : []).map((r) => ({ ...r }))
    original.value = JSON.stringify(stripped(rows.value))
  } catch (e) {
    error.value = apiErrorMessage(e, '收费标准加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  const list = stripped(rows.value)
  if (list.some((r) => !r.name)) {
    ElMessage.warning('收费项名称不能为空')
    return
  }
  if (isOnline.value && list.some((r) => !r.defaultValue && !r.catalog)) {
    ElMessage.warning('线上收费项请填写默认单价（点选生成报价单依赖它；模板库项可留空由项目内手填）')
    return
  }
  saving.value = true
  try {
    await saveBudgetFeeStandards(list)
    // 后端 saveFeeStandards 返回全场景（listFeeStandards(null)），直接赋值会把线上项混进线下页签
    // （2026-11-21 修复）：保存成功后重载当前页签，保证场景隔离并重置分页。
    await load()
    ElMessage.success('收费标准已保存')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
watch(scene, load)
</script>

<style scoped>
.fee-page { max-width: 1160px; }
.fee-heading { margin: 4px 0 20px; }
.fee-heading h1 { margin: 0; font-size: 23px; color: var(--ui-text-main); }
.fee-heading p { margin: 6px 0 0; color: var(--ui-text-sub); font-size: 13px; }
.fee-tabs { margin-bottom: 12px; }
.tab-label { font-weight: 600; }
.tab-tip { padding: 10px 12px; border-radius: var(--ui-radius-md); background: var(--ui-fill); color: var(--ui-text-sub); font-size: 13px; line-height: 1.55; }
.fee-card { min-width: 0; padding: clamp(12px, 2vw, 20px); }
.fee-card :deep(.el-table) { --el-table-header-bg-color: var(--ui-fill); }
.fee-error { margin-bottom: 12px; }
.fee-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 16px; }
.fee-actions :deep(.el-button + .el-button) { margin-left: 0; }
.hint-text { color: var(--ui-text-sub); font-size: 12px; }
</style>
