<template>
  <div class="fee-page">
    <header class="fee-heading">
      <h1>项目配置</h1>
      <p>维护会议预算的参考单价和数量规则。</p>
    </header>

    <div class="ui-card fee-card">
      <el-tabs v-model="scene" class="fee-tabs">
        <el-tab-pane name="OFFLINE">
          <template #label><span class="tab-label">线下收费标准</span></template>
          <div class="tab-tip">线下估算按用途读取参考单价、数量增量和每场数量。区间值填「低,高」，估算取低值。</div>
        </el-tab-pane>
        <el-tab-pane name="ONLINE">
          <template #label><span class="tab-label">线上收费标准</span></template>
          <div class="tab-tip">线上项目在「会议预算制作」阶段可点选这些收费项生成报价单。</div>
        </el-tab-pane>
      </el-tabs>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="fee-error"/>
      <!-- 2026-11-20 收费标准库：171 条模板费用项，支持类目筛选与关键字搜索 -->
      <div style="display:flex; gap:8px; margin-bottom:10px">
        <el-select v-model="catalogFilter" placeholder="全部类目" clearable style="width: 170px">
          <el-option v-for="(label, key) in CATALOGS" :key="key" :label="label" :value="key"/>
        </el-select>
        <el-input v-model="keyword" placeholder="搜索名称/详细内容" clearable style="width: 240px"/>
        <span class="hint-text" style="align-self:center">共 {{ filteredRows.length }} 条</span>
      </div>
      <el-table v-loading="loading" :data="filteredRows" row-key="feeKey" size="default" stripe empty-text="暂无收费项">
        <el-table-column label="类目" width="110">
          <template #default="{ row }">{{ CATALOGS[row.catalog] || '-' }}</template>
        </el-table-column>
        <el-table-column label="收费项名称" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="收费项名称" size="small"/>
            <div v-if="row.detail" class="hint-text">{{ row.detail }}</div>
          </template>
        </el-table-column>
        <el-table-column label="用途与默认值" min-width="174">
          <template #default="{ row }">
            <div class="fee-default">
              <span class="ui-badge" :class="(!isOnline && quantityRules[row.feeKey]) || estimateRuleMeta[row.feeKey] ? 'ui-badge-primary' : 'ui-badge-info'">
                {{ !isOnline && quantityRules[row.feeKey] ? quantityRules[row.feeKey].label : estimateRuleMeta[row.feeKey] ? '估算参数' : '参考单价' }}
              </span>
              <el-input v-model="row.defaultValue" :placeholder="isOnline ? '如 8000' : '如 500 或 300,400'" size="small"/>
            </div>
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
            <span v-if="!isOnline && quantityRules[row.feeKey]">{{ quantityRules[row.feeKey].unit }}</span>
            <span v-else-if="!isOnline && row.feeKey === 'STAFF_DAY'">元/人天</span>
            <span v-else-if="estimateRuleMeta[row.feeKey]">{{ estimateRuleMeta[row.feeKey].unit }}</span>
            <el-input v-else v-model="row.unit" placeholder="元/场 等" size="small"/>
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

      <div class="fee-actions">
        <el-button plain @click="addRow">+ 新增收费项</el-button>
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存修改</el-button>
        <span role="status" class="hint-text">{{ dirty ? '有未保存的修改' : '暂无未保存修改' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listBudgetFeeStandards, saveBudgetFeeStandards, deleteBudgetFeeStandard } from '../../api/project'
import { quantityRules, estimateRuleMeta } from '../project/components/budgetEstimate'
import { apiErrorMessage } from '../../utils/common'

const scene = ref('OFFLINE')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rows = ref([])
const original = ref('')

const isOnline = computed(() => scene.value === 'ONLINE')
const dirty = computed(() => JSON.stringify(stripped(rows.value)) !== original.value)

// 收费标准库类目（2026-11-20 灌入公司报价单/成本单模板全部费用项）
const CATALOGS = { ELEC: '电子物料', PRINT: '小型制作物料', ONLINE: '线上会议及直播', BUILD: '大型搭建物料', STAFF: '人员服务', SPECIAL: '非常规物料' }
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

function addRow() {
  // 键不展示，自动生成（保存时按 feeKey 幂等 upsert）。
  // 2026-11-20 修复"新增失效"：库扩到 160+ 条后空行 append 在末尾且被筛选挡住，感知不到——
  // 改为置顶插入并清除类目/关键字筛选，保证新行立即可见。
  const key = (isOnline.value ? 'OL_' : 'OFF_') + Date.now().toString(36).toUpperCase() + Math.floor(Math.random() * 90 + 10)
  catalogFilter.value = ''
  keyword.value = ''
  rows.value.unshift({ feeKey: key, name: '', defaultValue: isOnline.value ? '' : '500', unit: '', remark: '', isActive: 1, _new: true })
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
    const data = await saveBudgetFeeStandards(list)
    rows.value = (Array.isArray(data) ? data : []).map((r) => ({ ...r }))
    original.value = JSON.stringify(stripped(rows.value))
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
.fee-default { display: grid; justify-items: start; gap: 4px; }
.fee-default .ui-badge { line-height: 18px; }
.fee-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 16px; }
.fee-actions :deep(.el-button + .el-button) { margin-left: 0; }
.hint-text { color: var(--ui-text-sub); font-size: 12px; }
</style>
