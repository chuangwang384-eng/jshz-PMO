<template>
  <div class="budget-panel">
    <div v-if="loading" class="hint-text" style="padding: 12px 0">加载预算单…</div>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 10px"/>
      <el-alert v-if="isOnline" type="info" :closable="false" style="margin-bottom: 10px"
                title="线上项目 · 点选收费项生成报价单（收费标准由管理员在「管理员设置-项目配置」维护）"/>
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
                     @click="addRow(sec.key)">+ {{ isOnline ? '自定义费用项' : '添加费用项' }}</el-button>
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
          <el-table-column label="金额(元)" width="110">
            <template #default="{ row }">{{ fmt(amountOf(row)) }}</template>
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
          <span class="budget-total-label">预算合计</span>
          <strong class="budget-total">¥ {{ fmt(total) }}</strong>
          <span v-if="budget?.status === 'CONFIRMED'" class="ui-badge ui-badge-success">已确认</span>
        </div>
        <div v-if="!editingLocked" class="budget-total-actions">
          <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
          <el-button type="primary" :loading="saving" :disabled="!hasItems" @click="save(true)">确认报价</el-button>
        </div>
      </div>
      <ProjectBudgetQuotes v-if="budget" :project-id="projectId" :readonly="readonly"
                           :can-record="canRecord" :confirmed="budget.status === 'CONFIRMED'"
                           :documents="documents" @restored="applySaved" @generated="emit('generated')"/>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectBudget, saveProjectBudget } from '../../../api/project'
import ProjectBudgetEstimate from './ProjectBudgetEstimate.vue'
import ProjectBudgetQuotes from './ProjectBudgetQuotes.vue'

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
const editingLocked = computed(() => props.readonly || budget.value?.status === 'CONFIRMED' || !!error.value)

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
  row.quantity = checked ? 1 : 0
  if (checked && Number(row.unitPrice || 0) === 0) {
    row.unitPrice = null // 提示管理员未配默认单价，用户可手填
  }
}
const amountOf = (row) => Number(row.quantity || 0) * Number(row.unitPrice || 0)
const sectionTotal = (key) => rowsOf(key).reduce((s, r) => s + amountOf(r), 0)
const total = computed(() => items.value.reduce((s, r) => s + amountOf(r), 0))
const fmt = (n) => Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function addRow(section) {
  items.value.push({ section, itemKey: null, name: '', quantity: 1, unit: '', unitPrice: 0, remark: '' })
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
    error.value = e?.response?.data?.message || e?.message || '预算单加载失败'
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
      remark: r.remark || null
    })),
    remark: remark.value || null,
    estimateInputs: isOnline.value ? null : estimateState.value,
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
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
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
</style>
