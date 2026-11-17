<template>
  <section class="estimate">
    <header class="estimate-header">
      <div>
        <h3>估算参数</h3>
        <p>按已填写的参数预览可估算的费用，再选取要应用到预算表的费用行。</p>
      </div>
    </header>
    <el-form class="estimate-fields" label-position="top">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <el-input-number v-model="input[field.key]" :min="field.min" :max="9999"
                         :precision="0" :controls="false"/>
      </el-form-item>
      <el-form-item label="城市档位">
        <el-select v-model="input.city" placeholder="请选择">
          <el-option label="一线城市" value="FIRST"/>
          <el-option label="其他城市" value="OTHER"/>
        </el-select>
      </el-form-item>
      <el-form-item label="大交通">
        <el-select v-model="input.longDistance">
          <el-option label="手工填写" value="MANUAL"/>
          <el-option label="机票" value="FLIGHT"/>
          <el-option label="高铁" value="RAIL"/>
        </el-select>
      </el-form-item>
      <el-form-item label="地面交通">
        <el-select v-model="input.localTransport">
          <el-option label="不覆盖" value="NONE"/>
          <el-option label="车队专车" value="FLEET"/>
          <el-option label="平台专车" value="PLATFORM"/>
        </el-select>
      </el-form-item>
    </el-form>
    <div class="estimate-footer">
      <p>外地参会人数用于估算住宿和大交通。没有外地参会人员时填 0，人数待定时可留空。</p>
      <el-button type="primary" @click="preview">预览估算</el-button>
    </div>
    <el-alert v-if="legacyMaterialPrice" class="estimate-notice" type="warning" :closable="false"
              title="请核对已有物料单价。估算只建议数量和单位，原单价可手动修改。"/>
    <el-dialog v-model="visible" title="估算变更预览" width="min(820px, 95vw)">
      <p class="estimate-dialog-copy">{{ modelValue.applied ? '勾选要更新的费用行。' : '首次估算只补空白，保留已有值。' }}</p>
      <el-empty v-if="!changes.length" description="没有可预填的变更" :image-size="56"/>
      <el-checkbox-group v-else v-model="selected" class="estimate-changes">
        <div v-for="change in changes" :key="change.key" class="estimate-change">
          <el-checkbox :value="change.key">{{ change.name }}</el-checkbox>
          <strong class="estimate-delta">{{ describe(change) }}</strong>
          <span class="estimate-basis">{{ change.basis }}</span>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="apply">{{ selected.length ? '应用选中项' : '保留估算参数' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { estimateChanges, estimateDefaults, quantityRules } from './budgetEstimate'

const props = defineProps({
  modelValue: { type: Object, required: true },
  items: { type: Array, required: true },
  standards: { type: Object, required: true },
  persisted: Boolean
})
const emit = defineEmits(['update:modelValue'])
const fields = [
  { key: 'attendees', label: '参会总人数', min: 1 },
  { key: 'travelers', label: '外地参会人数', min: 0 },
  { key: 'sessions', label: '场次', min: 1 },
  { key: 'staff', label: '工作人员数', min: 0 },
  { key: 'days', label: '天数', min: 1 }
]
const input = ref({ ...estimateDefaults })
const visible = ref(false)
const changes = ref([])
const selected = ref([])
const legacyMaterialPrice = computed(() => props.persisted && props.items.some(
  row => quantityRules[row.itemKey] && Number(row.unitPrice) > 0))
watch(() => props.modelValue, value => { input.value = { ...estimateDefaults, ...value.inputs } }, { immediate: true })

function preview() {
  const invalid = fields.find(({ key, min }) => input.value[key] != null
    && (!Number.isInteger(input.value[key]) || input.value[key] < min))
  if (invalid) {
    ElMessage.warning(`请为「${invalid.label}」填写不小于 ${invalid.min} 的整数`)
    return
  }
  changes.value = estimateChanges(props.items, input.value, props.standards, !props.modelValue.applied, props.modelValue.inputs)
  selected.value = props.modelValue.applied ? [] : changes.value.map(row => row.key)
  visible.value = true
}

function describe(change) {
  const labels = { quantity: '数量', unitPrice: '单价', unit: '单位' }
  return Object.entries(change.changes).map(([field, next]) =>
    `${labels[field]} ${props.items[change.index][field] ?? '空白'} → ${next ?? '空白'}`).join('，')
}

function apply() {
  const basis = { ...props.modelValue.basis }
  for (const change of changes.value.filter(row => selected.value.includes(row.key))) {
    Object.assign(props.items[change.index], change.changes, { itemKey: change.key })
    basis[change.key] = change.basis
  }
  emit('update:modelValue', { inputs: { ...input.value }, basis, applied: true })
  visible.value = false
}
</script>

<style scoped>
.estimate {
  container-type: inline-size;
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid var(--ui-border-weak);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-fill);
}
.estimate-header h3 { margin: 0 0 4px; font-size: 16px; }
.estimate-header p, .estimate-footer p, .estimate-dialog-copy {
  margin: 0;
  color: var(--ui-text-sub);
  font-size: 13px;
  line-height: 1.6;
}
.estimate-fields {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.estimate-fields :deep(.el-form-item) { min-width: 0; margin: 0; }
.estimate-fields :deep(.el-form-item__label) { margin-bottom: 4px; font-weight: 600; }
.estimate-fields :deep(.el-input-number), .estimate-fields :deep(.el-select) { width: 100%; }
.estimate-footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 16px; }
.estimate-footer p { flex: 1 1 320px; }
.estimate-notice { margin-top: 12px; }
.estimate-dialog-copy { margin-bottom: 12px; }
.estimate-changes { display: grid; gap: 8px; max-height: 50vh; overflow-y: auto; }
.estimate-change {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--ui-border-weak);
  border-radius: var(--ui-radius-md);
  font-size: 14px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.estimate-change :deep(.el-checkbox) { height: auto; min-height: 24px; margin: 0; white-space: normal; }
.estimate-delta { color: var(--ui-text-main); font-size: 13px; }
.estimate-basis { color: var(--ui-text-sub); font-size: 12px; }
@container (max-width: 610px) {
  .estimate-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@container (max-width: 285px) {
  .estimate-fields { grid-template-columns: minmax(0, 1fr); }
}
</style>
