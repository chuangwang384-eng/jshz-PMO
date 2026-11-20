<template>
  <div class="budget-panel">
    <div v-if="loading" class="hint-text" style="padding: 12px 0">加载预算单…</div>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 10px"/>
      <el-alert v-if="isOnline" type="info" :closable="false" style="margin-bottom: 10px"
                title="线上项目 · 点选收费项生成报价单（收费标准由管理员在「管理员设置-项目配置」维护）"/>
      <div v-if="isOnline" style="margin: -4px 0 10px">
        <el-checkbox v-model="needsMainVisual">需要主视觉设计</el-checkbox>
        <span class="hint-text" style="margin-left: 8px">取消勾选后「设计」类费用项将被取消点选且不可勾选</span>
      </div>
      <el-alert v-else type="info" :closable="false" style="margin-bottom: 10px"
                title="线下项目 · 七类费用明细（搭建/媒体为非必需，礼品需与客户确认；收费标准可在「管理员设置-项目配置」维护）"/>

      <ProjectBudgetEstimate v-if="!isOnline && budget && !editingLocked" v-model="estimateState"
                             :items="items" :standards="feeStandards" :persisted="!!budget.id"/>
      <div v-for="sec in activeSections" :key="sec.key" class="budget-section">
        <div class="budget-sec-head">
          <span class="budget-sec-title">{{ isOnline ? '点选收费项（勾选即计入报价）' : sec.label }}</span>
          <span v-if="sec.optional" class="hint-text">非必需</span>
          <span class="budget-sec-total">¥ {{ fmt(sectionTotal(sec.key)) }}</span>
          <el-button v-if="!editingLocked" size="small" text type="primary" style="margin-left: auto"
                     @click="openLibrary(sec.key)">+ {{ isOnline ? '自定义费用项' : '从收费标准库添加' }}</el-button>
          <el-button v-if="!editingLocked" size="small" text @click="addRow(sec.key)">+ 空行</el-button>
          <el-button v-if="!editingLocked && !isOnline" size="small" text type="danger"
                     @click="removeSection(sec.key)">移除本节</el-button>
        </div>
        <el-table :data="rowsOf(sec.key)" size="small" stripe empty-text="暂无费用项">
          <el-table-column v-if="isOnline" label="点选" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="Number(row.quantity) > 0" :disabled="editingLocked || !row.itemKey"
                           @update:model-value="togglePick(row, $event)"/>
            </template>
          </el-table-column>
          <el-table-column label="费用项" min-width="200">
            <template #default="{ row }">
              <span v-if="editingLocked || (isOnline && row.itemKey)">{{ row.name }}</span>
              <el-input v-else v-model="row.name" placeholder="费用项名称" size="small"/>
              <div v-if="estimateState.basis?.[row.itemKey]" class="hint-text">{{ estimateState.basis[row.itemKey] }}</div>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <span v-if="editingLocked || (isOnline && row.itemKey && Number(row.quantity) === 0)">{{ row.quantity }}</span>
              <el-input-number v-else v-model="row.quantity" :min="isOnline && row.itemKey ? 1 : 0" :precision="0" :step="1" step-strictly :controls="false" size="small" style="width: 90px"/>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="100">
            <template #default="{ row }">
              <span v-if="editingLocked">{{ row.unit || '-' }}</span>
              <el-input v-else v-model="row.unit" placeholder="场/人/天" size="small"/>
            </template>
          </el-table-column>
          <el-table-column label="单价(元)" width="130">
            <template #default="{ row }">
              <span v-if="editingLocked">{{ fmt(row.unitPrice) }}</span>
              <el-input-number v-else v-model="row.unitPrice" :min="0" :precision="2" :controls="false" size="small" style="width: 110px"/>
            </template>
          </el-table-column>
          <el-table-column label="成本单价(元)" width="120">
            <template #default="{ row }">
              <span v-if="editingLocked">{{ fmt(row.costPrice) }}</span>
              <el-input-number v-else v-model="row.costPrice" :min="0" :precision="2" :controls="false" size="small" style="width: 100px"/>
            </template>
          </el-table-column>
          <el-table-column label="金额(元)" width="110">
            <template #default="{ row }">{{ fmt(amountOf(row)) }}</template>
          </el-table-column>
          <el-table-column label="毛利(元)" width="110">
            <template #default="{ row }">
              <span :class="{ 'profit-under': underMinProfit(row) }">{{ fmt(profitOf(row)) }}</span>
              <div v-if="underMinProfit(row)" class="hint-text" style="color: var(--ui-warning)">低于最低利润 {{ fmt(minProfitOf(row)) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="160">
            <template #default="{ row }">
              <span v-if="editingLocked">{{ row.remark || '-' }}</span>
              <el-input v-else v-model="row.remark" placeholder="备注" size="small"/>
            </template>
          </el-table-column>
          <el-table-column v-if="!editingLocked" label="操作" width="70">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeRow(sec.key, $index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="!editingLocked && !isOnline && hiddenSections.length" class="hint-text" style="margin: 6px 0">
        已移除分节：
        <el-button v-for="h in hiddenSections" :key="h.key" size="small" text type="primary" @click="restoreSection(h.key)">+ {{ h.label }}</el-button>
      </div>

      <div class="budget-remark">
        <div class="budget-sec-title">备注说明</div>
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="1000" placeholder="报价说明/与客户沟通口径等"
                  :disabled="editingLocked"/>
      </div>

      <div class="budget-total-row">
        <div class="budget-total-summary">
          <span class="budget-total-label">报价合计</span>
          <strong class="budget-total">¥ {{ fmt(total) }}</strong>
          <span v-if="budget?.status === 'CONFIRMED'" class="ui-badge ui-badge-success">已确认</span>
          <span class="budget-total-label" style="margin-left: 12px">成本合计 ¥ {{ fmt(costTotal) }}</span>
          <span class="budget-total-label">毛利 ¥ {{ fmt(total - costTotal) }}</span>
        </div>
        <div v-if="!editingLocked" class="budget-total-actions">
          <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
          <el-button type="primary" :loading="saving" :disabled="!hasItems" @click="save(true)">确认报价</el-button>
        </div>
      </div>
      <!-- 汇总链（2026-11-20，历史结算单共性口径：费用合计→服务费6%→不含税→增值税6%→含税） -->
      <div class="budget-summary-chain">
        <span>费用合计 <b>¥ {{ fmt(total) }}</b></span>
        <span>服务费(6%) <b>¥ {{ fmt(serviceFee) }}</b></span>
        <span>不含税总价 <b>¥ {{ fmt(subtotal) }}</b></span>
        <span>增值税(6%) <b>¥ {{ fmt(taxFee) }}</b></span>
        <span>含税总价 <b class="budget-grand">¥ {{ fmt(grandTotal) }}</b></span>
        <el-button size="small" plain :disabled="!budget?.id" :loading="exporting === 'QUOTE'" style="margin-left: auto"
                   @click="doExport('QUOTE')">导出报价单 Excel</el-button>
        <el-button size="small" plain :disabled="!budget?.id" :loading="exporting === 'COST'"
                   @click="doExport('COST')">导出成本单 Excel</el-button>
      </div>
      <!-- 收费标准库点选（2026-11-20 灌入公司报价单/成本单模板全部费用项） -->
      <el-dialog v-model="libraryVisible" title="从收费标准库添加费用项" width="860px" append-to-body>
        <div style="display:flex; gap:8px; margin-bottom:8px">
          <el-select v-model="libraryCatalog" placeholder="全部类目" clearable style="width: 180px">
            <el-option v-for="(label, key) in CATALOGS" :key="key" :label="label" :value="key"/>
          </el-select>
          <el-input v-model="libraryKeyword" placeholder="搜索名称/详细内容" clearable style="width: 260px"/>
        </div>
        <el-table :data="libraryRows" size="small" stripe max-height="420">
          <el-table-column label="类目" width="90"><template #default="{ row }">{{ CATALOGS[row.catalog] || row.catalog || '-' }}</template></el-table-column>
          <el-table-column label="费用项" min-width="200">
            <template #default="{ row }">{{ row.name }}<div v-if="row.detail" class="hint-text">{{ row.detail }}</div></template>
          </el-table-column>
          <el-table-column label="规格" width="110"><template #default="{ row }">{{ row.spec || '-' }}</template></el-table-column>
          <el-table-column label="报价(元)" width="110"><template #default="{ row }">{{ row.defaultValue ?? '-' }}</template></el-table-column>
          <el-table-column label="成本(元)" width="110"><template #default="{ row }">{{ row.costPrice ?? '-' }}</template></el-table-column>
          <el-table-column label="单位" width="100"><template #default="{ row }">{{ row.unit || '-' }}</template></el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="pickFromLibrary(row)">添加</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-dialog>
      <!-- 2026-11-21：导出报价单即对客报价，移除原「对客报价」生成/客户确认/另存草稿区块（ProjectBudgetQuotes） -->
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectBudget, saveProjectBudget, exportBudgetDoc } from '../../../api/project'
import { apiErrorMessage, formatMoney as fmt } from '../../../utils/common'
import ProjectBudgetEstimate from './ProjectBudgetEstimate.vue'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  readonly: { type: Boolean, default: false },
  canRecord: { type: Boolean, default: false },
  documents: { type: Array, default: () => [] }
})
const emit = defineEmits(['saved', 'generated'])

const SECTIONS = [
  { key: 'HOTEL', label: '一、酒店费用', optional: false },
  { key: 'TRAFFIC', label: '二、交通费用', optional: false },
  { key: 'BUILD', label: '三、搭建费（非必需）', optional: true },
  { key: 'MATERIAL', label: '四、物料费', optional: false },
  { key: 'MEDIA', label: '五、媒体费（非必需）', optional: true },
  { key: 'GIFT', label: '六、礼品费', optional: true },
  { key: 'STAFF', label: '七、人员费', optional: false },
  { key: 'ONLINE_QUOTE', label: '报价明细', optional: false }
]

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const budget = ref(null)
const items = ref([])
const remark = ref('')
const removedSections = ref([])
const feeStandards = ref({})
const estimateState = ref({})
// 2026-11-21：允许编辑已保存（含已确认）的费用明细——去掉 CONFIRMED 锁定，
// 仅 readonly（无操作权限）或加载失败时锁定。确认状态仍由「确认报价」按钮维护（阶段推进门禁需要）。
const editingLocked = computed(() => props.readonly || !!error.value)

const isOnline = computed(() => budget.value?.category === 'ONLINE')

const rowSection = (row) => (isOnline.value ? 'ONLINE_QUOTE' : row.section)
const rowsOf = (key) => items.value.filter((r) => rowSection(r) === key)
const activeSections = computed(() => {
  const list = isOnline.value ? SECTIONS.filter((s) => s.key === 'ONLINE_QUOTE') : SECTIONS.filter((s) => s.key !== 'ONLINE_QUOTE')
  return list.filter((s) => !removedSections.value.includes(s.key))
})
const hiddenSections = computed(() =>
  SECTIONS.filter((s) => s.optional && removedSections.value.includes(s.key)))

const hasItems = computed(() => items.value.some((r) => (r.name || '').trim()))
// 线上点选：勾选=数量 1（或恢复 1），取消=数量 0（不计入合计）
function togglePick(row, checked) {
  // A16/D3：未勾选「需要主视觉设计」时禁止点选设计类费用项
  if (checked && !needsMainVisual.value && isDesignRow(row)) {
    row.quantity = 0
    return
  }
  row.quantity = checked ? 1 : 0
  if (checked && Number(row.unitPrice || 0) === 0) {
    row.unitPrice = null // 提示管理员未配默认单价，用户可手填
  }
}
const isDesignRow = (row) => /设计|主视觉/.test(row.name || '')
const needsMainVisual = computed({
  get: () => estimateState.value.needsMainVisual !== false,
  set: (v) => {
    estimateState.value.needsMainVisual = v
    if (!v) items.value.filter(isDesignRow).forEach((row) => { row.quantity = 0 })
  }
})
const amountOf = (row) => Number(row.quantity || 0) * Number(row.unitPrice || 0)
const sectionTotal = (key) => rowsOf(key).reduce((s, r) => s + amountOf(r), 0)
const total = computed(() => items.value.reduce((s, r) => s + amountOf(r), 0))
// 成本单维度（2026-11-20）：成本合计、毛利、最低利润提示
const costTotal = computed(() => items.value.reduce((s, r) => s + Number(r.quantity || 0) * Number(r.costPrice || 0), 0))
const profitOf = (row) => Number(row.quantity || 0) * (Number(row.unitPrice || 0) - Number(row.costPrice || 0))
const minProfitOf = (row) => {
  if (!row.itemKey) return 0
  const std = (feeStandards.value[row.itemKey])
  return Number(std?.minProfit || 0)
}
const underMinProfit = (row) => {
  const min = minProfitOf(row)
  return min > 0 && Number(row.quantity || 0) > 0 && profitOf(row) < min
}
// 汇总链（历史结算单共性口径，费率固定 6%/6%，与后端 project_budget.service_rate/tax_rate 一致）
const serviceFee = computed(() => Math.round(total.value * 0.06 * 100) / 100)
const subtotal = computed(() => total.value + serviceFee.value)
const taxFee = computed(() => Math.round(subtotal.value * 0.06 * 100) / 100)
const grandTotal = computed(() => subtotal.value + taxFee.value)

// 导出报价单/成本单
const exporting = ref('')
async function doExport(docType) {
  exporting.value = docType
  try {
    const resp = await exportBudgetDoc(props.projectId, docType)
    const url = URL.createObjectURL(resp.data)
    const a = document.createElement('a')
    a.href = url
    a.download = (docType === 'COST' ? '会议成本单' : '会议报价单') + '.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导出失败'))
  } finally {
    exporting.value = ''
  }
}

// 收费标准库点选（模板费用项 171 条：类目/名称/详细内容/规格/报价/成本/最低利润）
const CATALOGS = { ELEC: '电子物料', PRINT: '小型制作物料', ONLINE: '线上会议及直播', BUILD: '大型搭建物料', STAFF: '人员服务', SPECIAL: '非常规物料' }
const libraryVisible = ref(false)
const libraryCatalog = ref('')
const libraryKeyword = ref('')
const librarySection = ref(null)
const libraryRows = computed(() => {
  const kw = libraryKeyword.value.trim()
  return Object.values(feeStandards.value || {}).filter((s) => {
    if (libraryCatalog.value && s.catalog !== libraryCatalog.value) return false
    if (kw && !(String(s.name || '') + String(s.detail || '')).includes(kw)) return false
    return true
  }).slice(0, 200)
})
function openLibrary(sectionKey) {
  librarySection.value = sectionKey
  libraryVisible.value = true
}
function pickFromLibrary(std) {
  const value = String(std.defaultValue || '')
  const low = value.includes(',') ? value.split(',')[0] : value
  items.value.push({
    section: librarySection.value || (isOnline.value ? 'ONLINE_QUOTE' : 'MATERIAL'),
    itemKey: std.feeKey,
    name: std.name || '',
    quantity: 1,
    unit: std.unit || '',
    unitPrice: Number(low) || 0,
    costPrice: Number(std.costPrice) || 0,
    remark: std.detail || null
  })
  libraryVisible.value = false
}

function addRow(section) {
  items.value.push({ section, itemKey: null, name: '', quantity: 1, unit: '', unitPrice: 0, costPrice: 0, remark: '' })
}
function removeRow(section, idx) {
  const list = rowsOf(section)
  const target = list[idx]
  const i = items.value.indexOf(target)
  if (i >= 0) items.value.splice(i, 1)
}
function removeSection(key) {
  items.value = items.value.filter((r) => rowSection(r) !== key)
  if (!removedSections.value.includes(key)) removedSections.value.push(key)
}
function restoreSection(key) {
  removedSections.value = removedSections.value.filter((k) => k !== key)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getProjectBudget(props.projectId)
    feeStandards.value = data?.feeStandards || {}
    applySaved(data)
  } catch (e) {
    error.value = apiErrorMessage(e, '预算单加载失败')
  } finally {
    loading.value = false
  }
}

function applySaved(data) {
  budget.value = data?.budget || null
  const raw = data?.items || []
  items.value = raw.map(row => {
    if (budget.value?.id || isOnline.value || Number(row.quantity) !== 0) return { ...row }
    return { ...row, quantity: null, unitPrice: Number(row.unitPrice) === 0 ? null : row.unitPrice }
  })
  remark.value = budget.value?.remark || ''
  removedSections.value = SECTIONS.filter(s => s.optional && !raw.some(row => row.section === s.key)).map(s => s.key)
  estimateState.value = JSON.parse(budget.value?.estimateInputs || '{}')
}

async function save(confirm) {
  const payload = {
    items: items.value.map((r) => ({
      section: rowSection(r),
      itemKey: r.itemKey || null,
      name: (r.name || '').trim(),
      quantity: Number(r.quantity || 0),
      unit: r.unit || null,
      unitPrice: Number(r.unitPrice || 0),
      costPrice: Number(r.costPrice || 0),
      remark: r.remark || null
    })),
    remark: remark.value || null,
    estimateInputs: estimateState.value && Object.keys(estimateState.value).length ? estimateState.value : null,
    confirm
  }
  if (!payload.items.some((r) => r.name)) {
    ElMessage.warning(confirm ? '请至少填写一条费用项再确认报价' : '当前没有可保存的费用项')
    return
  }
  saving.value = true
  try {
    const data = (await saveProjectBudget(props.projectId, payload)) || {}
    budget.value = data.budget || budget.value
    items.value = (data.items || payload.items).map((r) => ({ ...r }))
    remark.value = budget.value?.remark ?? remark.value
    ElMessage.success(confirm ? '报价单已确认' : '草稿已保存')
    emit('saved', budget.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
watch(() => props.projectId, load)
</script>

<style scoped>
.budget-panel { width: 100%; }
.budget-section { min-width: 0; margin-bottom: 16px; border: 1px solid var(--ui-border-weak); border-radius: var(--ui-radius-lg); overflow: hidden; }
.budget-sec-head { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; padding: 10px 12px; background: var(--ui-fill); }
.budget-sec-title { color: var(--ui-text-main); font-weight: 600; font-size: 14px; }
.budget-sec-total { color: var(--el-color-primary-dark-2); font-weight: 600; font-variant-numeric: tabular-nums; }
.budget-remark { display: grid; gap: 6px; }
.budget-total-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; margin-top: 12px; background: var(--ui-fill); border-radius: var(--ui-radius-lg); }
.budget-total-summary, .budget-total-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.budget-total-actions :deep(.el-button + .el-button) { margin-left: 0; }
.budget-total-label { color: var(--ui-text-sub); font-size: 13px; }
.budget-total { color: var(--ui-text-main); font-size: 22px; line-height: 1.2; font-variant-numeric: tabular-nums; }
.hint-text { color: var(--el-text-color-secondary); font-size: 12px; }
/* 汇总链与利润提示（2026-11-20） */
.budget-summary-chain { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 18px; padding: 10px 12px; margin-top: 10px; border: 1px dashed var(--ui-border-weak); border-radius: var(--ui-radius-lg); color: var(--ui-text-sub); font-size: 13px; }
.budget-summary-chain b { color: var(--ui-text-main); font-variant-numeric: tabular-nums; }
.budget-summary-chain .budget-grand { color: var(--ui-primary); font-size: 15px; }
.profit-under { color: var(--ui-warning); font-weight: 600; }
</style>
