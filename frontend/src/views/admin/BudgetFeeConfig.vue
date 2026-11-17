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
      <el-table v-loading="loading" :data="rows" row-key="feeKey" size="default" stripe empty-text="暂无收费项">
        <el-table-column label="收费项名称" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="收费项名称" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="用途与默认值" min-width="174">
          <template #default="{ row }">
            <div class="fee-default">
              <span class="ui-badge" :class="!isOnline && quantityRules[row.feeKey] ? 'ui-badge-primary' : 'ui-badge-info'">
                {{ !isOnline && quantityRules[row.feeKey] ? quantityRules[row.feeKey].label : '参考单价' }}
              </span>
              <el-input v-model="row.defaultValue" :placeholder="isOnline ? '如 8000' : '如 500 或 300,400'" size="small"/>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="110">
          <template #default="{ row }">
            <span v-if="!isOnline && quantityRules[row.feeKey]">{{ quantityRules[row.feeKey].unit }}</span>
            <span v-else-if="!isOnline && row.feeKey === 'STAFF_DAY'">元/人天</span>
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
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button v-if="rows[$index]._new" link type="danger" size="small" @click="rows.splice($index, 1)">移除</el-button>
            <span v-else class="hint-text">内置</span>
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
import { listBudgetFeeStandards, saveBudgetFeeStandards } from '../../api/project'
import { quantityRules } from '../project/components/budgetEstimate'

const scene = ref('OFFLINE')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rows = ref([])
const original = ref('')

const isOnline = computed(() => scene.value === 'ONLINE')
const dirty = computed(() => JSON.stringify(stripped(rows.value)) !== original.value)

function stripped(list) {
  return list.map((r) => ({
    feeKey: (r.feeKey || '').trim(),
    name: (r.name || '').trim(),
    scene: scene.value,
    defaultValue: (r.defaultValue || '').trim() || null,
    unit: (r.unit || '').trim() || null,
    remark: (r.remark || '').trim() || null,
    isActive: r.isActive
  }))
}

function addRow() {
  // 键不展示，自动生成（保存时按 feeKey 幂等 upsert）
  const key = (isOnline.value ? 'OL_' : 'OFF_') + Date.now().toString(36).toUpperCase() + Math.floor(Math.random() * 90 + 10)
  rows.value.push({ feeKey: key, name: '', defaultValue: isOnline.value ? '' : '500', unit: '', remark: '', isActive: 1, _new: true })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listBudgetFeeStandards(scene.value)
    rows.value = (Array.isArray(data) ? data : []).map((r) => ({ ...r }))
    original.value = JSON.stringify(stripped(rows.value))
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '收费标准加载失败'
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
  if (isOnline.value && list.some((r) => !r.defaultValue)) {
    ElMessage.warning('线上收费项请填写默认单价（点选生成报价单依赖它）')
    return
  }
  saving.value = true
  try {
    const data = await saveBudgetFeeStandards(list)
    rows.value = (Array.isArray(data) ? data : []).map((r) => ({ ...r }))
    original.value = JSON.stringify(stripped(rows.value))
    ElMessage.success('收费标准已保存')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
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
