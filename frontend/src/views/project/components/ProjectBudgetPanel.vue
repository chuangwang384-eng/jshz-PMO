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
              <template v-else>
                <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :controls="false" size="small" style="width: 110px"/>
                <!-- P2-12（2026-11-26）：数量已计入但单价为 0 的行加红点提示 -->
                <span v-if="isZeroPrice(row)" class="zero-price-dot" title="数量已计入但单价为 0，保存前会提示确认"/>
              </template>
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
          <span v-if="budget?.status === 'CONFIRMED'" class="ui-badge ui-badge-success">{{ BUDGET_STATUS_TEXT[budget.status] }}</span>
          <span class="budget-total-label" style="margin-left: 12px">成本合计 ¥ {{ fmt(costTotal) }}</span>
          <span class="budget-total-label">毛利 ¥ {{ fmt(total - costTotal) }}</span>
        </div>
        <div v-if="!editingLocked" class="budget-total-actions">
          <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
          <!-- P1-①（2026-11-26）：确认需至少一条数量大于零的行，与后端守卫对齐 -->
          <el-button type="primary" :loading="saving" :disabled="!hasPositiveItems" @click="save(true)">确认报价</el-button>
        </div>
      </div>
      <!-- 汇总链（2026-11-20，历史结算单共性口径：费用合计→服务费→不含税→增值税→含税；
           费率以后端 budget.serviceRate/taxRate 为唯一真源，P2-④ 2026-11-26 收口） -->
      <div class="budget-summary-chain">
        <span>费用合计 <b>¥ {{ fmt(total) }}</b></span>
        <span>服务费({{ ratePercent(serviceRate) }}%) <b>¥ {{ fmt(serviceFee) }}</b></span>
        <span>不含税总价 <b>¥ {{ fmt(subtotal) }}</b></span>
        <span>增值税({{ ratePercent(taxRate) }}%) <b>¥ {{ fmt(taxFee) }}</b></span>
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
        <!-- P3-15（2026-11-26）：无关键词浏览时截断前 200 条，显式提示总数 -->
        <div v-if="libraryTotal > libraryRows.length" class="hint-text" style="margin-bottom: 6px">
          共 {{ libraryTotal }} 条，已显示前 200 条，请用关键词或类目筛选缩小范围
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProjectBudget, saveProjectBudget, exportBudgetDoc } from '../../../api/project'
import { apiErrorMessage, downloadBlob, formatMoney as fmt } from '../../../utils/common'
import { BUDGET_FEE_CATALOGS as CATALOGS, BUDGET_STATUS_TEXT } from '../../../utils/dict'
import ProjectBudgetEstimate from './ProjectBudgetEstimate.vue'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  readonly: { type: Boolean, default: false }
})
const emit = defineEmits(['saved'])

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
// P1-①（2026-11-26）：确认报价需至少一条数量大于零的行，与后端守卫对齐
const hasPositiveItems = computed(() => items.value.some((r) => (r.name || '').trim() && Number(r.quantity) > 0))
// P2-12（2026-11-26）：数量已计入但单价为 0 的行（保存前弹警告确认，行内加红点）
const isZeroPrice = (row) => (row.name || '').trim() && Number(row.quantity) > 0 && !(Number(row.unitPrice) > 0)
const zeroPriceRows = computed(() => items.value.filter(isZeroPrice))
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
// 汇总链（历史结算单共性口径）：费率以后端 project_budget.service_rate/tax_rate 为唯一真源
// （P2-④ 2026-11-26 收口，0.06 仅为未保存草稿时的兜底，与导出 Excel 同源同值）
const serviceRate = computed(() => Number(budget.value?.serviceRate ?? 0.06))
const taxRate = computed(() => Number(budget.value?.taxRate ?? 0.06))
const ratePercent = (r) => `${Math.round(r * 10000) / 100}`
const serviceFee = computed(() => Math.round(total.value * serviceRate.value * 100) / 100)
const subtotal = computed(() => total.value + serviceFee.value)
const taxFee = computed(() => Math.round(subtotal.value * taxRate.value * 100) / 100)
const grandTotal = computed(() => subtotal.value + taxFee.value)

// 导出报价单/成本单
const exporting = ref('')
async function doExport(docType) {
  exporting.value = docType
  try {
    const resp = await exportBudgetDoc(props.projectId, docType)
    // P3-16（2026-11-26）：改用 utils/common downloadBlob 收口版（Safari 兼容 + 统一实现）
    downloadBlob(resp.data, (docType === 'COST' ? '会议成本单' : '会议报价单') + '.xlsx')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导出失败'))
  } finally {
    exporting.value = ''
  }
}

// 收费标准库点选（模板费用项：类目/名称/详细内容/规格/报价/成本/最低利润）
// CATALOGS：2026-11-26 收口到 utils/dict.js BUDGET_FEE_CATALOGS（与管理页共用）
const libraryVisible = ref(false)
const libraryCatalog = ref('')
const libraryKeyword = ref('')
const librarySection = ref(null)
const libraryAll = computed(() => {
  const kw = libraryKeyword.value.trim()
  return Object.values(feeStandards.value || {}).filter((s) => {
    // P2-13（2026-11-26）：防御性排除估算参数键（EST_*），不再作为费用项加入预算
    if (String(s.feeKey || '').startsWith('EST_')) return false
    if (libraryCatalog.value && s.catalog !== libraryCatalog.value) return false
    if (kw && !(String(s.name || '') + String(s.detail || '')).includes(kw)) return false
    return true
  })
})
const libraryTotal = computed(() => libraryAll.value.length)
const libraryRows = computed(() => libraryAll.value.slice(0, 200))
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
async function removeSection(key) {
  // P2-11（2026-11-26）：移除整节前确认，明示影响行数（未保存数据不可恢复）
  const count = rowsOf(key).length
  const label = SECTIONS.find((s) => s.key === key)?.label || key
  try {
    await ElMessageBox.confirm(
      `将移除「${label}」整节${count ? `及其 ${count} 行明细` : ''}，未保存的数据不可恢复，确定移除？`,
      '移除本节', { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' })
  } catch { return }
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
  // 2026-11-21：七大项分节默认全部可见（空大项不再自动隐藏），由用户按需「移除本节」或从收费库添加细项。
  removedSections.value = []
  // P3-14（2026-11-26）：估算参数解析容错，脏数据不再锁死整个面板
  let saved = {}
  try {
    saved = JSON.parse(budget.value?.estimateInputs || '{}')
  } catch (e) {
    ElMessage.warning('估算参数解析失败，已忽略，费用明细不受影响')
    saved = {}
  }
  estimateState.value = saved && typeof saved === 'object' ? saved : {}
}

async function save(confirm) {
  // P3-22（2026-11-26）：提交前过滤与当前项目大类不兼容的分节行，避免项目切类后保存死锁
  const submitItems = items.value
    .filter((r) => isOnline.value || r.section !== 'ONLINE_QUOTE')
    .map((r) => ({
      section: rowSection(r),
      itemKey: r.itemKey || null,
      name: (r.name || '').trim(),
      quantity: Number(r.quantity || 0),
      unit: r.unit || null,
      unitPrice: Number(r.unitPrice || 0),
      costPrice: Number(r.costPrice || 0),
      remark: r.remark || null
    }))
  // P3-24（2026-11-26）：估算参数长度预检（上限 1000，与后端一致）
  const inputsJson = estimateState.value && Object.keys(estimateState.value).length
    ? JSON.stringify(estimateState.value) : null
  if (inputsJson && inputsJson.length > 1000) {
    ElMessage.warning(`估算参数过长（当前 ${inputsJson.length} 字符，上限 1000），请重置估算后重试`)
    return
  }
  if (!submitItems.some((r) => r.name)) {
    ElMessage.warning(confirm ? '请至少填写一条费用项再确认报价' : '当前没有可保存的费用项')
    return
  }
  // P1-①（2026-11-26）：与后端守卫对齐，杜绝空点选确认
  if (confirm && !submitItems.some((r) => r.name && Number(r.quantity) > 0)) {
    ElMessage.warning('请至少勾选/填写一条数量大于零的费用项再确认报价')
    return
  }
  // P2-⑨（2026-11-26）：已确认单保存草稿=退回草稿，需显式确认（后端另写操作日志）
  if (!confirm && budget.value?.status === 'CONFIRMED') {
    try {
      await ElMessageBox.confirm(
        '当前报价单已确认。保存草稿会将其退回草稿状态（已归档的报价文件仍在，但与当前明细脱钩），确定继续？',
        '退回草稿', { type: 'warning', confirmButtonText: '退回草稿并保存', cancelButtonText: '取消' })
    } catch { return }
  }
  // P2-12（2026-11-26）：数量已计入但单价为 0 的行，保存/确认前警示后放行
  if (zeroPriceRows.value.length) {
    const names = zeroPriceRows.value.map((r) => r.name).slice(0, 5).join('、')
      + (zeroPriceRows.value.length > 5 ? ` 等 ${zeroPriceRows.value.length} 项` : '')
    try {
      await ElMessageBox.confirm(
        `以下费用行数量已计入但单价为 0：${names}。这些行将以 0 元进入报价/成本计算，确定继续？`,
        '单价未填写', { type: 'warning', confirmButtonText: '仍要保存', cancelButtonText: '返回修改' })
    } catch { return }
  }
  const payload = {
    items: submitItems,
    remark: remark.value || null,
    estimateInputs: estimateState.value && Object.keys(estimateState.value).length ? estimateState.value : null,
    // P2-10（2026-11-26）：乐观锁版本号，冲突时后端返回 409 提示刷新
    version: budget.value?.version ?? null,
    confirm
  }
  saving.value = true
  try {
    const data = (await saveProjectBudget(props.projectId, payload)) || {}
    budget.value = data.budget || budget.value
    items.value = (data.items || payload.items).map((r) => ({ ...r }))
    remark.value = budget.value?.remark ?? remark.value
    ElMessage.success(confirm ? '报价单已确认' : '草稿已保存')
    // P3-23（2026-11-26）：非预算阶段重确认未归档新报价时后端返回 warn
    if (data.warn) ElMessage.warning(data.warn)
    emit('saved', budget.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

// P3-20（2026-11-26）：保留 ProjectDetail 的 :key 重建机制，删除不可达的 projectId watch
onMounted(load)
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
/* P2-12（2026-11-26）：数量已计入但单价为 0 的行内红点提示 */
.zero-price-dot { display: inline-block; width: 8px; height: 8px; margin-left: 4px; border-radius: 50%; background: var(--ui-danger); vertical-align: middle; }
</style>
