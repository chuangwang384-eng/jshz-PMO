<template>
  <section v-loading="loading" class="cost-sheet" :aria-label="`${title}填写`">
    <!-- 2026-11-25 负责人要求：已提交后自动收起填写详情，只保留结论与「展开修改」入口 -->
    <div class="cs-status">
      <el-tag size="small" :type="submitted ? 'success' : 'warning'">{{ submitted ? '已提交' : '待填写' }}</el-tag>
      <span class="ui-desc">
        <template v-if="submitted && collapsed">已生成 Excel（最近合计：应付 ¥{{ money(totalPayable) }} · 结算 ¥{{ money(totalSettle) }} · 退款 ¥{{ money(totalRefund) }}）</template>
        <template v-else>按《项目成本单模板》填写，点击「生成 Excel」后由系统自动生成文件并登记为「{{ title }}」资料。</template>
      </span>
      <el-button v-if="submitted" link type="primary" size="small" @click="toggle">
        {{ collapsed ? '展开修改' : '收起详情' }}
      </el-button>
    </div>
    <div v-show="!collapsed" class="cs-body">
      <p class="ui-desc">
        应付金额 = 单价 × 数量，退款金额 = 结算金额 − 应付金额，合计自动计算。
      </p>
      <div v-if="form" class="cs-head">
        <label v-for="field in headFields" :key="field.key">
          <span>{{ field.label }}</span>
          <input v-model="form[field.key]" :disabled="readonly" maxlength="200" :aria-label="field.label" />
        </label>
      </div>
      <div v-for="section in sections" :key="section.key" class="cs-section">
        <h5>{{ section.label }}</h5>
        <div class="cs-scroll">
          <table class="cs-table">
            <thead>
              <tr>
                <th>项目</th><th>明细</th><th>单价</th><th>单位</th><th>数量</th>
                <th>应付金额</th><th>结算金额</th><th>退款金额</th>
                <th>付款方式</th><th>供应商</th><th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in section.items" :key="item.key">
                <td>{{ item.name }}</td>
                <td><input v-model="item.detail" :disabled="readonly" maxlength="200" :aria-label="`${item.name}明细`" /></td>
                <td><input v-model.number="item.price" type="number" min="0" step="0.01" :disabled="readonly" :aria-label="`${item.name}单价`" /></td>
                <td>{{ item.unit }}</td>
                <td><input v-model.number="item.qty" type="number" min="0" step="0.01" :disabled="readonly" :aria-label="`${item.name}数量`" /></td>
                <td class="num">{{ money(payable(item)) }}</td>
                <td><input v-model.number="item.settle" type="number" min="0" step="0.01" :disabled="readonly" :aria-label="`${item.name}结算金额`" /></td>
                <td class="num">{{ money(refund(item)) }}</td>
                <td><input v-model="item.payMethod" :disabled="readonly" maxlength="200" :aria-label="`${item.name}付款方式`" /></td>
                <td><input v-model="item.supplier" :disabled="readonly" maxlength="200" :aria-label="`${item.name}供应商`" /></td>
                <td><input v-model="item.remark" :disabled="readonly" maxlength="200" :aria-label="`${item.name}备注`" /></td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td colspan="5">此类目费用合计：</td>
                <td class="num">{{ money(sectionSum(section, 'payable')) }}</td>
                <td class="num">{{ money(sectionSum(section, 'settle')) }}</td>
                <td class="num">{{ money(sectionSum(section, 'refund')) }}</td>
                <td colspan="3"></td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
      <p v-if="sections.length" class="cs-total">
        本项目成本费用共计：应付 ¥{{ money(totalPayable) }} · 结算 ¥{{ money(totalSettle) }} · 退款 ¥{{ money(totalRefund) }}
      </p>
      <div class="cs-actions">
        <el-button type="primary" size="small" :loading="saving" :disabled="readonly || loading" @click="submit">
          生成 Excel
        </el-button>
        <span class="ui-desc">生成后可在上方批次中预览、下载；再次生成会保留历史批次。</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getCostSheetForm, saveCostSheetForm } from '../../../api/project'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  phaseId: { type: [String, Number], required: true },
  title: { type: String, default: '成本单' },
  readonly: { type: Boolean, default: false },
  // 结算资料细项是否已提交（item.complete）：已提交时默认收起填写详情
  submitted: { type: Boolean, default: false }
})
const emit = defineEmits(['saved'])

const expanded = ref(false)
// 只有「已提交」才收起；未提交时必须保持展开，避免用户看不到待填内容
const collapsed = computed(() => props.submitted && !expanded.value)

function toggle() {
  expanded.value = !expanded.value
}

// 首次加载完成若为已提交状态，保持收起；从已提交变为未提交（例如删除批次）时自动展开
watch(() => props.submitted, (value) => {
  if (!value) expanded.value = false
})

const headFields = [
  { key: 'projectName', label: '项目名称' },
  { key: 'projectTime', label: '项目时间' },
  { key: 'customerName', label: '客户名称' },
  { key: 'projectLocation', label: '项目地点' },
  { key: 'customerOwner', label: '客户负责人' },
  { key: 'projectOwner', label: '项目负责人' },
  { key: 'projectAssistant', label: '项目协助人' }
]

const loading = ref(false)
const saving = ref(false)
const sections = ref([])
const form = ref(null)

const HEADER_KEYS = headFields.map(field => field.key)

function number(value) {
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? n : 0
}
function payable(item) {
  return number(item.price) * number(item.qty)
}
function refund(item) {
  return number(item.settle) - payable(item)
}
function money(value) {
  return Number(value || 0).toFixed(2)
}
function sectionSum(section, field) {
  return section.items.reduce((sum, item) => {
    if (field === 'payable') return sum + payable(item)
    if (field === 'refund') return sum + refund(item)
    return sum + number(item.settle)
  }, 0)
}
const totalPayable = computed(() => sections.value.reduce((sum, section) => sum + sectionSum(section, 'payable'), 0))
const totalSettle = computed(() => sections.value.reduce((sum, section) => sum + sectionSum(section, 'settle'), 0))
const totalRefund = computed(() => sections.value.reduce((sum, section) => sum + sectionSum(section, 'refund'), 0))

/** 用服务端返回的表头默认值 + 上次保存值还原表单；未保存过的行保持空白。 */
function apply(data) {
  const saved = data?.form || null
  const head = { ...(data?.defaults || {}) }
  if (saved) for (const key of HEADER_KEYS) if (saved[key]) head[key] = saved[key]
  form.value = head

  const savedItems = new Map()
  for (const section of saved?.sections || []) {
    for (const item of section.items || []) savedItems.set(item.key, item)
  }
  sections.value = (data?.sections || []).map(section => ({
    key: section.key,
    label: section.label,
    items: (section.items || []).map(item => {
      const old = savedItems.get(item.key) || {}
      return {
        key: item.key,
        name: item.name,
        detail: old.detail ?? item.detail ?? '',
        unit: item.unit || '',
        price: old.price ?? '',
        qty: old.qty ?? '',
        settle: old.settle ?? '',
        payMethod: old.payMethod ?? '',
        supplier: old.supplier ?? '',
        remark: old.remark ?? ''
      }
    })
  }))
}

async function load() {
  loading.value = true
  try {
    apply(await getCostSheetForm(props.projectId, props.phaseId))
  } catch {
    ElMessage.error('成本单表单加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function body() {
  const payload = {}
  for (const key of HEADER_KEYS) payload[key] = form.value?.[key] || ''
  payload.sections = sections.value.map(section => ({
    key: section.key,
    items: section.items.map(item => ({
      key: item.key,
      detail: item.detail,
      price: item.price,
      qty: item.qty,
      settle: item.settle,
      payMethod: item.payMethod,
      supplier: item.supplier,
      remark: item.remark
    }))
  }))
  return payload
}

async function submit() {
  if (props.readonly || saving.value || !form.value) return
  if (totalPayable.value <= 0 && totalSettle.value <= 0) {
    ElMessage.warning('请至少填写一项单价/数量或结算金额')
    return
  }
  saving.value = true
  try {
    apply(await saveCostSheetForm(props.projectId, props.phaseId, body()))
    expanded.value = false
    ElMessage.success('成本单已生成，表单已收起，可在上方批次中预览下载')
    emit('saved')
  } catch {
    // 错误信息由请求拦截器统一提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cost-sheet { margin-top: 10px; }
.cs-status { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.cs-status .ui-desc { margin: 0; }
.cs-body { border-top: 1px dashed var(--el-border-color-lighter, #ebeef5); padding-top: 6px; }
.cs-head { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 8px; margin-bottom: 10px; }
.cs-head label { display: flex; flex-direction: column; gap: 2px; font-size: 12px; }
.cs-head input { font: inherit; padding: 4px 6px; }
.cs-section { margin-top: 12px; }
.cs-section h5 { margin: 0 0 6px; font-size: 13px; }
.cs-scroll { overflow-x: auto; }
.cs-table { border-collapse: collapse; width: 100%; min-width: 900px; font-size: 12px; }
.cs-table th, .cs-table td { border: 1px solid var(--el-border-color, #dcdfe6); padding: 3px 4px; white-space: nowrap; }
.cs-table th { background: var(--el-fill-color-light, #f5f7fa); font-weight: 600; }
.cs-table input { width: 100%; min-width: 56px; font: inherit; padding: 2px 4px; box-sizing: border-box; }
.cs-table .num { text-align: right; font-variant-numeric: tabular-nums; }
.cs-table tfoot td { background: var(--el-fill-color-lighter, #fafafa); font-weight: 600; }
.cs-total { margin-top: 10px; font-weight: 600; }
.cs-actions { display: flex; align-items: center; gap: 10px; margin-top: 10px; flex-wrap: wrap; }
</style>
