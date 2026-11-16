<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">项目配置</div>
      <div class="page-sub">会议预算收费标准默认值——线下按七类费用明细预填；线上配置后，线上项目在预算制作时直接点选收费项生成报价单</div>
    </div>

    <el-tabs v-model="scene" class="fee-tabs">
      <el-tab-pane name="OFFLINE">
        <template #label><span class="tab-label">线下收费标准</span></template>
        <div class="tab-tip">线下项目进入「会议预算制作」阶段时，按下方默认值预填费用明细（区间值填「低,高」，预填取低值）</div>
      </el-tab-pane>
      <el-tab-pane name="ONLINE">
        <template #label><span class="tab-label">线上收费标准</span></template>
        <div class="tab-tip">线上项目进入「会议预算制作」阶段时，点选下方收费项即可生成报价单（建议逐项填写默认单价）</div>
      </el-tab-pane>
    </el-tabs>

    <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 10px"/>

    <div class="fee-card">
      <el-table v-loading="loading" :data="rows" size="default" :border="false" :header-cell-style="{ background: '#f5f7fa' }">
        <el-table-column label="收费项名称" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="收费项名称" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="默认值" width="170">
          <template #default="{ row }">
            <el-input v-model="row.defaultValue" :placeholder="isOnline ? '默认单价，如 8000' : '500 或 300,400，留空=视需求报价'" size="small"/>
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
        <span v-if="!dirty" class="hint-text" style="align-self: center">暂无未保存修改</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listBudgetFeeStandards, saveBudgetFeeStandards } from '../../api/project'

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
.page { max-width: 1000px; }
.page-header { margin: 4px 0 16px; }
.page-title { font-size: 20px; font-weight: 700; }
.page-sub { color: var(--el-text-color-secondary); font-size: 13px; margin-top: 6px; }
.fee-tabs { margin-bottom: 4px; }
.tab-label { font-weight: 600; }
.tab-tip { color: var(--el-text-color-secondary); font-size: 13px; padding: 4px 0 12px; }
.fee-card { background: #fff; border: 1px solid var(--el-border-color-light); border-radius: 10px; padding: 16px; }
.fee-actions { display: flex; gap: 8px; margin-top: 14px; }
.hint-text { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
