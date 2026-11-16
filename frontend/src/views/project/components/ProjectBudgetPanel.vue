<template>
  <div class="budget-panel">
    <div v-if="loading" class="hint-text" style="padding: 12px 0">加载预算单…</div>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 10px"/>
      <el-alert v-if="isOnline" type="info" :closable="false" style="margin-bottom: 10px"
                title="线上项目 · 简化报价单（模板点选形式待定，当前为手动报价单）"/>
      <el-alert v-else type="info" :closable="false" style="margin-bottom: 10px"
                title="线下项目 · 六类费用明细（搭建/媒体为非必需，礼品需与客户确认；收费标准默认值可在「管理员设置-项目配置」维护）"/>

      <div v-for="sec in activeSections" :key="sec.key" class="budget-section">
        <div class="budget-sec-head">
          <span class="budget-sec-title">{{ sec.label }}</span>
          <span v-if="sec.optional" class="hint-text">非必需</span>
          <span class="budget-sec-total">{{ fmt(sectionTotal(sec.key)) }}</span>
          <el-button v-if="!readonly && !isOnline" size="small" text type="primary" style="margin-left: auto"
                     @click="addRow(sec.key)">+ 添加费用项</el-button>
          <el-button v-if="!readonly && !isOnline" size="small" text type="danger"
                     @click="removeSection(sec.key)">移除本节</el-button>
        </div>
        <el-table :data="rowsOf(sec.key)" size="small" :border="true">
          <el-table-column label="费用项" min-width="200">
            <template #default="{ row }">
              <span v-if="readonly">{{ row.name }}</span>
              <el-input v-else v-model="row.name" placeholder="费用项名称" size="small"/>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <span v-if="readonly">{{ row.quantity }}</span>
              <el-input-number v-else v-model="row.quantity" :min="0" :precision="0" :step="1" step-strictly :controls="false" size="small" style="width: 90px"/>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="100">
            <template #default="{ row }">
              <span v-if="readonly">{{ row.unit || '-' }}</span>
              <el-input v-else v-model="row.unit" placeholder="场/人/天" size="small"/>
            </template>
          </el-table-column>
          <el-table-column label="单价(元)" width="130">
            <template #default="{ row }">
              <span v-if="readonly">{{ fmt(row.unitPrice) }}</span>
              <el-input-number v-else v-model="row.unitPrice" :min="0" :precision="2" :controls="false" size="small" style="width: 110px"/>
            </template>
          </el-table-column>
          <el-table-column label="金额(元)" width="110">
            <template #default="{ row }">{{ fmt(amountOf(row)) }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="160">
            <template #default="{ row }">
              <span v-if="readonly">{{ row.remark || '-' }}</span>
              <el-input v-else v-model="row.remark" placeholder="备注" size="small"/>
            </template>
          </el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="70">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeRow(sec.key, $index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="!isOnline && hiddenSections.length" class="hint-text" style="margin: 6px 0">
        已移除分节：
        <el-button v-for="h in hiddenSections" :key="h.key" size="small" text type="primary" @click="restoreSection(h.key)">+ {{ h.label }}</el-button>
      </div>

      <div class="budget-remark" style="margin-top: 10px">
        <div class="budget-sec-title" style="margin-bottom: 6px">备注说明</div>
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="1000" placeholder="报价说明/与客户沟通口径等"
                  :disabled="readonly"/>
      </div>

      <div class="budget-total-row">
        <span>合计：</span>
        <span class="budget-total">{{ fmt(total) }}</span>
        <span v-if="budget && budget.status === 'CONFIRMED'" class="ui-badge ui-badge-success" style="margin-left: 8px">已确认</span>
        <template v-if="!readonly">
          <el-button :loading="saving" style="margin-left: auto" @click="save(false)">保存草稿</el-button>
          <el-button type="primary" :loading="saving" :disabled="!hasItems" @click="save(true)">确认报价</el-button>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectBudget, saveProjectBudget } from '../../../api/project'

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
    budget.value = data?.budget || null
    const raw = data?.items || []
    items.value = raw.map((r) => ({ ...r }))
    remark.value = budget.value?.remark || ''
    removedSections.value = SECTIONS.filter((s) => s.optional && !raw.some((r) => r.section === s.key)).map((s) => s.key)
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '预算单加载失败'
  } finally {
    loading.value = false
  }
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
.budget-section { margin-bottom: 14px; }
.budget-sec-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.budget-sec-title { font-weight: 600; font-size: 14px; }
.budget-sec-total { color: var(--el-color-primary); font-weight: 600; }
.budget-total-row { display: flex; align-items: center; padding: 12px; border-top: 1px solid var(--el-border-color); font-size: 15px; }
.budget-total { font-size: 20px; font-weight: 700; color: var(--el-color-danger); }
.hint-text { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
