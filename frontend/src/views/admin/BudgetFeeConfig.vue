<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">项目配置 · 收费标准</span>
      <span class="hint-text">会议预算默认收费标准；区间值填「低,高」（如 300,400），预算单预填取低值；停用后新预算单不再预填该项</span>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom: 10px"/>
    <el-table v-loading="loading" :data="rows" size="default" :border="true">
      <el-table-column prop="feeKey" label="键" width="180">
        <template #default="{ row }">
          <el-input v-if="row._new" v-model="row.feeKey" placeholder="大写字母/数字/下划线" size="small"/>
          <span v-else>{{ row.feeKey }}</span>
        </template>
      </el-table-column>
      <el-table-column label="名称" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.name" placeholder="收费项名称" size="small"/>
        </template>
      </el-table-column>
      <el-table-column label="默认值" width="160">
        <template #default="{ row }">
          <el-input v-model="row.defaultValue" placeholder="500 或 300,400，留空=视需求报价" size="small"/>
        </template>
      </el-table-column>
      <el-table-column label="单位" width="120">
        <template #default="{ row }">
          <el-input v-model="row.unit" placeholder="元/场 等" size="small"/>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="200">
        <template #default="{ row }">
          <el-input v-model="row.remark" placeholder="备注" size="small"/>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch v-model="row.isActive" :active-value="1" :inactive-value="0"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button v-if="rows[$index]._new" link type="danger" size="small" @click="rows.splice($index, 1)">移除</el-button>
          <span v-else class="hint-text">内置项</span>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 12px; display: flex; gap: 8px">
      <el-button @click="addRow">+ 新增收费项</el-button>
      <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存修改</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listBudgetFeeStandards, saveBudgetFeeStandards } from '../../api/project'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rows = ref([])
const original = ref('')

const dirty = computed(() => JSON.stringify(stripped(rows.value)) !== original.value)

function stripped(list) {
  return list.map((r) => ({
    feeKey: (r.feeKey || '').trim(),
    name: (r.name || '').trim(),
    defaultValue: (r.defaultValue || '').trim() || null,
    unit: (r.unit || '').trim() || null,
    remark: (r.remark || '').trim() || null,
    isActive: r.isActive
  }))
}

function addRow() {
  rows.value.push({ feeKey: '', name: '', defaultValue: '', unit: '', remark: '', isActive: 1, _new: true })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listBudgetFeeStandards()
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
  if (list.some((r) => !r.feeKey || !r.name)) {
    ElMessage.warning('新增/修改项的键与名称不能为空')
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
</script>

<style scoped>
.page-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 700; }
.hint-text { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
